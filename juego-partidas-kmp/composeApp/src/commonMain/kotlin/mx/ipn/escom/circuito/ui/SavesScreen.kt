package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.ipn.escom.circuito.core.formatClock
import mx.ipn.escom.circuito.saves.IsoTime
import mx.ipn.escom.circuito.saves.SaveEntry
import mx.ipn.escom.circuito.saves.SaveException
import mx.ipn.escom.circuito.saves.SaveFormat
import mx.ipn.escom.circuito.saves.SaveGame
import mx.ipn.escom.circuito.saves.SaveSerializer

/** Lista y gestión de partidas: cargar, ver crudo, exportar, compartir, etiquetar, borrar, importar. */
@Composable
fun SavesScreen(
    env: AppEnv,
    notify: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (SaveGame) -> Unit,
    onRaw: (String, String) -> Unit,
) {
    val repo = env.repo
    var refresh by remember { mutableIntStateOf(0) }
    val entries = remember(refresh) { runCatching { repo.list() }.getOrDefault(emptyList()) }
    var query by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<SaveEntry?>(null) }
    var tagsFor by remember { mutableStateOf<SaveEntry.Ok?>(null) }
    var exportFor by remember { mutableStateOf<SaveEntry.Ok?>(null) }

    val allTags = entries.filterIsInstance<SaveEntry.Ok>().flatMap { it.save.tags }.distinct().sorted()
    val shown = entries.filter { e ->
        when (e) {
            is SaveEntry.Broken -> query.isBlank() && tagFilter == null
            is SaveEntry.Ok -> (query.isBlank() || e.save.name.contains(query, ignoreCase = true)) &&
                (tagFilter == null || tagFilter in e.save.tags)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("< Atrás") }
                Text("Partidas guardadas", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = {
                    env.platform.importText { f ->
                        if (f != null) {
                            try {
                                val name = repo.import(f.name, f.content)
                                notify("Importada: $name"); refresh++
                            } catch (e: SaveException) {
                                notify("No se pudo importar: ${e.message}")
                            }
                        }
                    }
                }) { Text("Importar") }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar por nombre") }, singleLine = true)
            if (allTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allTags) { t ->
                        FilterChip(selected = tagFilter == t, onClick = { tagFilter = if (tagFilter == t) null else t }, label = { Text("#$t") })
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(
                    if (entries.isEmpty()) "Aún no hay partidas. Guarda una desde el juego o importa un archivo."
                    else "Ninguna partida coincide con el filtro.",
                    Modifier.padding(24.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.fileName }) { e ->
                    when (e) {
                        is SaveEntry.Ok -> OkCard(
                            e,
                            onLoad = { onOpen(e.save) },
                            onRaw = { runCatching { repo.readRaw(e.fileName) }.onSuccess { onRaw(e.fileName, it) }.onFailure { notify(it.message ?: "Error") } },
                            onExport = { exportFor = e },
                            onTags = { tagsFor = e },
                            onDelete = { toDelete = e },
                        )
                        is SaveEntry.Broken -> BrokenCard(
                            e,
                            onRaw = { runCatching { repo.readRaw(e.fileName) }.onSuccess { onRaw(e.fileName, it) }.onFailure { notify(it.message ?: "Error") } },
                            onDelete = { toDelete = e },
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { e ->
        ConfirmDialog("Eliminar partida", "¿Eliminar \"${e.fileName}\"? No se puede deshacer.", "Eliminar",
            onConfirm = {
                try { repo.delete(e.fileName); refresh++ } catch (x: SaveException) { notify(x.message) }
                toDelete = null
            }, onDismiss = { toDelete = null })
    }
    tagsFor?.let { e ->
        TagsDialog(e.save.tags, onDismiss = { tagsFor = null }, onSave = { tags ->
            try { repo.update(e.fileName) { it.copyWith(tags = tags) }; refresh++ } catch (x: SaveException) { notify(x.message) }
            tagsFor = null
        })
    }
    exportFor?.let { e ->
        fun content(f: SaveFormat) = SaveSerializer.of(f).encode(e.save)
        fun fname(f: SaveFormat) = e.fileName.substringBeforeLast('.') + "." + f.extension
        ExportDialog(
            e.format, onDismiss = { exportFor = null },
            onExport = { f ->
                exportFor = null
                env.platform.exportText(fname(f), f.mimeType, content(f)) { ok ->
                    notify(if (ok) "Archivo exportado." else "Exportación cancelada.")
                }
            },
            onShare = { f -> exportFor = null; env.platform.shareText(fname(f), f.mimeType, content(f)) },
        )
    }
}

@Composable
private fun OkCard(
    e: SaveEntry.Ok, onLoad: () -> Unit, onRaw: () -> Unit, onExport: () -> Unit, onTags: () -> Unit, onDelete: () -> Unit,
) {
    val g = e.save
    val s = g.state
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(g.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("${s.mode.label} - Nivel ${s.level} - ${s.difficulty.label} ${s.size}x${s.size}" + if (s.won) " - ¡Ganada!" else "")
            Text("${s.score} pts - ${formatClock(s.elapsedSeconds)} - ${s.moves.size} movs - ${e.format.extension.uppercase()}",
                style = MaterialTheme.typography.bodySmall)
            Text(IsoTime.format(g.savedAtMillis).replace('T', ' ').substringBefore('.') + " UTC", style = MaterialTheme.typography.bodySmall)
            if (g.tags.isNotEmpty()) Text(g.tags.joinToString("  ") { "#$it" }, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onLoad) { Text("Cargar") }
                TextButton(onClick = onRaw) { Text("Ver crudo") }
                Box {
                    TextButton(onClick = { menu = true }) { Text("Más") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Exportar / compartir") }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("Etiquetas") }, onClick = { menu = false; onTags() })
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun BrokenCard(e: SaveEntry.Broken, onRaw: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.fileName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Archivo dañado: ${e.error}", color = MaterialTheme.colorScheme.onErrorContainer)
            Row {
                TextButton(onClick = onRaw) { Text("Ver crudo") }
                TextButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}
