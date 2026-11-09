package com.davidsant7e.emperador.datos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Las fotos se guardan ya reducidas (lado mayor de 1600 px, JPEG 85 %), derechas según el EXIF y
 * sin metadatos (ni ubicación GPS). Así ocupan ~300 KB y se pueden enviar a Gemini tal cual.
 */
object Fotos {
    private const val LADO_MAX = 1600

    /** Copia una foto (de la cámara o de la galería) a la carpeta de la app; devuelve el nombre del archivo. */
    fun importar(ctx: Context, origen: Uri, carpeta: File): String {
        val abrir = { ctx.contentResolver.openInputStream(origen) ?: error("No se pudo abrir la foto") }
        return importar(abrir, carpeta)
    }

    fun importar(abrir: () -> InputStream, carpeta: File): String {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir().use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0) error("El archivo no es una imagen")
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= LADO_MAX) muestra *= 2
        var bmp = abrir().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra }) }
            ?: error("No se pudo leer la imagen")
        val giro = abrir().use { rotacion(ExifInterface(it)) }
        val escala = LADO_MAX.toFloat() / max(bmp.width, bmp.height)
        if (giro != 0 || escala < 1f) {
            val m = Matrix().apply {
                if (escala < 1f) postScale(escala, escala)
                if (giro != 0) postRotate(giro.toFloat())
            }
            val nuevo = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (nuevo !== bmp) bmp.recycle()
            bmp = nuevo
        }
        val nombre = "f" + System.currentTimeMillis().toString(36) + (Math.random() * 1296).toInt().toString(36) + ".jpg"
        File(carpeta, nombre).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        return nombre
    }

    private fun rotacion(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    /** Miniatura para la pantalla (no carga la foto completa en memoria). */
    fun miniatura(archivo: File, lado: Int): Bitmap? {
        if (!archivo.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, o)
        var muestra = 1
        while (max(o.outWidth, o.outHeight) / (muestra * 2) >= lado) muestra *= 2
        val bmp = BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestra }) ?: return null
        val escala = lado.toFloat() / max(bmp.width, bmp.height)
        return if (escala >= 1f) bmp else Bitmap.createScaledBitmap(bmp, (bmp.width * escala).roundToInt().coerceAtLeast(1), (bmp.height * escala).roundToInt().coerceAtLeast(1), true)
    }
}
