package mx.ipn.escom.gestor.core

/** Fragmento de un documento XML (etiqueta, texto, comentario...). */
class XmlPart(val kind: Kind, val text: String) {
    enum class Kind { Text, Open, Close, Other }
}

/** Divisor de XML a mano (sin dependencias) usado por el pretty printer. */
object XmlLexer {
    fun split(src: String): List<XmlPart> {
        val out = ArrayList<XmlPart>()
        var i = 0
        while (i < src.length) {
            if (src[i] != '<') {
                val end = src.indexOf('<', i).let { if (it < 0) src.length else it }
                out.add(XmlPart(XmlPart.Kind.Text, src.substring(i, end)))
                i = end
                continue
            }
            val end = markupEnd(src, i)
            val s = src.substring(i, end)
            val kind = when {
                s.startsWith("</") -> XmlPart.Kind.Close
                s.startsWith("<!") || s.startsWith("<?") || s.endsWith("/>") -> XmlPart.Kind.Other
                else -> XmlPart.Kind.Open
            }
            out.add(XmlPart(kind, s))
            i = end
        }
        return out
    }

    /** Índice (exclusivo) donde termina el marcado que empieza en [start]. */
    private fun markupEnd(src: String, start: Int): Int {
        fun after(terminator: String): Int {
            val k = src.indexOf(terminator, start)
            return if (k < 0) src.length else k + terminator.length
        }
        return when {
            src.startsWith("<!--", start) -> after("-->")
            src.startsWith("<![CDATA[", start) -> after("]]>")
            src.startsWith("<?", start) -> after("?>")
            else -> {
                // Etiqueta o declaración: terminar en '>' ignorando los que van entre comillas.
                var quote: Char? = null
                var j = start + 1
                while (j < src.length) {
                    val c = src[j]
                    if (quote != null) { if (c == quote) quote = null }
                    else if (c == '"' || c == '\'') quote = c
                    else if (c == '>') return j + 1
                    j++
                }
                src.length
            }
        }
    }

    /** Nombre de la etiqueta: "<a x='1'>" -> "a", "</a>" -> "a". */
    fun tagName(tag: String): String =
        tag.trimStart('<', '/').takeWhile { !it.isWhitespace() && it != '>' && it != '/' }
}
