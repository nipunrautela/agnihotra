package com.nipunrautela.agnihotra.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.audio.PracticePlaybackService
import com.nipunrautela.agnihotra.audio.SessionStatus
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.integrations.musicTitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimedPracticePage(prefs: AppPreferences, onOptions: () -> Unit, onMantras: () -> Unit,
    modifier: Modifier = Modifier, actionModifier: Modifier = Modifier, ringModifier: Modifier = Modifier,
    onDuration: (Int) -> Unit = {}) {
    val session by PracticePlaybackService.state.collectAsStateWithLifecycle()
    val tick by rememberCurrentInstant()
    val ended = session.status == SessionStatus.FINISHED && session.id != null
    val paused = session.timer?.paused == true && session.active
    val preparing = session.status == SessionStatus.PREPARING
    val remaining = remember(tick, session, prefs.practiceDurationMinutes) {
        when {
            session.active -> session.timer?.remainingMillis(SystemClock.elapsedRealtime()) ?: 0L
            ended -> 0L
            else -> prefs.practiceDurationMinutes * 60_000L
        }
    }
    val context = LocalContext.current
    KeepScreenAwake(prefs.keepScreenOn && session.active)
    val actionLabel = stringResource(when {
        ended -> R.string.session_new
        preparing -> R.string.session_preparing
        paused -> R.string.session_resume
        session.active -> R.string.session_pause
        session.status == SessionStatus.FAILED -> R.string.session_retry
        else -> R.string.practice_start
    })
    val primaryAction = {
        when {
            ended -> PracticePlaybackService.prepareNext()
            paused -> PracticePlaybackService.resume(context)
            session.active -> PracticePlaybackService.pause(context)
            else -> PracticePlaybackService.start(context, prefs)
        }
        Unit
    }
    val fraction = if (session.active) (remaining.toFloat() / (session.timer?.durationMillis ?: 1L)).coerceIn(0f, 1f) else 0f
    val progress = animateFloatAsState(fraction, if (paused) snap() else tween(900, easing = LinearEasing), label = "session_progress")
    val breathing = if (session.status == SessionStatus.PLAYING) {
        rememberInfiniteTransition(label = "session_ambience").animateFloat(0f, 1f,
            infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flame_breath")
    } else rememberUpdatedState(0f)
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val glow = MaterialTheme.colorScheme.primary
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minimumHeight = maxHeight
        val heroSize = (maxHeight * 0.28f).coerceIn(180.dp, 212.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = minimumHeight)
            .padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = if (paused) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow) {
                    AnimatedContent(session.status, label = "session_status",
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(90)) }) { status ->
                        Text(stringResource(when (status) {
                            SessionStatus.PLAYING -> R.string.session_in_progress
                            SessionStatus.PAUSED -> R.string.session_paused
                            SessionStatus.PREPARING -> R.string.session_preparing
                            SessionStatus.FINISHED -> if (ended) R.string.session_finished else R.string.session_ready_label
                            SessionStatus.FAILED -> R.string.session_audio_error
                            else -> R.string.session_ready_label
                        }), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box(Modifier.size(heroSize), contentAlignment = Alignment.Center) {
                Canvas(ringModifier.fillMaxSize().clearAndSetSemantics {}) {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2
                    val ringSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val origin = androidx.compose.ui.geometry.Offset(inset, inset)
                    if (session.status == SessionStatus.PLAYING) drawCircle(
                        Brush.radialGradient(listOf(glow.copy(alpha = 0.04f + breathing.value * 0.08f), Color.Transparent)),
                        radius = size.minDimension / 2)
                    drawArc(trackColor, 0f, 360f, false, origin, ringSize, style = Stroke(stroke))
                    if (session.active) drawArc(ringColor, -90f, 360f * progress.value, false, origin, ringSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                PracticeActionButton(onClick = primaryAction, description = actionLabel, enabled = !preparing,
                    modifier = actionModifier.size(heroSize - 24.dp).testTag("session_action"),
                    flameModifier = Modifier.graphicsLayer {
                        val scale = 1f + breathing.value * 0.018f
                        scaleX = scale; scaleY = scale
                    })
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(if (session.active || ended) R.string.session_remaining else R.string.session_duration),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val seconds = (remaining + 999) / 1000
                val spoken = countdownDescription(seconds, prefs.showSeconds)
                val timerText = if (prefs.showSeconds) stringResource(R.string.session_timer, seconds / 60, seconds % 60)
                    else stringResource(R.string.alarm_minutes, (remaining + 59_999) / 60_000)
                BasicText(timerText,
                    style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center, fontFeatureSettings = "tnum"), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 52.sp),
                    modifier = Modifier.fillMaxWidth().testTag("session_timer").clearAndSetSemantics { contentDescription = spoken })
            }
            Box(Modifier.fillMaxWidth().heightIn(min = if (largeText) 104.dp else 48.dp), contentAlignment = Alignment.Center) {
                if (!session.active && !ended) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        FilterChip(prefs.practiceDurationMinutes == minutes, onClick = { onDuration(minutes) },
                            label = { Text(stringResource(R.string.alarm_minutes, minutes)) })
                    }
                } else Text(stringResource(when {
                    ended -> R.string.session_end_note
                    paused -> if (session.hasAudio) R.string.session_pause_note else R.string.session_pause_silent_note
                    preparing -> R.string.session_preparing
                    else -> R.string.session_continue_note
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().testTag("session_controls"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionSideAction(Icons.Outlined.Tune, stringResource(R.string.session_options_short), onOptions,
                    Modifier.testTag("session_options_action"))
                Button(onClick = primaryAction, enabled = !preparing,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("session_primary"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                    AnimatedContent(targetState = preparing to paused, label = "session_control_icon",
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) }) { (loading, isPaused) ->
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Icon(if (session.active && !isPaused) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(when {
                        preparing -> R.string.session_starting_short
                        ended -> R.string.session_new
                        paused -> R.string.session_resume_short
                        session.active -> R.string.session_pause_short
                        session.status == SessionStatus.FAILED -> R.string.session_retry
                        else -> R.string.session_start_short
                    }), textAlign = TextAlign.Center)
                }
                SessionSideAction(Icons.Outlined.Stop, stringResource(R.string.session_end_short), { PracticePlaybackService.stop(context) },
                    Modifier.testTag("session_end"), enabled = session.active)
            }
            Surface(onClick = onOptions, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.MusicNote, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(if (session.active || ended) R.string.session_next_options else R.string.session_options),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (prefs.practiceMusicEnabled && prefs.practiceMusicSource != null) prefs.musicTitle(context)
                            else stringResource(R.string.session_silent), style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onMantras) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.read_mantras), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SessionSideAction(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = Color.Transparent, modifier = modifier.width(64.dp).heightIn(min = 64.dp)) {
        Column(Modifier.padding(vertical = 8.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}
