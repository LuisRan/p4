@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package mx.ipn.escom.gestor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.ipn.escom.gestor.core.formatBytes
import mx.ipn.escom.gestor.data.FileEntry
import mx.ipn.escom.gestor.data.RootInfo
import mx.ipn.escom.gestor.data.formatDateTime

/** Pantalla Explorar: lista de ubicaciones (sin ruta) o contenido de la carpeta actual con migas de pan. */
@Composable
fun ExplorerScreen(state: AppState, wide: Boolean) {
    Column(Modifier.fillMaxSize()) {
        if (state.trail.isEmpty()) RootsList(state) else DirectoryView(state, wide)
    }
}

@Composable
private fun RootsList(state: AppState) {
    TopAppBar(title = { Text("Gestor de archivos") })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Ubicaciones", style = MaterialTheme.typography.titleMedium) }
        items(state.roots, key = { it.id }) { root -> RootCard(state, root) }
        item {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { state.dialog = Dialog.AccessRationale }) { Text("Añadir carpeta del dispositivo…") }
            Spacer(Modifier.height(4.dp))
            Text(
                "La app sólo ve su propio espacio y las carpetas que usted elija con el selector del sistema.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RootCard(state: AppState, root: RootInfo) {
    Card(Modifier.fillMaxWidth().clickable { state.openRoot(root) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (root.external) "📂" else "🏠", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(root.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        !root.available -> "Permiso revocado o ubicación no disponible"
                        root.external -> "Carpeta elegida por el usuario"
                        else -> "Espacio propio de la app (sin permisos)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (root.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            if (root.external) TextButton(onClick = { state.dialog = Dialog.RemoveRoot(root) }) { Text("Quitar") }
        }
    }
}

@Composable
private fun DirectoryView(state: AppState, wide: Boolean) {
    val dir = state.currentDir!!
    TopAppBar(
        title = { Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { TextButton(onClick = { state.up() }) { Text("←") } },
        actions = {
            TextButton(onClick = { state.reload() }) { Text("↻") }
            TextButton(onClick = { state.dialog = Dialog.NewFolder }) { Text("＋📁") }
            TextButton(onClick = { state.importFiles() }) { Text("Importar") }
        },
    )
    Breadcrumbs(state)
    state.clip?.let { c ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (c.move) "Mover: " else "Copiar: ") + c.entry.name,
                    Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { state.paste() }) { Text("Pegar aquí") }
                TextButton(onClick = { state.clip = null }) { Text("Cancelar") }
            }
        }
    }
    when {
        state.loading && state.entries.isEmpty() -> LinearProgressIndicator(Modifier.fillMaxWidth())
        state.listError != null -> ErrorPane(state.listError!!.message ?: "Error", onRetry = { state.reload() }, onBack = { state.up() })
        state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Carpeta vacía") }
        wide -> LazyVerticalGrid(
            GridCells.Adaptive(168.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(state.entries, key = { it.id }) { GridCell(state, it) } }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(state.entries, key = { it.id }) { ListRow(state, it); HorizontalDivider() }
        }
    }
}

/** Migas de pan: cada tramo es pulsable y lleva a esa carpeta. */
@Composable
private fun Breadcrumbs(state: AppState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.trail.forEachIndexed { i, c ->
            if (i > 0) Text("›", color = MaterialTheme.colorScheme.outline)
            val last = i == state.trail.lastIndex
            TextButton(onClick = { if (!last) state.goToCrumb(i) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(c.name, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
fun ErrorPane(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚠", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("Reintentar") }
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }
    }
}

@Composable
private fun ListRow(state: AppState, e: FileEntry) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = { state.open(e) }, onLongClick = { state.dialog = Dialog.Details(e) }),
        leadingContent = { EntryThumb(state, e, 44.dp) },
        headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(if (e.isDirectory) "Carpeta" else "${formatBytes(e.size)} · ${e.modified?.let { formatDateTime(it) } ?: "Fecha no disponible"}")
        },
        trailingContent = { EntryMenu(state, e) },
    )
}

@Composable
private fun GridCell(state: AppState, e: FileEntry) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = { state.open(e) }, onLongClick = { state.dialog = Dialog.Details(e) })) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EntryThumb(state, e, 96.dp)
            Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (e.isDirectory) "Carpeta" else formatBytes(e.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                EntryMenu(state, e)
            }
        }
    }
}

/** Menú ⋮ con las acciones sobre un elemento. */
@Composable
fun EntryMenu(state: AppState, e: FileEntry) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("⋮") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            @Composable fun item(label: String, action: () -> Unit) =
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            item("Abrir") { state.open(e) }
            item(if (state.isFavorite(e.id)) "Quitar de favoritos" else "Añadir a favoritos") { state.toggleFavorite(e) }
            item("Copiar") { state.copyToClip(e, move = false) }
            item("Mover") { state.copyToClip(e, move = true) }
            item("Renombrar") { state.dialog = Dialog.Rename(e) }
            item("Eliminar") { state.dialog = Dialog.Delete(e) }
            item("Detalles") { state.dialog = Dialog.Details(e) }
            if (!e.isDirectory) item("Compartir") { state.share(e) }
        }
    }
}
