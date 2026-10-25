package com.nipunrautela.agnihotra

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nipunrautela.agnihotra.location.LocationService
import com.nipunrautela.agnihotra.ui.AgnihotraApp
import com.nipunrautela.agnihotra.ui.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val sessionRequests = MutableStateFlow(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bar transparency/cutout handling comes from the theme; avoid deprecated color setters.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        useWidgetPlace(intent)
        openSession(intent)
        setContent {
            val model: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(appGraph, LocationService(applicationContext)) as T
            })
            val request by sessionRequests.collectAsStateWithLifecycle()
            AgnihotraApp(model, request)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        useWidgetPlace(intent)
        openSession(intent)
    }

    private fun openSession(intent: Intent) {
        if (intent.getBooleanExtra("open_practice_session", false)) {
            intent.removeExtra("open_practice_session")
            sessionRequests.value++
        }
    }

    private fun useWidgetPlace(intent: Intent) {
        val id = intent.getStringExtra("widget_place_id") ?: return
        intent.removeExtra("widget_place_id")
        lifecycleScope.launch {
            if (appGraph.places.all().any { it.id == id }) appGraph.preferences.selectPlace(id)
        }
    }
}
