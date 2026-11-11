package com.samrat.cardboardhands

import kotlin.math.hypot

/** Semantic targets survive tremor. A different element cancels immediately.
 * Null has only a short, spatially bounded edge hysteresis; it never fires a click.
 * Coordinates are angular tangents, not target identifiers.
 */
internal class CinemaGazeController(
    var dwellMs: Long = 950L,
    private val repeatMs: Long = 600L,
    var deadZone: Float = .012f,
) {
    enum class State { IDLE, HOVERING, SELECTING, CONFIRMED, UNAVAILABLE, DISABLED }
    var state = State.IDLE; private set
    var progress = 0f; private set
    private var candidate: String? = null
    private var enteredAt = 0L
    private var seenAt = 0L
    private var lastFiredAt = 0L
    private var px = 0f
    private var py = 0f
    private var fired = false

    fun reset() {
        candidate = null; enteredAt = 0; seenAt = 0; lastFiredAt = 0
        fired = false; progress = 0f; state = State.IDLE
    }
    fun disabled(unavailable: Boolean = false) {
        reset(); state = if (unavailable) State.UNAVAILABLE else State.DISABLED
    }
    fun update(key: String?, nowMs: Long, repeatable: Boolean = false, x: Float = 0f, y: Float = 0f): Boolean {
        if (nowMs < seenAt) reset()
        if (key == null) {
            if (candidate != null && nowMs - seenAt <= 90L && hypot(x - px, y - py) <= deadZone) {
                state = State.HOVERING
                return false
            }
            reset(); return false
        }
        seenAt = nowMs; px = x; py = y
        if (key != candidate) {
            candidate = key; enteredAt = nowMs; fired = false; progress = 0f
            state = State.HOVERING; return false
        }
        progress = ((nowMs - enteredAt).toFloat() / dwellMs.coerceAtLeast(250L)).coerceIn(0f, 1f)
        state = if (fired) State.CONFIRMED else State.SELECTING
        if (progress < 1f) return false
        if (!fired || repeatable && nowMs - lastFiredAt >= repeatMs) {
            fired = true; lastFiredAt = nowMs; state = State.CONFIRMED; return true
        }
        return false
    }
}
