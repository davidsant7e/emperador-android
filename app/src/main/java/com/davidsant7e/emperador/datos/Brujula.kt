package com.davidsant7e.emperador.datos

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.davidsant7e.emperador.nucleo.Sol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Brújula para medir hacia dónde mira una ventana: el teléfono se sostiene derecho (vertical), con la
 * cámara de atrás apuntando hacia afuera por la ventana. Devuelve el azimut respecto al norte VERDADERO
 * (corrige la declinación magnética con la ubicación de la casa, unos −7° en Bogotá).
 */
object Brujula {
    data class Lectura(
        val azimut: Double,
        /** 0 = poco fiable (hay que calibrar moviendo el teléfono en forma de 8) … 3 = alta. */
        val precision: Int,
        /** Inclinación respecto a la vertical: si es grande, la lectura no sirve. */
        val inclinado: Boolean,
    )

    fun disponible(ctx: Context): Boolean =
        (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    fun lecturas(ctx: Context, latitud: Double?, longitud: Double?): Flow<Lectura> = callbackFlow {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val declinacion = if (latitud != null && longitud != null) {
            GeomagneticField(latitud.toFloat(), longitud.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
        } else 0.0
        val r = FloatArray(9)
        val remapeada = FloatArray(9)
        val angulos = FloatArray(3)
        var precision = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        var suave: Double? = null
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                // Teléfono vertical: el eje que importa es el que sale por la cámara de atrás (−Z del teléfono).
                SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapeada)
                SensorManager.getOrientation(remapeada, angulos)
                val magnetico = Math.toDegrees(angulos[0].toDouble())
                val verdadero = Sol.norm360(magnetico + declinacion)
                // Suavizado circular para que el número no salte.
                val anterior = suave
                suave = if (anterior == null) verdadero else {
                    var d = verdadero - anterior
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    Sol.norm360(anterior + d * 0.15)
                }
                val inclinacion = Math.toDegrees(angulos[1].toDouble()) // ~0 cuando está vertical
                trySend(Lectura(suave!!, precision, kotlin.math.abs(inclinacion) > 35))
            }

            override fun onAccuracyChanged(s: Sensor, a: Int) {
                precision = a
            }
        }
        sm.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(oyente) }
    }
}
