package com.samrat.cardboardhands

/** CPU fallback shared by preview and MediaPipe; downsample before conversion.
 * Pixel format numbers and padded plane layout from SDK 4.11 HelloARMotionTracking/BGRenderer.
 * https://www.easyar.com/doc/en/api/native/easyar.PixelFormat.html
 * Conversion runs on the existing tracking executor, never on the GL or UI thread.
 */
internal object EasyArPixels {
    data class Rgb(val width: Int, val height: Int, val pixels: IntArray)

    fun decode(bytes: ByteArray, format: Int, width: Int, height: Int, stride: Int, rows: Int, reuse:IntArray?=null): Rgb? {
        if (width <= 0 || height <= 0 || stride < width || rows < height ||
            stride > 8192 || rows > 8192) return null
        val size = stride * rows
        val channels = when (format) { 1 -> 1; in 2..5 -> 0; 6, 7 -> 3; 8, 9 -> 4; else -> return null }
        if (format in 2..5 && (stride % 2 != 0 || rows % 2 != 0)) return null
        val required = if (channels == 0) size * 3 / 2 else size * channels
        if (bytes.size < required) return null
        val step = ((maxOf(width, height) + 639) / 640).coerceAtLeast(1)
        val w = (width + step - 1) / step; val h = (height + step - 1) / step
        val out = reuse?.takeIf {it.size==w*h} ?: IntArray(w * h)
        fun b(i: Int) = bytes[i].toInt() and 255
        for (y in 0 until h) for (x in 0 until w) {
            val sx = x * step; val sy = y * step
            var r: Int; var g: Int; var blue: Int
            if (channels == 0) {
                val yy = b(sy * stride + sx)
                val uv = (sy / 2) * (stride / 2) + sx / 2
                val u: Int; val v: Int
                when (format) {
                    2 -> { v = b(size + uv * 2); u = b(size + uv * 2 + 1) }
                    3 -> { u = b(size + uv * 2); v = b(size + uv * 2 + 1) }
                    4 -> { u = b(size + uv); v = b(size + size / 4 + uv) }
                    else -> { v = b(size + uv); u = b(size + size / 4 + uv) }
                }
                val c = 1192 * (yy - 16).coerceAtLeast(0)
                r = (c + 1634 * (v - 128)) shr 10
                g = (c - 833 * (v - 128) - 400 * (u - 128)) shr 10
                blue = (c + 2066 * (u - 128)) shr 10
            } else {
                val i = (sy * stride + sx) * channels
                if (format == 1) { r = b(i); g = r; blue = r }
                else { r = b(i + if (format == 7 || format == 9) 2 else 0); g = b(i + 1); blue = b(i + if (format == 7 || format == 9) 0 else 2) }
            }
            out[y * w + x] = (255 shl 24) or (r.coerceIn(0,255) shl 16) or
                (g.coerceIn(0,255) shl 8) or blue.coerceIn(0,255)
        }
        return Rgb(w, h, out)
    }
}
