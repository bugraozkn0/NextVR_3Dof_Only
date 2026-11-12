package com.samrat.cardboardhands

import org.junit.Assert.*
import org.junit.Test

class EasyArPixelsTest {
    @Test fun paddedRgbRowsDoNotLeakPaddingIntoNextRow() {
        val bytes=byteArrayOf(-1,0,0, 0,0,0, 0,-1,0, 0,0,0)
        val rgb=EasyArPixels.decode(bytes,6,1,2,2,2)!!
        assertArrayEquals(intArrayOf(0xffff0000.toInt(),0xff00ff00.toInt()),rgb.pixels)
    }

    @Test fun bgrAndBgraAreNotTreatedAsRgb() {
        assertEquals(0xff0000ff.toInt(),EasyArPixels.decode(byteArrayOf(-1,0,0),7,1,1,1,1)!!.pixels[0])
        assertEquals(0xffff0000.toInt(),EasyArPixels.decode(byteArrayOf(0,0,-1,0),9,1,1,1,1)!!.pixels[0])
    }

    @Test fun allFourYuvLayoutsDecodeNeutralWhite() {
        for(format in 2..5) {
            val rgb=EasyArPixels.decode(byteArrayOf(-21,-21,-21,-21,-128,-128),format,2,2,2,2)!!
            rgb.pixels.forEach {assertEquals(0xfffefefe.toInt(),it)}
        }
    }

    @Test fun truncatedUnsupportedAndMalformedImagesAreExplicitlyRejected() {
        assertNull(EasyArPixels.decode(byteArrayOf(),2,2,2,2,2))
        assertNull(EasyArPixels.decode(ByteArray(32),0,2,2,2,2))
        assertNull(EasyArPixels.decode(ByteArray(32),2,2,2,3,2))
        assertNull(EasyArPixels.decode(ByteArray(32),6,4,2,2,2))
    }

    @Test fun downsamplingBoundsHandAndPreviewConversionWork() {
        val rgb=EasyArPixels.decode(ByteArray(1280*720),1,1280,720,1280,720)!!
        assertEquals(640,rgb.width);assertEquals(360,rgb.height)
    }
}
