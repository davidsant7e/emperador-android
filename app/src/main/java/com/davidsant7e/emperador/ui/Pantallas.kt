package com.davidsant7e.emperador.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidsant7e.emperador.datos.Fotos
import com.davidsant7e.emperador.nucleo.Categorias
import com.davidsant7e.emperador.nucleo.Gemini
import com.davidsant7e.emperador.nucleo.Habitacion
import com.davidsant7e.emperador.nucleo.Inventario
import com.davidsant7e.emperador.nucleo.Objeto
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SUGERENCIAS = listOf("Sala", "Comedor", "Cocina", "Habitación principal", "Habitación", "Baño", "Estudio", "Patio", "Garaje", "Zona de ropas")

@Composable
fun App(e: Estado, vm: EmperadorViewModel, carpetaFotos: File, acciones: Acciones) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(e.mensaje) {
        e.mensaje?.let {
            snack.showSnackbar(it)
            vm.avisar(null)
        }
    }
    var ajustes by rememberSaveable { mutableStateOf(false) }
    var verSol by rememberSaveable { mutableStateOf(false) }
    val h = e.abierta?.let { e.casa.habitacion(it) }
    if (verSol) {
        PantallaSol(e, acciones, onCerrar = { verSol = false })
    } else if (h != null) {
        BackHandler { vm.abrir(null) }
        PantallaHabitacion(h, e, vm, carpetaFotos, acciones, snack, abrirAjustes = { ajustes = true })
    } else {
        PantallaCasa(e, vm, carpetaFotos, acciones, snack, abrirAjustes = { ajustes = true }, abrirSol = { verSol = true })
    }
    if (ajustes) DialogoAjustes(e, onGuardar = { clave, modelo -> vm.guardarAjustes(clave, modelo); ajustes = false }, onCerrar = { ajustes = false })
}

// ===================== la casa =====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PantallaCasa(e: Estado, vm: EmperadorViewModel, carpetaFotos: File, acciones: Acciones, snack: SnackbarHostState, abrirAjustes: () -> Unit, abrirSol: () -> Unit) {
    var nueva by rememberSaveable { mutableStateOf(false) }
    var renombrar by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { renombrar = true }) {
                        Text("IV · El Emperador", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(e.casa.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }, enabled = e.casa.habitaciones.isNotEmpty()) { Text("Exportar") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Enviar a Claude (texto)") }, onClick = { menu = false; acciones.compartirTexto(Inventario.markdown(e.casa)) })
                            DropdownMenuItem(text = { Text("Copiar como texto") }, onClick = { menu = false; acciones.copiarTexto(Inventario.markdown(e.casa)) })
                            DropdownMenuItem(text = { Text("Hoja de cálculo (CSV)") }, onClick = { menu = false; acciones.compartirCsv() })
                            DropdownMenuItem(text = { Text("Datos completos (JSON)") }, onClick = { menu = false; acciones.compartirJson() })
                        }
                    }
                    TextButton(onClick = abrirAjustes) { Text("⚙", style = MaterialTheme.typography.titleLarge) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nueva = true }) { Text("+ Habitación") } },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!e.hayClave) item {
                Aviso("Para reconocer objetos en las fotos hace falta una clave gratuita de Gemini (aistudio.google.com/apikey). Pégala en ⚙.", "Abrir ajustes", abrirAjustes)
            }
            if (e.casa.habitaciones.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tu casa está vacía", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Agrega una habitación, tómale fotos desde varios ángulos y Gemini hará la lista de lo que hay. Tú revisas y corriges.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (e.casa.habitaciones.isNotEmpty()) item { TarjetaUbicacion(e, vm, acciones, abrirSol) }
            items(e.casa.habitaciones, key = { it.id }) { h -> TarjetaHabitacion(h, carpetaFotos, e.analizando == h.id) { vm.abrir(h.id) } }
            val resumen = Inventario.porCategoria(e.casa)
            if (resumen.isNotEmpty()) item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("En toda la casa: ${e.casa.habitaciones.sumOf { it.totalObjetos }} objetos", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        resumen.forEach { (k, n) -> SuggestionChip(onClick = {}, label = { Text("${Categorias.nombre(k)} $n") }) }
                    }
                }
            }
        }
    }
    if (nueva) DialogoNombre("Nueva habitación", "", SUGERENCIAS, onListo = { vm.nuevaHabitacion(it); nueva = false }, onCerrar = { nueva = false })
    if (renombrar) DialogoNombre("Nombre de la casa", e.casa.nombre, emptyList(), onListo = { vm.renombrarCasa(it); renombrar = false }, onCerrar = { renombrar = false })
}

@Composable
private fun TarjetaHabitacion(h: Habitacion, carpetaFotos: File, analizando: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val primera = h.fotos.firstOrNull()
            if (primera != null) FotoMini(File(carpetaFotos, primera), 64)
            else Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text("📷", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val partes = buildList {
                    add(if (h.objetos.isEmpty()) "Sin objetos" else "${h.totalObjetos} objetos")
                    add("${h.fotos.size} foto${if (h.fotos.size == 1) "" else "s"}")
                    if (h.sinRevisar > 0) add("${h.sinRevisar} sin revisar")
                }
                Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (analizando) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

// ===================== una habitación =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaHabitacion(h: Habitacion, e: Estado, vm: EmperadorViewModel, carpetaFotos: File, acciones: Acciones, snack: SnackbarHostState, abrirAjustes: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renombrar by rememberSaveable { mutableStateOf(false) }
    var borrar by rememberSaveable { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Objeto?>(null) }
    var verFoto by rememberSaveable { mutableStateOf<String?>(null) }
    val analizando = e.analizando == h.id
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = { vm.abrir(null) }) { Text("←", style = MaterialTheme.typography.titleLarge) } },
                title = { Text(h.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { renombrar = true }) },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Cambiar nombre") }, onClick = { menu = false; renombrar = true })
                            DropdownMenuItem(text = { Text("Subir en la lista") }, onClick = { menu = false; vm.mover(h.id, -1) })
                            DropdownMenuItem(text = { Text("Bajar en la lista") }, onClick = { menu = false; vm.mover(h.id, 1) })
                            DropdownMenuItem(text = { Text("Ajustes de Gemini") }, onClick = { menu = false; abrirAjustes() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Borrar habitación", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; borrar = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // --- fotos ---
            item {
                Text("Fotos", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                if (h.fotos.isEmpty()) {
                    Text(
                        "Toma 3 a 6 fotos desde las esquinas, que se vea cada pared. Con buena luz Gemini reconoce más.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(h.fotos, key = { it }) { f -> Box(Modifier.clickable { verFoto = f }) { FotoMini(File(carpetaFotos, f), 96) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { acciones.camara(h.id) }, enabled = !e.importando) { Text("📷 Tomar foto") }
                    OutlinedButton(onClick = { acciones.galeria(h.id) }, enabled = !e.importando) { Text("Galería") }
                    if (e.importando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            // --- Gemini ---
            item {
                Spacer(Modifier.height(4.dp))
                Button(onClick = { vm.analizar(h.id) }, enabled = !analizando && e.analizando == null && h.fotos.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    if (analizando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Gemini está mirando las fotos…")
                    } else {
                        Text(if (h.analizada == null) "Reconocer objetos con Gemini" else "Buscar objetos que falten")
                    }
                }
                val detalle = buildList {
                    h.analizada?.let { add("Último análisis: ${it.replace('T', ' ')}") }
                    if (h.fotos.size > Gemini.MAX_FOTOS) add("se envían las ${Gemini.MAX_FOTOS} fotos más recientes")
                }
                if (detalle.isNotEmpty()) Text(detalle.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // --- sugerencias pendientes ---
            if (h.sinRevisar > 0) item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${h.sinRevisar} sugerido${if (h.sinRevisar == 1) "" else "s"} por Gemini sin revisar", fontWeight = FontWeight.SemiBold)
                        Text("Toca uno para corregirlo, o ✓ para aceptarlo tal cual.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.confirmarTodos(h.id) }) { Text("Aceptar todos") }
                            TextButton(onClick = { vm.descartarSugeridos(h.id) }) { Text("Descartar sugeridos") }
                        }
                    }
                }
            }
            // --- orientación (soleamiento) ---
            item { SeccionOrientacion(h, e, vm) }
            // --- objetos por categoría ---
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Objetos (${h.totalObjetos})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editando = Objeto(nombre = "") }) { Text("+ Agregar") }
                }
            }
            var categoria: String? = null
            for (o in Inventario.ordenados(h.objetos)) {
                if (o.categoria != categoria) {
                    categoria = o.categoria
                    val titulo = Categorias.nombre(o.categoria)
                    item(key = "cat-${o.categoria}") {
                        Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item(key = o.id) { FilaObjeto(o, onEditar = { editando = o }, onAceptar = { vm.confirmar(h.id, o.id) }) }
            }
            if (h.objetos.isEmpty()) item {
                Text("Todavía no hay objetos. Usa Gemini con las fotos o agrégalos a mano.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // --- notas ---
            item {
                var notas by remember(h.id) { mutableStateOf(h.notas) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notas, onValueChange = { notas = it; vm.notas(h.id, it) },
                    label = { Text("Notas de la habitación") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
    }
    editando?.let { o ->
        DialogoObjeto(o, carpetaFotos, nuevo = h.objetos.none { it.id == o.id },
            onGuardar = { vm.guardarObjeto(h.id, it); editando = null },
            onBorrar = { vm.borrarObjeto(h.id, o.id); editando = null },
            onCerrar = { editando = null })
    }
    verFoto?.let { f ->
        AlertDialog(
            onDismissRequest = { verFoto = null },
            text = { FotoMini(File(carpetaFotos, f), 900, Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { verFoto = null }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { vm.quitarFoto(h.id, f); verFoto = null }) { Text("Quitar foto", color = MaterialTheme.colorScheme.error) } },
        )
    }
    if (renombrar) DialogoNombre("Nombre de la habitación", h.nombre, SUGERENCIAS, onListo = { vm.renombrar(h.id, it); renombrar = false }, onCerrar = { renombrar = false })
    if (borrar) AlertDialog(
        onDismissRequest = { borrar = false },
        title = { Text("¿Borrar «${h.nombre}»?") },
        text = { Text("Se borran sus ${h.fotos.size} fotos y ${h.objetos.size} objetos de la app. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { borrar = false; vm.borrarHabitacion(h.id) }) { Text("Borrar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun FilaObjeto(o: Objeto, onEditar: () -> Unit, onAceptar: () -> Unit) {
    val fondo = if (o.confirmado) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    Card(Modifier.fillMaxWidth().clickable(onClick = onEditar), colors = CardDefaults.cardColors(containerColor = fondo)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.nombre + if (o.cantidad > 1) "  ×${o.cantidad}" else "", style = MaterialTheme.typography.bodyLarge)
                val extra = listOf(o.ubicacion, o.notas).filter { it.isNotBlank() }.joinToString(" · ")
                if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!o.confirmado) {
                Text("sugerido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                TextButton(onClick = onAceptar) { Text("✓", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

// ===================== diálogos =====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoObjeto(o: Objeto, carpetaFotos: File, nuevo: Boolean, onGuardar: (Objeto) -> Unit, onBorrar: () -> Unit, onCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(o.nombre) }
    var categoria by remember { mutableStateOf(o.categoria) }
    var cantidad by remember { mutableStateOf(o.cantidad) }
    var ubicacion by remember { mutableStateOf(o.ubicacion) }
    var notas by remember { mutableStateOf(o.notas) }
    var menuCat by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (nuevo) "Agregar objeto" else "Objeto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                o.foto?.let { FotoMini(File(carpetaFotos, it), 480, Modifier.fillMaxWidth().height(140.dp)) }
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Box {
                    OutlinedButton(onClick = { menuCat = true }, modifier = Modifier.fillMaxWidth()) { Text("Categoría: ${Categorias.nombre(categoria)}") }
                    DropdownMenu(expanded = menuCat, onDismissRequest = { menuCat = false }) {
                        Categorias.TODAS.forEach { (k, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { categoria = k; menuCat = false }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cantidad", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { if (cantidad > 1) cantidad-- }) { Text("−") }
                    Text("$cantidad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
                    OutlinedButton(onClick = { cantidad++ }) { Text("+") }
                }
                OutlinedTextField(ubicacion, { ubicacion = it }, label = { Text("Dónde está (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notas, { notas = it }, label = { Text("Notas (marca, estado…)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = nombre.isNotBlank(), onClick = {
                onGuardar(o.copy(nombre = nombre.trim(), categoria = categoria, cantidad = cantidad, ubicacion = ubicacion.trim(), notas = notas.trim()))
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (!nuevo) TextButton(onClick = onBorrar) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoNombre(titulo: String, inicial: String, sugerencias: List<String>, onListo: (String) -> Unit, onCerrar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(texto, { texto = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                if (sugerencias.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sugerencias.forEach { s -> AssistChip(onClick = { texto = s }, label = { Text(s) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onListo(texto) }, enabled = texto.isNotBlank()) { Text("Listo") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoAjustes(e: Estado, onGuardar: (String?, String) -> Unit, onCerrar: () -> Unit) {
    var clave by remember { mutableStateOf("") }
    var borrarClave by remember { mutableStateOf(false) }
    var modelo by remember { mutableStateOf(e.modelo) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Ajustes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (e.hayClave) "Clave de Gemini guardada (cifrada en el teléfono). Pega otra solo si quieres cambiarla."
                    else "Crea una clave gratuita en aistudio.google.com/apikey y pégala aquí. Se guarda cifrada en el teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(clave, { clave = it; borrarClave = false }, label = { Text("Clave de Gemini") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                if (e.hayClave) TextButton(onClick = { borrarClave = true; clave = "" }) { Text(if (borrarClave) "La clave se borrará al guardar" else "Borrar la clave guardada") }
                OutlinedTextField(modelo, { modelo = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "Privacidad: las fotos de la habitación que analizas se envían a Google (Gemini) con tu clave. " +
                        "Las fotos quedan en el teléfono sin ubicación GPS.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(if (borrarClave) "" else clave.ifBlank { null }, modelo.ifBlank { Gemini.MODELO }) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun Aviso(texto: String, boton: String, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(texto, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(boton) }
        }
    }
}

// ===================== fotos =====================

private object Miniaturas {
    val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
}

@Composable
private fun FotoMini(archivo: File, lado: Int, modifier: Modifier = Modifier.size(lado.dp)) {
    val clave = "${archivo.path}@$lado"
    val bmp by produceState(Miniaturas.cache.get(clave), clave) {
        if (value == null) value = withContext(Dispatchers.IO) { Fotos.miniatura(archivo, lado * 2)?.also { Miniaturas.cache.put(clave, it) } }
    }
    val forma = RoundedCornerShape(10.dp)
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(forma))
    } else {
        Box(modifier.clip(forma).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
