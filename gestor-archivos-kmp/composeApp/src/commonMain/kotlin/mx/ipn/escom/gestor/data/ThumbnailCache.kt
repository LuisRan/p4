package mx.ipn.escom.gestor.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caché de miniaturas en dos niveles:
 *  1) memoria (LRU de [memoryItems] bitmaps);
 *  2) disco: PNG de 128 px en `<cacheDir>/thumbs`, con límite de [maxDiskBytes] y expulsión de los más antiguos.
 * La clave incluye id + tamaño + fecha de modificación, así que un archivo cambiado invalida su miniatura.
 */
class ThumbnailCache(
    private val store: FileStore,
    cacheDir: String,
    private val memoryItems: Int = 80,
    private val maxDiskBytes: Long = 20L * 1024 * 1024,
) {
    private val dir = joinPath(cacheDir, "thumbs")
    private val memory = LinkedHashMap<String, ImageBitmap>()

    fun key(e: FileEntry): String = hex(fnv1a("${e.id}|${e.size}|${e.modified}")) + ".png"

    /** Devuelve la miniatura (desde caché o generándola). Bloqueante: llamar fuera del hilo principal. */
    suspend fun get(entry: FileEntry): ImageBitmap? {
        val k = key(entry)
        lock.withLock { memory.remove(k)?.let { memory[k] = it; return it } } // reinserta = más reciente
        val bmp = loadFromDisk(k) ?: generate(entry, k) ?: return null
        lock.withLock {
            memory[k] = bmp
            while (memory.size > memoryItems) memory.remove(memory.keys.first())
        }
        return bmp
    }

    private fun loadFromDisk(k: String): ImageBitmap? = try {
        if (!store.exists(dir, k)) null else decodeImage(store.readBytes(joinPath(dir, k), 2_000_000))
    } catch (_: Exception) { null }

    private fun generate(entry: FileEntry, k: String): ImageBitmap? = try {
        if ((entry.size ?: 0L) > 25_000_000L) null else {
            val png = createThumbnailPng(store.readBytes(entry.id, 25_000_000), 128) ?: return null
            store.createDirectory(dirParent(), "thumbs")
            store.writeBytes(dir, k, png)
            trimDisk()
            decodeImage(png)
        }
    } catch (_: Exception) { null }

    private fun dirParent() = dir.substringBeforeLast('/')

    /** Borra las miniaturas más antiguas hasta quedar bajo el límite. */
    private fun trimDisk() {
        val files = store.list(dir).filter { !it.isDirectory }
        var total = files.sumOf { it.size ?: 0L }
        if (total <= maxDiskBytes) return
        for (f in files.sortedBy { it.modified ?: 0L }) {
            if (total <= maxDiskBytes * 8 / 10) break
            store.delete(f.id)
            total -= f.size ?: 0L
        }
    }

    /** Vacía memoria y disco (Ajustes). */
    suspend fun clear() {
        lock.withLock { memory.clear() }
        try { store.list(dir).forEach { store.delete(it.id) } } catch (_: Exception) {}
    }

    fun diskUsage(): Long = try { store.list(dir).sumOf { it.size ?: 0L } } catch (_: Exception) { 0L }

    private val lock = Mutex()

    companion object {
        fun fnv1a(s: String): Long {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (c in s) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
            return h
        }
        fun hex(v: Long): String = v.toULong().toString(16).padStart(16, '0')
    }
}
