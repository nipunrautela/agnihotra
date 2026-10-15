package com.nipunrautela.agnihotra.core

/** Reject invalid/future/stale provider fixes before constructing coordinates or travel prompts. */
fun usableLocationFix(latitude: Double, longitude: Double, accuracyMeters: Float?, ageNanos: Long): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters >= 0f &&
        ageNanos in 0..120_000_000_000L
