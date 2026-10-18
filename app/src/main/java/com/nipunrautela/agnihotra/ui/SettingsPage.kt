package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.layout.*
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
import com.nipunrautela.agnihotra.audio.ChimePreviewPlayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SettingsPage(prefs: AppPreferences, analyticsConfigured: Boolean, notificationsAllowed: Boolean,
    exactAllowed: Boolean, onBoolean: (String, Boolean) -> Unit, onString: (String, String) -> Unit,
    onPreparation: (Int) -> Unit, onNotifications: () -> Unit, onExactAlarms: () -> Unit,
    onPrivacy: () -> Unit, onAddWidget: (() -> Unit)? = null,
    onChimePreview: () -> Unit = {}, onAlarmDuration: (Int) -> Unit = {}) {
    var customPreparation by remember { mutableStateOf(false) }
    var customMinutes by remember(prefs.preparationMinutes) { mutableFloatStateOf(prefs.preparationMinutes.toFloat()) }
    var reminderHelp by remember { mutableStateOf(false) }
    var durationDialog by remember { mutableStateOf(false) }
    var durationMinutes by remember(prefs.alarmDurationMinutes) { mutableFloatStateOf(prefs.alarmDurationMinutes.toFloat()) }
    var chimeUnavailable by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val preview = remember { ChimePreviewPlayer(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(preview, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) preview.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); preview.stop() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionHeading(R.string.reminders)
        Text(stringResource(R.string.reminder_scope), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSwitch(R.string.sunrise_reminder, prefs.sunriseReminder) { onBoolean("sunrise_reminder", it) }
        SettingsSwitch(R.string.sunset_reminder, prefs.sunsetReminder) { onBoolean("sunset_reminder", it) }
        if (prefs.sunriseReminder || prefs.sunsetReminder) {
            if (!notificationsAllowed) PermissionCard(R.string.notification_access_title, R.string.notification_access_body, onNotifications)
            if (!exactAllowed) PermissionCard(R.string.exact_access_title, R.string.exact_access_body, onExactAlarms)
        }
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
        OutlinedButton(onClick = { chimeUnavailable = !preview.play(); if (!chimeUnavailable) onChimePreview() }) { Text(stringResource(R.string.preview_chime)) }
        if (chimeUnavailable) Text(stringResource(R.string.chime_unavailable), color = MaterialTheme.colorScheme.error)
        SettingsSwitch(R.string.vibration, prefs.vibration) { onBoolean("vibration", it) }
        Text(stringResource(R.string.alarm_duration), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.alarm_duration_detail), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceRow(listOf(1, 3, 5, 10), prefs.alarmDurationMinutes,
            label = { stringResource(R.string.alarm_minutes, it) }, onSelect = onAlarmDuration)
        TextButton(onClick = { durationMinutes = prefs.alarmDurationMinutes.toFloat(); durationDialog = true }) {
            Text(stringResource(R.string.custom_alarm_duration, prefs.alarmDurationMinutes))
        }
        TextButton(onClick = { reminderHelp = true }) { Text(stringResource(R.string.reminder_help_title)) }
        HorizontalDivider()
        SectionHeading(R.string.appearance)
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
        SettingsSwitch(R.string.keep_screen_on, prefs.keepScreenOn) { onBoolean("keep_screen_on", it) }
        Text(stringResource(R.string.keep_screen_on_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        SectionHeading(R.string.privacy)
        SettingsSwitch(R.string.usage_analytics, prefs.analytics) { onBoolean("analytics", it) }
        Text(stringResource(R.string.analytics_body),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (prefs.analytics && !analyticsConfigured) Text(stringResource(R.string.analytics_not_connected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_details)) }
        HorizontalDivider()
        SectionHeading(R.string.widget_heading)
        Text(stringResource(R.string.widget_body), style = MaterialTheme.typography.bodyMedium)
        onAddWidget?.let { add -> FilledTonalButton(onClick = add) { Text(stringResource(R.string.add_widget)) } }
        SectionHeading(R.string.about)
        Text(stringResource(R.string.about_body, versionName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (durationDialog) AlertDialog(onDismissRequest = { durationDialog = false },
        title = { Text(stringResource(R.string.alarm_duration)) },
        text = { Column {
            Text(stringResource(R.string.alarm_minutes, durationMinutes.roundToInt()))
            Slider(durationMinutes, { durationMinutes = it }, valueRange = 1f..30f, steps = 28,
                modifier = Modifier.semanticsLabel(stringResource(R.string.alarm_duration)))
        } },
        confirmButton = { TextButton(onClick = { onAlarmDuration(durationMinutes.roundToInt()); durationDialog = false }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = { durationDialog = false }) { Text(stringResource(R.string.cancel)) } })
    if (reminderHelp) AlertDialog(onDismissRequest = { reminderHelp = false },
        title = { Text(stringResource(R.string.reminder_help_title)) }, text = { Text(stringResource(R.string.reminder_help_detail)) },
        confirmButton = { TextButton(onClick = { reminderHelp = false }) { Text(stringResource(R.string.got_it)) } })
}

@Composable private fun SectionHeading(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
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
