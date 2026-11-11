#!/usr/bin/env python3
"""CI-only bounded downloads. Genuine Poly Haven 2K RGBE panoramas -> tone-mapped mobile JPEG.
No pip packages, runtime network, telemetry, or full-resolution HDR downloads.
RGBE format: https://radsite.lbl.gov/radiance/refer/filefmts.pdf
"""
import hashlib, io, json, math, pathlib, struct, subprocess, urllib.request, zlib
ASSETS = {
 "pretville_cinema": ("406c01275af36c26387a9a2464fb34c3",6088474),
 "warm_bar": ("0d4e532d924ecbb167d5704c2f5a85b1",5816931),
 "rooftop_night": ("966095e85f971fbc00aeb67e7d60d1c9",6509158),
 "rogland_clear_night": ("bd652268098521c5fd23e9fd1a4368b0",6838156),
}
OUT=pathlib.Path("app/src/be/assets/environments")
TMP=pathlib.Path("build/cinema-panorama-conversion")
def decode(data):
 f=io.BytesIO(data)
 if not f.readline().startswith(b"#?"): raise ValueError("Not a Radiance panorama")
 while f.readline().strip(): pass
 res=f.readline().decode().split()
 if len(res)!=4 or res[0]!="-Y" or res[2]!="+X": raise ValueError("Unsupported HDR orientation")
 h,w=int(res[1]),int(res[3])
 if (w,h)!=(2048,1024): raise ValueError("Expected genuine 2:1 2K panorama")
 pixels=bytearray(w*h*4)
 for y in range(h):
  if f.read(4)!=bytes([2,2,w>>8,w&255]): raise ValueError("Unsupported HDR RLE")
  for c in range(4):
   x=0
   while x<w:
    n=f.read(1)[0]
    if n==0: raise ValueError("Invalid RLE")
    if n>128: count=n-128; value=f.read(1)*count
    else: count=n; value=f.read(count)
    if x+count>w or len(value)!=count: raise ValueError("Invalid RLE width")
    pixels[(y*w+x)*4+c:(y*w+x+count)*4+c:4]=value
    x+=count
 return w,h,pixels
def png(w,h,p):
 factors=[math.ldexp(1.0,e-136) if e else 0 for e in range(256)]
 logs=[]
 for i in range(0,len(p),64):
  r,g,b,e=p[i:i+4];lum=(.2126*r+.7152*g+.0722*b)*factors[e]
  logs.append(math.log(max(lum,1e-5)))
 exposure=.18/math.exp(sum(logs)/len(logs))
 raw=bytearray()
 for y in range(h):
  raw.append(0)
  for x in range(w):
   i=(y*w+x)*4;r,g,b,e=p[i:i+4];f=factors[e]*exposure
   lum=(.2126*r+.7152*g+.0722*b)*f
   for v in (r,g,b):
    raw.append(min(255,round(255*max(0,v*f/(1+lum))**(1/2.2))))
 def chunk(t,d): return struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d)&0xffffffff)
 return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0))+chunk(b"IDAT",zlib.compress(raw,6))+chunk(b"IEND",b"")
def main():
 OUT.mkdir(parents=True,exist_ok=True);TMP.mkdir(parents=True,exist_ok=True)
 credits=[]
 for asset,(md5,size) in ASSETS.items():
  url=f"https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/2k/{asset}_2k.hdr"
  path=OUT/(asset+".jpg")
  if not path.exists():
   req=urllib.request.Request(url,headers={"User-Agent":"NextVR-Cinema-Build/2.0"})
   with urllib.request.urlopen(req,timeout=60) as r: data=r.read(size+1)
   if len(data)!=size or hashlib.md5(data).hexdigest()!=md5: raise ValueError("Source integrity mismatch: "+asset)
   w,h,p=decode(data);intermediate=TMP/(asset+".png");intermediate.write_bytes(png(w,h,p))
   subprocess.run(["java","-Djava.awt.headless=true","scripts/CinemaJpeg.java",str(intermediate),str(path)],check=True)
   intermediate.unlink()
  credits.append({"id":asset,"source":"https://polyhaven.com/a/"+asset,"license":"CC0-1.0","source_url":url,"source_md5":md5,"width":2048,"height":1024,"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"conversion":"Reinhard luminance, log-average exposure 0.18, gamma 2.2, JPEG"})
  print(asset,path.stat().st_size,"bytes")
 (OUT/"credits.json").write_text(json.dumps(credits,indent=2)+"\n",encoding="utf-8")
if __name__=="__main__": main()
