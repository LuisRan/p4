package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.ipn.escom.circuito.core.formatClock
import mx.ipn.escom.circuito.core.nowMillis
import mx.ipn.escom.circuito.game.GameMode
import mx.ipn.escom.circuito.saves.SaveFormat

/** Pantalla de juego: cabecera, tablero con efectos y acciones. */
@Composable
fun GameScreen(
    env: AppEnv,
    gameKey: Int,
    notify: (String) -> Unit,
    onExit: () -> Unit,
    onNext: (GameMode, Int) -> Unit,
    onSaves: () -> Unit,
    onSettings: () -> Unit,
    onRaw: (String, String) -> Unit,
) {
    val c = env.controller
    val settings = env.settings
    val scope = rememberCoroutineScope()
    val fx = remember(gameKey) { BoardFx(scope).also { it.reset(c.state.size * c.state.size, c.state.size) } }
    @Suppress("UNUSED_VARIABLE") val rev = c.rev
    val state = c.state
    var showSave by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var resultDismissed by remember(gameKey) { mutableStateOf(false) }
    val partColors = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, Color(0xFFFFD54F))

    // Conecta los callbacks de efectos del controlador con la UI de esta partida.
    DisposableEffect(gameKey, settings.particles, settings.animations) {
        c.onTileEffect = { i, connected, undo ->
            fx.spin(i, undo, settings.animations)
            if (settings.particles) fx.burstAt(i, partColors, if (connected) 22 else 4)
        }
        c.onFinished = { won ->
            if (won && settings.particles) fx.celebrate(partColors)
            resultDismissed = false
        }
        onDispose {
            c.onTileEffect = null; c.onFinished = null
            // Al salir de la pantalla se persiste el estado para "Continuar".
            runCatching {
                if (settings.autosave && c.started && !c.state.finished) env.repo.writeAuto(c.toSave("autosave", nowMillis()))
            }
        }
    }

    // Cronómetro: un tick por segundo mientras la partida no ha terminado.
    LaunchedEffect(gameKey) {
        var n = 0
        while (true) {
            delay(1000)
            c.tick()
            if (++n % 10 == 0 && settings.autosave && !c.state.finished) {
                runCatching { env.repo.writeAuto(c.toSave("autosave", nowMillis())) }
            }
        }
    }

    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("< Inicio") }
            Column(Modifier.weight(1f)) {
                Text("${state.mode.label} - Nivel ${state.level}", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Text("${state.difficulty.label} ${state.size}x${state.size}", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("Menú") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Guardar partida...") }, onClick = { menu = false; showSave = true })
                    Text("Formato de guardado", Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall)
                    SaveFormat.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text((if (settings.format == f) "(x) " else "( ) ") + f.label) },
                            onClick = { settings.update(format = f); menu = false },
                        )
                    }
                    DropdownMenuItem(text = { Text("Gestionar partidas") }, onClick = { menu = false; onSaves() })
                    DropdownMenuItem(text = { Text("Ajustes") }, onClick = { menu = false; onSettings() })
                    DropdownMenuItem(text = { Text("Reiniciar nivel") }, onClick = {
                        menu = false; onNext(state.mode, state.level)
                    })
                }
            }
        }
    }

    val stats: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Puntos", "${state.score}")
            Stat("Tiempo", formatClock(state.elapsedSeconds))
            Stat("Movs", if (state.moveLimit > 0) "${state.moves.size}/${state.moveLimit}" else "${state.moves.size}")
            Stat("Conectadas", "${c.powered.size}/${state.size * state.size}")
        }
    }

    val actions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { c.undo() }, enabled = state.moves.isNotEmpty() && !state.finished,
                modifier = Modifier.weight(1f)) { Text("Deshacer") }
            Button(onClick = { showSave = true }, modifier = Modifier.weight(1f)) { Text("Guardar") }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val w = maxWidth
        val h = maxHeight
        val landscape = w > h * 1.15f
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val side = if (h < w * 0.6f) h else w * 0.6f
                BoardView(c, fx, settings.glow, Modifier.size(side))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header(); stats(); actions()
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                header(); stats()
                val side = if (w < h - 220.dp) w else (h - 220.dp).coerceAtLeast(160.dp)
                BoardView(c, fx, settings.glow, Modifier.size(side))
                actions()
            }
        }
    }

    if (showSave) {
        SaveDialog(
            defaultName = "${state.mode.label} nivel ${state.level}",
            defaultFormat = settings.format,
            onDismiss = { showSave = false },
            onSave = { name, tags, fmt ->
                showSave = false
                try {
                    val file = env.repo.save(c.toSave(name, nowMillis(), tags), fmt)
                    notify("Guardada como $file")
                } catch (e: Exception) {
                    notify("No se pudo guardar: ${e.message}")
                }
            },
        )
    }

    if (state.finished && !resultDismissed) {
        val won = state.won
        ConfirmDialog(
            title = if (won) "¡Nivel completado!" else "Sin movimientos",
            text = if (won) "Puntuación: ${state.score}\nMovimientos: ${state.moves.size} (mínimo ${c.minMoves})\nTiempo: ${formatClock(state.elapsedSeconds)}"
            else "Agotaste los ${state.moveLimit} movimientos. Inténtalo de nuevo.",
            confirm = if (won) "Siguiente nivel" else "Reintentar",
            onConfirm = {
                resultDismissed = true
                onNext(state.mode, if (won) state.level + 1 else state.level)
            },
            dismiss = "Inicio",
            onDismiss = { resultDismissed = true; onExit() },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
