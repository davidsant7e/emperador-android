package com.davidsant7e.emperador.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidsant7e.emperador.datos.Brujula
import com.davidsant7e.emperador.nucleo.Abertura
import com.davidsant7e.emperador.nucleo.Coordenadas
import com.davidsant7e.emperador.nucleo.Habitacion
import com.davidsant7e.emperador.nucleo.Inventario
import com.davidsant7e.emperador.nucleo.Sol
import java.time.LocalDate
import java.time.ZoneId

// ===================== ubicación de la casa =====================

@Composable
fun TarjetaUbicacion(e: Estado, vm: EmperadorViewModel, acciones: Acciones, abrirSol: () -> Unit) {
    var escribir by rememberSaveable { mutableStateOf(false) }
    val c = e.casa
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Ubicación y soleamiento", style = MaterialTheme.typography.titleSmall)
            Text(
                if (c.tieneUbicacion) "📍 ${Coordenadas.texto(c.latitud!!, c.longitud!!)}"
                else "Sin ubicación. Hace falta para calcular el sol en cada ventana.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = acciones.ubicar, enabled = !e.ubicando) { Text("Usar GPS") }
                TextButton(onClick = { escribir = true }) { Text("Escribir o pegar") }
                if (e.ubicando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            val aberturas = c.habitaciones.sumOf { it.aberturas.size }
            FilledTonalButton(onClick = abrirSol, enabled = c.tieneUbicacion && aberturas > 0, modifier = Modifier.fillMaxWidth()) {
                Text("☀ Estudio de soleamiento" + if (aberturas > 0) " ($aberturas ventanas)" else "")
            }
            if (aberturas == 0) Text(
                "Agrega la orientación de las ventanas en cada habitación (sección «Orientación»).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (escribir) {
        var texto by rememberSaveable { mutableStateOf(if (c.tieneUbicacion) Coordenadas.texto(c.latitud!!, c.longitud!!) else "") }
        val leido = Coordenadas.leer(texto)
        AlertDialog(
            onDismissRequest = { escribir = false },
            title = { Text("Ubicación de la casa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Escribe «latitud, longitud» (por ejemplo 4.711, -74.072) o pega el enlace de Google Maps: mantén presionado el techo de tu casa → copia las coordenadas o «Compartir».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(texto, { texto = it }, singleLine = false, modifier = Modifier.fillMaxWidth(), label = { Text("Coordenadas o enlace") })
                    Text(
                        when {
                            texto.isBlank() -> ""
                            leido != null -> "✓ ${Coordenadas.texto(leido.first, leido.second)}"
                            else -> "No encuentro coordenadas en ese texto. Un enlace corto (maps.app.goo.gl) no las trae: ábrelo y copia las coordenadas."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (leido != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = { TextButton(enabled = leido != null, onClick = { vm.fijarUbicacion(leido!!.first, leido.second); escribir = false }) { Text("Guardar") } },
            dismissButton = {
                Row {
                    if (c.tieneUbicacion) TextButton(onClick = { vm.fijarUbicacion(null, null); escribir = false }) { Text("Quitar") }
                    TextButton(onClick = { escribir = false }) { Text("Cancelar") }
                }
            },
        )
    }
}

// ===================== orientación de una habitación =====================

@Composable
fun SeccionOrientacion(h: Habitacion, e: Estado, vm: EmperadorViewModel) {
    var brujula by rememberSaveable { mutableStateOf(false) }
    var elegir by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val hayBrujula = remember { Brujula.disponible(ctx) }
    Column {
        Text("Orientación", style = MaterialTheme.typography.titleSmall)
        Text(
            "Hacia dónde mira cada ventana o pared exterior (de adentro hacia afuera). Sirve para el estudio de soleamiento.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        h.aberturas.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("↑", modifier = Modifier.rotate(a.azimut.toFloat()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${a.nombreTipo} · mira al ${Sol.nombrePunto(a.azimut)}", style = MaterialTheme.typography.bodyMedium)
                    Text(Inventario.direccion(a) + if (a.medida == "brujula") " · con brújula" else " · elegida a mano", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.borrarAbertura(h.id, a.id) }) { Text("✕") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hayBrujula) FilledTonalButton(onClick = { brujula = true }) { Text("🧭 Medir con brújula") }
            OutlinedButton(onClick = { elegir = true }) { Text("Elegir dirección") }
        }
    }
    if (brujula) DialogoBrujula(e, onGuardar = { vm.agregarAbertura(h.id, it); brujula = false }, onCerrar = { brujula = false })
    if (elegir) DialogoDireccion(onGuardar = { vm.agregarAbertura(h.id, it); elegir = false }, onCerrar = { elegir = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorTipo(tipo: String, onTipo: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Abertura.TIPOS.forEach { (k, n) -> FilterChip(selected = tipo == k, onClick = { onTipo(k) }, label = { Text(n) }) }
    }
}

@Composable
private fun DialogoBrujula(e: Estado, onGuardar: (Abertura) -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val flujo = remember { Brujula.lecturas(ctx, e.casa.latitud, e.casa.longitud) }
    val lectura by flujo.collectAsState(initial = null)
    var tipo by rememberSaveable { mutableStateOf("ventana") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Medir con la brújula") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Párate frente a la ventana y sostén el teléfono derecho, con la cámara de atrás apuntando hacia afuera. Aléjate un poco de metales y electrodomésticos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val l = lectura
                if (l == null) {
                    CircularProgressIndicator()
                } else {
                    Text("↑", fontSize = 40.sp, modifier = Modifier.rotate(-l.azimut.toFloat()), color = MaterialTheme.colorScheme.secondary)
                    Text("${Sol.punto(l.azimut)}  ${Math.round(l.azimut)}°", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("mira al ${Sol.nombrePunto(l.azimut)} (la flecha señala el norte)", style = MaterialTheme.typography.bodySmall)
                    if (l.inclinado) AvisoCorto("Pon el teléfono más derecho (vertical).")
                    if (l.precision <= 1) AvisoCorto("Brújula poco precisa: mueve el teléfono dibujando un 8 en el aire y vuelve a apuntar.")
                    if (!e.casa.tieneUbicacion) AvisoCorto("Sin la ubicación de la casa no se corrige la declinación magnética (unos 7° en Colombia).")
                }
                SelectorTipo(tipo) { tipo = it }
            }
        },
        confirmButton = {
            TextButton(enabled = lectura?.inclinado == false, onClick = {
                lectura?.let { onGuardar(Abertura(tipo = tipo, azimut = Math.round(it.azimut).toDouble(), medida = "brujula")) }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun AvisoCorto(texto: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoDireccion(onGuardar: (Abertura) -> Unit, onCerrar: () -> Unit) {
    var punto by rememberSaveable { mutableStateOf<String?>(null) }
    var tipo by rememberSaveable { mutableStateOf("ventana") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Hacia dónde mira?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("De adentro hacia afuera. En Bogotá el sol sale por el oriente (E) y se oculta por el occidente (O).", style = MaterialTheme.typography.bodySmall)
                // Rosa de los vientos en una cuadrícula de 3 × 3.
                val filas = listOf(listOf("NO", "N", "NE"), listOf("O", null, "E"), listOf("SO", "S", "SE"))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    filas.forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            fila.forEach { p ->
                                if (p == null) Spacer(Modifier.size(64.dp, 40.dp))
                                else FilterChip(selected = punto == p, onClick = { punto = p }, label = { Text(p, modifier = Modifier.width(40.dp)) })
                            }
                        }
                    }
                }
                SelectorTipo(tipo) { tipo = it }
            }
        },
        confirmButton = {
            TextButton(enabled = punto != null, onClick = { onGuardar(Abertura(tipo = tipo, azimut = Sol.azimutDe(punto!!)!!, medida = "manual")) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

// ===================== estudio de soleamiento =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaSol(e: Estado, acciones: Acciones, onCerrar: () -> Unit) {
    BackHandler(onBack = onCerrar)
    val zona = remember { ZoneId.systemDefault() }
    var anio by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    val estudio = remember(e.casa, anio) { Inventario.soleamiento(e.casa, zona, anio) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onCerrar) { Text("←", style = MaterialTheme.typography.titleLarge) } },
                title = { Text("Soleamiento $anio") },
                actions = { TextButton(onClick = { acciones.compartirTexto(Inventario.markdown(e.casa, zona, anio)) }) { Text("Enviar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Horas de sol directo sobre cada ventana en los equinoccios y solsticios, calculadas con la posición del sol " +
                        "en ${Coordenadas.texto(e.casa.latitud ?: 0.0, e.casa.longitud ?: 0.0)} (hora local ${zona.id}). " +
                        "No incluye sombras de edificios vecinos, árboles, aleros ni nubes: es el máximo posible.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { anio-- }) { Text("‹ ${anio - 1}") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { anio++ }) { Text("${anio + 1} ›") }
                }
            }
            items(estudio, key = { it.abertura.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${s.habitacion.nombre} · ${s.abertura.nombreTipo}", style = MaterialTheme.typography.titleSmall)
                        Text("Mira al ${Sol.nombrePunto(s.abertura.azimut)} · ${Inventario.direccion(s.abertura)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(6.dp))
                        s.dias.forEach { (nombre, d) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(nombre.replace("Equinoccio de ", "Eq. ").replace("Solsticio de ", "Sol. "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(118.dp))
                                Text(Inventario.tramos(d), style = MaterialTheme.typography.bodySmall, fontWeight = if (d.horas > 0) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}
