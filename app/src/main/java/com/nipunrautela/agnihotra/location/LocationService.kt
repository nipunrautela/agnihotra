package com.nipunrautela.agnihotra.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.nipunrautela.agnihotra.core.Coordinates
import com.nipunrautela.agnihotra.core.usableLocationFix
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import java.util.Locale

data class LocationFix(val coordinates: Coordinates, val accuracyMeters: Float, val elapsedRealtimeNanos: Long,
    val areaName: String? = null)

class LocationService(private val context: Context) {
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): LocationFix? = withContext(Dispatchers.Main) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java)
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { (precise || it != LocationManager.GPS_PROVIDER) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return@withContext null
        withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                        if (!usableLocationFix(location.latitude, location.longitude,
                                location.accuracy.takeIf { location.hasAccuracy() }, age)) return
                        if (continuation.isActive) {
                            runCatching { manager.removeUpdates(this) }
                            continuation.resume(LocationFix(Coordinates(location.latitude, location.longitude),
                                location.accuracy, location.elapsedRealtimeNanos))
                        }
                    }
                    @Deprecated("Legacy provider callback")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                var registered = false
                providers.forEach { provider ->
                    if (continuation.isActive) {
                        try {
                            manager.requestLocationUpdates(provider, 0, 0f, listener, Looper.getMainLooper())
                            registered = true
                        } catch (_: SecurityException) { /* Permission revoked; try remaining providers. */ }
                        catch (_: IllegalArgumentException) { /* Provider disappeared during registration. */ }
                    }
                }
                // Cancellation may race with registration on a provider binder call.
                if (!continuation.isActive) runCatching { manager.removeUpdates(listener) }
                else if (!registered) continuation.resume(null)
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun areaName(coordinates: Coordinates, locale: Locale): String? = withTimeoutOrNull(5_000) {
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            try {
                val geocoder = Geocoder(context, locale)
                val addresses = if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine<List<Address>> { continuation ->
                        geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) continuation.resume(addresses)
                                }
                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) continuation.resume(emptyList())
                                }
                            })
                    }
                } else {
                    // Legacy geocoding can block its worker thread; never block the UI or saving.
                    geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1).orEmpty()
                }
                addresses.firstOrNull()?.let {
                    areaLabel(it.subLocality, it.locality, it.subAdminArea, it.adminArea)
                }
            } catch (_: java.io.IOException) { null }
            catch (_: IllegalArgumentException) { null }
            catch (_: SecurityException) { null }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun search(query: String): List<Address> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext emptyList()
        try { Geocoder(context).getFromLocationName(query, 5).orEmpty().filter {
            it.hasLatitude() && it.hasLongitude() && it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
                it.longitude.isFinite() && it.longitude in -180.0..180.0
        } }
        catch (_: java.io.IOException) { emptyList() }
        catch (_: IllegalArgumentException) { emptyList() }
    }
}
