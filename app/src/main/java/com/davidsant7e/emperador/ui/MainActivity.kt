package com.davidsant7e.emperador.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidsant7e.emperador.nucleo.Inventario
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: EmperadorViewModel by viewModels()

    /** Habitación a la que van las fotos que se están tomando o eligiendo. */
    private var destinoFotos: String? = null
    private var fotoCamara: Uri? = null

    private val elegirFotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_GALERIA)) { uris ->
        destinoFotos?.let { vm.agregarFotos(it, uris) }
    }

    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val id = destinoFotos
        val uri = fotoCamara
        if (ok && id != null && uri != null) vm.agregarFotos(id, listOf(uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("destino")?.let { destinoFotos = it }
        savedInstanceState?.getString("camara")?.let { fotoCamara = Uri.parse(it) }
        setContent {
            TemaEmperador {
                val e by vm.estado.collectAsStateWithLifecycle()
                App(
                    e, vm,
                    carpetaFotos = vm.carpetaFotos,
                    acciones = Acciones(
                        camara = ::camara,
                        galeria = ::galeria,
                        compartirCsv = { compartirArchivo("csv", "text/csv", Inventario.csv(vm.estado.value.casa)) },
                        compartirJson = { compartirArchivo("json", "application/json", vm.estado.value.casa.aJson()) },
                        compartirTexto = ::compartirTexto,
                        copiarTexto = ::copiar,
                    ),
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        destinoFotos?.let { outState.putString("destino", it) }
        fotoCamara?.let { outState.putString("camara", it.toString()) }
    }

    private fun camara(id: String) {
        destinoFotos = id
        val carpeta = File(cacheDir, "camara").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() } // fotos anteriores ya copiadas a la app
        val archivo = File(carpeta, "foto-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)
        fotoCamara = uri
        try {
            tomarFoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay una app de cámara.", Toast.LENGTH_LONG).show()
        }
    }

    private fun galeria(id: String) {
        destinoFotos = id
        elegirFotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun compartirArchivo(extension: String, tipo: String, contenido: String) {
        val carpeta = File(cacheDir, "exportar").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }
        val nombre = Inventario.nombreArchivo(vm.estado.value.casa, extension, LocalDate.now().toString())
        val archivo = File(carpeta, nombre).apply { writeText(contenido) }
        val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = tipo
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nombre)
            clipData = ClipData.newRawUri(nombre, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Enviar $nombre"))
    }

    private fun compartirTexto(texto: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        startActivity(Intent.createChooser(i, "Enviar inventario"))
    }

    private fun copiar(texto: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Inventario", texto))
        Toast.makeText(this, "Inventario copiado.", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MAX_GALERIA = 20
    }
}

/** Lo que la interfaz le pide a la actividad (cámara, galería, compartir). */
class Acciones(
    val camara: (String) -> Unit,
    val galeria: (String) -> Unit,
    val compartirCsv: () -> Unit,
    val compartirJson: () -> Unit,
    val compartirTexto: (String) -> Unit,
    val copiarTexto: (String) -> Unit,
)
