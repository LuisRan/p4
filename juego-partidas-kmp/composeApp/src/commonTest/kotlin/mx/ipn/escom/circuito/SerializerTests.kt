package mx.ipn.escom.circuito

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import mx.ipn.escom.circuito.platform.MemoryFileStore
import mx.ipn.escom.circuito.saves.*

class SerializerTests {
    private fun assertSame(a: SaveGame, b: SaveGame) {
        assertEquals(a.name, b.name)
        assertEquals(a.savedAtMillis, b.savedAtMillis)
        assertEquals(a.tags, b.tags)
        assertEquals(a.settings, b.settings)
        assertTrue(a.state.sameAs(b.state), "El estado de juego difiere")
    }

    @Test fun roundTripAllFormats() {
        val save = sampleSave()
        for (f in SaveFormat.entries) {
            val ser = SaveSerializer.of(f)
            val text = ser.encode(save)
            assertSame(save, ser.decode(text))
            // idempotente: re-codificar da el mismo texto
            assertEquals(text, ser.encode(ser.decode(text)))
        }
    }

    @Test fun emptyTagsAndMovesRoundTrip() {
        val s = sampleSave("simple")
        s.state.moves.clear()
        val save = SaveGame("simple", 0L, s.state, emptyList())
        for (f in SaveFormat.entries) assertSame(save, SaveSerializer.of(f).decode(SaveSerializer.of(f).encode(save)))
    }

    @Test fun emptyFilesRejected() {
        for (f in SaveFormat.entries) {
            assertFailsWith<EmptySaveException> { SaveSerializer.of(f).decode("") }
            assertFailsWith<EmptySaveException> { SaveSerializer.of(f).decode("  \n\t ") }
        }
    }

    @Test fun truncatedFilesRejectedWithReadableError() {
        for (f in SaveFormat.entries) {
            val text = SaveSerializer.of(f).encode(sampleSave())
            val cut = text.substring(0, text.length / 2)
            val e = assertFailsWith<SaveException> { SaveSerializer.of(f).decode(cut) }
            assertTrue(e.message.isNotBlank())
        }
    }

    @Test fun garbageRejected() {
        for (f in SaveFormat.entries) {
            assertFailsWith<SaveException> { SaveSerializer.of(f).decode("esto no es un guardado \u0000 ###") }
        }
        assertFailsWith<CorruptSaveException> { JsonSerializer.decode("[1,2,3]") }
        assertFailsWith<CorruptSaveException> { XmlSerializer.decode("<otro/>") }
    }

    @Test fun missingFieldIsIncomplete() {
        val json = JsonSerializer.encode(sampleSave()).replace("\"tiles\"", "\"tilez\"")
        val e = assertFailsWith<IncompleteSaveException> { JsonSerializer.decode(json) }
        assertEquals("tiles", e.field)
        val txt = TxtSerializer.encode(sampleSave()).lines().filterNot { it.startsWith("game.score") }.joinToString("\n")
        assertFailsWith<IncompleteSaveException> { TxtSerializer.decode(txt) }
    }

    @Test fun invalidValuesRejected() {
        val txt = TxtSerializer.encode(sampleSave())
        assertFailsWith<CorruptSaveException> { TxtSerializer.decode(txt.replace("game.size=4", "game.size=99")) }
        assertFailsWith<CorruptSaveException> { TxtSerializer.decode(txt.replace(Regex("game.tiles=.*"), "game.tiles=1,2,3")) }
        assertFailsWith<CorruptSaveException> { TxtSerializer.decode(txt.replace("game.mode=reto", "game.mode=xx")) }
        assertFailsWith<CorruptSaveException> { TxtSerializer.decode(txt.replace(Regex("savedAt=.*"), "savedAt=ayer")) }
        assertFailsWith<CorruptSaveException> { TxtSerializer.decode(txt.replace("game.won=false", "game.won=quizas")) }
    }

    @Test fun futureSchemaVersionRejected() {
        val json = JsonSerializer.encode(sampleSave()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        val e = assertFailsWith<UnsupportedVersionException> { JsonSerializer.decode(json) }
        assertEquals(99, e.found)
    }

    @Test fun isoTimeRoundTrip() {
        for (ms in listOf(0L, 1_793_874_615_123L, 951_782_400_000L /* 2000-02-29 */, 4_102_444_800_000L)) {
            assertEquals(ms, IsoTime.parse(IsoTime.format(ms)))
        }
        assertEquals("1970-01-01T00:00:00.000Z", IsoTime.format(0))
        assertEquals("2000-02-29T00:00:00.000Z", IsoTime.format(951_782_400_000L))
    }

    @Test fun repositoryIsolatesCorruptFiles() {
        val store = MemoryFileStore()
        val repo = SaveRepository(store)
        val a = repo.save(sampleSave("A"), SaveFormat.JSON)
        repo.save(sampleSave("B"), SaveFormat.XML)
        store.write(SaveRepository.DIR, "roto.json", "{ no es json")
        store.write(SaveRepository.DIR, "vacio.txt", "")
        val list = repo.list()
        assertEquals(2, list.filterIsInstance<SaveEntry.Ok>().size)
        assertEquals(2, list.filterIsInstance<SaveEntry.Broken>().size)
        assertEquals("A", repo.load(a).name)
    }

    @Test fun importValidatesBeforeCopying() {
        val repo = SaveRepository(MemoryFileStore())
        assertFailsWith<SaveException> { repo.import("x.json", "basura") }
        assertEquals(0, repo.list().size)
        val text = XmlSerializer.encode(sampleSave("Ext"))
        repo.import("externo.xml", text)
        assertEquals("Ext", (repo.list().single() as SaveEntry.Ok).save.name)
    }

    @Test fun updateTagsKeepsFormat() {
        val repo = SaveRepository(MemoryFileStore())
        val f = repo.save(sampleSave("T"), SaveFormat.TXT)
        repo.update(f) { it.copyWith(tags = listOf("nueva")) }
        assertEquals(listOf("nueva"), repo.load(f).tags)
    }

    @Test fun xmlParserHandlesEntitiesAndComments() {
        val xml = XmlSerializer.encode(sampleSave("a<b>&c")).replace("<save", "<!-- hola -->\n<save")
        assertEquals("a<b>&c", XmlSerializer.decode(xml).name)
    }
}
