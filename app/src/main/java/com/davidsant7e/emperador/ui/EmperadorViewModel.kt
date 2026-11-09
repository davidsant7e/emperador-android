package com.davidsant7e.emperador.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davidsant7e.emperador.datos.Ajustes
import com.davidsant7e.emperador.datos.Almacen
import com.davidsant7e.emperador.datos.Boveda
import com.davidsant7e.emperador.datos.Fotos
import com.davidsant7e.emperador.datos.Red
import com.davidsant7e.emperador.nucleo.Casa
import com.davidsant7e.emperador.nucleo.Gemini
import com.davidsant7e.emperador.nucleo.Habitacion
import com.davidsant7e.emperador.nucleo.Inventario
import com.davidsant7e.emperador.nucleo.Objeto
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Estado(
    val casa: Casa = Casa(),
    /** Habitación abierta (null = lista de habitaciones). */
    val abierta: String? = null,
    /** Habitación que se está analizando con Gemini. */
    val analizando: String? = null,
    val importando: Boolean = false,
    val hayClave: Boolean = false,
    val modelo: String = Gemini.MODELO,
    val mensaje: String? = null,
)

class EmperadorViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = Almacen(app)
    private val ajustes = Ajustes(app)
    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    val carpetaFotos get() = almacen.carpetaFotos

    init {
        viewModelScope.launch {
            val casa = withContext(Dispatchers.IO) { almacen.cargar() }
            _estado.update { it.copy(casa = casa, hayClave = Boveda.hay(app), modelo = ajustes.modelo) }
        }
    }

    private fun cambiarCasa(f: (Casa) -> Casa) {
        _estado.update { it.copy(casa = f(it.casa)) }
        val casa = _estado.value.casa
        viewModelScope.launch(Dispatchers.IO) { almacen.guardar(casa) }
    }

    private fun cambiar(id: String, f: (Habitacion) -> Habitacion) = cambiarCasa { it.cambiar(id, f) }

    fun avisar(texto: String?) = _estado.update { it.copy(mensaje = texto) }

    // ---------- casa y habitaciones ----------

    fun renombrarCasa(nombre: String) {
        if (nombre.isNotBlank()) cambiarCasa { it.copy(nombre = nombre.trim()) }
    }

    fun abrir(id: String?) = _estado.update { it.copy(abierta = id) }

    /** Crea la habitación y la abre. */
    fun nuevaHabitacion(nombre: String) {
        val h = Habitacion(nombre = nombre.trim().ifEmpty { "Habitación ${_estado.value.casa.habitaciones.size + 1}" })
        cambiarCasa { it.copy(habitaciones = it.habitaciones + h) }
        abrir(h.id)
    }

    fun renombrar(id: String, nombre: String) {
        if (nombre.isNotBlank()) cambiar(id) { it.copy(nombre = nombre.trim()) }
    }

    fun notas(id: String, notas: String) = cambiar(id) { it.copy(notas = notas) }

    fun borrarHabitacion(id: String) {
        cambiarCasa { c -> c.copy(habitaciones = c.habitaciones.filterNot { it.id == id }) }
        abrir(null)
        val casa = _estado.value.casa
        viewModelScope.launch(Dispatchers.IO) { almacen.limpiarFotos(casa) }
    }

    fun mover(id: String, paso: Int) = cambiarCasa { c ->
        val l = c.habitaciones.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + paso
        if (i < 0 || j !in l.indices) c else c.copy(habitaciones = l.apply { add(j, removeAt(i)) })
    }

    // ---------- fotos ----------

    fun agregarFotos(id: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        _estado.update { it.copy(importando = true) }
        viewModelScope.launch {
            val nombres = ArrayList<String>()
            var fallos = 0
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    try {
                        nombres += Fotos.importar(getApplication<Application>(), u, almacen.carpetaFotos)
                    } catch (e: Exception) {
                        fallos++
                    }
                }
            }
            cambiar(id) { it.copy(fotos = it.fotos + nombres) }
            _estado.update {
                it.copy(importando = false, mensaje = if (fallos > 0) "No se pudieron leer $fallos foto(s)." else null)
            }
        }
    }

    fun quitarFoto(id: String, archivo: String) {
        cambiar(id) { Inventario.quitarFoto(it, archivo) }
        viewModelScope.launch(Dispatchers.IO) { almacen.foto(archivo).delete() }
    }

    // ---------- Gemini ----------

    fun analizar(id: String) {
        val h = _estado.value.casa.habitacion(id) ?: return
        if (_estado.value.analizando != null) return
        if (h.fotos.isEmpty()) { avisar("Primero agrega fotos de la habitación."); return }
        val clave = Boveda.leer(getApplication<Application>())
        if (clave.isNullOrBlank()) { avisar("Falta la clave de Gemini: ábrela en Ajustes (⚙)."); return }
        _estado.update { it.copy(analizando = id, mensaje = null) }
        viewModelScope.launch {
            try {
                // Las últimas fotos primero: si hay más de 8, se usan las más recientes.
                val usar = h.fotos.takeLast(Gemini.MAX_FOTOS)
                val r = withContext(Dispatchers.IO) {
                    val fotos = usar.mapNotNull { n -> almacen.foto(n).takeIf { it.exists() }?.let { Gemini.Foto(n, it.readBytes()) } }
                    Gemini(Red::postJson).analizar(h.nombre, fotos, h.objetos.filter { it.confirmado }, clave, ajustes.modelo)
                }
                val fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                cambiar(id) { Inventario.aplicarAnalisis(it, r.objetos, fecha) }
                val nuevos = _estado.value.casa.habitacion(id)?.sinRevisar ?: 0
                val texto = buildString {
                    append(if (nuevos == 0) "Gemini no encontró objetos nuevos." else "Gemini sugirió $nuevos objeto(s): revísalos.")
                    if (h.fotos.size > Gemini.MAX_FOTOS) append(" Se usaron las ${Gemini.MAX_FOTOS} fotos más recientes.")
                    r.nota?.let { append(" ").append(it) }
                }
                _estado.update { it.copy(analizando = null, mensaje = texto) }
                // Si la habitación se llama genérico ("Habitación 3"), se propone el tipo que vio Gemini.
                val tipo = r.tipoHabitacion
                if (tipo != null && Regex("^Habitación \\d+$").matches(h.nombre)) renombrar(id, tipo)
            } catch (e: Gemini.SinClaveException) {
                _estado.update { it.copy(analizando = null, mensaje = "Falta la clave de Gemini.") }
            } catch (e: IOException) {
                _estado.update { it.copy(analizando = null, mensaje = e.message ?: "Sin conexión con Gemini.") }
            } catch (e: Exception) {
                _estado.update { it.copy(analizando = null, mensaje = "Error: ${e.message}") }
            }
        }
    }

    // ---------- objetos ----------

    fun guardarObjeto(id: String, o: Objeto) = cambiar(id) { h ->
        val revisado = o.copy(confirmado = true)
        if (h.objetos.any { it.id == o.id }) h.copy(objetos = h.objetos.map { if (it.id == o.id) revisado else it })
        else h.copy(objetos = h.objetos + revisado)
    }

    fun borrarObjeto(id: String, objetoId: String) = cambiar(id) { h -> h.copy(objetos = h.objetos.filterNot { it.id == objetoId }) }

    fun confirmar(id: String, objetoId: String) = cambiar(id) { h ->
        h.copy(objetos = h.objetos.map { if (it.id == objetoId) it.copy(confirmado = true) else it })
    }

    fun confirmarTodos(id: String) = cambiar(id) { h -> h.copy(objetos = h.objetos.map { it.copy(confirmado = true) }) }

    fun descartarSugeridos(id: String) = cambiar(id) { h -> h.copy(objetos = h.objetos.filter { it.confirmado }) }

    // ---------- ajustes ----------

    fun guardarAjustes(clave: String?, modelo: String) {
        val app = getApplication<Application>()
        when {
            clave == null -> Unit // sin cambios
            clave.isBlank() -> Boveda.borrar(app)
            else -> Boveda.guardar(app, clave.trim())
        }
        ajustes.modelo = modelo
        _estado.update { it.copy(hayClave = Boveda.hay(app), modelo = ajustes.modelo) }
    }
}
