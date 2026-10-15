package com.nipunrautela.agnihotra.core

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Context.use24HourTime(preference: String): Boolean = when (preference) {
    "24" -> true
    "12" -> false
    else -> DateFormat.is24HourFormat(this)
}

fun timeFormatter(use24Hour: Boolean, locale: Locale, seconds: Boolean = false): DateTimeFormatter =
    DateTimeFormatter.ofPattern(when {
        use24Hour && seconds -> "HH:mm:ss"
        use24Hour -> "HH:mm"
        seconds -> "h:mm:ss a"
        else -> "h:mm a"
    }, locale)

fun Instant.formatTime(zone: ZoneId, use24Hour: Boolean, locale: Locale, seconds: Boolean = false): String =
    atZone(zone).format(timeFormatter(use24Hour, locale, seconds))

/** Accept Devanagari digits without treating a decimal comma as a coordinate separator. */
fun String.coordinateNumberOrNull(): Double? = trim().map { character ->
    when {
        character == '−' -> '-'
        Character.digit(character, 10) >= 0 -> ('0'.code + Character.digit(character, 10)).toChar()
        else -> character
    }
}.joinToString("").toDoubleOrNull()?.takeIf { it.isFinite() }
