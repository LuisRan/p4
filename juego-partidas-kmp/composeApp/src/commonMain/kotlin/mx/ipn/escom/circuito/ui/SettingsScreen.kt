package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.ipn.escom.circuito.game.Difficulty

/** Opciones: formato de guardado, ajustes visuales y de jugabilidad. */
@Composable
fun SettingsScreen(env: AppEnv, onBack: () -> Unit) {
    val s = env.settings
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("< Atrás") }
                Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Section("Formato de guardado")
            FormatPicker(s.format) { s.update(format = it) }
            SwitchRow("Autoguardar (para Continuar)", s.autosave) { s.update(autosave = it) }

            Section("Apariencia")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.palette == "guinda", { s.update(palette = "guinda") }, label = { Text("Guinda") })
                FilterChip(s.palette == "azul", { s.update(palette = "azul") }, label = { Text("Azul") })
            }
            Text("El modo claro/oscuro sigue al sistema.", style = MaterialTheme.typography.bodySmall)
            SwitchRow("Animaciones de giro", s.animations) { s.update(animations = it) }
            SwitchRow("Partículas", s.particles) { s.update(particles = it) }
            SwitchRow("Brillo en tubos energizados", s.glow) { s.update(glow = it) }

            Section("Jugabilidad")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(s.difficulty == d, { s.update(difficulty = d) }, label = { Text("${d.label} ${d.baseSize}x${d.baseSize}") })
                }
            }
            Text("Se aplica a las partidas nuevas.", style = MaterialTheme.typography.bodySmall)
            SwitchRow("Sonido", s.sound) { s.update(sound = it) }
            SwitchRow("Vibración", s.vibration) { s.update(vibration = it) }
        }
    }
}

@Composable
private fun Section(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}
