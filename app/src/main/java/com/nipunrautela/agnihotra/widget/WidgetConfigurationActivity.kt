package com.nipunrautela.agnihotra.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nipunrautela.agnihotra.MainActivity
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.appGraph
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.ui.AgnihotraAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.nipunrautela.agnihotra.analytics.UsageEvent

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || info?.provider != ComponentName(this, AgnihotraWidgetReceiver::class.java)) {
            finish(); return
        }
        enableEdgeToEdge()
        setContent {
            val places by appGraph.places.observe().collectAsStateWithLifecycle(emptyList())
            val prefs by appGraph.preferences.preferences.collectAsStateWithLifecycle(AppPreferences())
            var selection by rememberSaveable(widgetId) { mutableStateOf<String?>(null) }
            var loaded by rememberSaveable(widgetId) { mutableStateOf(false) }
            var saving by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            var loadAttempt by remember { mutableIntStateOf(0) }
            LaunchedEffect(widgetId, loadAttempt) {
                if (!loaded) {
                    try {
                        val id = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(widgetId)
                        selection = getAppWidgetState(this@WidgetConfigurationActivity, PreferencesGlanceStateDefinition, id)[WidgetPreferences.placeId]
                        loaded = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                }
            }
            AgnihotraAppearance(prefs) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.widget_configure), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.widget_configure_detail), style = MaterialTheme.typography.bodyMedium)
                        LazyColumn(Modifier.weight(1f).selectableGroup()) {
                            item { WidgetChoice(stringResource(R.string.widget_follow_selected), selection == null, loaded && !saving) { selection = null } }
                            items(places, key = { it.id }) { place -> WidgetChoice(place.name, selection == place.id,
                                loaded && !saving, place.areaName) { selection = place.id } }
                        }
                        if (places.isEmpty()) {
                            Text(stringResource(R.string.widget_setup), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { startActivity(Intent(this@WidgetConfigurationActivity, MainActivity::class.java)) }) {
                                Text(stringResource(R.string.choose_location))
                            }
                        }
                        if (failed) Text(stringResource(R.string.widget_save_failed), color = MaterialTheme.colorScheme.error)
                        if (failed && !loaded) TextButton(onClick = { failed = false; loadAttempt++ }) { Text(stringResource(R.string.try_again)) }
                        Button(enabled = loaded && !saving && (selection == null || places.any { it.id == selection }), onClick = {
                            saving = true
                            lifecycleScope.launch {
                                try {
                                    val id = GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(widgetId)
                                    updateAppWidgetState(this@WidgetConfigurationActivity, id) { widgetPrefs ->
                                        selection?.let { widgetPrefs[WidgetPreferences.placeId] = it } ?: widgetPrefs.remove(WidgetPreferences.placeId)
                                    }
                                    AgnihotraWidget().update(this@WidgetConfigurationActivity, id)
                                    appGraph.analytics.record(UsageEvent.WIDGET_CONFIGURED)
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                    finish()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { failed = true; saving = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_widget)) }
                    }
                }
            }
        }
    }
}

@Composable private fun WidgetChoice(label: String, selected: Boolean, enabled: Boolean, subtitle: String? = null, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
