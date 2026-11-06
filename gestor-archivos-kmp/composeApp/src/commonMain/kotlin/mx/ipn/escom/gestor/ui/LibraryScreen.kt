@file:OptIn(ExperimentalMaterial3Api::class)

package mx.ipn.escom.gestor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.ipn.escom.gestor.data.formatDateTime

/** Recientes (máx. 20) o Favoritos; ambos persisten en prefs.json. */
@Composable
fun LibraryScreen(state: AppState, recents: Boolean) {
    val items = if (recents) state.prefs.recents else state.prefs.favorites
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (recents) "Recientes" else "Favoritos") },
            actions = { if (recents && items.isNotEmpty()) TextButton(onClick = { state.clearRecents() }) { Text("Vaciar") } },
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (recents) "Aún no ha abierto archivos" else "Marque archivos o carpetas con ⋮ > Añadir a favoritos")
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { it ->
                ListItem(
                    modifier = Modifier.clickable { state.openSaved(it) },
                    leadingContent = { Text(if (it.isDirectory) "📁" else "📄", style = MaterialTheme.typography.titleLarge) },
                    headlineContent = { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { if (recents) Text(formatDateTime(it.timeMillis)) else Text(it.id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        TextButton(onClick = { if (recents) state.removeRecent(it.id) else state.toggleFavorite(it) }) {
                            Text(if (recents) "✕" else "★")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
