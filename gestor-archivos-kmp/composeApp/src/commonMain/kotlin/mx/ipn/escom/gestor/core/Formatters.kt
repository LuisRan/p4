package mx.ipn.escom.gestor.core

import kotlinx.serialization.json.Json

const val NO_DISPONIBLE = "No disponible"

/** Tamaño legible en base 1024. null o negativo -> "No disponible". */
fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return NO_DISPONIBLE
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    // Un decimal salvo valores grandes (evita depender de String.format, que no existe en common).
    val rounded = if (v >= 100) v.toLong().toString() else {
        val t = (v * 10).toLong()
        "${t / 10}.${t % 10}"
    }
    return "$rounded ${units[i]}"
}

/** Rellena con ceros a la izquierda (para fechas). */
fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()

/** Pretty print de JSON con sangría de 2 espacios. Lanza excepción si el JSON es inválido. */
private val prettyJsonFormat = Json { prettyPrint = true; prettyPrintIndent = "  " }

fun prettyJson(source: String): String {
    val element = prettyJsonFormat.parseToJsonElement(source)
    return prettyJsonFormat.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
}

/**
 * Pretty print de XML basado en el tokenizador de marcado (sin librerías).
 * Cada etiqueta va en su línea con sangría de 2 espacios; el texto sin espacios
 * entre etiquetas se mantiene en línea con su elemento (<a>texto</a>).
 * Lanza [IllegalArgumentException] si las etiquetas no están balanceadas.
 */
fun prettyXml(source: String): String {
    val parts = XmlLexer.split(source)
    val out = StringBuilder()
    var depth = 0
    var i = 0
    fun indent() = "  ".repeat(depth)
    val stack = ArrayList<String>()
    while (i < parts.size) {
        val p = parts[i]
        when (p.kind) {
            XmlPart.Kind.Text -> {
                val t = p.text.trim()
                if (t.isNotEmpty()) {
                    if (out.isNotEmpty() && !out.endsWith("\n")) out.append(t) else out.append(indent()).append(t).append('\n')
                }
            }
            XmlPart.Kind.Open -> {
                stack.add(XmlLexer.tagName(p.text))
                // <a>texto</a>: abrir, texto y cierre en una sola línea.
                val next = parts.getOrNull(i + 1)
                val after = parts.getOrNull(i + 2)
                if (next != null && next.kind == XmlPart.Kind.Text && next.text.isNotBlank() &&
                    after != null && after.kind == XmlPart.Kind.Close
                ) {
                    val name = stack.removeAt(stack.lastIndex)
                    require(XmlLexer.tagName(after.text) == name) { "Etiqueta de cierre inesperada: ${after.text}" }
                    out.append(indent()).append(p.text).append(next.text.trim()).append(after.text).append('\n')
                    i += 2
                } else {
                    out.append(indent()).append(p.text).append('\n')
                    depth++
                }
            }
            XmlPart.Kind.Close -> {
                require(stack.isNotEmpty() && stack.removeAt(stack.lastIndex) == XmlLexer.tagName(p.text)) {
                    "Etiqueta de cierre inesperada: ${p.text}"
                }
                depth--
                out.append(indent()).append(p.text).append('\n')
            }
            XmlPart.Kind.Other -> out.append(indent()).append(p.text).append('\n')
        }
        i++
    }
    require(stack.isEmpty()) { "Etiquetas sin cerrar: ${stack.joinToString()}" }
    return out.toString().trimEnd('\n')
}
