@file:OptIn(ExperimentalMaterial3Api::class)

package mx.ipn.escom.gestor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ipn.escom.gestor.core.NO_DISPONIBLE
import mx.ipn.escom.gestor.core.formatBytes
import mx.ipn.escom.gestor.data.FileEntry
import mx.ipn.escom.gestor.data.formatDateTime

/** Todos los diálogos modales de la app, controlados por state.dialog y state.progress. */
@Composable
fun AppDialogs(state: AppState) {
    state.progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(p.label) },
            text = {
                Column {
                    if (p.total > 0) {
                        LinearProgressIndicator(progress = { (p.done.toFloat() / p.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${formatBytes(p.done)} de ${formatBytes(p.total)}")
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
        )
    }
    when (val d = state.dialog) {
        null -> {}
        is Dialog.Rename -> TextPrompt("Renombrar", d.entry.name, "Aceptar", { state.dialog = null }) { state.rename(d.entry, it) }
        Dialog.NewFolder -> TextPrompt("Nueva carpeta", "", "Crear", { state.dialog = null }) { state.createFolder(it) }
        is Dialog.Delete -> AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text("Eliminar") },
            text = { Text("¿Eliminar \"${d.entry.name}\"?" + (if (d.entry.isDirectory) " Se borrará también todo su contenido." else "") + " Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { state.delete(d.entry) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { state.dialog = null }) { Text("Cancelar") } },
        )
        is Dialog.Details -> DetailsDialog(d.entry) { state.dialog = null }
        is Dialog.Unsupported -> AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text("Formato no soportado") },
            text = {
                Text("La app no puede mostrar \"${d.entry.name}\" (${d.entry.mime ?: "tipo desconocido"}). " +
                    "Puede abrirlo con otra aplicación, compartirlo o exportarlo.")
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { state.openWith(d.entry) }) { Text("Abrir con…") }
                    TextButton(onClick = { state.share(d.entry) }) { Text("Compartir") }
                    TextButton(onClick = { state.export(d.entry) }) { Text("Exportar…") }
                }
            },
            dismissButton = { TextButton(onClick = { state.dialog = null }) { Text("Cancelar") } },
        )
        Dialog.AccessRationale -> AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text("Acceso a una carpeta") },
            text = {
                Text("Para explorar archivos fuera de esta app, el sistema le mostrará su selector de carpetas. " +
                    "Sólo se accederá a la carpeta que elija (y a su contenido), para listar, abrir y administrar sus archivos. " +
                    "No se envía nada fuera del dispositivo y puede quitar el acceso cuando quiera.")
            },
            confirmButton = { TextButton(onClick = { state.pickFolder() }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { state.dialog = null }) { Text("Ahora no") } },
        )
        is Dialog.RemoveRoot -> AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text("Quitar carpeta") },
            text = { Text("Se dejará de mostrar \"${d.root.name}\" en la app. Sus archivos no se borran.") },
            confirmButton = { TextButton(onClick = { state.removeRoot(d.root) }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { state.dialog = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TextPrompt(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Metadatos: nombre, tamaño, fechas y MIME/UTType; "No disponible" cuando falta el dato. */
@Composable
fun DetailsDialog(e: FileEntry, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Detalles") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row2("Nombre", e.name)
                Row2("Tipo", if (e.isDirectory) "Carpeta" else "Archivo")
                Row2("Tamaño", if (e.isDirectory) NO_DISPONIBLE else formatBytes(e.size))
                Row2("Modificado", e.modified?.let { formatDateTime(it) } ?: NO_DISPONIBLE)
                Row2("Creado", e.created?.let { formatDateTime(it) } ?: NO_DISPONIBLE)
                Row2("MIME", e.mime ?: NO_DISPONIBLE)
                Row2("UTType", e.utType ?: NO_DISPONIBLE)
                Row2("Ubicación", e.id)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}

@Composable
private fun Row2(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
