package mx.ipn.escom.circuito.saves

import mx.ipn.escom.circuito.platform.FileStore

/** Entrada de la lista de partidas: válida (con metadatos) o dañada (con error legible). */
sealed class SaveEntry {
    abstract val fileName: String
    abstract val format: SaveFormat

    class Ok(override val fileName: String, override val format: SaveFormat, val save: SaveGame) : SaveEntry()
    class Broken(override val fileName: String, override val format: SaveFormat, val error: String) : SaveEntry()
}

/**
 * Gestiona la carpeta privada `saves/`. Cada archivo se valida de forma
 * independiente: uno corrupto NO afecta al resto de partidas.
 */
class SaveRepository(private val store: FileStore) {
    companion object {
        const val DIR = "saves"
        const val AUTO_DIR = "auto"
        const val AUTO_FILE = "autosave.json"
    }

    private fun formatOf(name: String) =
        SaveFormat.fromExtension(name.substringAfterLast('.', ""))

    private fun sanitize(name: String): String {
        val s = name.trim().map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        return s.take(40).ifEmpty { "partida" }
    }

    /** Lista todas las partidas, las más recientes primero (las dañadas al final). */
    fun list(): List<SaveEntry> {
        val entries = store.list(DIR).mapNotNull { file ->
            val fmt = formatOf(file) ?: return@mapNotNull null // ignora archivos ajenos / .tmp
            try {
                SaveEntry.Ok(file, fmt, load(file))
            } catch (e: SaveException) {
                SaveEntry.Broken(file, fmt, e.message)
            }
        }
        val ok = entries.filterIsInstance<SaveEntry.Ok>().sortedByDescending { it.save.savedAtMillis }
        val bad = entries.filterIsInstance<SaveEntry.Broken>().sortedBy { it.fileName }
        return ok + bad
    }

    fun readRaw(fileName: String): String =
        store.read(DIR, fileName) ?: throw SaveIoException("No se pudo leer \"$fileName\".")

    fun load(fileName: String): SaveGame {
        val fmt = formatOf(fileName) ?: throw CorruptSaveException("Extensión no reconocida.")
        return SaveSerializer.of(fmt).decode(readRaw(fileName))
    }

    /** Guarda en el formato elegido y devuelve el nombre de archivo creado. */
    fun save(game: SaveGame, format: SaveFormat): String {
        val base = "${sanitize(game.name)}_${game.savedAtMillis}"
        val file = "$base.${format.extension}"
        store.write(DIR, file, SaveSerializer.of(format).encode(game))
        return file
    }

    /** Reescribe un archivo existente con nuevas etiquetas/nombre, conservando su formato. */
    fun update(fileName: String, transform: (SaveGame) -> SaveGame) {
        val fmt = formatOf(fileName) ?: throw CorruptSaveException("Extensión no reconocida.")
        val updated = transform(load(fileName))
        store.write(DIR, fileName, SaveSerializer.of(fmt).encode(updated))
    }

    fun delete(fileName: String) {
        if (!store.delete(DIR, fileName)) throw SaveIoException("No se pudo eliminar \"$fileName\".")
    }

    /**
     * Importa texto externo: se VALIDA primero y solo si es correcto se copia a
     * `saves/` (nunca se acepta un archivo roto). Devuelve el nombre creado.
     */
    fun import(originalName: String, content: String): String {
        val fmt = formatOf(originalName) ?: SaveFormat.sniff(content)
        val game = SaveSerializer.of(fmt).decode(content) // lanza SaveException
        var file = "${sanitize(game.name)}_${game.savedAtMillis}.${fmt.extension}"
        if (store.read(DIR, file) != null) file = "imp_" + file
        // Se conserva el contenido original byte a byte.
        store.write(DIR, file, content)
        return file
    }

    // ---- Autoguardado ("Continuar") ----

    fun writeAuto(game: SaveGame) =
        store.write(AUTO_DIR, AUTO_FILE, JsonSerializer.encode(game))

    /** Devuelve la partida autoguardada o null si no existe o está dañada. */
    fun readAuto(): SaveGame? {
        val txt = store.read(AUTO_DIR, AUTO_FILE) ?: return null
        return try { JsonSerializer.decode(txt) } catch (e: SaveException) { null }
    }

    fun clearAuto() { store.delete(AUTO_DIR, AUTO_FILE) }
}
