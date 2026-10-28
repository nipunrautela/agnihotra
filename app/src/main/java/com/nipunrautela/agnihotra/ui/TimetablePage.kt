package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.SolarCalculator
import com.nipunrautela.agnihotra.core.SolarDay
import com.nipunrautela.agnihotra.core.use24HourTime
import com.nipunrautela.agnihotra.core.timeFormatter
import com.nipunrautela.agnihotra.core.timetableCsv
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.time.*
import com.nipunrautela.agnihotra.appGraph
import com.nipunrautela.agnihotra.analytics.UsageEvent
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimetablePage(place: SavedPlace?, calculator: SolarCalculator, prefs: AppPreferences = AppPreferences()) {
    if (place == null) { Text(stringResource(R.string.location_required), Modifier.padding(24.dp)); return }
    val now by rememberCurrentInstant()
    val today = now.atZone(place.zone).toLocalDate()
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(selectedMonth)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jumpDate by remember { mutableStateOf(false) }
    var selectedDate by rememberSaveable(place.id, place.latitude, place.longitude, place.zoneId) { mutableStateOf<String?>(null) }
    val selectedDay = remember(selectedDate, place) {
        selectedDate?.let { calculator.calculate(LocalDate.parse(it), place.coordinates, place.zone) }
    }
    var pendingCsv by rememberSaveable { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<Int?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri != null && csv != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                }
                actionMessage = R.string.export_saved
                context.appGraph.analytics.record(UsageEvent.TIMETABLE_EXPORT_COMPLETED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                actionMessage = R.string.export_failed
                context.appGraph.analytics.record(UsageEvent.TIMETABLE_EXPORT_FAILED)
            }
        }
    }
    val days by key(place, month) {
        produceState<List<SolarDay>?>(null) {
            value = withContext(Dispatchers.Default) { (1..month.lengthOfMonth()).map { calculator.calculate(month.atDay(it), place.coordinates, place.zone) } }
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val use24 = context.use24HourTime(prefs.timeFormat)
    val timeFormat = remember(locale, use24, prefs.showSeconds) { timeFormatter(use24, locale, prefs.showSeconds) }
    val stacked = LocalConfiguration.current.fontScale >= 1.4f || LocalConfiguration.current.screenWidthDp < 360
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column {
                Text(stringResource(R.string.timetable_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(place.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                place.areaName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { selectedMonth = month.minusMonths(1).toString() }, enabled = month > YearMonth.of(1900, 1)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_month)) }
                    TextButton(onClick = { jumpDate = true }, modifier = Modifier.weight(1f)) {
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { selectedMonth = month.plusMonths(1).toString() }, enabled = month < YearMonth.of(2100, 12)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_month)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedMonth = YearMonth.from(today).toString() }) { Text(stringResource(R.string.this_month)) }
                    TextButton(onClick = { jumpDate = true }) { Text(stringResource(R.string.choose_date)) }
                    TextButton(enabled = days != null, onClick = {
                        pendingCsv = timetableCsv(place.name, place.coordinates, place.zone, days.orEmpty())
                        export.launch("Agnihotra-$month.csv")
                    }) { Text(stringResource(R.string.export_month)) }
                }
                Text(place.zoneId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                actionMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
            }
        }
        if (!stacked) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.date), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.sunrise), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.sunset), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
        }
        if (days == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(days.orEmpty(), key = { it.date.toEpochDay() }) { day ->
            Surface(onClick = { selectedDate = day.date.toString() }, shape = MaterialTheme.shapes.medium,
                color = if (day.date == today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                if (stacked) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("d EEE", locale)), style = MaterialTheme.typography.titleMedium)
                    listOf(R.string.sunrise to day.sunrise, R.string.sunset to day.sunset).forEach { (label, time) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(label), Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BasicText(time?.atZone(place.zone)?.format(timeFormat) ?: "—",
                                modifier = Modifier.weight(1.3f), maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 18.sp),
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, fontFeatureSettings = "tnum"))
                        }
                    }
                } else Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("d EEE", locale)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    listOf(day.sunrise, day.sunset).forEach { time ->
                        BasicText(time?.atZone(place.zone)?.format(timeFormat) ?: "—", Modifier.weight(1f), maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
    }
    if (jumpDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialDisplayedMonthMillis = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..2100)
        DatePickerDialog(onDismissRequest = { jumpDate = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { timestamp ->
                    val selected = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
                    selectedMonth = YearMonth.from(selected).toString()
                    selectedDate = selected.toString()
                }
                jumpDate = false
            }) { Text(stringResource(R.string.apply)) } },
            dismissButton = { TextButton(onClick = { jumpDate = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state = picker) }
    }
    selectedDay?.let { day ->
        val sunriseLabel = stringResource(R.string.sunrise)
        val sunsetLabel = stringResource(R.string.sunset)
        val sunrise = day.sunrise?.atZone(place.zone)?.format(timeFormat) ?: stringResource(R.string.event_unavailable)
        val sunset = day.sunset?.atZone(place.zone)?.format(timeFormat) ?: stringResource(R.string.event_unavailable)
        val description = "${place.name}\n${day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))}\n$sunriseLabel: $sunrise\n$sunsetLabel: $sunset\n${place.zoneId}"
        val shareLabel = stringResource(R.string.share_timings)
        AlertDialog(onDismissRequest = { selectedDate = null },
            title = { Text(day.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                place.areaName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("$sunriseLabel · $sunrise")
                Text("$sunsetLabel · $sunset")
                Text(place.zoneId, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { if (!shareText(context, description, shareLabel)) actionMessage = R.string.share_unavailable }) { Text(shareLabel) } },
            dismissButton = { TextButton(onClick = { selectedDate = null }) { Text(stringResource(R.string.close)) } })
    }
}
