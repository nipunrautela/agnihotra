package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.integrations.musicTitle
import com.nipunrautela.agnihotra.audio.ChimePreviewPlayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nipunrautela.agnihotra.reminders.ReminderScheduler
import android.media.AudioManager
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.net.Uri

@Composable
fun SettingsPage(prefs: AppPreferences, analyticsConfigured: Boolean, notificationsAllowed: Boolean,
    exactAllowed: Boolean, onBoolean: (String, Boolean) -> Unit, onString: (String, String) -> Unit,
    onPreparation: (Int) -> Unit, onNotifications: () -> Unit, onExactAlarms: () -> Unit,
    onPrivacy: () -> Unit, onAddWidget: (() -> Unit)? = null,
    onChimePreview: () -> Unit = {}, onSessionOptions: () -> Unit = {}) {
    var alarmOptions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var customPreparation by remember { mutableStateOf(false) }
    var customMinutes by remember(prefs.preparationMinutes) { mutableFloatStateOf(prefs.preparationMinutes.toFloat()) }
    var reminderHelp by remember { mutableStateOf(false) }
    var backgroundHelp by remember { mutableStateOf(false) }
    var chimeUnavailable by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    var alarmMuted by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) }
    var channelMuted by remember { mutableStateOf(false) }
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val preview = remember { ChimePreviewPlayer(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(preview, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) preview.stop()
            if (event == Lifecycle.Event.ON_RESUME) alarmMuted = audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); preview.stop() }
    }
    // Re-read after returning from Android's channel controls; do not override user sound choices.
    LifecycleResumeEffect(prefs.sound, prefs.vibration) {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ReminderScheduler.channelId(prefs))
        channelMuted = channel != null && channel.sound == null
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsSection(R.string.reminders) {
            Text(stringResource(R.string.reminder_scope), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsSwitch(R.string.sunrise_reminder, prefs.sunriseReminder) { onBoolean("sunrise_reminder", it) }
            SettingsSwitch(R.string.sunset_reminder, prefs.sunsetReminder) { onBoolean("sunset_reminder", it) }
            if (prefs.sunriseReminder || prefs.sunsetReminder) {
                if (!notificationsAllowed) PermissionCard(R.string.notification_access_title, R.string.notification_access_body, onNotifications)
                if (!exactAllowed) PermissionCard(R.string.exact_access_title, R.string.exact_access_body, onExactAlarms)
            }
            TextButton(onClick = { alarmOptions = !alarmOptions }) {
                Text(stringResource(R.string.alarm_options))
                Spacer(Modifier.width(8.dp))
                Icon(if (alarmOptions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
            }
            AnimatedVisibility(alarmOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.preparation_time), style = MaterialTheme.typography.titleSmall)
                    ChoiceRow(listOf(0, 5, 10, 15, 30), prefs.preparationMinutes, label = {
                        if (it == 0) stringResource(R.string.at_event_only) else stringResource(R.string.minutes_before, it)
                    }, onSelect = onPreparation)
                    TextButton(onClick = { customMinutes = prefs.preparationMinutes.toFloat(); customPreparation = true }) {
                        Text(stringResource(R.string.custom_preparation, prefs.preparationMinutes))
                    }
                    SettingsSwitch(R.string.sound, prefs.sound) { onBoolean("sound", it) }
                    Text(stringResource(R.string.chime_detail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        alarmMuted = audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
                        chimeUnavailable = !preview.play()
                        if (!chimeUnavailable) onChimePreview()
                    }) { Text(stringResource(R.string.preview_chime)) }
                    if (chimeUnavailable) Text(stringResource(R.string.chime_unavailable), color = MaterialTheme.colorScheme.error)
                    if (prefs.sound && alarmMuted) Text(stringResource(R.string.alarm_volume_muted), color = MaterialTheme.colorScheme.error)
                    if (prefs.sound && channelMuted) Text(stringResource(R.string.alarm_channel_muted), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onNotifications) { Text(stringResource(R.string.reminder_sound_settings)) }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }) {
                        Text(stringResource(R.string.alarm_volume_settings))
                    }
                    SettingsSwitch(R.string.vibration, prefs.vibration) { onBoolean("vibration", it) }
                    TextButton(onClick = { reminderHelp = true }) { Text(stringResource(R.string.reminder_help_title)) }
                    TextButton(onClick = { backgroundHelp = true }) { Text(stringResource(R.string.background_alarm_settings)) }
                }
            }
        }
        SettingsSection(R.string.session_options) {
            OutlinedCard(onClick = onSessionOptions, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.session_change_options), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.alarm_minutes, prefs.practiceDurationMinutes), style = MaterialTheme.typography.bodyLarge)
                    Text(if (prefs.practiceMusicEnabled) prefs.musicTitle(context) else stringResource(R.string.session_silent),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsSwitch(R.string.keep_screen_on, prefs.keepScreenOn) { onBoolean("keep_screen_on", it) }
        }
        SettingsSection(R.string.reading_options) {
            SettingsSwitch(R.string.transliteration, prefs.showTransliteration) { onBoolean("transliteration", it) }
            SettingsSwitch(R.string.large_text, prefs.largeMantraText) { onBoolean("large_mantra_text", it) }
        }
        SettingsSection(R.string.appearance) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("system", "light", "dark"), prefs.theme, label = {
                stringResource(when (it) { "light" -> R.string.light; "dark" -> R.string.dark; else -> R.string.system })
            }, onSelect = { onString("theme", it) })
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("system", "en", "hi"), prefs.language, label = {
                stringResource(when (it) { "en" -> R.string.english; "hi" -> R.string.hindi; else -> R.string.system })
            }, onSelect = { onString("language", it) })
            Text(stringResource(R.string.time_format), style = MaterialTheme.typography.titleSmall)
            ChoiceRow(listOf("system", "12", "24"), prefs.timeFormat, label = {
                stringResource(when (it) { "12" -> R.string.time_format_12; "24" -> R.string.time_format_24; else -> R.string.system })
            }, onSelect = { onString("time_format", it) })
            SettingsSwitch(R.string.show_seconds, prefs.showSeconds) { onBoolean("show_seconds", it) }
        }
        SettingsSection(R.string.privacy) {
            SettingsSwitch(R.string.usage_analytics, prefs.analytics) { onBoolean("analytics", it) }
            Text(stringResource(R.string.analytics_body),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_details)) }
        }
        SettingsSection(R.string.widget_heading) {
            onAddWidget?.let { add -> FilledTonalButton(onClick = add) { Text(stringResource(R.string.add_widget)) } }
        }
        SettingsSection(R.string.about) {
            Text(stringResource(R.string.about_body, versionName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
    if (customPreparation) AlertDialog(onDismissRequest = { customPreparation = false },
        title = { Text(stringResource(R.string.preparation_time)) },
        text = { Column {
            Text(if (customMinutes.roundToInt() == 0) stringResource(R.string.at_event_only) else stringResource(R.string.minutes_before, customMinutes.roundToInt()))
            Slider(customMinutes, { customMinutes = it }, valueRange = 0f..60f, steps = 59,
                modifier = Modifier.semanticsLabel(stringResource(R.string.preparation_time)))
        } },
        confirmButton = { TextButton(onClick = { onPreparation(customMinutes.roundToInt()); customPreparation = false }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = { customPreparation = false; customMinutes = prefs.preparationMinutes.toFloat() }) { Text(stringResource(R.string.cancel)) } })
    if (reminderHelp) AlertDialog(onDismissRequest = { reminderHelp = false },
        title = { Text(stringResource(R.string.reminder_help_title)) }, text = { Text(stringResource(R.string.reminder_help_detail)) },
        confirmButton = { TextButton(onClick = { reminderHelp = false }) { Text(stringResource(R.string.got_it)) } })
    if (backgroundHelp) AlertDialog(onDismissRequest = { backgroundHelp = false },
        title = { Text(stringResource(R.string.background_alarm_settings)) },
        text = { Text(stringResource(R.string.background_alarm_help)) },
        confirmButton = { TextButton(onClick = {
            backgroundHelp = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)))
        }) { Text(stringResource(R.string.open_app_settings)) } },
        dismissButton = { TextButton(onClick = { backgroundHelp = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SettingsSection(label: Int, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable private fun SettingsSwitch(label: Int, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable private fun PermissionCard(title: Int, body: Int, onAllow: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected == option, onClick = { onSelect(option) }, label = { Text(label(option)) }) }
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier = this.then(
    Modifier.semantics { contentDescription = label },
)
