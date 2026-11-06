package mx.ipn.escom.gestor

import mx.ipn.escom.gestor.core.*
import mx.ipn.escom.gestor.data.*
import kotlin.test.*

class HighlighterTest {
    @Test fun jsonTokensRoundTrip() {
        val src = """{"a": [1, 2.5e3, true, null], "b": "x\"y"}"""
        val t = SyntaxHighlighter.tokenizeJson(src)
        assertEquals(src, t.joinToString("") { it.text })
        assertTrue(t.any { it.type == TokenType.Key && it.text == "\"a\"" })
        assertTrue(t.any { it.type == TokenType.Number && it.text == "2.5e3" })
        assertTrue(t.any { it.type == TokenType.Keyword && it.text == "null" })
        assertTrue(t.any { it.type == TokenType.String && it.text == "\"x\\\"y\"" })
    }

    @Test fun xmlTokensRoundTrip() {
        val src = "<?xml version=\"1.0\"?><!-- c --><a id=\"1\"><b/>texto</a>"
        val t = SyntaxHighlighter.tokenizeXml(src)
        assertEquals(src, t.joinToString("") { it.text })
        assertTrue(t.any { it.type == TokenType.Comment })
        assertTrue(t.any { it.type == TokenType.AttrName && it.text == "id" })
        assertTrue(t.any { it.type == TokenType.AttrValue && it.text == "\"1\"" })
        assertTrue(t.any { it.type == TokenType.Tag && it.text == "<a" })
    }

    @Test fun emptyInput() {
        assertTrue(SyntaxHighlighter.tokenizeJson("").isEmpty())
        assertTrue(SyntaxHighlighter.tokenizeXml("").isEmpty())
    }
}

class FormattersTest {
    @Test fun bytes() {
        assertEquals(NO_DISPONIBLE, formatBytes(null))
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1536L * 1024))
        assertEquals("200 MB", formatBytes(200L * 1024 * 1024))
    }

    @Test fun prettyJsonIndents() {
        assertEquals("{\n  \"a\": 1,\n  \"b\": [\n    true\n  ]\n}", prettyJson("""{"a":1,"b":[true]}"""))
        assertFailsWith<Exception> { prettyJson("{no json") }
    }

    @Test fun prettyXmlIndents() {
        val out = prettyXml("<a><b x=\"1\">hi</b><c/></a>")
        assertEquals("<a>\n  <b x=\"1\">hi</b>\n  <c/>\n</a>", out)
        assertFailsWith<IllegalArgumentException> { prettyXml("<a><b></a>") }
        assertFailsWith<IllegalArgumentException> { prettyXml("<a>") }
    }

    @Test fun mimeAndKinds() {
        assertEquals("application/json", FileTypes.mimeOf("x.JSON"))
        assertEquals("public.png", FileTypes.utTypeOf("a.png"))
        assertNull(FileTypes.mimeOf("sin_extension"))
        assertEquals(ViewerKind.Markdown, FileTypes.kindOf("r.md"))
        assertEquals(ViewerKind.Unsupported, FileTypes.kindOf("d.bin"))
    }
}

class MarkdownTest {
    @Test fun blocks() {
        val b = Markdown.parse("# T\n\n- uno\n1. dos\n> q\n```\ncode\n```\n---\ntexto")
        assertEquals(MdBlock.Heading(1, "T"), b[0])
        assertEquals(MdBlock.Blank, b[1])
        assertEquals(MdBlock.ListItem("•", "uno", 0), b[2])
        assertEquals(MdBlock.ListItem("1.", "dos", 0), b[3])
        assertEquals(MdBlock.Quote("q"), b[4])
        assertEquals(MdBlock.Code("code"), b[5])
        assertEquals(MdBlock.Rule, b[6])
        assertEquals(MdBlock.Paragraph("texto"), b[7])
    }

    @Test fun inline() {
        val s = Markdown.inline("a **b** *c* `d`")
        assertEquals(listOf("a ", "b", " ", "c", " ", "d"), s.map { it.text })
        assertTrue(s[1].bold && s[3].italic && s[5].code)
    }
}

class PrefsTest {
    @Test fun roundTrip() {
        val p = PrefsData(
            favorites = listOf(SavedItem("/a", "a.txt")), recents = listOf(SavedItem("/b", "b", true, 5L)),
            theme = "azul", roots = listOf(SavedRoot("tok", "Carpeta")), seeded = true,
        )
        assertEquals(p, PrefsCodec.decode(PrefsCodec.encode(p)))
    }

    @Test fun corruptOrEmptyGivesDefaults() {
        assertEquals(PrefsData(), PrefsCodec.decode(null))
        assertEquals(PrefsData(), PrefsCodec.decode("{ esto no es json"))
        assertEquals("x", PrefsCodec.decode("""{"theme":"x","desconocido":1}""").theme)
    }

    @Test fun recentsAndFavorites() {
        var p = PrefsData()
        for (i in 1..25) p = p.addRecent(SavedItem("/f$i", "f$i"))
        assertEquals(PrefsData.MAX_RECENTS, p.recents.size)
        assertEquals("/f25", p.recents.first().id)
        p = p.addRecent(SavedItem("/f10", "f10")) // sube al principio sin duplicarse
        assertEquals("/f10", p.recents.first().id)
        assertEquals(1, p.recents.count { it.id == "/f10" })
        val fav = SavedItem("/x", "x")
        p = p.toggleFavorite(fav); assertTrue(p.isFavorite("/x"))
        p = p.toggleFavorite(fav); assertFalse(p.isFavorite("/x"))
    }
}

class PngTest {
    @Test fun signatureAndSize() {
        val png = PngEncoder.encode(4, 4) { x, y -> x * 40 shl 16 or (y * 40) }
        assertEquals(0x89.toByte(), png[0])
        assertEquals("PNG", png.copyOfRange(1, 4).decodeToString())
        assertTrue(png.size > 60)
    }
}

/** Almacén en memoria para probar FileOps sin tocar el disco. Ids = rutas. */
class FakeStore : FileStore {
    val files = mutableMapOf<String, ByteArray?>() // null = carpeta
    var free: Long? = null
    var writable = true
    init { files["/"] = null }
    private fun name(id: String) = id.substringAfterLast('/')
    private fun entry(id: String) = FileEntry(id, name(id), files[id] == null, files[id]?.size?.toLong())
    override fun list(dirId: String) = files.keys.filter { it != "/" && it.substringBeforeLast('/').ifEmpty { "/" } == dirId }.map { entry(it) }
    override fun stat(id: String) = if (id in files) entry(id) else throw AppException(FailureKind.NotFound)
    override fun exists(dirId: String, name: String) = joinPath(dirId, name).replace("//", "/") in files
    override fun createDirectory(parentId: String, name: String): String { val p = joinPath(parentId, name).replace("//", "/"); files[p] = null; return p }
    override fun copyFile(srcId: String, destDirId: String, destName: String, onBytes: (Long) -> Unit): String {
        val p = joinPath(destDirId, destName).replace("//", "/"); files[p] = files[srcId]; onBytes(files[srcId]!!.size.toLong()); return p
    }
    override fun delete(id: String) { files.keys.filter { it == id || it.startsWith("$id/") }.forEach { files.remove(it) } }
    override fun rename(id: String, newName: String): String { val n = id.substringBeforeLast('/') + "/" + newName; files[n] = files.remove(id); return n }
    override fun readBytes(id: String, maxBytes: Int) = files[id] ?: ByteArray(0)
    override fun writeBytes(dirId: String, name: String, bytes: ByteArray): String { val p = joinPath(dirId, name).replace("//", "/"); files[p] = bytes; return p }
    override fun canWrite(dirId: String) = writable
    override fun freeSpace(dirId: String) = free
    override fun isInside(candidateId: String, ancestorId: String) = candidateId == ancestorId || candidateId.startsWith("$ancestorId/")
}

class FileOpsTest {
    private fun setup(): Pair<FakeStore, FileOps> {
        val s = FakeStore()
        s.files["/a"] = null; s.files["/b"] = null
        s.files["/a/f.txt"] = ByteArray(10); s.files["/a/sub"] = null; s.files["/a/sub/g.txt"] = ByteArray(5)
        return s to FileOps(s)
    }

    @Test fun validateNames() {
        val ops = FileOps(FakeStore())
        ops.validateName("ok.txt")
        for (bad in listOf("", "  ", ".", "..", "a/b", "a:b", "x".repeat(256))) {
            val e = assertFailsWith<AppException>(bad) { ops.validateName(bad) }
            assertEquals(FailureKind.InvalidName, e.kind)
        }
    }

    @Test fun uniqueNameAddsSuffix() {
        val (s, ops) = setup()
        assertEquals("nuevo.txt", ops.uniqueName("/a", "nuevo.txt"))
        assertEquals("f (1).txt", ops.uniqueName("/a", "f.txt"))
        s.files["/a/f (1).txt"] = ByteArray(0)
        assertEquals("f (2).txt", ops.uniqueName("/a", "f.txt"))
    }

    @Test fun copyFolderRecursivelyWithProgress() {
        val (s, ops) = setup()
        var last = 0L
        val e = s.stat("/a/sub")
        ops.copy(e, "/b") { d, t -> last = d; assertEquals(5L, t) }
        assertEquals(5L, last)
        assertTrue("/b/sub/g.txt" in s.files)
    }

    @Test fun cannotCopyFolderIntoItself() {
        val (s, ops) = setup()
        val e = assertFailsWith<AppException> { ops.copy(s.stat("/a"), "/a/sub") }
        assertEquals(FailureKind.NotAllowed, e.kind)
    }

    @Test fun moveRemovesSourceAndRejectsSameDir() {
        val (s, ops) = setup()
        ops.move(s.stat("/a/f.txt"), "/b", "/a")
        assertTrue("/b/f.txt" in s.files); assertFalse("/a/f.txt" in s.files)
        assertFailsWith<AppException> { ops.move(s.stat("/b/f.txt"), "/b", "/b") }
    }

    @Test fun destinationValidation() {
        val (s, ops) = setup()
        s.writable = false
        assertEquals(FailureKind.NotAllowed, assertFailsWith<AppException> { ops.copy(s.stat("/a/f.txt"), "/b") }.kind)
        s.writable = true; s.free = 3
        assertEquals(FailureKind.NoSpace, assertFailsWith<AppException> { ops.copy(s.stat("/a/f.txt"), "/b") }.kind)
    }

    @Test fun renameChecksCollision() {
        val (s, ops) = setup()
        s.files["/a/h.txt"] = ByteArray(1)
        assertFailsWith<AppException> { ops.rename(s.stat("/a/f.txt"), "h.txt", "/a") }
        assertEquals("/a/z.txt", ops.rename(s.stat("/a/f.txt"), "z.txt", "/a"))
    }

    @Test fun seedIsIdempotent() {
        val s = FakeStore()
        SeedData.seed(s, "/")
        val n = s.files.size
        SeedData.seed(s, "/")
        assertEquals(n, s.files.size)
        assertTrue(s.files.keys.any { it.endsWith("degradado.png") } && s.files.keys.any { it.endsWith("datos.bin") })
    }
}
