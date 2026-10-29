package cl.kcorp.syncpass.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationProvider {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val providers = if (fineGranted) {
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
        } else {
            listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
        }
        val lastKnown = providers
            .asSequence()
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FIVE_MINUTES_MS) {
            return lastKnown
        }

        val activeProviders = if (fineGranted) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER)
        }
        val activeProvider = activeProviders
            .firstOrNull { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
            ?: return null

        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                runCatching {
                    manager.requestLocationUpdates(
                        activeProvider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    runCatching { manager.removeUpdates(listener) }
                }
            }
        }
        return fresh
    }

    fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            startLatitude,
            startLongitude,
            endLatitude,
            endLongitude,
            result,
        )
        return result[0]
    }

    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
    private const val LOCATION_TIMEOUT_MS = 8_000L
}
