package com.davidsant7e.emperador.nucleo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Posición del sol y horas de sol directo sobre una fachada (estudio de soleamiento básico).
 * Algoritmo de la NOAA (precisión de ~0,5° para fechas entre 1950 y 2050), sin dependencias.
 * Azimut en grados desde el norte verdadero, en el sentido del reloj (90 = este).
 */
object Sol {

    data class Posicion(val altura: Double, val azimut: Double)

    /** Un tramo de sol directo, en hora local. */
    data class Tramo(val inicio: LocalTime, val fin: LocalTime) {
        val horas: Double get() = (fin.toSecondOfDay() - inicio.toSecondOfDay()) / 3600.0
    }

    data class Dia(val fecha: LocalDate, val tramos: List<Tramo>) {
        val horas: Double get() = tramos.sumOf { it.horas }
    }

    fun posicion(latitud: Double, longitud: Double, momento: Instant): Posicion {
        val jd = momento.epochSecond / 86400.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = norm360(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mr = rad(m)
        val c = sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * mr) * (0.019993 - 0.000101 * t) + sin(3 * mr) * 0.000289
        val verdadera = l0 + c
        val omega = 125.04 - 1934.136 * t
        val aparente = verdadera - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val decl = asin(sin(rad(eps)) * sin(rad(aparente)))
        val y = Math.tan(rad(eps / 2)).let { it * it }
        val l0r = rad(l0)
        val ecuTiempo = 4 * grados(
            y * sin(2 * l0r) - 2 * e * sin(mr) + 4 * e * y * sin(mr) * cos(2 * l0r) - 0.5 * y * y * sin(4 * l0r) - 1.25 * e * e * sin(2 * mr),
        ) // minutos
        val minutosUtc = (momento.epochSecond % 86400 + 86400) % 86400 / 60.0
        val tiempoSolar = minutosUtc + ecuTiempo + 4 * longitud
        var angHorario = tiempoSolar / 4 - 180
        if (angHorario < -180) angHorario += 360
        val lat = rad(latitud)
        val ha = rad(angHorario)
        val cosZenit = (sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(ha)).coerceIn(-1.0, 1.0)
        val zenit = acos(cosZenit)
        val azimut = norm360(grados(atan2(sin(ha), cos(ha) * sin(lat) - Math.tan(decl) * cos(lat))) + 180)
        return Posicion(altura = 90 - grados(zenit), azimut = azimut)
    }

    /**
     * ¿Le da el sol a una fachada que mira hacia [azimutFachada]? El sol tiene que estar sobre el horizonte
     * (más de [alturaMinima] grados, para descontar el sol rasante) y delante de la fachada (a menos de 90°).
     */
    fun daSol(p: Posicion, azimutFachada: Double, alturaMinima: Double = 3.0): Boolean =
        p.altura > alturaMinima && diferencia(p.azimut, azimutFachada) < 90.0

    /** Tramos de sol directo sobre la fachada ese día, muestreando cada [pasoMin] minutos en hora local. */
    fun dia(latitud: Double, longitud: Double, azimutFachada: Double, fecha: LocalDate, zona: ZoneId, pasoMin: Int = 5): Dia {
        val tramos = ArrayList<Tramo>()
        var inicio: LocalTime? = null
        var minuto = 0
        while (minuto <= 24 * 60) {
            val hora = if (minuto == 24 * 60) LocalTime.MAX else LocalTime.ofSecondOfDay(minuto * 60L)
            val instante = ZonedDateTime.of(fecha, hora, zona).toInstant()
            val sol = minuto < 24 * 60 && daSol(posicion(latitud, longitud, instante), azimutFachada)
            if (sol && inicio == null) inicio = hora
            if (!sol && inicio != null) {
                tramos += Tramo(inicio, hora)
                inicio = null
            }
            minuto += pasoMin
        }
        return Dia(fecha, tramos)
    }

    /** Fechas de referencia del estudio: equinoccios y solsticios del año. */
    fun fechasClave(anio: Int): List<Pair<String, LocalDate>> = listOf(
        "Equinoccio de marzo" to LocalDate.of(anio, 3, 20),
        "Solsticio de junio" to LocalDate.of(anio, 6, 21),
        "Equinoccio de septiembre" to LocalDate.of(anio, 9, 22),
        "Solsticio de diciembre" to LocalDate.of(anio, 12, 21),
    )

    // ---------- direcciones ----------

    val PUNTOS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    private val NOMBRES = listOf("norte", "nororiente", "oriente", "suroriente", "sur", "suroccidente", "occidente", "noroccidente")

    fun punto(azimut: Double): String = PUNTOS[(floor((norm360(azimut) + 22.5) / 45.0).toInt()) % 8]

    fun nombrePunto(azimut: Double): String = NOMBRES[PUNTOS.indexOf(punto(azimut))]

    fun azimutDe(punto: String): Double? = PUNTOS.indexOf(punto.uppercase().replace("W", "O")).takeIf { it >= 0 }?.let { it * 45.0 }

    /** Diferencia angular mínima (0–180). */
    fun diferencia(a: Double, b: Double): Double {
        val d = abs(norm360(a) - norm360(b))
        return if (d > 180) 360 - d else d
    }

    fun norm360(g: Double): Double = ((g % 360) + 360) % 360

    private fun rad(g: Double) = Math.toRadians(g)
    private fun grados(r: Double) = Math.toDegrees(r)
}
