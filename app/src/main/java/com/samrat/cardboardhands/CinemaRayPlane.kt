package com.samrat.cardboardhands

/** Window-local ray/plane intersection. Both origin and direction must use the same world. */
internal object CinemaRayPlane {
    fun intersect(dx: Float, dy: Float, dz: Float, ox: Float, oy: Float, oz: Float, radius: Float): FloatArray? {
        if (!dx.isFinite() || !dy.isFinite() || !dz.isFinite() || !ox.isFinite() ||
            !oy.isFinite() || !oz.isFinite() || !radius.isFinite() || dz >= -1e-3f) return null
        val t = (-radius - oz) / dz
        if (t <= 0f) return null
        return floatArrayOf(ox + dx * t, oy + dy * t)
    }
}
