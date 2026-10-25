package com.nipunrautela.agnihotra.ui

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import androidx.core.app.ActivityCompat
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.analytics.UsageEvent
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.widget.AgnihotraWidgetReceiver
import com.nipunrautela.agnihotra.widget.WidgetPinReceiver
import java.util.Locale
import com.nipunrautela.agnihotra.audio.PracticePlaybackService

enum class Page(val label: Int) {
    TODAY(R.string.today), TIMETABLE(R.string.timetable), MANTRAS(R.string.mantras),
    PLACES(R.string.places), SETTINGS(R.string.settings),
    PRIVACY(R.string.privacy_details), FOCUS(R.string.focus_mode), PRACTICE(R.string.session_running),
}

@Composable
fun AgnihotraApp(model: AppViewModel, sessionRequest: Int = 0) {
    val state by model.state.collectAsStateWithLifecycle()
    AgnihotraAppearance(state.preferences) { AppContent(model, state, sessionRequest) }
}

@Composable
fun AgnihotraAppearance(prefs: AppPreferences, content: @Composable () -> Unit) {
    val originalContext = LocalContext.current
    val originalConfiguration = LocalConfiguration.current
    val configuration = remember(prefs.language, originalConfiguration) {
        Configuration(originalConfiguration).apply {
            if (prefs.language != "system") setLocale(Locale.forLanguageTag(prefs.language))
        }
    }
    // Keep the Activity in the ContextWrapper chain so permission launchers,
    // back handling, and settings navigation still find their Activity owner.
    val localizedContext = remember<Context>(configuration, originalContext) {
        val wrapper = ContextThemeWrapper(originalContext, R.style.Theme_Agnihotra)
        wrapper.applyOverrideConfiguration(configuration)
        wrapper
    }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration) {
        AgnihotraTheme(prefs.theme) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(model: AppViewModel, state: AppState, sessionRequest: Int) {
    val context = LocalContext.current
    var pageName by rememberSaveable { mutableStateOf(Page.TODAY.name) }
    val page = Page.valueOf(pageName)
    var sessionOptions by rememberSaveable { mutableStateOf(false) }
    val practice by PracticePlaybackService.state.collectAsStateWithLifecycle()
    LaunchedEffect(sessionRequest) { if (sessionRequest > 0) pageName = Page.PRACTICE.name }
    var addPlace by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = state.places.firstOrNull { it.id == editingId }
    var privacyPrevious by rememberSaveable { mutableStateOf(Page.SETTINGS.name) }
    val snackbar = remember { SnackbarHostState() }
    val message by model.message.collectAsStateWithLifecycle()
    val messageText = message?.let { stringResource(it) }
    val travel by model.travel.collectAsStateWithLifecycle()
    val savingPlace by model.savingPlace.collectAsStateWithLifecycle()
    val savedPlaceId by model.savedPlaceId.collectAsStateWithLifecycle()
    LaunchedEffect(savedPlaceId) {
        if (savedPlaceId != null) {
            addPlace = false
            pageName = Page.TODAY.name
            model.savedPlaceId.value = null
        }
    }
    var accessRevision by remember { mutableIntStateOf(0) }
    val exactAccess = remember(accessRevision) { model.graph.scheduler.hasExactAccess() }
    val notificationAccess = remember(accessRevision, state.preferences.sound, state.preferences.vibration) {
        model.graph.scheduler.hasNotificationAccess(state.preferences)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { accessRevision++; model.checkTravel(); model.refreshAreaName() }
            if (event == Lifecycle.Event.ON_STOP) model.stopLocation()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.loading, state.activePlace) {
        if (!state.loading && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            model.checkTravel()
            model.refreshAreaName()
        }
    }
    LaunchedEffect(messageText) {
        messageText?.let { snackbar.showSnackbar(it); model.message.value = null }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) model.locate() else model.message.value = R.string.location_denied
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        accessRevision++
        if (granted) model.graph.analytics.record(UsageEvent.NOTIFICATION_ACCESS_GRANTED)
    }
    val widgetManager = context.getSystemService(AppWidgetManager::class.java)
    val locate = {
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val showAdd = {
        model.graph.analytics.record(UsageEvent.LOCATION_SETUP_OPENED)
        editingId = null
        model.locationFix.value = null
        model.searchResults.value = emptyList()
        model.placeSaveFailed.value = false
        addPlace = true
    }
    BackHandler(page != Page.TODAY || addPlace) {
        if (addPlace) { if (!savingPlace) addPlace = false }
        else pageName = if (page == Page.PRIVACY) privacyPrevious else Page.TODAY.name
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(if (page == Page.TODAY) R.string.app_name else page.label), style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    if (page in listOf(Page.SETTINGS, Page.PRIVACY, Page.FOCUS)) IconButton(onClick = {
                        pageName = if (page == Page.PRIVACY) privacyPrevious else Page.TODAY.name
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (page !in listOf(Page.SETTINGS, Page.PRIVACY, Page.FOCUS)) IconButton(onClick = {
                        model.graph.analytics.record(UsageEvent.SETTINGS_OPENED)
                        pageName = Page.SETTINGS.name
                    }) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (!state.loading && page !in listOf(Page.SETTINGS, Page.PRIVACY, Page.FOCUS)) NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)) {
                listOf(Page.TODAY, Page.TIMETABLE, Page.PRACTICE, Page.MANTRAS, Page.PLACES).forEach { destination ->
                    if (destination == Page.PRACTICE) {
                        val description = stringResource(if (practice.active) R.string.session_open else R.string.practice_start)
                        Box(Modifier.weight(1f).heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
                            FloatingActionButton(onClick = {
                                if (!practice.active) PracticePlaybackService.start(context, state.preferences)
                                pageName = Page.PRACTICE.name
                            }, shape = CircleShape, modifier = Modifier.size(64.dp).testTag("start_agnihotra")
                                .semantics { contentDescription = description }) {
                                Image(painterResource(R.drawable.ic_agnihotra), null, Modifier.size(56.dp).clip(CircleShape))
                            }
                        }
                        return@forEach
                    }
                    val icon = when (destination) {
                        Page.TODAY -> Icons.Outlined.WbSunny
                        Page.TIMETABLE -> Icons.Outlined.CalendarMonth
                        Page.MANTRAS -> Icons.AutoMirrored.Outlined.MenuBook
                        else -> Icons.Outlined.Place
                    }
                    NavigationBarItem(selected = page == destination, onClick = {
                        pageName = destination.name
                        if (destination == Page.TIMETABLE) model.graph.analytics.record(UsageEvent.TIMETABLE_OPENED)
                    }, icon = { Icon(icon, null) }, label = { Text(stringResource(destination.label)) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
                page == Page.PRIVACY -> PrivacyPage()
                page == Page.SETTINGS -> SettingsPage(state.preferences, model.graph.analytics.configured,
                    notificationAccess, exactAccess, model::boolean, model::string, model::preparation,
                    onNotifications = {
                        val activity = context.activityOrNull()
                        val canRequest = Build.VERSION.SDK_INT >= 33 && (!state.preferences.notificationAsked || (activity != null &&
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)))
                        if (Build.VERSION.SDK_INT >= 33 && !model.graph.scheduler.hasNotificationAccess() && canRequest) {
                            model.boolean("notification_asked", true)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else context.startActivity(model.graph.scheduler.notificationSettingsIntent(state.preferences))
                    }, onExactAlarms = {
                        if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")))
                    }, onPrivacy = { privacyPrevious = Page.SETTINGS.name; pageName = Page.PRIVACY.name },
                    onAddWidget = if (widgetManager.isRequestPinAppWidgetSupported) ({
                        val callback = PendingIntent.getBroadcast(context, 50, Intent(context, WidgetPinReceiver::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        if (widgetManager.requestPinAppWidget(ComponentName(context, AgnihotraWidgetReceiver::class.java), null, callback))
                            model.graph.analytics.record(UsageEvent.WIDGET_PIN_REQUESTED)
                        Unit
                    }) else null,
                    onChimePreview = { model.graph.analytics.record(UsageEvent.CHIME_PREVIEWED) },
                    onAlarmDuration = model::alarmDuration, onSessionOptions = { sessionOptions = true })
                page == Page.PRACTICE -> TimedPracticePage(state.preferences, state.activePlace?.name,
                    onOptions = { sessionOptions = true }, onMantras = { pageName = Page.MANTRAS.name })
                state.places.isEmpty() && page in listOf(Page.TODAY, Page.FOCUS) -> WelcomeScreen(showAdd)
                page == Page.FOCUS -> FocusPage(state, model.graph.calculator, model::boolean,
                    model::practiceQuiet, model::practiceMusic, model::bundledPracticeMusic, onDuration = model::practiceDuration,
                    onTimedSession = { pageName = Page.PRACTICE.name })
                page == Page.TODAY -> TodayPage(state, model.graph.calculator,
                    onPlaces = { pageName = Page.PLACES.name }, onReminders = { pageName = Page.SETTINGS.name },
                    remindersReady = exactAccess && notificationAccess,
                    onFocus = { pageName = Page.FOCUS.name }, onMantras = { pageName = Page.MANTRAS.name })
                page == Page.TIMETABLE -> TimetablePage(state.activePlace, model.graph.calculator, state.preferences)
                page == Page.MANTRAS -> MantrasPage(state.preferences, model::boolean)
                page == Page.PLACES -> PlacesPage(state, showAdd, model::select, model::home, model::delete) {
                    editingId = it.id; model.locationFix.value = null; model.searchResults.value = emptyList()
                    model.placeSaveFailed.value = false; addPlace = true
                }
            }
        }
    }
    if (sessionOptions) SessionOptionsSheet(state.preferences, model::practiceDuration, model::practiceQuiet,
        model::boolean, model::practiceMusic, model::bundledPracticeMusic, onDismiss = { sessionOptions = false })
    if (addPlace && !state.loading) PlaceDialog(model, editing, locate,
        onDismiss = { if (!savingPlace) addPlace = false }, onSave = { place, makeHome -> model.save(place, makeHome) })
    travel?.let { suggestion ->
        AlertDialog(onDismissRequest = { model.dismissTravel() },
            title = { Text(stringResource(R.string.travel_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.travel_body, suggestion.distanceKm))
                TextButton(onClick = {
                    editingId = null; model.searchResults.value = emptyList()
                    model.placeSaveFailed.value = false
                    model.useCurrentFix(suggestion.fix); model.dismissTravel(); addPlace = true
                }) { Text(stringResource(R.string.save_current_place)) }
            } },
            confirmButton = { TextButton(onClick = {
                suggestion.nearestPlace?.let { model.select(it.id) } ?: run { pageName = Page.PLACES.name }
                model.dismissTravel()
            }) {
                Text(suggestion.nearestPlace?.let { stringResource(R.string.use_nearby_place, it.name) } ?: stringResource(R.string.choose_another_place))
            } }, dismissButton = { TextButton(onClick = { model.dismissTravel() }) {
                Text(stringResource(R.string.keep_current_place))
            } })
    }
}

internal fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}
