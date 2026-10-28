package com.nipunrautela.agnihotra.ui

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.audio.PracticePlaybackService
import com.nipunrautela.agnihotra.audio.SessionStatus
import com.nipunrautela.agnihotra.core.*
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.integrations.ReleaseConfiguration
import com.nipunrautela.agnihotra.integrations.PracticeMusicTrack
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val sessionSaver = listSaver<PracticeSession?, Any>(
    save = { session -> session?.let { listOf(it.event.type.name, it.event.instant.toEpochMilli(), it.quietMinutes,
        it.musicUri.orEmpty(), it.quietUntilElapsed ?: -1L, it.autoMusic, SystemClock.elapsedRealtime()) } ?: emptyList() },
    restore = { if (it.isEmpty() || (it[6] as Long) > SystemClock.elapsedRealtime()) null else PracticeSession(SolarEvent(SolarEventType.valueOf(it[0] as String), Instant.ofEpochMilli(it[1] as Long)),
        it[2] as Int, (it[3] as String).ifEmpty { null }, (it[4] as Long).takeIf { value -> value >= 0 }, it[5] as Boolean) },
)

@Composable
fun FocusPage(state: AppState, calculator: SolarCalculator,
    onBoolean: (String, Boolean) -> Unit = { _, _ -> }, onQuiet: (Int) -> Unit = {},
    onMusic: (Uri?) -> Unit = {}, onBundledMusic: (String) -> Unit = {},
    nowOverride: Instant? = null, elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    musicTracks: List<PracticeMusicTrack> = ReleaseConfiguration.practiceMusic,
    onDuration: (Int) -> Unit = {}, onTimedSession: () -> Unit = {}) {
    val place = state.activePlace ?: return
    // An area-name lookup must not reset a running session. A coordinate/zone change must.
    key(place.id, place.latitude, place.longitude, place.zoneId) {
        FocusSessionPage(state, place, calculator, onBoolean, onQuiet, onMusic, onBundledMusic, nowOverride, elapsedClock, musicTracks, onDuration, onTimedSession)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSessionPage(state: AppState, place: SavedPlace, calculator: SolarCalculator,
    onBoolean: (String, Boolean) -> Unit, onQuiet: (Int) -> Unit,
    onMusic: (Uri?) -> Unit, onBundledMusic: (String) -> Unit,
    nowOverride: Instant?, elapsedClock: () -> Long, musicTracks: List<PracticeMusicTrack>,
    onDuration: (Int) -> Unit, onTimedSession: () -> Unit) {
    val tickingNow by rememberCurrentInstant()
    val now = nowOverride ?: tickingNow
    val prefs = state.preferences
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val use24 = context.use24HourTime(prefs.timeFormat)
    var session by rememberSaveable(stateSaver = sessionSaver) { mutableStateOf<PracticeSession?>(null) }
    var showMantra by rememberSaveable { mutableStateOf(false) }
    val day = now.atZone(place.zone).toLocalDate()
    val events = remember(day, place.coordinates, place.zone) { calculator.calculate(day, place.coordinates, place.zone).events }
    val upcoming = remember(day, events.count { it.instant <= now }) { calculator.nextEvent(now, place.coordinates, place.zone) }
    val event = session?.event ?: upcoming
    val elapsed = elapsedClock()
    val phase = session?.phase(now, elapsed)
    val playback by PracticePlaybackService.state.collectAsStateWithLifecycle()
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var autoArmed by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, action ->
            if (action == Lifecycle.Event.ON_PAUSE) autoArmed = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun playSession() {
        session?.musicUri?.let { uri ->
            PracticePlaybackService.start(context, prefs.copy(practiceMusicEnabled = true), uri, selectedTitle(context, prefs, musicTracks))
            onTimedSession()
        }
    }
    LaunchedEffect(phase, autoArmed) {
        if (phase == PracticePhase.COMPLETE && autoArmed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            autoArmed = false
            playSession()
        }
    }
    KeepScreenAwake(prefs.keepScreenOn)
    val selectedBundle = musicTracks.firstOrNull { "bundle:${it.id}" == prefs.practiceMusicSource }
    val selectedUri = selectedBundle?.let { "android.resource://${context.packageName}/${it.audioResource}" }
        ?: prefs.practiceMusicSource?.takeIf { it.startsWith("content:") }
    val selectedName = selectedBundle?.let { stringResource(it.titleResource) }
        ?: prefs.practiceMusicName ?: stringResource(R.string.session_silent)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(place.name, style = MaterialTheme.typography.titleLarge)
        place.areaName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(place.zoneId, style = MaterialTheme.typography.bodySmall)
        if (event == null) Text(stringResource(R.string.no_solar_event)) else {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Color(0xFF32271F)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (event.type == SolarEventType.SUNRISE) R.string.sunrise else R.string.sunset), color = Amber)
                    val remaining = when (phase) {
                        PracticePhase.QUIET -> ((session?.quietUntilElapsed ?: elapsed) - elapsed + 999) / 1000
                        else -> Duration.between(now, event.instant).seconds.coerceAtLeast(0)
                    }
                    val label = when (phase) {
                        PracticePhase.OFFERINGS -> stringResource(R.string.event_now)
                        PracticePhase.COMPLETE -> stringResource(R.string.practice_complete)
                        else -> if (prefs.showSeconds) stringResource(R.string.countdown_format, remaining / 3600, remaining / 60 % 60, remaining % 60)
                            else stringResource(R.string.countdown_minutes, remaining / 3600, remaining / 60 % 60)
                    }
                    if (phase == PracticePhase.QUIET) Text(stringResource(R.string.practice_quiet), color = Amber)
                    if (phase in listOf(PracticePhase.OFFERINGS, PracticePhase.COMPLETE))
                        Text(label, color = Color(0xFFFFF4E6), style = MaterialTheme.typography.headlineMedium)
                    else AdaptiveCountdown(label, countdownDescription(remaining, prefs.showSeconds))
                    Text(event.instant.atZone(place.zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)), color = Color(0xFFDDC9B7))
                    Text(event.instant.formatTime(place.zone, use24, locale, prefs.showSeconds), color = Color(0xFFDDC9B7))
                }
            }
        }
        Text(stringResource(R.string.practice_duration_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (session == null) {
            Text(stringResource(R.string.alarm_minutes, prefs.practiceDurationMinutes) + " · " + selectedName,
                style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showOptions = true }) { Text(stringResource(R.string.session_options)) }
            Button(enabled = event != null, onClick = {
                // Recalculate at the tap: never pin an already-passed preview event.
                calculator.nextEvent(now, place.coordinates, place.zone)?.let {
                    session = PracticeSession(it, prefs.practiceQuietMinutes, selectedUri, autoMusic = prefs.practiceMusicEnabled)
                    showMantra = true
                }
            }, modifier = Modifier.fillMaxWidth().testTag("practice_start")) { Text(stringResource(R.string.practice_prepare)) }
        } else {
            if (phase in listOf(PracticePhase.PREPARATION, PracticePhase.OFFERINGS)) {
                Text(stringResource(R.string.practice_offering_guidance))
                Button(enabled = phase == PracticePhase.OFFERINGS, onClick = {
                    session = session?.completeOfferings(now, elapsedClock())
                    autoArmed = session?.autoMusic == true
                }, modifier = Modifier.fillMaxWidth().testTag("practice_complete_offerings")) { Text(stringResource(R.string.practice_offerings_done)) }
            }
            if (phase == PracticePhase.QUIET) Text(stringResource(R.string.practice_quiet_guidance))
            if (phase == PracticePhase.COMPLETE && session?.musicUri != null) {
                FilledTonalButton(onClick = {
                    autoArmed = false
                    if (playback.active) onTimedSession() else playSession()
                }) { Text(stringResource(if (playback.active) R.string.session_open else R.string.practice_play_music)) }
            }
            if (playback.status == SessionStatus.FAILED) Text(stringResource(R.string.practice_music_failed), color = MaterialTheme.colorScheme.error)

            TextButton(onClick = { autoArmed = false; session = null }) {
                Text(stringResource(if (phase == PracticePhase.COMPLETE) R.string.practice_finish else R.string.practice_end))
            }
        }
        if (event != null) {
            TextButton(onClick = { showMantra = !showMantra }) {
                Text(stringResource(if (showMantra) R.string.practice_hide_mantra else R.string.read_mantras))
            }
            if (showMantra) MantraCard(event.type, prefs, false, {}, showAudio = false)
        }
        Spacer(Modifier.height(16.dp))
    }
    if (showOptions) SessionOptionsSheet(prefs, onDuration, onQuiet, onBoolean, onMusic, onBundledMusic,
        onDismiss = { showOptions = false }, showQuiet = true)

}

private fun selectedTitle(context: android.content.Context, prefs: com.nipunrautela.agnihotra.data.AppPreferences,
    tracks: List<PracticeMusicTrack>): String = tracks.firstOrNull { "bundle:${it.id}" == prefs.practiceMusicSource }
    ?.let { context.getString(it.titleResource) } ?: prefs.practiceMusicName ?: context.getString(R.string.session_silent)
