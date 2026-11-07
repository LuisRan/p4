package mx.ipn.escom.gestor.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Elemento guardado en favoritos o recientes. */
@Serializable
data class SavedItem(
    val id: String,
    val name: String,
    val isDirectory: Boolean = false,
    val timeMillis: Long = 0L,
)

/** Carpeta externa autorizada por el usuario (token = URI SAF o bookmark de iOS). */
@Serializable
data class SavedRoot(val token: String, val name: String)

/** Todo lo persistente de la app, serializado como un único prefs.json en el directorio privado. */
@Serializable
data class PrefsData(
    val favorites: List<SavedItem> = emptyList(),
    val recents: List<SavedItem> = emptyList(),
    val theme: String = "guinda",
    /** "sistema" (por defecto), "claro" u "oscuro". */
    val appearance: String = "sistema",
    val roots: List<SavedRoot> = emptyList(),
    val seeded: Boolean = false,
) {
    /** Alterna un favorito (por id). */
    fun toggleFavorite(item: SavedItem): PrefsData =
        if (favorites.any { it.id == item.id }) copy(favorites = favorites.filterNot { it.id == item.id })
        else copy(favorites = listOf(item) + favorites)

    /** Añade a recientes (sin duplicados, más nuevo primero, máx. [MAX_RECENTS]). */
    fun addRecent(item: SavedItem): PrefsData =
        copy(recents = (listOf(item) + recents.filterNot { it.id == item.id }).take(MAX_RECENTS))

    fun isFavorite(id: String) = favorites.any { it.id == id }

    companion object { const val MAX_RECENTS = 20 }
}

/** Codec JSON de las preferencias. Un archivo corrupto o vacío devuelve valores por defecto. */
object PrefsCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(data: PrefsData): String = json.encodeToString(PrefsData.serializer(), data)

    fun decode(text: String?): PrefsData =
        if (text.isNullOrBlank()) PrefsData()
        else try { json.decodeFromString(PrefsData.serializer(), text) } catch (e: Exception) { PrefsData() }
}

/** Persistencia de [PrefsData] en `<privateDir>/prefs.json` usando el FileStore de la plataforma. */
class PrefsStore(private val store: FileStore, private val dir: String) {
    private val fileName = "prefs.json"

    fun load(): PrefsData = try {
        PrefsCodec.decode(store.readBytes(joinPath(dir, fileName), 1_000_000).decodeToString())
    } catch (e: Exception) {
        PrefsData() // primera ejecución o archivo ilegible
    }

    fun save(data: PrefsData) {
        try { store.writeBytes(dir, fileName, PrefsCodec.encode(data).encodeToByteArray()) } catch (_: Exception) { /* no bloquear la UI */ }
    }
}

fun joinPath(dir: String, name: String) = dir.trimEnd('/') + "/" + name
