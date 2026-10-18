package com.nipunrautela.agnihotra

import android.app.Application
import android.content.Context
import android.content.ComponentName
import android.appwidget.AppWidgetManager
import android.util.Log
import androidx.room.Room
import com.nipunrautela.agnihotra.analytics.Analytics
import com.nipunrautela.agnihotra.core.SolarCalculator
import com.nipunrautela.agnihotra.data.*
import com.nipunrautela.agnihotra.reminders.ReminderScheduler
import com.nipunrautela.agnihotra.widget.AgnihotraWidget
import com.nipunrautela.agnihotra.widget.AgnihotraWidgetReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import androidx.glance.appwidget.GlanceAppWidgetManager

class AgnihotraApplication : Application() {
    val graph by lazy { AppGraph(this) }
    override fun onCreate() {
        super.onCreate()
        graph.start()
    }
}

class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = Room.databaseBuilder(context, AppDatabase::class.java, "agnihotra.db")
        .addMigrations(AppDatabase.MIGRATION_1_2).build()
    val places = database.places()
    val preferences = PreferencesRepository(context)
    val calculator = SolarCalculator()
    val analytics = Analytics(context)
    val scheduler = ReminderScheduler(context)

    fun start() {
        scope.launch {
            combine(places.observe(), preferences.preferences) { places, prefs -> places to prefs }
                .collect { (places, prefs) ->
                    analytics.configure(prefs.analytics)
                    scheduler.reschedule(places.firstOrNull { it.id == prefs.activePlaceId }, prefs)
                    updateWidgets()
                }
        }
    }

    suspend fun refresh() {
        val prefs = preferences.preferences.first()
        scheduler.reschedule(places.all().firstOrNull { it.id == prefs.activePlaceId }, prefs)
        updateWidgets()
    }

    private suspend fun updateWidgets() {
        try {
            // Glance 1.1 persists provider class names, which R8 can rename between releases.
            // Android's registered widget IDs remain valid across an app upgrade.
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, AgnihotraWidgetReceiver::class.java))
            val manager = GlanceAppWidgetManager(context)
            val widget = AgnihotraWidget()
            ids.forEach { widget.update(context, manager.getGlanceIdBy(it)) }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // A launcher/widget failure must not stop future preference/alarm updates.
            Log.w("Agnihotra", "Widget update unavailable; will retry on the next refresh")
        }
    }
}

val Context.appGraph: AppGraph get() = (applicationContext as AgnihotraApplication).graph
