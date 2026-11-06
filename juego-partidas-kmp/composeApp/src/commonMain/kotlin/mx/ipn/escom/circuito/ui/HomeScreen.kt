package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.ipn.escom.circuito.core.formatClock
import mx.ipn.escom.circuito.game.Difficulty
import mx.ipn.escom.circuito.game.GameMode

/** Menú principal: continuar, elegir modo/dificultad, partidas y ajustes. */
@Composable
fun HomeScreen(
    env: AppEnv,
    notify: (String) -> Unit,
    onPlay: (GameMode, Int) -> Unit,
    onContinue: () -> Unit,
    onSaves: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = env.progress
    val s = env.settings
    // Contenedor centrado con ancho máximo: se ve bien en teléfono y tablet.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Circuito Guinda", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("Gira las losetas y energiza todo el tablero.", style = MaterialTheme.typography.bodyLarge)

            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tu progreso", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Puntos totales: ${p.totalScore}   |   Mejor: ${p.bestScore}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Victorias: ${p.gamesWon}   Derrotas: ${p.gamesLost}   Tiempo: ${formatClock(p.totalSeconds)}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Mejor nivel: Clásico ${p.bestLevelClasico}, Reto ${p.bestLevelReto}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continuar partida") }

            Text("Dificultad", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = s.difficulty == d,
                        onClick = { s.update(difficulty = d) },
                        label = { Text("${d.label} ${d.baseSize}x${d.baseSize}") },
                    )
                }
            }

            GameMode.entries.forEach { mode ->
                val level = p.nextLevel(mode)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Modo ${mode.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (mode == GameMode.CLASICO) "Sin límite de movimientos. Gana puntos por rapidez y eficiencia."
                            else "Movimientos limitados y puntuación x1.5. ¡Si se agotan, pierdes el nivel!",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { onPlay(mode, level) }) { Text("Jugar nivel $level") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSaves, modifier = Modifier.weight(1f)) { Text("Partidas guardadas") }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Ajustes") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
