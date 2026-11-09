package com.davidsant7e.emperador.datos

import android.content.Context
import com.davidsant7e.emperador.nucleo.Casa
import java.io.File

/** La casa se guarda como un JSON en la memoria privada de la app; las fotos, en filesDir/fotos. */
class Almacen(private val ctx: Context) {
    private val archivo = File(ctx.filesDir, "casa.json")
    val carpetaFotos: File = File(ctx.filesDir, "fotos").apply { mkdirs() }

    fun cargar(): Casa = try {
        if (archivo.exists()) Casa.deJson(archivo.readText()) else Casa()
    } catch (e: Exception) {
        // Un archivo dañado no debe impedir abrir la app: se aparta y se empieza de nuevo.
        archivo.renameTo(File(ctx.filesDir, "casa-danada-${System.currentTimeMillis()}.json"))
        Casa()
    }

    /** Escritura atómica: primero a un temporal y luego se renombra. */
    @Synchronized
    fun guardar(casa: Casa) {
        val tmp = File(ctx.filesDir, "casa.json.tmp")
        tmp.writeText(casa.aJson())
        if (!tmp.renameTo(archivo)) {
            archivo.delete()
            tmp.renameTo(archivo)
        }
    }

    fun foto(nombre: String) = File(carpetaFotos, nombre)

    /** Borra las fotos que ya no usa ninguna habitación. */
    fun limpiarFotos(casa: Casa) {
        val usadas = casa.habitaciones.flatMap { it.fotos }.toSet()
        carpetaFotos.listFiles()?.filter { it.name !in usadas }?.forEach { it.delete() }
    }
}
