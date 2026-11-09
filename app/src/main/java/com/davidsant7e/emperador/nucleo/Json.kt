package com.davidsant7e.emperador.nucleo

/**
 * JSON mínimo, sin dependencias (así el núcleo se prueba en la JVM sin Android).
 * Objetos → Map<String, Any?>, arreglos → List<Any?>, números → Double, más String, Boolean y null.
 */
object Json {

    fun parse(texto: String): Any? {
        val p = Lector(texto)
        p.espacios()
        val valor = p.valor()
        p.espacios()
        if (!p.fin()) throw IllegalArgumentException("JSON con texto sobrante en la posición ${p.i}")
        return valor
    }

    fun escribir(valor: Any?): String = StringBuilder().also { escribir(valor, it) }.toString()

    private fun escribir(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is String -> cadena(v, sb)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Number -> {
                val d = v.toDouble()
                require(d.isFinite()) { "Número no válido en JSON: $d" }
                if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
            }
            is Map<*, *> -> {
                sb.append('{')
                var primero = true
                for ((k, valor) in v) {
                    if (!primero) sb.append(',')
                    primero = false
                    cadena(k.toString(), sb)
                    sb.append(':')
                    escribir(valor, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var primero = true
                for (x in v) {
                    if (!primero) sb.append(',')
                    primero = false
                    escribir(x, sb)
                }
                sb.append(']')
            }
            is Array<*> -> escribir(v.asList(), sb)
            else -> cadena(v.toString(), sb)
        }
    }

    private fun cadena(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Lector(val s: String) {
        var i = 0

        fun fin() = i >= s.length

        fun espacios() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun valor(): Any? {
            espacios()
            if (fin()) throw IllegalArgumentException("JSON incompleto")
            return when (val c = s[i]) {
                '{' -> objeto()
                '[' -> arreglo()
                '"' -> cadena()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) numero() else throw IllegalArgumentException("Carácter inesperado '$c' en $i")
            }
        }

        private fun literal(palabra: String, valor: Any?): Any? {
            if (!s.startsWith(palabra, i)) throw IllegalArgumentException("Se esperaba $palabra en $i")
            i += palabra.length
            return valor
        }

        private fun numero(): Double {
            val inicio = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(inicio, i).toDouble()
        }

        private fun cadena(): String {
            i++ // comilla
            val sb = StringBuilder()
            while (true) {
                if (fin()) throw IllegalArgumentException("Cadena sin cerrar")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arreglo(): List<Any?> {
            i++
            val lista = ArrayList<Any?>()
            espacios()
            if (s[i] == ']') { i++; return lista }
            while (true) {
                lista += valor()
                espacios()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return lista
                    else -> throw IllegalArgumentException("Se esperaba , o ] en ${i - 1}")
                }
            }
        }

        private fun objeto(): Map<String, Any?> {
            i++
            val mapa = LinkedHashMap<String, Any?>()
            espacios()
            if (s[i] == '}') { i++; return mapa }
            while (true) {
                espacios()
                val clave = cadena()
                espacios()
                if (s[i++] != ':') throw IllegalArgumentException("Se esperaba : en ${i - 1}")
                mapa[clave] = valor()
                espacios()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return mapa
                    else -> throw IllegalArgumentException("Se esperaba , o } en ${i - 1}")
                }
            }
        }
    }
}

// Accesos cómodos para leer respuestas.
@Suppress("UNCHECKED_CAST")
fun Any?.obj(clave: String): Map<String, Any?>? = (this as? Map<String, Any?>)?.get(clave) as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.lista(clave: String): List<Any?>? = (this as? Map<String, Any?>)?.get(clave) as? List<Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.texto(clave: String): String? = (this as? Map<String, Any?>)?.get(clave)?.let { if (it is String) it else null }

@Suppress("UNCHECKED_CAST")
fun Any?.num(clave: String): Double? = when (val v = (this as? Map<String, Any?>)?.get(clave)) {
    is Double -> v
    is String -> v.toDoubleOrNull()
    else -> null
}
