package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WelcomeScreen(onChoose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Outlined.Place, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.choose_location))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.location_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TodayPage(state: AppState, calculator: SolarCalculator, onPlaces: () -> Unit, onReminders: () -> Unit,
    remindersReady: Boolean = true, onFocus: () -> Unit = {}, onMantras: () -> Unit = {}) {
    val place = state.activePlace ?: return
    var timingInfo by remember { mutableStateOf(false) }
    val now by rememberCurrentInstant()
    val use24Hour = LocalContext.current.use24HourTime(state.preferences.timeFormat)
    KeepScreenAwake(state.preferences.keepScreenOn)
    val date = now.atZone(place.zone).toLocalDate()
    val day = remember(place, date) { calculator.calculate(date, place.coordinates, place.zone) }
    // Cache the next event until its crossing instead of rerunning the model every second.
    val next = remember(place, date, day.events.count { it.instant <= now }) { calculator.nextEvent(now, place.coordinates, place.zone) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.today_heading), style = MaterialTheme.typography.headlineLarge)
        }
        Surface(onClick = onPlaces, shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Place, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    place.areaName?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(onClick = onFocus, enabled = next != null, shape = MaterialTheme.shapes.large, color = Color(0xFF342820), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val nextLabel = stringResource(if (next?.type == SolarEventType.SUNSET) R.string.sunset else R.string.sunrise)
                Text(if (next == null) stringResource(R.string.no_solar_event) else stringResource(R.string.next_event, nextLabel),
                    color = Amber, style = MaterialTheme.typography.labelLarge)
                if (next != null) {
                    val time = next.instant.formatTime(place.zone, use24Hour, locale, state.preferences.showSeconds)
                    BasicText(time, style = MaterialTheme.typography.displayMedium.copy(color = Color(0xFFFFF4E6), fontFeatureSettings = "tnum"),
                        maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 40.sp), modifier = Modifier.fillMaxWidth())
                    Text(next.instant.atZone(place.zone).format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale)),
                        color = Color(0xFFDDC9B7), style = MaterialTheme.typography.bodySmall)
                }
                val progress = if (day.sunrise != null && day.sunset != null)
                    (Duration.between(day.sunrise, now).seconds.toFloat() / Duration.between(day.sunrise, day.sunset).seconds).coerceIn(0f, 1f)
                    else null
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.sun_path_today), Modifier.weight(1f),
                        color = Color(0xFFDDC9B7), style = MaterialTheme.typography.labelSmall)
                    val phase = when {
                        day.sunrise == null || day.sunset == null -> R.string.event_unavailable
                        now < day.sunrise -> R.string.sun_before_rise
                        now >= day.sunset -> R.string.sun_after_set
                        else -> R.string.sun_daylight
                    }
                    Text(stringResource(phase), Modifier.weight(1f), textAlign = TextAlign.End,
                        color = Amber, style = MaterialTheme.typography.labelSmall)
                }
                key(place.id, date) { SolarArc(progress) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.sunrise), color = Color(0xFFDDC9B7), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.sunset), color = Color(0xFFDDC9B7), style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(color = Color(0xFF6A5140))
                if (next != null) {
                    val seconds = Duration.between(now, next.instant).seconds.coerceAtLeast(0)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.focus_mode), color = Amber, style = MaterialTheme.typography.labelMedium)
                            val text = if (state.preferences.showSeconds) stringResource(R.string.countdown_format, seconds / 3600, seconds / 60 % 60, seconds % 60)
                                else stringResource(R.string.countdown_minutes, seconds / 3600, seconds / 60 % 60)
                            val spoken = countdownDescription(seconds, state.preferences.showSeconds)
                            BasicText(text, Modifier.fillMaxWidth().testTag("countdown").clearAndSetSemantics { contentDescription = spoken },
                                style = MaterialTheme.typography.headlineMedium.copy(color = Color(0xFFFFF4E6), fontFeatureSettings = "tnum"),
                                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 28.sp))
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(22.dp), tint = Amber)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.daily_times), style = MaterialTheme.typography.titleMedium)
            if (LocalConfiguration.current.fontScale >= 1.3f || LocalConfiguration.current.screenWidthDp < 360) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EventCard(SolarEventType.SUNRISE, day.sunrise, place.zone, now, locale, use24Hour, state.preferences.showSeconds, Modifier.fillMaxWidth())
                    EventCard(SolarEventType.SUNSET, day.sunset, place.zone, now, locale, use24Hour, state.preferences.showSeconds, Modifier.fillMaxWidth())
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EventCard(SolarEventType.SUNRISE, day.sunrise, place.zone, now, locale, use24Hour, state.preferences.showSeconds, Modifier.weight(1f))
                EventCard(SolarEventType.SUNSET, day.sunset, place.zone, now, locale, use24Hour, state.preferences.showSeconds, Modifier.weight(1f))
            }
        }
        Surface(onClick = onReminders, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reminder_heading), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(when {
                        !(state.preferences.sunriseReminder || state.preferences.sunsetReminder) -> R.string.reminders_off
                        !remindersReady -> R.string.reminders_need_access
                        else -> R.string.reminders_on
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onMantras, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.read_mantras)) }
        TextButton(onClick = { timingInfo = true }) { Text(stringResource(R.string.timing_details)) }
        Spacer(Modifier.height(8.dp))
    }
    if (timingInfo) AlertDialog(onDismissRequest = { timingInfo = false }, title = { Text(stringResource(R.string.timing_details)) },
        text = { Text(stringResource(R.string.calculation_note)) },
        confirmButton = { TextButton(onClick = { timingInfo = false }) { Text(stringResource(R.string.got_it)) } })

}

@Composable
internal fun countdownDescription(seconds: Long, showSeconds: Boolean): String {
    val hours = (seconds / 3600).toInt()
    val minutes = (seconds / 60 % 60).toInt()
    val remainingSeconds = (seconds % 60).toInt()
    val hourText = pluralStringResource(R.plurals.countdown_hours, hours, hours)
    val minuteText = pluralStringResource(R.plurals.countdown_minute_units, minutes, minutes)
    return if (showSeconds) stringResource(R.string.countdown_accessible, hourText, minuteText,
        pluralStringResource(R.plurals.countdown_second_units, remainingSeconds, remainingSeconds))
    else stringResource(R.string.countdown_minutes_accessible, hourText, minuteText)
}

@Composable
internal fun AdaptiveCountdown(text: String, spoken: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    BasicText(text, modifier.fillMaxWidth().testTag("countdown")
        .clearAndSetSemantics { contentDescription = spoken },
        style = MaterialTheme.typography.displayMedium.copy(color = Color(0xFFFFF4E6), fontFeatureSettings = "tnum", textAlign = textAlign),
        maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 38.sp))
}

@Composable
private fun EventCard(type: SolarEventType, time: Instant?, zone: ZoneId, now: Instant, locale: Locale, use24Hour: Boolean, showSeconds: Boolean, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (type == SolarEventType.SUNRISE) Icons.Outlined.WbSunny else Icons.Outlined.WbTwilight,
                null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (type == SolarEventType.SUNRISE) R.string.sunrise else R.string.sunset), style = MaterialTheme.typography.labelLarge)
            BasicText(time?.formatTime(zone, use24Hour, locale, showSeconds) ?: "—",
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontFeatureSettings = "tnum"),
                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 20.sp), modifier = Modifier.fillMaxWidth())
            Text(stringResource(if (time == null) R.string.event_unavailable else if (time <= now) R.string.past_event else R.string.upcoming_event),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
