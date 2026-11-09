package com.davidsant7e.emperador.nucleo

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NucleoTest {

    private fun respuestaGemini(interno: String): String =
        Json.escribir(mapOf("candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to interno)))))))

    private val casa = Casa(
        nombre = "Casa Bogotá",
        habitaciones = listOf(
            Habitacion(
                id = "h1", nombre = "Cocina", fotos = listOf("a.jpg", "b.jpg"),
                objetos = listOf(
                    Objeto(id = "o1", nombre = "Nevera gris", categoria = "electrodomesticos", ubicacion = "junto a la puerta"),
                    Objeto(id = "o2", nombre = "Silla", categoria = "muebles", cantidad = 4, notas = "madera, \"vieja\""),
                    Objeto(id = "o3", nombre = "Microondas", categoria = "electrodomesticos", confirmado = false, foto = "b.jpg"),
                ),
            ),
            Habitacion(id = "h2", nombre = "Baño"),
        ),
    )

    @Test
    fun casaIdaYVueltaPorJson() {
        val vuelta = Casa.deJson(casa.aJson())
        assertEquals(casa, vuelta)
    }

    @Test
    fun jsonDañadoODesconocidoNoRompe() {
        val c = Casa.deJson("""{"habitaciones":[{"nombre":""},{"nombre":"Sala","objetos":[{"nombre":"Sofá","categoria":"Muebles","cantidad":0},{"x":1}]}]}""")
        assertEquals("Mi casa", c.nombre)
        assertEquals(1, c.habitaciones.size)
        val o = c.habitaciones[0].objetos.single()
        assertEquals("muebles", o.categoria)
        assertEquals(1, o.cantidad)
        assertTrue(o.confirmado)
    }

    @Test
    fun categoriasSeNormalizan() {
        assertEquals("electrodomesticos", Categorias.normalizar("Electrodomésticos"))
        assertEquals("bano", Categorias.normalizar("baño y aseo"))
        assertEquals("puertas-ventanas", Categorias.normalizar("puertas-ventanas"))
        assertEquals("otros", Categorias.normalizar("cosas raras"))
        assertEquals("otros", Categorias.normalizar(null))
    }

    @Test
    fun cuerpoParaGeminiLlevaFotosYRegistrados() {
        val cuerpo = Gemini.cuerpo("Cocina", listOf(Gemini.Foto("a.jpg", byteArrayOf(1, 2, 3))), casa.habitaciones[0].objetos.take(1))
        val m = Json.parse(cuerpo)
        val partes = m.lista("contents")!!.first().lista("parts")!!
        assertTrue(partes[0].texto("text")!!.contains("Nevera gris ×1"))
        assertEquals("Foto 1:", partes[1].texto("text"))
        assertEquals("AQID", partes[2].obj("inlineData").texto("data"))
        assertEquals("image/jpeg", partes[2].obj("inlineData").texto("mimeType"))
        val enumCat = m.obj("generationConfig").obj("responseSchema").obj("properties").obj("objetos").obj("items").obj("properties").obj("categoria").lista("enum")
        assertEquals(Categorias.CLAVES, enumCat)
    }

    @Test
    fun leerRespuestaDeGemini() {
        val interno = """{"tipo_habitacion":"Cocina","nota":"","objetos":[
            {"nombre":"estufa de 4 puestos","categoria":"electrodomesticos","cantidad":1,"ubicacion":"pared del fondo","foto":2},
            {"nombre":"Taburete","categoria":"Muebles","cantidad":3,"foto":9},
            {"nombre":"  ","categoria":"otros","cantidad":1}]}"""
        val r = Gemini.leer(respuestaGemini(interno), listOf("a.jpg", "b.jpg"))
        assertEquals("Cocina", r.tipoHabitacion)
        assertNull(r.nota)
        assertEquals(2, r.objetos.size)
        assertEquals("Estufa de 4 puestos", r.objetos[0].nombre)
        assertEquals("b.jpg", r.objetos[0].foto)
        assertNull(r.objetos[1].foto) // foto 9 no existe
        assertEquals(3, r.objetos[1].cantidad)
        assertTrue(r.objetos.none { it.confirmado })
    }

    @Test
    fun leerRespuestaConCercoMarkdown() {
        val r = Gemini.leer(respuestaGemini("```json\n{\"objetos\":[{\"nombre\":\"Lámpara\",\"categoria\":\"iluminacion\",\"cantidad\":1}]}\n```"), listOf("a.jpg"))
        assertEquals("Lámpara", r.objetos.single().nombre)
    }

    @Test
    fun respuestaBloqueadaDaError() {
        val bloqueada = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        try {
            Gemini.leer(bloqueada, listOf("a.jpg"))
            fail("debía fallar")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("SAFETY"))
        }
    }

    @Test
    fun analizarConTransporteFalso() {
        var urlUsada = ""
        var cabeceras = emptyMap<String, String>()
        val g = Gemini({ url, _, cab ->
            urlUsada = url; cabeceras = cab
            200 to respuestaGemini("""{"objetos":[{"nombre":"Cama doble","categoria":"muebles","cantidad":1}]}""")
        })
        val r = g.analizar("Habitación", listOf(Gemini.Foto("a.jpg", byteArrayOf(9))), emptyList(), " clave ")
        assertTrue(urlUsada.endsWith("/gemini-3.5-flash:generateContent"))
        assertEquals("clave", cabeceras["x-goog-api-key"])
        assertEquals("Cama doble", r.objetos.single().nombre)
    }

    @Test
    fun analizarSinClaveOConError() {
        val g = Gemini({ _, _, _ -> 429 to """{"error":{"message":"quota"}}""" })
        try { g.analizar("x", listOf(Gemini.Foto("a.jpg", byteArrayOf(1))), emptyList(), null); fail() } catch (e: Gemini.SinClaveException) { }
        try { g.analizar("x", listOf(Gemini.Foto("a.jpg", byteArrayOf(1))), emptyList(), "k"); fail() } catch (e: IOException) {
            assertTrue(e.message!!.contains("cupo"))
        }
    }

    @Test
    fun aplicarAnalisisConservaLoRevisado() {
        val h = casa.habitaciones[0]
        val nuevos = listOf(
            Objeto(nombre = "Nevera  GRIS", categoria = "electrodomesticos", confirmado = false), // ya revisada
            Objeto(nombre = "Licuadora", categoria = "electrodomesticos", confirmado = false),
            Objeto(nombre = "licuadora", categoria = "electrodomesticos", confirmado = false), // repetida
        )
        val r = Inventario.aplicarAnalisis(h, nuevos, "2026-11-09T16:00")
        assertEquals(listOf("Nevera gris", "Silla", "Licuadora"), r.objetos.map { it.nombre }) // el microondas sin revisar se reemplaza
        assertFalse(r.objetos.last().confirmado)
        assertEquals("2026-11-09T16:00", r.analizada)
    }

    @Test
    fun quitarFotoLimpiaReferencias() {
        val r = Inventario.quitarFoto(casa.habitaciones[0], "b.jpg")
        assertEquals(listOf("a.jpg"), r.fotos)
        assertNull(r.objetos.first { it.id == "o3" }.foto)
    }

    @Test
    fun csvConComillasYProteccionDeFormulas() {
        val c = casa.copy(habitaciones = casa.habitaciones + Habitacion(nombre = "Sala", objetos = listOf(Objeto(nombre = "=SUMA(A1)", ubicacion = "a, b"))))
        val csv = Inventario.csv(c)
        val lineas = csv.removePrefix("﻿").split("\r\n")
        assertEquals("Habitación,Objeto,Categoría,Cantidad,Ubicación,Notas,Revisado", lineas[0])
        assertEquals("Cocina,Silla,Muebles,4,,\"madera, \"\"vieja\"\"\",sí", lineas[1])
        assertEquals("Cocina,Microondas,Electrodomésticos,1,,,no", lineas[2])
        assertEquals("Cocina,Nevera gris,Electrodomésticos,1,junto a la puerta,,sí", lineas[3])
        assertEquals("Sala,'=SUMA(A1),Otros,1,\"a, b\",,sí", lineas[4])
        assertFalse(Inventario.csv(casa, incluirSinRevisar = false).contains("Microondas"))
    }

    @Test
    fun markdownParaClaude() {
        val md = Inventario.markdown(casa)
        assertTrue(md.startsWith("# Inventario de Casa Bogotá\n\n2 habitaciones · 6 objetos · 1 sin revisar"))
        assertTrue(md.contains("## Cocina (6)"))
        assertTrue(md.contains("**Electrodomésticos**\n- Microondas *\n- Nevera gris — junto a la puerta"))
        assertTrue(md.contains("- Silla ×4 — madera, \"vieja\""))
        assertTrue(md.contains("## Baño (0)\n- (sin objetos)"))
    }

    @Test
    fun resumenPorCategoria() {
        assertEquals(listOf("muebles" to 4, "electrodomesticos" to 2), Inventario.porCategoria(casa))
        assertEquals("inventario-casa-bogota-2026-11-09.csv", Inventario.nombreArchivo(casa, "csv", "2026-11-09"))
    }
}
