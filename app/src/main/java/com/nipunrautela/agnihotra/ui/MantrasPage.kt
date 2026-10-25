package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.*
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nipunrautela.agnihotra.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nipunrautela.agnihotra.audio.MantraAudioPlayer
import com.nipunrautela.agnihotra.core.SolarEventType
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.integrations.ReleaseConfiguration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MantrasPage(prefs: AppPreferences = AppPreferences(), onBoolean: (String, Boolean) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    var guidance by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val audio = remember { MantraAudioPlayer(context.applicationContext) }
    val playback by audio.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    KeepScreenAwake(prefs.keepScreenOn)
    DisposableEffect(audio, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) audio.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); audio.release() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(prefs.showTransliteration, onClick = { onBoolean("transliteration", !prefs.showTransliteration) }, label = { Text(stringResource(R.string.transliteration)) })
            FilterChip(prefs.largeMantraText, onClick = { onBoolean("large_mantra_text", !prefs.largeMantraText) }, label = { Text(stringResource(R.string.large_text)) })
        }
        SolarEventType.entries.forEach { event ->
            MantraCard(event, prefs, playback.event == event && playback.playing, onAudio = { audio.toggle(event) })
        }
        if (playback.failed) Text(stringResource(R.string.audio_unavailable), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { guidance = !guidance }) { Text(stringResource(R.string.mantra_details)) }
        if (guidance) {
        Text(stringResource(R.string.mantra_meaning), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.mantra_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { uriHandler.openUri("https://www.agnihotra.org/how-to-perform-agnihotra/") }) {
            Text(stringResource(R.string.mantra_source))
        }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable internal fun MantraCard(event: SolarEventType, prefs: AppPreferences, playing: Boolean, onAudio: () -> Unit, showAudio: Boolean = true) {
    val context = LocalContext.current
    val morning = event == SolarEventType.SUNRISE
    val title = stringResource(if (morning) R.string.sunrise_mantra else R.string.sunset_mantra)
    val text = stringResource(if (morning) R.string.mantra_morning else R.string.mantra_evening)
    val latin = stringResource(if (morning) R.string.mantra_morning_latin else R.string.mantra_evening_latin)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(if (morning) Icons.Outlined.WbSunny else Icons.Outlined.WbTwilight, null, tint = MaterialTheme.colorScheme.primary)
                IconButton(onClick = {
                    val copied = if (prefs.showTransliteration) "$text\n\n$latin" else text
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, copied))
                }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy_named_mantra, title)) }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (morning) R.string.sunrise_mantra_guidance else R.string.sunset_mantra_guidance),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(text, fontSize = if (prefs.largeMantraText) 30.sp else 23.sp,
                lineHeight = if (prefs.largeMantraText) 46.sp else 38.sp) }
            if (prefs.showTransliteration) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectionContainer { Text(latin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (showAudio && ReleaseConfiguration.mantraRecordings.containsKey(event)) FilledTonalButton(onClick = onAudio) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (playing) R.string.pause_recording else R.string.play_recording))
            }
        }
    }
}
