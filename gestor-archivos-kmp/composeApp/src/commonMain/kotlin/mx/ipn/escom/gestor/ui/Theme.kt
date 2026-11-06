package mx.ipn.escom.gestor.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Temas de la práctica: Guinda (IPN, #6A1B3A) y Azul (ESCOM, #0B3C8C). */
enum class AppTheme(val key: String, val label: String) {
    Guinda("guinda", "Guinda (IPN)"),
    Azul("azul", "Azul (ESCOM)");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Guinda
    }
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val guindaLight = lightColorScheme(
    primary = c(0x6A1B3A), onPrimary = c(0xFFFFFF), primaryContainer = c(0xFFD9E3), onPrimaryContainer = c(0x3A0720),
    secondary = c(0x75565F), onSecondary = c(0xFFFFFF), secondaryContainer = c(0xFFD9E2), onSecondaryContainer = c(0x2B151C),
    tertiary = c(0x7D5635), background = c(0xFFF8F8), onBackground = c(0x201A1B),
    surface = c(0xFFF8F8), onSurface = c(0x201A1B), surfaceVariant = c(0xF3DDE1), onSurfaceVariant = c(0x514347),
    outline = c(0x827377),
)
private val guindaDark = darkColorScheme(
    primary = c(0xFFB1C8), onPrimary = c(0x4A0F27), primaryContainer = c(0x6A1B3A), onPrimaryContainer = c(0xFFD9E3),
    secondary = c(0xE3BDC6), onSecondary = c(0x422932), secondaryContainer = c(0x5A3F47), onSecondaryContainer = c(0xFFD9E2),
    tertiary = c(0xF0BC95), background = c(0x181112), onBackground = c(0xECDFE0),
    surface = c(0x181112), onSurface = c(0xECDFE0), surfaceVariant = c(0x514347), onSurfaceVariant = c(0xD5C2C6),
    outline = c(0x9E8C90),
)
private val azulLight = lightColorScheme(
    primary = c(0x0B3C8C), onPrimary = c(0xFFFFFF), primaryContainer = c(0xD9E2FF), onPrimaryContainer = c(0x001945),
    secondary = c(0x575E71), onSecondary = c(0xFFFFFF), secondaryContainer = c(0xDBE2F9), onSecondaryContainer = c(0x141B2C),
    tertiary = c(0x715573), background = c(0xFEFBFF), onBackground = c(0x1A1B20),
    surface = c(0xFEFBFF), onSurface = c(0x1A1B20), surfaceVariant = c(0xE1E2EC), onSurfaceVariant = c(0x44474F),
    outline = c(0x74777F),
)
private val azulDark = darkColorScheme(
    primary = c(0xB0C6FF), onPrimary = c(0x002D6E), primaryContainer = c(0x0B3C8C), onPrimaryContainer = c(0xD9E2FF),
    secondary = c(0xBFC6DC), onSecondary = c(0x293041), secondaryContainer = c(0x3F4759), onSecondaryContainer = c(0xDBE2F9),
    tertiary = c(0xDEBCDF), background = c(0x1A1B20), onBackground = c(0xE3E2E6),
    surface = c(0x1A1B20), onSurface = c(0xE3E2E6), surfaceVariant = c(0x44474F), onSurfaceVariant = c(0xC4C6D0),
    outline = c(0x8E9099),
)

fun colorSchemeFor(theme: AppTheme, dark: Boolean): ColorScheme = when (theme) {
    AppTheme.Guinda -> if (dark) guindaDark else guindaLight
    AppTheme.Azul -> if (dark) azulDark else azulLight
}

/** [dark] se obtiene de isSystemInDarkTheme(): sigue al sistema automáticamente. */
@Composable
fun GestorTheme(theme: AppTheme, dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorSchemeFor(theme, dark), content = content)
}
