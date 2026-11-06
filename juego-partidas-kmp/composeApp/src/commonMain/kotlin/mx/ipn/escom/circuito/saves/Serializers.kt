package mx.ipn.escom.circuito.saves

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Contrato común de los tres serializadores. Cada uno convierte texto <-> mapa
 * neutral; la validación semántica es única ([SaveGame.fromMap]), así los tres
 * formatos son equivalentes.
 */
abstract class SaveSerializer {
    abstract val format: SaveFormat
    abstract fun encode(game: SaveGame): String

    /** Convierte el texto a mapa neutral (solo sintaxis). */
    protected abstract fun parse(content: String): Any?

    /** Decodifica de forma defensiva: solo lanza [SaveException]. */
    fun decode(content: String): SaveGame {
        if (content.isBlank()) throw EmptySaveException()
        try {
            return SaveGame.fromMap(parse(content))
        } catch (e: SaveException) {
            throw e
        } catch (e: Exception) {
            throw CorruptSaveException("No se pudo interpretar el archivo: ${e.message}")
        }
    }

    companion object {
        fun of(f: SaveFormat): SaveSerializer = when (f) {
            SaveFormat.TXT -> TxtSerializer
            SaveFormat.XML -> XmlSerializer
            SaveFormat.JSON -> JsonSerializer
        }
    }
}

// ------------------------------------------------------------------ JSON ----

object JsonSerializer : SaveSerializer() {
    override val format = SaveFormat.JSON
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    override fun encode(game: SaveGame): String =
        pretty.encodeToString(JsonElement.serializer(), toJson(game.toMap()))

    override fun parse(content: String): Any? {
        val el = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            throw CorruptSaveException("JSON inválido: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        return fromJson(el)
    }

    private fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
        is List<*> -> JsonArray(v.map { toJson(it) })
        else -> JsonPrimitive(v.toString())
    }

    private fun fromJson(el: JsonElement): Any? = when (el) {
        is JsonNull -> null
        is JsonObject -> el.entries.associate { it.key to fromJson(it.value) }
        is JsonArray -> el.map { fromJson(it) }
        is JsonPrimitive -> when {
            el.isString -> el.content
            el.booleanOrNull != null -> el.booleanOrNull
            el.longOrNull != null -> el.longOrNull
            else -> el.content.toDoubleOrNull() ?: el.content
        }
    }
}

// ------------------------------------------------------------------- XML ----

/**
 * `<save schemaVersion="1"><name/><savedAt/><tags/><game/><settings/></save>`.
 * Los elementos llevan el mismo nombre que las claves del mapa neutral.
 */
object XmlSerializer : SaveSerializer() {
    override val format = SaveFormat.XML

    @Suppress("UNCHECKED_CAST")
    override fun encode(game: SaveGame): String {
        val m = game.toMap()
        val g = m["game"] as Map<String, Any?>
        val s = m["settings"] as Map<String, Any?>
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<save schemaVersion="${m["schemaVersion"]}">""")
        sb.appendLine("  <name>${MiniXml.escape(game.name)}</name>")
        sb.appendLine("  <savedAt>${m["savedAt"]}</savedAt>")
        sb.appendLine("  <tags>")
        for (t in game.tags) sb.appendLine("    <tag>${MiniXml.escape(t)}</tag>")
        sb.appendLine("  </tags>")
        sb.appendLine("  <game>")
        for ((k, v) in g) {
            val text = if (v is List<*>) v.joinToString(",") else v.toString()
            sb.appendLine("    <$k>$text</$k>")
        }
        sb.appendLine("  </game>")
        sb.appendLine("  <settings>")
        for ((k, v) in s) sb.appendLine("    <$k>$v</$k>")
        sb.appendLine("  </settings>")
        sb.appendLine("</save>")
        return sb.toString()
    }

    override fun parse(content: String): Any? {
        val root = MiniXml.parse(content)
        if (root.name != "save") throw CorruptSaveException("El elemento raíz debe ser <save>.")

        fun csv(v: String?): List<String> =
            if (v == null || v.isBlank()) emptyList() else v.split(',').map { it.trim() }

        fun section(n: String): Map<String, Any?>? {
            val el = root.child(n) ?: return null
            val out = LinkedHashMap<String, Any?>()
            for (c in el.children) out[c.name] = c.text
            // Listas: si el elemento no existe, la clave no se agrega (-> "falta el campo").
            for (k in listOf("tiles", "moves")) if (k in out) out[k] = csv(out[k] as String?)
            return out
        }

        return mapOf(
            "schemaVersion" to root.attrs["schemaVersion"],
            "name" to root.child("name")?.text,
            "savedAt" to root.child("savedAt")?.text?.trim(),
            "tags" to (root.child("tags")?.children?.map { it.text } ?: emptyList<String>()),
            "game" to section("game"),
            "settings" to section("settings"),
        )
    }
}

/** Nodo de un mini-DOM XML. */
class XmlNode(val name: String, val attrs: Map<String, String>) {
    val children = ArrayList<XmlNode>()
    val textBuf = StringBuilder()
    val text: String get() = textBuf.toString()
    fun child(n: String): XmlNode? = children.firstOrNull { it.name == n }
}

/**
 * Parser XML mínimo y estricto (sin dependencias). Soporta prólogo,
 * comentarios, CDATA, atributos, entidades básicas y numéricas. Detecta
 * etiquetas sin cerrar o mal anidadas (archivos truncados).
 */
object MiniXml {
    fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private fun unescape(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') { sb.append(c); i++; continue }
            val end = s.indexOf(';', i)
            if (end < 0) throw CorruptSaveException("XML mal formado: entidad sin cerrar.")
            val ent = s.substring(i + 1, end)
            when {
                ent == "amp" -> sb.append('&')
                ent == "lt" -> sb.append('<')
                ent == "gt" -> sb.append('>')
                ent == "quot" -> sb.append('"')
                ent == "apos" -> sb.append('\'')
                ent.startsWith("#x") || ent.startsWith("#X") ->
                    sb.appendCodePointSafe(ent.substring(2).toIntOrNull(16) ?: throw CorruptSaveException("XML mal formado: entidad &$ent;."))
                ent.startsWith("#") ->
                    sb.appendCodePointSafe(ent.substring(1).toIntOrNull() ?: throw CorruptSaveException("XML mal formado: entidad &$ent;."))
                else -> throw CorruptSaveException("XML mal formado: entidad desconocida &$ent;.")
            }
            i = end + 1
        }
        return sb.toString()
    }

    private fun StringBuilder.appendCodePointSafe(cp: Int) {
        if (cp < 0x10000) append(cp.toChar())
        else {
            val c = cp - 0x10000
            append((0xD800 + (c shr 10)).toChar()); append((0xDC00 + (c and 0x3FF)).toChar())
        }
    }

    fun parse(src: String): XmlNode {
        var i = 0
        var root: XmlNode? = null
        val stack = ArrayList<XmlNode>()
        fun fail(msg: String): Nothing = throw CorruptSaveException("XML mal formado: $msg")

        while (i < src.length) {
            if (src[i] != '<') {
                val end = src.indexOf('<', i).let { if (it < 0) src.length else it }
                val txt = src.substring(i, end)
                if (stack.isEmpty()) {
                    if (txt.isNotBlank()) fail("texto fuera del elemento raíz.")
                } else stack.last().textBuf.append(unescape(txt))
                i = end
                continue
            }
            when {
                src.startsWith("<?", i) -> {
                    val e = src.indexOf("?>", i); if (e < 0) fail("declaración sin cerrar."); i = e + 2
                }
                src.startsWith("<!--", i) -> {
                    val e = src.indexOf("-->", i); if (e < 0) fail("comentario sin cerrar."); i = e + 3
                }
                src.startsWith("<![CDATA[", i) -> {
                    val e = src.indexOf("]]>", i); if (e < 0) fail("CDATA sin cerrar.")
                    if (stack.isEmpty()) fail("CDATA fuera del elemento raíz.")
                    stack.last().textBuf.append(src.substring(i + 9, e)); i = e + 3
                }
                src.startsWith("<!", i) -> {
                    val e = src.indexOf('>', i); if (e < 0) fail("declaración sin cerrar."); i = e + 1
                }
                src.startsWith("</", i) -> {
                    val e = src.indexOf('>', i); if (e < 0) fail("etiqueta de cierre sin terminar.")
                    val name = src.substring(i + 2, e).trim()
                    val open = stack.removeLastOrNull() ?: fail("etiqueta de cierre </$name> inesperada.")
                    if (open.name != name) fail("se esperaba </${open.name}> y se encontró </$name>.")
                    i = e + 1
                }
                else -> {
                    // Etiqueta de apertura: nombre + atributos (respetando comillas).
                    var j = i + 1
                    var quote: Char? = null
                    while (j < src.length) {
                        val c = src[j]
                        if (quote != null) { if (c == quote) quote = null }
                        else if (c == '"' || c == '\'') quote = c
                        else if (c == '>') break
                        j++
                    }
                    if (j >= src.length) fail("etiqueta de apertura sin terminar.")
                    var body = src.substring(i + 1, j)
                    val selfClose = body.endsWith("/")
                    if (selfClose) body = body.dropLast(1)
                    val nameEnd = body.indexOfFirst { it.isWhitespace() }.let { if (it < 0) body.length else it }
                    val name = body.substring(0, nameEnd)
                    if (name.isEmpty()) fail("etiqueta sin nombre.")
                    val node = XmlNode(name, parseAttrs(body.substring(nameEnd)))
                    if (stack.isEmpty()) {
                        if (root != null) fail("más de un elemento raíz.")
                        root = node
                    } else stack.last().children.add(node)
                    if (!selfClose) stack.add(node)
                    i = j + 1
                }
            }
        }
        if (stack.isNotEmpty()) fail("falta cerrar <${stack.last().name}> (archivo truncado).")
        return root ?: fail("no hay elemento raíz.")
    }

    private fun parseAttrs(s: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val rx = Regex("""\s*([^\s=]+)\s*=\s*("([^"]*)"|'([^']*)')""")
        var pos = 0
        while (pos < s.length) {
            if (s.substring(pos).isBlank()) break
            val m = rx.matchAt(s, pos) ?: throw CorruptSaveException("XML mal formado: atributo inválido.")
            out[m.groupValues[1]] = unescape(m.groupValues[3].ifEmpty { m.groupValues[4] })
            pos = m.range.last + 1
        }
        return out
    }
}

// ------------------------------------------------------------------- TXT ----

/**
 * Formato propio legible `clave=valor`. Se ignoran líneas vacías y las que
 * empiezan con '#'. En textos se escapan `\`, saltos de línea y `;`
 * (separador de etiquetas).
 */
object TxtSerializer : SaveSerializer() {
    override val format = SaveFormat.TXT
    private const val HEADER = "# Circuito Guinda - partida guardada (formato TXT)"

    fun escape(s: String): String = s
        .replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace(";", "\\;")

    /** Revierte [escape]; con [split] separa por los `;` no escapados. */
    fun unescapeSplit(s: String, split: Boolean = false): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                i++
                cur.append(when (s[i]) { 'n' -> '\n'; 'r' -> '\r'; else -> s[i] })
            } else if (c == ';' && split) {
                out.add(cur.toString()); cur.clear()
            } else cur.append(c)
            i++
        }
        out.add(cur.toString())
        return out
    }

    @Suppress("UNCHECKED_CAST")
    override fun encode(game: SaveGame): String {
        val m = game.toMap()
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        sb.appendLine("schemaVersion=${m["schemaVersion"]}")
        sb.appendLine("name=${escape(game.name)}")
        sb.appendLine("savedAt=${m["savedAt"]}")
        sb.appendLine("tags=${game.tags.joinToString(";") { escape(it) }}")
        sb.appendLine()
        sb.appendLine("# Estado de la partida")
        for ((k, v) in m["game"] as Map<String, Any?>) {
            sb.appendLine("game.$k=${if (v is List<*>) v.joinToString(",") else v}")
        }
        sb.appendLine()
        sb.appendLine("# Configuración personalizada")
        for ((k, v) in m["settings"] as Map<String, Any?>) sb.appendLine("settings.$k=$v")
        return sb.toString()
    }

    override fun parse(content: String): Any? {
        val flat = LinkedHashMap<String, String>()
        var lineNo = 0
        for (raw in content.lines()) {
            lineNo++
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) throw CorruptSaveException("Línea $lineNo inválida (se esperaba clave=valor).")
            flat[line.substring(0, eq).trim()] = line.substring(eq + 1)
        }
        if (flat.isEmpty()) throw EmptySaveException()

        fun sub(prefix: String): Map<String, Any?>? {
            val out = LinkedHashMap<String, Any?>()
            for ((k, v) in flat) if (k.startsWith("$prefix.")) out[k.substring(prefix.length + 1)] = v
            if (out.isEmpty()) return null
            for (k in listOf("tiles", "moves")) {
                if (k in out) {
                    val v = (out[k] as String).trim()
                    out[k] = if (v.isEmpty()) emptyList<String>() else v.split(',')
                }
            }
            return out
        }

        val tagsRaw = flat["tags"]
        return mapOf(
            "schemaVersion" to flat["schemaVersion"],
            "name" to flat["name"]?.let { unescapeSplit(it).first() },
            "savedAt" to flat["savedAt"]?.trim(),
            "tags" to if (tagsRaw.isNullOrEmpty()) emptyList<String>() else unescapeSplit(tagsRaw, split = true),
            "game" to sub("game"),
            "settings" to sub("settings"),
        )
    }
}
