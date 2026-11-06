package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Visor del contenido crudo (texto) de un archivo de guardado. */
@Composable
fun RawViewerScreen(title: String, content: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Atrás") }
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        SelectionContainer {
            Text(
                content.ifEmpty { "(archivo vacío)" },
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            )
        }
    }
}
