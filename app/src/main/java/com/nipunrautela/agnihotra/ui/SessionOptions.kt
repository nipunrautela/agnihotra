package com.nipunrautela.agnihotra.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.PracticeSession
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.integrations.ReleaseConfiguration
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionOptionsSheet(prefs: AppPreferences, onDuration: (Int) -> Unit, onQuiet: (Int) -> Unit,
    onBoolean: (String, Boolean) -> Unit, onMusic: (Uri?) -> Unit, onBundle: (String) -> Unit,
    onDismiss: () -> Unit, showQuiet: Boolean = false, forNextSession: Boolean = false) {
    var custom by remember { mutableStateOf(false) }
    var minutes by remember(prefs.practiceDurationMinutes) { mutableFloatStateOf(prefs.practiceDurationMinutes.toFloat()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { onMusic(uri); onBoolean("practice_music_enabled", true) }
    }
    val contentDensity = LocalDensity.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Dialog windows have their own Android context; reapply the in-app locale and text scale.
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            AgnihotraAppearance(prefs) {
                Column(Modifier.fillMaxWidth().testTag("session_options_sheet").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (forNextSession) R.string.session_next_options else R.string.session_options), style = MaterialTheme.typography.headlineSmall)
                    if (forNextSession) Text(stringResource(R.string.session_next_note), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.session_timer_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.session_duration), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { duration ->
                            FilterChip(prefs.practiceDurationMinutes == duration, onClick = { onDuration(duration) },
                                label = { Text(stringResource(R.string.alarm_minutes, duration)) })
                        }
                    }
                    TextButton(onClick = { minutes = prefs.practiceDurationMinutes.toFloat(); custom = true }) {
                        Text(stringResource(R.string.session_custom_duration, prefs.practiceDurationMinutes))
                    }
                    if (custom) {
                        Text(stringResource(R.string.alarm_minutes, minutes.roundToInt()))
                        Slider(minutes, { minutes = it }, valueRange = 1f..120f, steps = 118)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { onDuration(minutes.roundToInt()); custom = false }) { Text(stringResource(R.string.apply)) }
                            TextButton(onClick = { custom = false }) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(prefs.practiceMusicEnabled, role = Role.Switch, onValueChange = { onBoolean("practice_music_enabled", it) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.session_music), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(prefs.practiceMusicEnabled, onCheckedChange = null)
                    }
                    ReleaseConfiguration.practiceMusic.forEach { track ->
                        val selected = prefs.practiceMusicSource == "bundle:${track.id}"
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(selected, role = Role.RadioButton, onClick = { onBundle(track.id); onBoolean("practice_music_enabled", true) }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null)
                            Text(stringResource(track.titleResource), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (prefs.practiceMusicSource?.startsWith("content:") == true) {
                        Text(prefs.practiceMusicName ?: stringResource(R.string.practice_chosen_audio), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onMusic(null) }) { Text(stringResource(R.string.practice_remove_audio)) }
                    }
                    OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.practice_choose_audio))
                    }
                    if (showQuiet) {
                        HorizontalDivider()
                        Text(stringResource(R.string.practice_quiet_choice), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PracticeSession.quietOptions.forEach { duration ->
                                FilterChip(prefs.practiceQuietMinutes == duration, onClick = { onQuiet(duration) }, label = {
                                    Text(if (duration == 0) stringResource(R.string.practice_no_wait) else stringResource(R.string.alarm_minutes, duration))
                                })
                            }
                        }
                    }
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.session_done)) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
