package com.davidsant7e.emperador.datos

import java.net.HttpURLConnection
import java.net.URI

/** POST JSON bloqueante (se llama desde Dispatchers.IO). Devuelve (código, texto) sin lanzar por código HTTP. */
object Red {
    fun postJson(url: String, cuerpo: String, cabeceras: Map<String, String>): Pair<Int, String> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 120_000 // varias fotos tardan
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val bytes = cuerpo.toByteArray(Charsets.UTF_8)
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val codigo = c.responseCode
            val texto = (if (codigo in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return codigo to texto
        } finally {
            c.disconnect()
        }
    }
}
