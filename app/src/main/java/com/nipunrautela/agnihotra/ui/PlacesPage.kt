package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.Coordinates
import com.nipunrautela.agnihotra.core.coordinateNumberOrNull
import com.nipunrautela.agnihotra.core.formatTime
import com.nipunrautela.agnihotra.core.use24HourTime
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.location.indianCities
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PlacesPage(state: AppState, onAdd: () -> Unit, onSelect: (String) -> Unit, onHome: (String) -> Unit,
    onDelete: (String) -> Unit, onEdit: (SavedPlace) -> Unit) {
    var deleting by remember { mutableStateOf<SavedPlace?>(null) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val use24 = context.use24HourTime(state.preferences.timeFormat)
    val now by rememberCurrentInstant()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(stringResource(R.string.places_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_place))
            }
        }
        items(state.places, key = { it.id }) { place ->
            val active = place.id == state.preferences.activePlaceId
            Surface(shape = RoundedCornerShape(22.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(place.id) }), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (place.id == state.preferences.homePlaceId) Icons.Outlined.Home else Icons.Outlined.Place,
                            null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, style = MaterialTheme.typography.titleLarge)
                            place.areaName?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(place.zoneId, style = MaterialTheme.typography.bodySmall)
                        }
                        if (active) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("%.4f, %.4f".format(java.util.Locale.ROOT, place.latitude, place.longitude),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val localDate = now.atZone(place.zone).toLocalDate()
                    val day = remember(place, localDate) { com.nipunrautela.agnihotra.core.SolarCalculator().calculate(localDate, place.coordinates, place.zone) }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sunrise), style = MaterialTheme.typography.labelSmall)
                            Text(day.sunrise?.formatTime(place.zone, use24, locale) ?: "—", style = MaterialTheme.typography.bodyLarge)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sunset), style = MaterialTheme.typography.labelSmall)
                            Text(day.sunset?.formatTime(place.zone, use24, locale) ?: "—", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onHome(place.id) }, enabled = place.id != state.preferences.homePlaceId) {
                            Text(stringResource(if (place.id == state.preferences.homePlaceId) R.string.home else R.string.make_home))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onEdit(place) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_named_place, place.name)) }
                        IconButton(onClick = { deleting = place }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_named_place, place.name)) }
                    }
                }
            }
        }
    }
    deleting?.let { place ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_place_title, place.name)) },
            text = { Text(stringResource(R.string.delete_place_body)) },
            confirmButton = { TextButton(onClick = { onDelete(place.id); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceDialog(model: AppViewModel, existing: SavedPlace?, onLocate: () -> Unit, onDismiss: () -> Unit, onSave: (SavedPlace, Boolean) -> Unit) {
    val appState by model.state.collectAsStateWithLifecycle()
    val mandatoryHome = appState.places.isEmpty() || existing?.id == appState.preferences.homePlaceId
    val placeId = rememberSaveable(existing?.id) { existing?.id ?: java.util.UUID.randomUUID().toString() }
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var nameEdited by rememberSaveable(existing?.id) { mutableStateOf(existing != null) }
    var areaName by rememberSaveable(existing?.id) { mutableStateOf(existing?.areaName) }
    var fromCurrentLocation by rememberSaveable(existing?.id) { mutableStateOf(existing?.fromCurrentLocation ?: false) }
    var latitude by rememberSaveable(existing?.id) { mutableStateOf(existing?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable(existing?.id) { mutableStateOf(existing?.longitude?.toString().orEmpty()) }
    var zone by rememberSaveable(existing?.id) { mutableStateOf(existing?.zoneId ?: "Asia/Kolkata") }
    var query by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var makeHome by rememberSaveable(existing?.id) { mutableStateOf(mandatoryHome) }
    var cityCentre by rememberSaveable { mutableStateOf(false) }
    var appliedFixTime by rememberSaveable(existing?.id) { mutableStateOf<Long?>(null) }
    val fix by model.locationFix.collectAsStateWithLifecycle()
    val results by model.searchResults.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val saving by model.savingPlace.collectAsStateWithLifecycle()
    val saveFailed by model.placeSaveFailed.collectAsStateWithLifecycle()
    val currentName = stringResource(R.string.current_location_name)
    DisposableEffect(Unit) { onDispose { model.cancelPlaceLookup() } }
    LaunchedEffect(fix) { fix?.let {
        // Restoring the dialog must preserve manual edits made after the previous GPS fix.
        if (appliedFixTime != it.elapsedRealtimeNanos) {
            latitude = it.coordinates.latitude.toString(); longitude = it.coordinates.longitude.toString()
            cityCentre = false
            fromCurrentLocation = true
            areaName = null
            appliedFixTime = it.elapsedRealtimeNanos
        }
        if (!nameEdited) name = currentName
        if (fromCurrentLocation && latitude.coordinateNumberOrNull() == it.coordinates.latitude &&
            longitude.coordinateNumberOrNull() == it.coordinates.longitude) areaName = it.areaName
    } }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_place else R.string.edit_place)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onLocate, enabled = !busy && !saving, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.use_current_location))
                }
                Text(stringResource(R.string.area_lookup_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.offline_cities), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    indianCities.forEach { city ->
                        val cityName = stringResource(city.nameResource)
                        SuggestionChip(onClick = {
                            model.cancelPlaceLookup()
                            nameEdited = true
                            areaName = null; fromCurrentLocation = false
                            name = cityName; latitude = city.coordinates.latitude.toString()
                            longitude = city.coordinates.longitude.toString(); zone = "Asia/Kolkata"
                            cityCentre = true; invalid = false
                        }, label = { Text(cityName) })
                    }
                }
                if (cityCentre) Text(stringResource(R.string.city_centre_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_city)) }, singleLine = true)
                TextButton(onClick = { model.search(query) }, enabled = query.isNotBlank() && !busy) { Text(stringResource(R.string.search)) }
                results.forEach { address ->
                    TextButton(onClick = {
                        model.cancelPlaceLookup()
                        nameEdited = true
                        areaName = null; fromCurrentLocation = false
                        latitude = address.latitude.toString(); longitude = address.longitude.toString()
                        name = address.locality ?: address.featureName ?: query
                        if (address.countryCode == "IN") zone = "Asia/Kolkata"
                        cityCentre = true
                        model.searchResults.value = emptyList()
                    }) { Text(address.getAddressLine(0) ?: address.locality ?: query) }
                }
                Text(stringResource(R.string.or_enter_coordinates), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(name, { name = it; nameEdited = true; invalid = false }, label = { Text(stringResource(R.string.place_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                areaName?.let { Text(stringResource(R.string.approximate_area, it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(latitude, { model.cancelPlaceLookup(); latitude = it; areaName = null; fromCurrentLocation = false; invalid = false }, label = { Text(stringResource(R.string.latitude)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                OutlinedTextField(longitude, { model.cancelPlaceLookup(); longitude = it; areaName = null; fromCurrentLocation = false; invalid = false }, label = { Text(stringResource(R.string.longitude)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                OutlinedTextField(zone, { zone = it; invalid = false }, label = { Text(stringResource(R.string.time_zone)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(stringResource(R.string.zone_hint), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(makeHome, enabled = !mandatoryHome, role = Role.Checkbox, onValueChange = { makeHome = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = makeHome, onCheckedChange = null, enabled = !mandatoryHome)
                    Text(stringResource(R.string.make_home), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                fix?.let { Text(stringResource(R.string.location_accuracy, it.accuracyMeters.toInt()), style = MaterialTheme.typography.bodySmall) }
                if (invalid) Text(stringResource(R.string.place_validation), color = MaterialTheme.colorScheme.error)
                if (saveFailed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val coordinates = runCatching { Coordinates(requireNotNull(latitude.coordinateNumberOrNull()), requireNotNull(longitude.coordinateNumberOrNull())) }.getOrNull()
            val validZone = zone.trim() in ZoneId.getAvailableZoneIds()
            if (name.isBlank() || coordinates == null || !validZone) invalid = true
            else onSave(SavedPlace(id = placeId, name = name.trim(),
                latitude = coordinates.latitude, longitude = coordinates.longitude, zoneId = zone.trim(),
                areaName = areaName, fromCurrentLocation = fromCurrentLocation), makeHome)
        }, enabled = !busy && !saving) { Text(stringResource(R.string.save_place)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}
