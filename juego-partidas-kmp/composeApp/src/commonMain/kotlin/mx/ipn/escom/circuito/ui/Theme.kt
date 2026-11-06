package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Guinda = Color(0xFF6A1B3A)
val Azul = Color(0xFF0B3C8C)

/** Color semilla de la paleta elegida. */
fun seedOf(palette: String) = if (palette == "azul") Azul else Guinda

/** Esquema claro/oscuro derivado del color semilla (mezclas con blanco/negro). */
fun schemeFor(seed: Color, dark: Boolean): ColorScheme =
    if (!dark) lightColorScheme(
        primary = seed, onPrimary = Color.White,
        primaryContainer = lerp(seed, Color.White, 0.85f), onPrimaryContainer = lerp(seed, Color.Black, 0.5f),
        secondary = lerp(seed, Color(0xFF777777), 0.5f), onSecondary = Color.White,
        secondaryContainer = lerp(seed, Color.White, 0.9f), onSecondaryContainer = lerp(seed, Color.Black, 0.5f),
        tertiary = Color(0xFFB8860B), onTertiary = Color.White,
        background = lerp(seed, Color.White, 0.96f), onBackground = Color(0xFF1C1B1D),
        surface = lerp(seed, Color.White, 0.97f), onSurface = Color(0xFF1C1B1D),
        surfaceVariant = lerp(seed, Color.White, 0.9f), onSurfaceVariant = Color(0xFF494649),
        outline = Color(0xFF7B777A), error = Color(0xFFB3261E),
    ) else darkColorScheme(
        primary = lerp(seed, Color.White, 0.6f), onPrimary = lerp(seed, Color.Black, 0.6f),
        primaryContainer = lerp(seed, Color.Black, 0.25f), onPrimaryContainer = lerp(seed, Color.White, 0.85f),
        secondary = lerp(seed, Color.White, 0.7f), onSecondary = lerp(seed, Color.Black, 0.6f),
        secondaryContainer = lerp(seed, Color.Black, 0.45f), onSecondaryContainer = lerp(seed, Color.White, 0.85f),
        tertiary = Color(0xFFE8C468), onTertiary = Color(0xFF3B2F00),
        background = lerp(seed, Color.Black, 0.88f), onBackground = Color(0xFFE6E1E4),
        surface = lerp(seed, Color.Black, 0.86f), onSurface = Color(0xFFE6E1E4),
        surfaceVariant = lerp(seed, Color.Black, 0.7f), onSurfaceVariant = Color(0xFFCBC4C8),
        outline = Color(0xFF948F94), error = Color(0xFFF2B8B5),
    )

/** Tema de la app: paleta Guinda/Azul y modo claro/oscuro según el sistema. */
@Composable
fun CircuitoTheme(palette: String, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(seedOf(palette), isSystemInDarkTheme()), content = content)
}
