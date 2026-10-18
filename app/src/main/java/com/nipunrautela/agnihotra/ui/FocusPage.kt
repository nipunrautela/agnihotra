package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.*
import java.time.Duration

@Composable
fun FocusPage(state: AppState, calculator: SolarCalculator, onMantras: () -> Unit) {
    val place = state.activePlace ?: return
    val now by rememberCurrentInstant()
    val initialEvent = remember(place) { calculator.nextEvent(now, place.coordinates, place.zone) }
    val targetTime by rememberSaveable(place) { mutableStateOf(initialEvent?.instant?.toEpochMilli()) }
    val targetType by rememberSaveable(place) { mutableStateOf(initialEvent?.type?.name) }
    val event = targetTime?.let { timestamp -> targetType?.let { SolarEvent(SolarEventType.valueOf(it), java.time.Instant.ofEpochMilli(timestamp)) } }
    val locale = LocalConfiguration.current.locales[0]
    val use24 = LocalContext.current.use24HourTime(state.preferences.timeFormat)
    KeepScreenAwake(state.preferences.keepScreenOn)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF503A2B), Color(0xFF211E1B))))
        .verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(56.dp), tint = Amber)
        Spacer(Modifier.height(28.dp))
        Text(place.name, color = Color(0xFFF4E4D2), style = MaterialTheme.typography.titleLarge)
        place.areaName?.let { Text(it, color = Color(0xFFDDC9B7), style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(24.dp))
        if (event != null) {
            val eventName = stringResource(if (event.type == SolarEventType.SUNRISE) R.string.sunrise else R.string.sunset)
            Text(eventName, color = Amber, style = MaterialTheme.typography.titleMedium)
            val seconds = Duration.between(now, event.instant).seconds
            val eventReached = now >= event.instant
            val spoken = if (eventReached) stringResource(R.string.event_now)
                else countdownDescription(seconds, state.preferences.showSeconds)
            Spacer(Modifier.height(16.dp))
            AdaptiveCountdown(if (eventReached) stringResource(R.string.event_now)
                else if (state.preferences.showSeconds) stringResource(R.string.countdown_format, seconds / 3600, seconds / 60 % 60, seconds % 60)
                else stringResource(R.string.countdown_minutes, seconds / 3600, seconds / 60 % 60),
                spoken, textAlign = TextAlign.Center)
            Text(event.instant.formatTime(place.zone, use24, locale, state.preferences.showSeconds),
                color = Color(0xFFDDC9B7), modifier = Modifier.padding(top = 12.dp))
        } else Text(stringResource(R.string.no_solar_event), color = Color(0xFFF4E4D2))
        Spacer(Modifier.height(36.dp))
        FilledTonalButton(onClick = onMantras) { Text(stringResource(R.string.read_mantras)) }
        Text(stringResource(R.string.focus_note), style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFDDC9B7), modifier = Modifier.padding(top = 24.dp))
    }
}
