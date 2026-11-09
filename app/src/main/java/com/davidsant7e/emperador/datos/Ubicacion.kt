package com.davidsant7e.emperador.datos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Ubicación actual con el GPS del celular (sin Google Play Services), para ubicar la casa. */
object Ubicacion {

    fun tienePermiso(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** La ubicación actual, o la última conocida si el GPS tarda más de `esperaMs`. */
    @SuppressLint("MissingPermission")
    suspend fun actual(ctx: Context, esperaMs: Long = 12_000): Pair<Double, Double>? {
        if (!tienePermiso(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (proveedor in proveedores) {
            val loc = withTimeoutOrNull(esperaMs) { una(ctx, lm, proveedor) }
            if (loc != null) return loc.latitude to loc.longitude
        }
        val ultima = proveedores.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return ultima?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    private suspend fun una(ctx: Context, lm: LocationManager, proveedor: String): Location? =
        suspendCancellableCoroutine { cont ->
            val cancelar = CancellationSignal()
            cont.invokeOnCancellation { cancelar.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, proveedor, cancelar, ContextCompat.getMainExecutor(ctx)) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }
}
