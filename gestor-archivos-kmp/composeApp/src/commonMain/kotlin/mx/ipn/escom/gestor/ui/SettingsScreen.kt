@file:OptIn(ExperimentalMaterial3Api::class)

package mx.ipn.escom.gestor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.escom.gestor.core.formatBytes

/** Selector de tema (Guinda/Azul); el modo claro/oscuro lo decide el sistema. */
@Composable
fun SettingsScreen(state: AppState) {
    var cacheSize by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.progress) { cacheSize = withContext(Dispatchers.Default) { state.thumbs.diskUsage() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(title = { Text("Ajustes") })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            AppTheme.entries.forEach { t ->
                Row {
                    RadioButton(selected = state.theme == t, onClick = { state.setTheme(t) })
                    TextButton(onClick = { state.setTheme(t) }) { Text(t.label) }
                }
            }
            Text(
                "El modo claro u oscuro sigue automáticamente la configuración del sistema.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("Caché de miniaturas", style = MaterialTheme.typography.titleMedium)
            Text("En disco: ${formatBytes(cacheSize)} (límite 20 MB)")
            OutlinedButton(onClick = { state.clearThumbnails(); cacheSize = 0 }) { Text("Vaciar caché") }
            HorizontalDivider()
            Text("Acerca de", style = MaterialTheme.typography.titleMedium)
            Text("Gestor de archivos KMP - IPN ESCOM. Kotlin Multiplatform + Compose Multiplatform.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
