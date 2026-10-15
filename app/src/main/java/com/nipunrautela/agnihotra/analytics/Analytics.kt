package com.nipunrautela.agnihotra.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/** Typed, parameter-free events prevent place labels or devotional activity leaking into events. */
enum class UsageEvent(val eventName: String) {
    LOCATION_SETUP_COMPLETED("location_setup_completed"),
    REMINDER_SETTINGS_CHANGED("reminder_settings_changed"),
    TIMETABLE_OPENED("timetable_opened"),
    LOCATION_SETUP_OPENED("location_setup_opened"),
    SETTINGS_OPENED("settings_opened"),
    NOTIFICATION_ACCESS_GRANTED("notification_access_granted"),
    TIMETABLE_EXPORT_COMPLETED("timetable_export_completed"),
    TIMETABLE_EXPORT_FAILED("timetable_export_failed"),
    WIDGET_PIN_REQUESTED("widget_pin_requested"),
    WIDGET_PIN_COMPLETED("widget_pin_completed"),
    WIDGET_CONFIGURED("widget_configured"),
    CHIME_PREVIEWED("chime_previewed"),
}

class Analytics(private val context: Context) {
    private var firebase: FirebaseAnalytics? = null
    private val collection = AnalyticsCollectionGate()
    val configured: Boolean @Synchronized get() = firebase != null

    @Synchronized
    fun configure(collectionEnabled: Boolean) {
        collection.applyPersisted(collectionEnabled)
        applyCollection()
    }

    private fun applyCollection() {
        // Don't initialize an SDK with a previously persisted enabled flag on an opt-out startup.
        if (firebase == null && collection.enabled && FirebaseApp.initializeApp(context) != null) {
            firebase = FirebaseAnalytics.getInstance(context)
        }
        firebase?.setConsent(mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (collection.enabled)
                    FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ))
        firebase?.setAnalyticsCollectionEnabled(collection.enabled)
    }

    @Synchronized
    fun beginChoice() {
        collection.beginChoice()
        firebase?.setAnalyticsCollectionEnabled(false)
    }

    @Synchronized
    fun choicePersisted(value: Boolean) {
        collection.choicePersisted(value)
        applyCollection()
    }

    @Synchronized
    fun record(event: UsageEvent) {
        if (collection.enabled) firebase?.logEvent(event.eventName, null)
    }
}
