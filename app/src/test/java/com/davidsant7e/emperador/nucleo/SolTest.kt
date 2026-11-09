package com.davidsant7e.emperador.nucleo

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val lat = 4.711
    private val lon = -74.072

    private fun maximo(fecha: LocalDate): Sol.Posicion =
        (0 until 24 * 60 step 2).map { Sol.posicion(lat, lon, ZonedDateTime.of(fecha, LocalTime.ofSecondOfDay(it * 60L), bogota).toInstant()) }
            .maxBy { it.altura }

    @Test
    fun alturaYAzimutAlMediodiaEnBogota() {
        // Al mediodía solar la altura es 90° − |latitud − declinación|.
        val marzo = maximo(LocalDate.of(2026, 3, 20))
        assertEquals(85.3, marzo.altura, 0.8)
        val junio = maximo(LocalDate.of(2026, 6, 21))
        assertEquals(71.3, junio.altura, 0.5)
        assertTrue("en junio el sol pasa por el norte", Sol.diferencia(junio.azimut, 0.0) < 5)
        val diciembre = maximo(LocalDate.of(2026, 12, 21))
        assertEquals(61.9, diciembre.altura, 0.5)
        assertEquals(180.0, diciembre.azimut, 5.0)
    }

    @Test
    fun amanecerAlOrienteYMediodiaSolarCercaDeLas12() {
        val p = Sol.posicion(lat, lon, ZonedDateTime.of(2026, 3, 20, 8, 0, 0, 0, bogota).toInstant())
        assertEquals(90.0, p.azimut, 5.0) // en el equinoccio sale casi por el oriente exacto
        assertTrue(p.altura in 20.0..35.0)
        val noche = Sol.posicion(lat, lon, ZonedDateTime.of(2026, 3, 20, 23, 0, 0, 0, bogota).toInstant())
        assertTrue(noche.altura < -30)
    }

    @Test
    fun horasDeSolPorFachada() {
        val junio = LocalDate.of(2026, 6, 21)
        val diciembre = LocalDate.of(2026, 12, 21)
        val marzo = LocalDate.of(2026, 3, 20)
        val norteJunio = Sol.dia(lat, lon, 0.0, junio, bogota)
        assertEquals(1, norteJunio.tramos.size)
        assertTrue("fachada norte en junio: casi todo el día (${norteJunio.horas})", norteJunio.horas in 10.5..12.0)
        assertEquals(0.0, Sol.dia(lat, lon, 180.0, junio, bogota).horas, 0.01)
        assertEquals(0.0, Sol.dia(lat, lon, 0.0, diciembre, bogota).horas, 0.01)
        val oriente = Sol.dia(lat, lon, 90.0, marzo, bogota)
        assertTrue("oriente: mañana", oriente.tramos.single().inicio.hour in 5..6 && oriente.tramos.single().fin.hour in 11..12)
        val occidente = Sol.dia(lat, lon, 270.0, marzo, bogota)
        assertTrue("occidente: tarde", occidente.tramos.single().inicio.hour in 11..12 && occidente.tramos.single().fin.hour in 17..18)
        assertEquals(oriente.horas, occidente.horas, 0.4)
    }

    @Test
    fun puntosCardinales() {
        assertEquals("N", Sol.punto(359.0))
        assertEquals("NE", Sol.punto(30.0))
        assertEquals("SE", Sol.punto(135.0))
        assertEquals("O", Sol.punto(-90.0))
        assertEquals("noroccidente", Sol.nombrePunto(310.0))
        assertEquals(225.0, Sol.azimutDe("so")!!, 0.0)
        assertEquals(270.0, Sol.azimutDe("W")!!, 0.0)
        assertNull(Sol.azimutDe("X"))
        assertEquals(20.0, Sol.diferencia(350.0, 10.0), 1e-9)
    }

    @Test
    fun coordenadasEscritasOPegadas() {
        assertEquals(4.711 to -74.072, Coordenadas.leer("4.711, -74.072"))
        assertEquals(4.711 to -74.072, Coordenadas.leer("4,711; -74,072"))
        assertEquals(4.6097 to -74.0817, Coordenadas.leer("https://www.google.com/maps/place/X/@4.61,-74.08,17z/data=!3m1!4b1!4m6!3m5!8m2!3d4.6097!4d-74.0817"))
        assertEquals(4.61 to -74.08, Coordenadas.leer("https://www.google.com/maps/@4.61,-74.08,15z"))
        assertEquals(4.61 to -74.08, Coordenadas.leer("geo:4.61,-74.08?z=17"))
        assertEquals(4.61 to -74.08, Coordenadas.leer("https://maps.google.com/?q=4.61,-74.08"))
        assertNull(Coordenadas.leer("Calle 100 # 15-20"))
        assertNull(Coordenadas.leer("95, 10"))
    }

    @Test
    fun casaConUbicacionYAberturasEnJsonYTexto() {
        val casa = Casa(
            nombre = "Casa", latitud = lat, longitud = lon,
            habitaciones = listOf(Habitacion(nombre = "Sala", aberturas = listOf(Abertura(tipo = "ventana", azimut = 92.34, medida = "brujula"), Abertura(tipo = "balcon", azimut = 0.0)))),
        )
        val vuelta = Casa.deJson(casa.aJson())
        assertEquals(lat, vuelta.latitud!!, 1e-9)
        assertEquals(92.3, vuelta.habitaciones[0].aberturas[0].azimut, 1e-9)
        assertEquals("brujula", vuelta.habitaciones[0].aberturas[0].medida)
        val md = Inventario.markdown(casa, bogota, 2026)
        assertTrue(md, md.contains("Ubicación: 4.71100, -74.07200 (zona horaria America/Bogota)"))
        assertTrue(md, md.contains("Orientación: Ventana hacia el oriente E (92°), medida con brújula; Balcón hacia el norte N (0°)"))
        assertTrue(md, md.contains("## Soleamiento"))
        assertTrue(md, md.contains("**Sala · Balcón N (0°)**\n- Equinoccio de marzo (2026-03-20): "))
        assertTrue(md, md.contains("- Solsticio de diciembre (2026-12-21): sin sol directo"))
        val sinUbicacion = Inventario.markdown(casa.copy(latitud = null, longitud = null), bogota, 2026)
        assertTrue(sinUbicacion.contains("Falta la ubicación"))
    }
}
