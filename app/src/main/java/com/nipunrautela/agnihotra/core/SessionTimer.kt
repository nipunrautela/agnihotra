package com.nipunrautela.agnihotra.core

/** User-chosen audio-session length, independent of the astronomical event or ritual duration. */
data class SessionTimer(val startedElapsed: Long, val durationMillis: Long,
    val pausedAtElapsed: Long? = null, val pausedDurationMillis: Long = 0) {
    init {
        require(startedElapsed >= 0 && durationMillis in 1..120 * 60_000L && pausedDurationMillis >= 0)
        require(pausedAtElapsed == null || pausedAtElapsed >= startedElapsed)
    }
    val paused: Boolean get() = pausedAtElapsed != null
    /** A running deadline; while paused the service must cancel its scheduled expiry. */
    val endsElapsed: Long get() = startedElapsed + durationMillis + pausedDurationMillis
    fun remainingMillis(elapsed: Long): Long = (endsElapsed - (pausedAtElapsed ?: elapsed)).coerceIn(0, durationMillis)
    fun finished(elapsed: Long): Boolean = remainingMillis(elapsed) == 0L
    fun pause(elapsed: Long): SessionTimer =
        if (paused || finished(elapsed)) this else copy(pausedAtElapsed = elapsed.coerceAtLeast(startedElapsed))
    fun resume(elapsed: Long): SessionTimer = pausedAtElapsed?.let {
        copy(pausedAtElapsed = null, pausedDurationMillis = pausedDurationMillis + (elapsed - it).coerceAtLeast(0))
    } ?: this
}
