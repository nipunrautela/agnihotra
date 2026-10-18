package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

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
    val now by rememberCurrentInstant()
    val use24Hour = LocalContext.current.use24HourTime(state.preferences.timeFormat)
    KeepScreenAwake(state.preferences.keepScreenOn)
    val date = now.atZone(place.zone).toLocalDate()
    val day = remember(place, date) { calculator.calculate(date, place.coordinates, place.zone) }
    // Cache the next event until its crossing instead of rerunning the model every second.
    val next = remember(place, date, day.events.count { it.instant <= now }) { calculator.nextEvent(now, place.coordinates, place.zone) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column {
            Text(stringResource(R.string.today_heading), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.today_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.heightIn(min = 48.dp).clickable(onClick = onPlaces).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.labelLarge)
                    place.areaName?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
            }
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF32271F), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF503A2B), Color(0xFF2A2522)))).padding(24.dp)) {
                val nextLabel = stringResource(if (next?.type == SolarEventType.SUNSET) R.string.sunset else R.string.sunrise)
                Text(if (next == null) stringResource(R.string.no_solar_event) else stringResource(R.string.next_event, nextLabel),
                    color = Amber, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(20.dp))
                if (next != null) {
                    val seconds = Duration.between(now, next.instant).seconds.coerceAtLeast(0)
                    val spoken = countdownDescription(seconds, state.preferences.showSeconds)
                    AdaptiveCountdown(if (state.preferences.showSeconds) stringResource(R.string.countdown_format, seconds / 3600, seconds / 60 % 60, seconds % 60)
                        else stringResource(R.string.countdown_minutes, seconds / 3600, seconds / 60 % 60),
                        spoken)
                    Text(stringResource(R.string.time_remaining), color = Color(0xFFDDC9B7), style = MaterialTheme.typography.bodySmall)
                }
                val progress = if (day.sunrise != null && day.sunset != null)
                    (Duration.between(day.sunrise, now).seconds.toFloat() / Duration.between(day.sunrise, day.sunset).seconds).coerceIn(0f, 1f)
                    else null
                Spacer(Modifier.height(12.dp))
                SolarArc(progress)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.sunrise), color = Color(0xFFDDC9B7), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.sunset), color = Color(0xFFDDC9B7), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(16.dp))
                if (next != null) {
                    val time = next.instant.formatTime(place.zone, use24Hour, locale, state.preferences.showSeconds)
                    Text(stringResource(if (next.instant.atZone(place.zone).toLocalDate() == date) R.string.event_at else R.string.tomorrow_at, time),
                        color = Color(0xFFF4E4D2), style = MaterialTheme.typography.bodyMedium)
                }
                if (next != null) TextButton(onClick = onFocus, colors = ButtonDefaults.textButtonColors(contentColor = Amber)) {
                    Icon(Icons.Outlined.CenterFocusStrong, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.focus_mode))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.daily_times), style = MaterialTheme.typography.titleMedium)
            if (LocalConfiguration.current.fontScale >= 1.3f || LocalConfiguration.current.screenWidthDp < 360) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EventCard(SolarEventType.SUNRISE, day.sunrise, place.zone, now, locale, use24Hour, Modifier.fillMaxWidth())
                    EventCard(SolarEventType.SUNSET, day.sunset, place.zone, now, locale, use24Hour, Modifier.fillMaxWidth())
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EventCard(SolarEventType.SUNRISE, day.sunrise, place.zone, now, locale, use24Hour, Modifier.weight(1f))
                EventCard(SolarEventType.SUNSET, day.sunset, place.zone, now, locale, use24Hour, Modifier.weight(1f))
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.NotificationsNone, null)
                    Text(stringResource(R.string.reminder_heading), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(when {
                    !(state.preferences.sunriseReminder || state.preferences.sunsetReminder) -> R.string.reminders_off
                    !remindersReady -> R.string.reminders_need_access
                    else -> R.string.reminders_on
                }),
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onReminders, contentPadding = PaddingValues(top = 12.dp, bottom = 0.dp)) {
                    Text(stringResource(R.string.manage_reminders)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 6.dp).size(16.dp))
                }
            }
        }
        OutlinedButton(onClick = onMantras, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.read_mantras)) }
        Text(stringResource(R.string.calculation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
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
internal fun SolarArc(progress: Float?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Reserve the full glow at the top, bottom and both ends at every width.
        // Drawing is clipped to its own measured region, never into adjacent text.
        val chartHeight = (maxWidth / 2.6f).coerceIn(112.dp, 168.dp)
        Canvas(Modifier.fillMaxWidth().height(chartHeight).clipToBounds().testTag("solar_arc")) {
            val inset = 28.dp.toPx().coerceAtMost(size.width / 4)
            val halfWidth = (size.width / 2 - inset).coerceAtLeast(0f)
            val baseline = size.height - inset
            val rise = (size.height - 2 * inset).coerceAtLeast(0f)
            val arcSize = androidx.compose.ui.geometry.Size(halfWidth * 2, rise * 2)
            val topLeft = Offset(inset, inset)
            drawLine(Color(0xFFB79B82).copy(alpha = 0.3f), Offset(inset, baseline),
                Offset(size.width - inset, baseline), 1.dp.toPx())
            drawArc(Amber.copy(alpha = 0.32f), 180f, 180f, false, topLeft, arcSize,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))))
            if (progress != null) {
                val fraction = progress.coerceIn(0f, 1f)
                drawArc(Amber.copy(alpha = 0.8f), 180f, 180f * fraction, false, topLeft, arcSize,
                    style = Stroke(width = 2.dp.toPx()))
                val angle = PI + PI * fraction
                val sun = Offset(size.width / 2 + halfWidth * cos(angle).toFloat(), baseline + rise * sin(angle).toFloat())
                drawCircle(Amber.copy(alpha = 0.07f), 26.dp.toPx(), sun)
                drawCircle(Amber.copy(alpha = 0.14f), 17.dp.toPx(), sun)
                drawCircle(Amber, 7.dp.toPx(), sun)
            }
        }
    }
}

@Composable
private fun EventCard(type: SolarEventType, time: Instant?, zone: ZoneId, now: Instant, locale: Locale, use24Hour: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (type == SolarEventType.SUNRISE) Icons.Outlined.WbSunny else Icons.Outlined.WbTwilight,
                null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (type == SolarEventType.SUNRISE) R.string.sunrise else R.string.sunset), style = MaterialTheme.typography.labelLarge)
            Text(time?.formatTime(zone, use24Hour, locale) ?: "—",
                style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
            Text(stringResource(if (time == null) R.string.event_unavailable else if (time <= now) R.string.past_event else R.string.upcoming_event),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
