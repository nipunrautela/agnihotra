package com.nipunrautela.agnihotra.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.audio.PracticePlaybackService
import com.nipunrautela.agnihotra.audio.SessionStatus
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.integrations.musicTitle
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun TimedPracticePage(prefs: AppPreferences, placeName: String?, onOptions: () -> Unit, onMantras: () -> Unit) {
    val session by PracticePlaybackService.state.collectAsStateWithLifecycle()
    val tick by rememberCurrentInstant()
    val remaining = remember(tick, session, prefs.practiceDurationMinutes) {
        if (session.status in listOf(SessionStatus.FINISHED, SessionStatus.FAILED)) 0L
        else session.timer?.remainingMillis(SystemClock.elapsedRealtime()) ?: prefs.practiceDurationMinutes * 60_000L
    }
    val context = LocalContext.current
    KeepScreenAwake(prefs.keepScreenOn && session.active)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        placeName?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
            androidx.compose.foundation.Image(painterResource(R.drawable.ic_agnihotra), null, Modifier.padding(8.dp).clip(CircleShape))
        }
        Text(stringResource(when (session.status) {
            SessionStatus.IDLE -> R.string.session_ready
            SessionStatus.FINISHED -> R.string.session_finished
            SessionStatus.FAILED -> R.string.practice_music_failed
            SessionStatus.PAUSED -> R.string.session_paused
            else -> R.string.session_running
        }), style = MaterialTheme.typography.headlineSmall)
        val seconds = (remaining + 999) / 1000
        val spoken = countdownDescription(seconds, prefs.showSeconds)
        val timerText = if (prefs.showSeconds) stringResource(R.string.session_timer, seconds / 60, seconds % 60)
            else stringResource(R.string.alarm_minutes, (remaining + 59_999) / 60_000)
        BasicText(timerText,
            style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFeatureSettings = "tnum"),
            maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 56.sp),
            modifier = Modifier.fillMaxWidth().testTag("session_timer").clearAndSetSemantics { contentDescription = spoken })
        Text(if (session.id != null) session.title else prefs.musicTitle(context), style = MaterialTheme.typography.titleMedium)
        if (session.active && session.hasAudio) FilledTonalButton(enabled = session.status != SessionStatus.PREPARING, onClick = {
            if (session.status == SessionStatus.PLAYING) PracticePlaybackService.pause(context) else PracticePlaybackService.resume(context)
        }, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(if (session.status == SessionStatus.PLAYING) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (session.status == SessionStatus.PLAYING) R.string.practice_pause_music else R.string.practice_play_music))
        }
        if (session.active) OutlinedButton(onClick = { PracticePlaybackService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.practice_end))
        } else Button(onClick = { PracticePlaybackService.start(context, prefs) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.practice_start))
        }
        TextButton(onClick = onOptions) {
            Icon(Icons.Outlined.Tune, null); Spacer(Modifier.width(8.dp))
            Text(stringResource(if (session.active) R.string.session_next_options else R.string.session_options))
        }
        TextButton(onClick = onMantras) { Text(stringResource(R.string.read_mantras)) }
        Text(stringResource(R.string.session_timer_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
