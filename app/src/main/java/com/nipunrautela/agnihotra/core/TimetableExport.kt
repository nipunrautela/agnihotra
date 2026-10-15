package com.nipunrautela.agnihotra.core

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stable columns and explicit zone/coordinates make exports usable independently of the app. */
fun timetableCsv(placeName: String, coordinates: Coordinates, zone: ZoneId, days: List<SolarDay>): String {
    fun cell(value: String): String {
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    val time = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(zone)
    return buildString {
        append('\uFEFF') // UTF-8 BOM improves Hindi place-name handling in spreadsheet applications.
        append("Place,Latitude,Longitude,Time zone,Date,Sunrise,Sunset\r\n")
        days.forEach { day ->
            append(listOf(cell(placeName), coordinates.latitude.toString(), coordinates.longitude.toString(),
                cell(zone.id), day.date.toString(), day.sunrise?.let(time::format).orEmpty(),
                day.sunset?.let(time::format).orEmpty()).joinToString(","))
            append("\r\n")
        }
    }
}
