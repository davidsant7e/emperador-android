package com.davidsant7e.emperador.nucleo

import java.io.IOException
import java.net.URLEncoder
import java.util.Base64

/**
 * Reconoce los objetos de una habitación a partir de sus fotos con Gemini (visión).
 * Las fotos viajan a Google con tu clave; nada más (ni el resto de la casa) se envía.
 *
 * El transporte se inyecta para probar sin red: (url, cuerpo, cabeceras) → (código HTTP, texto).
 */
class Gemini(
    private val transporte: (String, String, Map<String, String>) -> Pair<Int, String>,
    private val base: String = "https://generativelanguage.googleapis.com/v1beta/models/",
) {
    class SinClaveException : IOException("Falta la clave de Gemini")

    /** Foto lista para enviar: nombre del archivo (para saber dónde aparece cada objeto) y su JPEG. */
    class Foto(val archivo: String, val jpeg: ByteArray)

    data class Resultado(val objetos: List<Objeto>, val tipoHabitacion: String?, val nota: String?)

    fun analizar(
        habitacion: String,
        fotos: List<Foto>,
        yaRegistrados: List<Objeto>,
        clave: String?,
        modelo: String = MODELO,
    ): Resultado {
        if (clave.isNullOrBlank()) throw SinClaveException()
        require(fotos.isNotEmpty()) { "La habitación no tiene fotos" }
        val url = base + URLEncoder.encode(modelo.trim(), "UTF-8") + ":generateContent"
        val (codigo, json) = transporte(url, cuerpo(habitacion, fotos, yaRegistrados), mapOf("x-goog-api-key" to clave.trim()))
        if (codigo !in 200..299) throw IOException(mensajeDeError(codigo, json))
        return leer(json, fotos.map { it.archivo })
    }

    companion object {
        const val MODELO = "gemini-3.5-flash"
        const val MAX_FOTOS = 8

        private val INSTRUCCIONES = """
            Eres El Emperador, un asistente que hace el inventario de una casa a partir de fotos.
            Te llegan una o varias fotos de UNA misma habitación (pueden mostrar los mismos objetos desde
            ángulos distintos). Haz la lista de los objetos que se ven:
            - muebles, electrodomésticos, electrónica, lámparas, cortinas y tapetes, decoración, plantas,
              organizadores, objetos sueltos que se distingan bien, y también puertas y ventanas;
            - NO cuentes dos veces lo que aparece en varias fotos: usa la posición y el aspecto para saber si es el mismo;
            - agrupa los idénticos con "cantidad" (por ejemplo 4 sillas iguales = 1 ítem con cantidad 4);
            - no incluyas paredes, piso, techo, enchufes ni personas o mascotas; no adivines lo que no se ve
              (lo que está dentro de cajones o cajas cerradas no se lista);
            - "nombre": corto y concreto en español, con el rasgo que lo distingue si sirve
              (por ejemplo «Sofá gris de 3 puestos», «Televisor de unas 50 pulgadas»);
            - "categoria": una de las claves permitidas;
            - "ubicacion": dónde está en la habitación, en pocas palabras («junto a la ventana», «pared del fondo»);
            - "foto": número (desde 1) de la foto donde mejor se ve;
            - "notas": solo si aporta (marca visible, estado: «tapizado roto»); si no, vacío.
            Si te paso objetos YA REGISTRADOS, no los repitas: lista solo lo que falte.
            En "tipo_habitacion" di qué parece la habitación (Cocina, Sala, Habitación principal, Baño…).
            En "nota", una frase si las fotos no alcanzan para ver algo (oscuras, borrosas, falta una pared); si no, vacío.
        """.trimIndent()

        fun cuerpo(habitacion: String, fotos: List<Foto>, yaRegistrados: List<Objeto>): String {
            val partes = ArrayList<Any?>()
            val contexto = StringBuilder("Habitación: ").append(habitacion.ifBlank { "(sin nombre)" }).append('\n')
            contexto.append("Fotos: ").append(fotos.size).append('\n')
            if (yaRegistrados.isNotEmpty()) {
                contexto.append("YA REGISTRADOS (no repetir):\n")
                yaRegistrados.forEach { contexto.append("- ").append(it.nombre).append(" ×").append(it.cantidad).append('\n') }
            }
            partes += mapOf("text" to contexto.toString())
            fotos.forEachIndexed { i, f ->
                partes += mapOf("text" to "Foto ${i + 1}:")
                partes += mapOf("inlineData" to mapOf("mimeType" to "image/jpeg", "data" to Base64.getEncoder().encodeToString(f.jpeg)))
            }
            return Json.escribir(
                mapOf(
                    "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to INSTRUCCIONES))),
                    "contents" to listOf(mapOf("role" to "user", "parts" to partes)),
                    "generationConfig" to mapOf(
                        "temperature" to 0.2,
                        "responseMimeType" to "application/json",
                        "responseSchema" to mapOf(
                            "type" to "OBJECT",
                            "properties" to mapOf(
                                "tipo_habitacion" to mapOf("type" to "STRING"),
                                "nota" to mapOf("type" to "STRING"),
                                "objetos" to mapOf(
                                    "type" to "ARRAY",
                                    "items" to mapOf(
                                        "type" to "OBJECT",
                                        "properties" to mapOf(
                                            "nombre" to mapOf("type" to "STRING"),
                                            "categoria" to mapOf("type" to "STRING", "enum" to Categorias.CLAVES),
                                            "cantidad" to mapOf("type" to "INTEGER"),
                                            "ubicacion" to mapOf("type" to "STRING"),
                                            "foto" to mapOf("type" to "INTEGER"),
                                            "notas" to mapOf("type" to "STRING"),
                                        ),
                                        "required" to listOf("nombre", "categoria", "cantidad"),
                                    ),
                                ),
                            ),
                            "required" to listOf("objetos"),
                        ),
                    ),
                ),
            )
        }

        /** Lee la respuesta; los objetos salen como sugeridos (confirmado = false). */
        fun leer(respuesta: String, archivos: List<String>): Resultado {
            val raiz = Json.parse(respuesta)
            val candidato = raiz.lista("candidates")?.firstOrNull()
            val interno = candidato.obj("content").lista("parts").orEmpty()
                .joinToString("") { it.texto("text").orEmpty() }.trim()
            if (interno.isEmpty()) {
                val motivo = candidato.texto("finishReason") ?: raiz.obj("promptFeedback").texto("blockReason")
                throw IOException("Gemini no devolvió objetos" + (motivo?.let { " ($it)" } ?: ""))
            }
            val datos = try {
                Json.parse(interno.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            } catch (e: IllegalArgumentException) {
                throw IOException("La respuesta de Gemini no se entendió")
            }
            val objetos = datos.lista("objetos").orEmpty().mapNotNull { o ->
                val nombre = o.texto("nombre")?.trim().orEmpty()
                if (nombre.isEmpty()) return@mapNotNull null
                val n = o.num("foto")?.toInt()
                Objeto(
                    nombre = nombre.replaceFirstChar { it.uppercase() },
                    categoria = Categorias.normalizar(o.texto("categoria")),
                    cantidad = (o.num("cantidad") ?: 1.0).toInt().coerceIn(1, 999),
                    ubicacion = o.texto("ubicacion")?.trim().orEmpty(),
                    notas = o.texto("notas")?.trim().orEmpty(),
                    foto = n?.let { archivos.getOrNull(it - 1) },
                    confirmado = false,
                )
            }
            return Resultado(
                objetos = objetos,
                tipoHabitacion = datos.texto("tipo_habitacion")?.trim()?.takeIf { it.isNotEmpty() },
                nota = datos.texto("nota")?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        fun mensajeDeError(codigo: Int, json: String): String {
            val detalle = runCatching { Json.parse(json).obj("error").texto("message") }.getOrNull()
            return when (codigo) {
                400, 403 -> "Gemini rechazó la clave o la consulta" + (detalle?.let { ": $it" } ?: "")
                404 -> "No existe el modelo de Gemini indicado (revísalo en Ajustes)"
                413 -> "Las fotos pesan demasiado; quita algunas e intenta de nuevo"
                429 -> "Se acabó el cupo de Gemini por ahora; intenta en un rato"
                else -> "Gemini respondió $codigo" + (detalle?.let { ": $it" } ?: "")
            }
        }
    }
}
