package com.samrat.cardboardhands

/** Pure timing state machine. A stable target fires once; scroll targets can repeat.
 *  Any target change or tracking loss disarms the old candidate.
 */
internal class CinemaGazeController(
    private val dwellMs: Long = 1050L,
    private val repeatMs: Long = 650L,
) {
    private var candidate: String? = null
    private var enteredAt: Long = 0L
    private var lastFiredAt: Long = 0L
    private var fired = false

    fun reset() {
        candidate = null
        enteredAt = 0L
        lastFiredAt = 0L
        fired = false
    }

    fun update(key: String?, nowMs: Long, repeatable: Boolean = false): Boolean {
        if (key == null) { reset(); return false }
        if (key != candidate || nowMs < enteredAt) {
            candidate = key
            enteredAt = nowMs
            fired = false
            return false
        }
        if (nowMs - enteredAt < dwellMs) return false
        if (!fired || (repeatable && nowMs - lastFiredAt >= repeatMs)) {
            fired = true
            lastFiredAt = nowMs
            return true
        }
        return false
    }
}
