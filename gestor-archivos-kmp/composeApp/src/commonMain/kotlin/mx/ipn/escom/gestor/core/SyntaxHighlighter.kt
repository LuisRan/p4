package mx.ipn.escom.gestor.core

/** Categorías de token; el color lo decide la UI (paleta distinta en claro/oscuro). */
enum class TokenType { Plain, Key, String, Number, Keyword, Punct, Tag, AttrName, AttrValue, Comment }

data class Token(val text: String, val type: TokenType)

/**
 * Resaltador propio (sin librerías) para JSON y XML.
 * Es lógica pura: concatenar los tokens siempre reproduce el texto original.
 */
object SyntaxHighlighter {
    // JSON: cadena (con ':' posterior => clave), número, literal, puntuación.
    private val jsonRe = Regex(
        """("(?:\\.|[^"\\])*")(\s*:)?|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b|([{}\[\],:])"""
    )

    fun tokenizeJson(src: String): List<Token> {
        val out = ArrayList<Token>()
        var last = 0
        for (m in jsonRe.findAll(src)) {
            if (m.range.first > last) out.add(Token(src.substring(last, m.range.first), TokenType.Plain))
            val g = m.groups
            when {
                g[1] != null && g[2] != null -> {
                    out.add(Token(g[1]!!.value, TokenType.Key))
                    out.add(Token(g[2]!!.value, TokenType.Punct))
                }
                g[1] != null -> out.add(Token(g[1]!!.value, TokenType.String))
                g[3] != null -> out.add(Token(g[3]!!.value, TokenType.Number))
                g[4] != null -> out.add(Token(g[4]!!.value, TokenType.Keyword))
                else -> out.add(Token(g[5]!!.value, TokenType.Punct))
            }
            last = m.range.last + 1
        }
        if (last < src.length) out.add(Token(src.substring(last), TokenType.Plain))
        return out
    }

    private val attrRe = Regex("""([^\s=/<>]+)(\s*=\s*)("[^"]*"|'[^']*')""")

    fun tokenizeXml(src: String): List<Token> {
        val out = ArrayList<Token>()
        // Reutiliza el lexer: texto -> Plain; comentarios/CDATA -> Comment; PI/doctype -> Keyword.
        for (p in XmlLexer.split(src)) {
            val s = p.text
            when {
                p.kind == XmlPart.Kind.Text -> out.add(Token(s, TokenType.Plain))
                s.startsWith("<!--") || s.startsWith("<![CDATA[") -> out.add(Token(s, TokenType.Comment))
                s.startsWith("<?") || s.startsWith("<!") -> out.add(Token(s, TokenType.Keyword))
                else -> {
                    val head = tagHead(s)
                    out.add(Token(head, TokenType.Tag))
                    val closeLen = if (s.endsWith("/>")) 2 else 1
                    val bodyEnd = (s.length - closeLen).coerceAtLeast(head.length)
                    val body = s.substring(head.length, bodyEnd)
                    var bl = 0
                    for (a in attrRe.findAll(body)) {
                        if (a.range.first > bl) out.add(Token(body.substring(bl, a.range.first), TokenType.Plain))
                        out.add(Token(a.groupValues[1], TokenType.AttrName))
                        out.add(Token(a.groupValues[2], TokenType.Punct))
                        out.add(Token(a.groupValues[3], TokenType.AttrValue))
                        bl = a.range.last + 1
                    }
                    if (bl < body.length) out.add(Token(body.substring(bl), TokenType.Plain))
                    if (bodyEnd < s.length) out.add(Token(s.substring(bodyEnd), TokenType.Tag))
                }
            }
        }
        return out
    }

    /** "<a x='1'>" -> "<a"; "</a>" -> "</a". */
    private fun tagHead(s: String): String {
        var i = 1
        if (i < s.length && s[i] == '/') i++
        while (i < s.length && !s[i].isWhitespace() && s[i] != '>' && s[i] != '/') i++
        return s.substring(0, i)
    }
}
