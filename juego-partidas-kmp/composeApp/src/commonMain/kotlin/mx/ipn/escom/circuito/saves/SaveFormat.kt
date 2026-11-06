package mx.ipn.escom.circuito.saves

/** Formatos de guardado soportados. */
enum class SaveFormat(val extension: String, val label: String, val mimeType: String) {
    TXT("txt", "TXT (clave=valor)", "text/plain"),
    XML("xml", "XML", "application/xml"),
    JSON("json", "JSON", "application/json");

    companion object {
        /** Formato por extensión (con o sin punto), o null. */
        fun fromExtension(ext: String): SaveFormat? {
            val e = ext.lowercase().replace(".", "")
            return entries.firstOrNull { it.extension == e }
        }

        /** Detecta el formato mirando el contenido (para importar con extensión rara). */
        fun sniff(content: String): SaveFormat {
            val t = content.trimStart()
            return when {
                t.startsWith("{") -> JSON
                t.startsWith("<") -> XML
                else -> TXT
            }
        }
    }
}
