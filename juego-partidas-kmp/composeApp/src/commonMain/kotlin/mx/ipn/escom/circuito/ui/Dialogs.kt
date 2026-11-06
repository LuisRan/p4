package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import mx.ipn.escom.circuito.saves.SaveFormat

/** Convierte "a, b;c" en ["a","b","c"] (sin vacíos ni duplicados). */
fun parseTags(text: String): List<String> =
    text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

@Composable
fun FormatPicker(selected: SaveFormat, onSelect: (SaveFormat) -> Unit) {
    Column {
        SaveFormat.entries.forEach { f ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(f) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == f, onClick = { onSelect(f) })
                Text(f.label)
            }
        }
    }
}

/** Diálogo para guardar: nombre, etiquetas y formato. */
@Composable
fun SaveDialog(
    defaultName: String,
    defaultFormat: SaveFormat,
    onDismiss: () -> Unit,
    onSave: (name: String, tags: List<String>, format: SaveFormat) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    var tags by remember { mutableStateOf("") }
    var fmt by remember { mutableStateOf(defaultFormat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar partida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(tags, { tags = it }, label = { Text("Etiquetas (separadas por coma)") }, singleLine = true)
                Text("Formato", Modifier.padding(top = 4.dp))
                FormatPicker(fmt) { fmt = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { defaultName }, parseTags(tags), fmt) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun TagsDialog(initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(initial.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiquetas") },
        text = { OutlinedTextField(text, { text = it }, label = { Text("Separadas por coma") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(parseTags(text)) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Exportar a ubicación elegida o compartir, con el formato deseado. */
@Composable
fun ExportDialog(
    initial: SaveFormat,
    onDismiss: () -> Unit,
    onExport: (SaveFormat) -> Unit,
    onShare: (SaveFormat) -> Unit,
) {
    var fmt by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar o compartir") },
        text = { FormatPicker(fmt) { fmt = it } },
        confirmButton = {
            Row {
                TextButton(onClick = { onShare(fmt) }) { Text("Compartir") }
                TextButton(onClick = { onExport(fmt) }) { Text("Exportar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String, onConfirm: () -> Unit,
    dismiss: String = "Cancelar", onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}
