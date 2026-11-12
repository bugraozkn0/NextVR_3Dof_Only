package com.samrat.cardboardhands

import android.opengl.EGL14
import android.opengl.GLES20 as GL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GL-thread camera preview, independent of inference. Raw EasyAR output is copied into
 * a reused direct buffer; YUV conversion, rotation and crop happen in this shader.
 * Plane layout verified against 4.11 HelloARMotionTracking/BGRenderer, not a custom camera.
 * No bitmap conversion, per-eye uploads, readback or per-frame direct allocations.
 */
internal class CinemaGpuCamera {
    private val context = EGL14.eglGetCurrentContext()
    private val textures = IntArray(3).also { GL.glGenTextures(3,it,0) }
    private val program: Int
    private val positionLocation:Int
    private val uvLocation:Int
    private val modeLocation:Int
    private val samplerLocations:IntArray
    private val depthMask=IntArray(1)
    private val corners=floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)
    private val vertices = ByteBuffer.allocateDirect(20*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var signature = ""
    private var w=0;private var h=0;private var stride=0;private var rows=0
    private var format=0;private var rotation=0;private var mirror=false
    var ready=false;private set
    var uploaded=0L;private set
    var lastArrivalNs=0L;private set
    val aspect get() = if(rotation%180==0) w.toFloat()/h.coerceAtLeast(1) else h.toFloat()/w.coerceAtLeast(1)

    init {
        fun shader(kind:Int,text:String):Int {
            val id=GL.glCreateShader(kind);GL.glShaderSource(id,text);GL.glCompileShader(id)
            val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0) {GL.glGetShaderInfoLog(id)}
            return id
        }
        val vs=shader(GL.GL_VERTEX_SHADER,"attribute vec3 aPosition;attribute vec2 aUv;varying vec2 uv;void main(){gl_Position=vec4(aPosition,1.0);uv=aUv;}")
        val fs=shader(GL.GL_FRAGMENT_SHADER,"""
            precision mediump float;
            varying vec2 uv;
            uniform sampler2D imageY; uniform sampler2D imageU; uniform sampler2D imageV;
            uniform int imageMode;
            void main(){
                vec4 p=texture2D(imageY,uv);
                if(imageMode>=2 && imageMode<=5){
                    float y=1.16438356*(p.r-0.0627451);
                    vec4 u=texture2D(imageU,uv);
                    float cb;float cr;
                    if(imageMode==2){cb=u.a-0.5;cr=u.r-0.5;}
                    else if(imageMode==3){cb=u.r-0.5;cr=u.a-0.5;}
                    else{cb=u.r-0.5;cr=texture2D(imageV,uv).r-0.5;}
                    gl_FragColor=vec4(y+1.596*cr,y-0.392*cb-0.813*cr,y+2.017*cb,1.0);
                }else if(imageMode==1){gl_FragColor=vec4(p.rrr,1.0);}
                else if(imageMode==7 || imageMode==9){gl_FragColor=vec4(p.bgr,1.0);}
                else{gl_FragColor=vec4(p.rgb,1.0);}
            }
        """.trimIndent())
        program=GL.glCreateProgram();GL.glAttachShader(program,vs);GL.glAttachShader(program,fs);GL.glLinkProgram(program)
        GL.glDeleteShader(vs);GL.glDeleteShader(fs)
        val ok=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) {GL.glGetProgramInfoLog(program)}
        positionLocation=GL.glGetAttribLocation(program,"aPosition");uvLocation=GL.glGetAttribLocation(program,"aUv")
        modeLocation=GL.glGetUniformLocation(program,"imageMode")
        samplerLocations=intArrayOf(GL.glGetUniformLocation(program,"imageY"),GL.glGetUniformLocation(program,"imageU"),GL.glGetUniformLocation(program,"imageV"))
        textures.forEach {id ->
            GL.glBindTexture(GL.GL_TEXTURE_2D,id)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
            val neutral=ByteBuffer.allocateDirect(1).put(128.toByte()).apply {position(0)}
            GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_LUMINANCE,1,1,0,GL.GL_LUMINANCE,GL.GL_UNSIGNED_BYTE,neutral)
        }
    }

    fun upload(bytes:ByteBuffer,format:Int,w:Int,h:Int,stride:Int,rows:Int,rotation:Int,mirror:Boolean,arrival:Long):Boolean {
        val required=CinemaCameraLayout.bytes(format,w,h,stride,rows) ?: return false
        if(bytes.capacity()<required) return false
        val next="$format/$stride/$rows"
        val allocate=next!=signature
        this.format=format;this.w=w;this.h=h;this.stride=stride;this.rows=rows;this.rotation=rotation;this.mirror=mirror
        val alignment=IntArray(1);GL.glGetIntegerv(GL.GL_UNPACK_ALIGNMENT,alignment,0);GL.glPixelStorei(GL.GL_UNPACK_ALIGNMENT,1)
        fun plane(unit:Int,offset:Int,width:Int,height:Int,channels:Int){
            GL.glActiveTexture(GL.GL_TEXTURE0+unit);GL.glBindTexture(GL.GL_TEXTURE_2D,textures[unit])
            val glFormat=when(channels){1->GL.GL_LUMINANCE;2->GL.GL_LUMINANCE_ALPHA;3->GL.GL_RGB;else->GL.GL_RGBA}
            bytes.position(offset)
            if(allocate) GL.glTexImage2D(GL.GL_TEXTURE_2D,0,glFormat,width,height,0,glFormat,GL.GL_UNSIGNED_BYTE,bytes)
            else GL.glTexSubImage2D(GL.GL_TEXTURE_2D,0,0,0,width,height,glFormat,GL.GL_UNSIGNED_BYTE,bytes)
        }
        try {
            val size=stride*rows
            plane(0,0,stride,rows,when(format){6,7->3;8,9->4;else->1})
            if(format==2 || format==3) plane(1,size,stride/2,rows/2,2)
            else if(format==4 || format==5){
                plane(1,if(format==4) size else size*5/4,stride/2,rows/2,1)
                plane(2,if(format==4) size*5/4 else size,stride/2,rows/2,1)
            }
            signature=next;ready=true;uploaded++;lastArrivalNs=arrival
        } finally {bytes.position(0);GL.glPixelStorei(GL.GL_UNPACK_ALIGNMENT,alignment[0]);GL.glActiveTexture(GL.GL_TEXTURE0)}
        return true
    }

    fun draw(eyeAspect:Float,correction:Int):Boolean {
        if(!ready || EGL14.eglGetCurrentContext()!=context) return false
        val total=(rotation+correction).mod(360)
        val a=if(total%180==0) w.toFloat()/h else h.toFloat()/w
        val us=if(eyeAspect<a) eyeAspect/a else 1f;val vs=if(eyeAspect>a) a/eyeAspect else 1f
        vertices.clear()
        for(i in 0..3){
            val offset=i*4
            val u=.5f+(corners[offset+2]-.5f)*us;val v=.5f+(corners[offset+3]-.5f)*vs
            val (x,y)=CinemaCameraUv.rotate(if(mirror) 1f-u else u,v,total)
            vertices.put(corners[offset]).put(corners[offset+1]).put(0f).put(x*w/stride).put(y*h/rows)
        }
        vertices.position(0);GL.glUseProgram(program)
        for(i in 0..2){GL.glActiveTexture(GL.GL_TEXTURE0+i);GL.glBindTexture(GL.GL_TEXTURE_2D,textures[i])}
        for(i in 0..2) GL.glUniform1i(samplerLocations[i],i)
        GL.glUniform1i(modeLocation,format)
        val position=positionLocation;val uv=uvLocation
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,0)
        vertices.position(0);GL.glEnableVertexAttribArray(position);GL.glVertexAttribPointer(position,3,GL.GL_FLOAT,false,20,vertices)
        vertices.position(3);GL.glEnableVertexAttribArray(uv);GL.glVertexAttribPointer(uv,2,GL.GL_FLOAT,false,20,vertices)
        val depth=GL.glIsEnabled(GL.GL_DEPTH_TEST)
        GL.glGetIntegerv(GL.GL_DEPTH_WRITEMASK,depthMask,0)
        GL.glDisable(GL.GL_DEPTH_TEST);GL.glDepthMask(false)
        GL.glDrawArrays(GL.GL_TRIANGLE_STRIP,0,4)
        GL.glDepthMask(depthMask[0]!=0);if(depth) GL.glEnable(GL.GL_DEPTH_TEST)
        GL.glDisableVertexAttribArray(position);GL.glDisableVertexAttribArray(uv);GL.glActiveTexture(GL.GL_TEXTURE0)
        return true
    }

    fun release(){
        if(EGL14.eglGetCurrentContext()==context){GL.glDeleteTextures(3,textures,0);GL.glDeleteProgram(program)}
        ready=false
    }
}

internal object CinemaCameraLayout {
    fun bytes(format:Int,w:Int,h:Int,stride:Int,rows:Int):Int? {
        if(w<=0 || h<=0 || stride<w || rows<h || stride>8192 || rows>8192) return null
        val size=stride*rows
        return when(format){
            1->size
            in 2..5->if(stride%2==0 && rows%2==0) size*3/2 else null
            6,7->size*3;8,9->size*4;else->null
        }
    }
}
