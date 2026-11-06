package mx.ipn.escom.gestor.core

/** Bloques de un Markdown simple (lógica pura, probada en commonTest). */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class ListItem(val bullet: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data object Rule : MdBlock
    data object Blank : MdBlock
}

/** Fragmento en línea con su estilo. */
data class MdInline(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

object Markdown {
    private val heading = Regex("""^(#{1,6})\s+(.*)""")
    private val listItem = Regex("""^(\s*)([-*+]|\d+\.)\s+(.*)""")
    private val rule = Regex("""^\s*(---|\*\*\*|___)\s*$""")
    private val inlineRe = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")

    fun parse(source: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        var inCode = false
        val code = StringBuilder()
        for (line in source.replace("\r\n", "\n").split('\n')) {
            if (line.trimStart().startsWith("```")) {
                if (inCode) { out.add(MdBlock.Code(code.toString().trimEnd())); code.clear() }
                inCode = !inCode
                continue
            }
            if (inCode) { code.append(line).append('\n'); continue }
            val h = heading.find(line)
            val li = listItem.find(line)
            out.add(
                when {
                    h != null -> MdBlock.Heading(h.groupValues[1].length, h.groupValues[2])
                    rule.matches(line) -> MdBlock.Rule
                    li != null -> {
                        val marker = li.groupValues[2]
                        MdBlock.ListItem(if (marker[0].isDigit()) marker else "•", li.groupValues[3], li.groupValues[1].length)
                    }
                    line.startsWith(">") -> MdBlock.Quote(line.removePrefix(">").trimStart())
                    line.isBlank() -> MdBlock.Blank
                    else -> MdBlock.Paragraph(line)
                }
            )
        }
        // Bloque de código sin cerrar: se muestra igualmente.
        if (inCode) out.add(MdBlock.Code(code.toString().trimEnd()))
        return out
    }

    /** Divide una línea en fragmentos **negrita**, *cursiva* y `código`. */
    fun inline(s: String): List<MdInline> {
        val out = ArrayList<MdInline>()
        var last = 0
        for (m in inlineRe.findAll(s)) {
            if (m.range.first > last) out.add(MdInline(s.substring(last, m.range.first)))
            val g = m.groups
            when {
                g[1] != null -> out.add(MdInline(g[1]!!.value, bold = true))
                g[2] != null -> out.add(MdInline(g[2]!!.value, italic = true))
                else -> out.add(MdInline(g[3]!!.value, code = true))
            }
            last = m.range.last + 1
        }
        if (last < s.length) out.add(MdInline(s.substring(last)))
        return out
    }
}
