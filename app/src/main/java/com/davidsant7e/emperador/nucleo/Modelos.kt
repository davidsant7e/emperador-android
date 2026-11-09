package com.davidsant7e.emperador.nucleo

import java.util.UUID

/** Categorías fijas, para poder agrupar, sumar y exportar igual siempre. */
object Categorias {
    val TODAS: List<Pair<String, String>> = listOf(
        "muebles" to "Muebles",
        "electrodomesticos" to "Electrodomésticos",
        "electronica" to "Electrónica",
        "iluminacion" to "Iluminación",
        "cocina" to "Cocina y comedor",
        "bano" to "Baño y aseo",
        "textiles" to "Textiles",
        "almacenamiento" to "Almacenamiento",
        "decoracion" to "Decoración",
        "plantas" to "Plantas",
        "herramientas" to "Herramientas",
        "deporte" to "Deporte y ocio",
        "ropa" to "Ropa y calzado",
        "documentos" to "Libros y documentos",
        "puertas-ventanas" to "Puertas y ventanas",
        "otros" to "Otros",
    )
    val CLAVES: List<String> = TODAS.map { it.first }

    fun nombre(clave: String): String = TODAS.firstOrNull { it.first == clave }?.second ?: "Otros"

    /** Acepta la clave o el nombre (con o sin tildes); lo desconocido va a «otros». */
    fun normalizar(texto: String?): String {
        val t = Texto.simple(texto.orEmpty())
        return TODAS.firstOrNull { Texto.simple(it.first) == t || Texto.simple(it.second) == t }?.first ?: "otros"
    }
}

data class Objeto(
    val id: String = nuevoId(),
    val nombre: String,
    val categoria: String = "otros",
    val cantidad: Int = 1,
    val ubicacion: String = "",
    val notas: String = "",
    /** Archivo de la foto donde se ve (nombre dentro de la carpeta de fotos), si se sabe. */
    val foto: String? = null,
    /** false = sugerido por Gemini y aún no revisado. */
    val confirmado: Boolean = true,
) {
    fun aMapa(): Map<String, Any?> = linkedMapOf(
        "id" to id, "nombre" to nombre, "categoria" to categoria, "cantidad" to cantidad,
        "ubicacion" to ubicacion, "notas" to notas, "foto" to foto, "confirmado" to confirmado,
    )

    companion object {
        fun deMapa(m: Any?): Objeto? {
            val nombre = m.texto("nombre")?.trim().orEmpty()
            if (nombre.isEmpty()) return null
            return Objeto(
                id = m.texto("id") ?: nuevoId(),
                nombre = nombre,
                categoria = Categorias.normalizar(m.texto("categoria")),
                cantidad = (m.num("cantidad") ?: 1.0).toInt().coerceAtLeast(1),
                ubicacion = m.texto("ubicacion").orEmpty(),
                notas = m.texto("notas").orEmpty(),
                foto = m.texto("foto"),
                confirmado = (m as? Map<*, *>)?.get("confirmado") as? Boolean ?: true,
            )
        }
    }
}

/**
 * Una ventana, puerta-ventana, balcón o pared exterior de la habitación, con la dirección hacia la que MIRA
 * (de adentro hacia afuera), en grados desde el norte verdadero. Es lo que usa el estudio de soleamiento.
 */
data class Abertura(
    val id: String = nuevoId(),
    val tipo: String = "ventana",
    val azimut: Double,
    /** "brujula" (medida con el teléfono) o "manual" (elegida entre los 8 puntos). */
    val medida: String = "manual",
) {
    val nombreTipo: String get() = TIPOS.firstOrNull { it.first == tipo }?.second ?: "Ventana"

    fun aMapa(): Map<String, Any?> = linkedMapOf("id" to id, "tipo" to tipo, "azimut" to Math.round(azimut * 10) / 10.0, "medida" to medida)

    companion object {
        val TIPOS = listOf("ventana" to "Ventana", "puerta-ventana" to "Puerta-ventana", "balcon" to "Balcón", "pared" to "Pared exterior")

        fun deMapa(m: Any?): Abertura? {
            val az = m.num("azimut") ?: return null
            if (!az.isFinite()) return null
            return Abertura(
                id = m.texto("id") ?: nuevoId(),
                tipo = m.texto("tipo")?.takeIf { t -> TIPOS.any { it.first == t } } ?: "ventana",
                azimut = Sol.norm360(az),
                medida = if (m.texto("medida") == "brujula") "brujula" else "manual",
            )
        }
    }
}

data class Habitacion(
    val id: String = nuevoId(),
    val nombre: String,
    val fotos: List<String> = emptyList(),
    val objetos: List<Objeto> = emptyList(),
    val notas: String = "",
    /** Fecha y hora (ISO local) del último análisis con Gemini. */
    val analizada: String? = null,
    val aberturas: List<Abertura> = emptyList(),
) {
    val totalObjetos: Int get() = objetos.sumOf { it.cantidad }
    val sinRevisar: Int get() = objetos.count { !it.confirmado }

    fun aMapa(): Map<String, Any?> = linkedMapOf(
        "id" to id, "nombre" to nombre, "fotos" to fotos, "objetos" to objetos.map { it.aMapa() },
        "notas" to notas, "analizada" to analizada, "aberturas" to aberturas.map { it.aMapa() },
    )

    companion object {
        fun deMapa(m: Any?): Habitacion? {
            val nombre = m.texto("nombre")?.trim().orEmpty()
            if (nombre.isEmpty()) return null
            return Habitacion(
                id = m.texto("id") ?: nuevoId(),
                nombre = nombre,
                fotos = m.lista("fotos").orEmpty().filterIsInstance<String>(),
                objetos = m.lista("objetos").orEmpty().mapNotNull { Objeto.deMapa(it) },
                notas = m.texto("notas").orEmpty(),
                analizada = m.texto("analizada"),
                aberturas = m.lista("aberturas").orEmpty().mapNotNull { Abertura.deMapa(it) },
            )
        }
    }
}

data class Casa(
    val nombre: String = "Mi casa",
    val habitaciones: List<Habitacion> = emptyList(),
    /** Ubicación de la casa (grados decimales, WGS84), para el soleamiento. */
    val latitud: Double? = null,
    val longitud: Double? = null,
) {
    val tieneUbicacion: Boolean get() = latitud != null && longitud != null

    fun habitacion(id: String): Habitacion? = habitaciones.firstOrNull { it.id == id }

    fun cambiar(id: String, f: (Habitacion) -> Habitacion): Casa =
        copy(habitaciones = habitaciones.map { if (it.id == id) f(it) else it })

    fun aJson(): String = Json.escribir(
        linkedMapOf(
            "formato" to "el-emperador", "version" to 1, "nombre" to nombre,
            "ubicacion" to if (tieneUbicacion) linkedMapOf("latitud" to latitud, "longitud" to longitud) else null,
            "habitaciones" to habitaciones.map { it.aMapa() },
        ),
    )

    companion object {
        fun deJson(texto: String): Casa {
            val raiz = Json.parse(texto)
            val lugar = Coordenadas.validas(raiz.obj("ubicacion").num("latitud"), raiz.obj("ubicacion").num("longitud"))
            return Casa(
                nombre = raiz.texto("nombre")?.takeIf { it.isNotBlank() } ?: "Mi casa",
                habitaciones = raiz.lista("habitaciones").orEmpty().mapNotNull { Habitacion.deMapa(it) },
                latitud = lugar?.first,
                longitud = lugar?.second,
            )
        }
    }
}

fun nuevoId(): String = UUID.randomUUID().toString().replace("-", "").take(10)

object Texto {
    /** Minúsculas, sin tildes ni signos, espacios simples: para comparar nombres. */
    fun simple(s: String): String {
        val sin = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return sin.replace(Regex("[^a-z0-9ñ]+"), " ").trim()
    }
}

/** Leer coordenadas escritas o pegadas (de Google Maps, Waze, geo:…). */
object Coordenadas {
    private const val NUM = "(-?\\d{1,3}(?:[.,]\\d+)?)"
    private val PATRONES = listOf(
        Regex("!3d$NUM!4d$NUM"),                       // enlace largo de Google Maps (punto exacto)
        Regex("[?&](?:q|query|ll|destination)=$NUM\\s*,\\s*$NUM"),
        Regex("@$NUM,$NUM"),                            // centro del mapa en Google Maps
        Regex("geo:$NUM,$NUM"),
        Regex("^\\s*$NUM\\s*[,; ]\\s*$NUM\\s*$"),       // «4.711, -74.072»
    )

    fun leer(texto: String): Pair<Double, Double>? {
        val t = texto.trim()
        for (r in PATRONES) {
            val m = r.find(t) ?: continue
            val lat = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            val lon = m.groupValues[2].replace(',', '.').toDoubleOrNull()
            validas(lat, lon)?.let { return it }
        }
        return null
    }

    fun validas(lat: Double?, lon: Double?): Pair<Double, Double>? =
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) lat to lon else null

    fun texto(lat: Double, lon: Double): String = String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon)
}
