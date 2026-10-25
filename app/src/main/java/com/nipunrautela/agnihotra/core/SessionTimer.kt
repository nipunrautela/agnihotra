package com.nipunrautela.agnihotra.core

/** User-chosen audio-session length, independent of the astronomical event or ritual duration. */
data class SessionTimer(val startedElapsed: Long, val durationMillis: Long) {
    init { require(startedElapsed >= 0 && durationMillis in 1..120 * 60_000L) }
    val endsElapsed: Long get() = startedElapsed + durationMillis
    fun remainingMillis(elapsed: Long): Long = (endsElapsed - elapsed).coerceIn(0, durationMillis)
    fun finished(elapsed: Long): Boolean = elapsed >= endsElapsed
}
