package com.samrat.cardboardhands

/** Pure, device-independent transition contracts used by the activity and tests. */
internal class CinemaSession {
    enum class Playback { UNKNOWN, PAUSED, PLAYING, BUFFERING, ENDED }
    var cinema = false; private set
    var controls = true; private set
    var websiteControls = false; private set
    var foreground = true; private set
    var playback = Playback.UNKNOWN; private set
    fun enter() { cinema = true; controls = true; websiteControls = false }
    fun leave() { cinema = false; controls = true; websiteControls = false }
    fun toggleControls() { controls = !controls; websiteControls = false }
    fun toggleWebsite() { websiteControls = !websiteControls; controls = true }
    fun media(state: String) { playback = when (state) {
        "playing" -> Playback.PLAYING; "buffering" -> Playback.BUFFERING
        "paused" -> Playback.PAUSED; "ended" -> Playback.ENDED; else -> Playback.UNKNOWN
    } }
    fun foreground(value: Boolean) { foreground = value; if (!value) websiteControls = false }
    val surfaceEnabled get() = foreground && (!cinema || websiteControls)
}
internal object CinemaCameraDemand {
    fun required(resumed: Boolean, permission: Boolean, passthrough: Boolean, hands: Boolean) =
        resumed && permission && (passthrough || hands)
}
internal class CinemaInputArbiter {
    enum class Source { GAZE, HAND }
    private var lastHand = Long.MIN_VALUE
    fun hand(now: Long) { lastHand = now }
    fun clear() { lastHand = Long.MIN_VALUE }
    fun source(now: Long) = if (lastHand != Long.MIN_VALUE && now >= lastHand && now - lastHand < 300) Source.HAND else Source.GAZE
}
internal class CinemaPinchGate(private val holdMs:Long=65, private val requireOpen:Boolean=false) {
    enum class Event { NONE, DOWN, UP }
    private var down = false
    private var candidate = false
    private var since = 0L
    private var armed = !requireOpen
    val ready get()=armed
    fun update(seen: Boolean, pinch: Boolean, now: Long): Event {
        if (!seen) {
            val was = down; down = false; candidate = false; since = now;armed = !requireOpen
            return if (was) Event.UP else Event.NONE
        }
        if(!pinch) armed=true
        if(!armed) {candidate=false;since=now;return Event.NONE}
        if (pinch != candidate || now < since) { candidate = pinch; since = now }
        if (candidate != down && now - since >= holdMs) {
            down = candidate; return if (down) Event.DOWN else Event.UP
        }
        return Event.NONE
    }
}
