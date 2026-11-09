package com.davidsant7e.emperador.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Duotono de los Arcanos: azul noche y durazno. */
val AzulNoche = Color(0xFF1F2A5C)
val Durazno = Color(0xFFF4A582)

private val Claro = lightColorScheme(
    primary = AzulNoche,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1F5),
    onPrimaryContainer = AzulNoche,
    secondary = Color(0xFFB4532A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE3D6),
    onSecondaryContainer = Color(0xFF4A1E0A),
    background = Color(0xFFFBF8F6),
    surface = Color(0xFFFBF8F6),
)

private val Oscuro = darkColorScheme(
    primary = Durazno,
    onPrimary = AzulNoche,
    primaryContainer = Color(0xFF2E3B78),
    onPrimaryContainer = Color(0xFFFDE3D6),
    secondary = Durazno,
    onSecondary = AzulNoche,
    secondaryContainer = Color(0xFF5A3424),
    onSecondaryContainer = Color(0xFFFDE3D6),
    background = Color(0xFF12183A),
    surface = Color(0xFF12183A),
)

@Composable
fun TemaEmperador(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = contenido)
}
