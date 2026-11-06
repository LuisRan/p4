package mx.ipn.escom.gestor.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.ipn.escom.gestor.core.AppException
import mx.ipn.escom.gestor.core.FailureKind
import mx.ipn.escom.gestor.core.FileTypes
import mx.ipn.escom.gestor.core.ViewerKind
import mx.ipn.escom.gestor.core.asAppException
import mx.ipn.escom.gestor.data.*

/** Migas de pan: cada tramo es una carpeta navegada. */
data class Crumb(val name: String, val id: String)

enum class Section(val label: String, val glyph: String) {
    Explore("Explorar", "📁"), Recents("Recientes", "🕘"), Favorites("Favoritos", "★"), Settings("Ajustes", "⚙")
}

/** Elemento copiado/cortado pendiente de pegar. */
data class Clip(val entry: FileEntry, val move: Boolean, val sourceDirId: String?)

data class Progress(val label: String, val done: Long = 0, val total: Long = 0)

sealed interface Dialog {
    data class Rename(val entry: FileEntry) : Dialog
    data class Delete(val entry: FileEntry) : Dialog
    data object NewFolder : Dialog
    data class Details(val entry: FileEntry) : Dialog
    data class Unsupported(val entry: FileEntry) : Dialog
    data object AccessRationale : Dialog
    data class RemoveRoot(val root: RootInfo) : Dialog
}

/**
 * Estado único de la app (equivalente a un ViewModel simple, sin dependencias extra).
 * La UI observa las propiedades mutableStateOf; las operaciones de archivo corren en
 * Dispatchers.Default y los errores se convierten en mensajes (nunca propagan un crash).
 */
class AppState(
    val platform: PlatformServices,
    private val scope: CoroutineScope,
    val snackbar: SnackbarHostState,
) {
    private val store = platform.fileStore
    val ops = FileOps(store)
    val thumbs = ThumbnailCache(store, platform.cacheDir)
    private val prefsStore = PrefsStore(store, platform.privateDir)

    var prefs by mutableStateOf(PrefsData()); private set
    var ready by mutableStateOf(false); private set
    var section by mutableStateOf(Section.Explore)
    var roots by mutableStateOf<List<RootInfo>>(emptyList()); private set
    var trail by mutableStateOf<List<Crumb>>(emptyList()); private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var listError by mutableStateOf<AppException?>(null); private set
    var clip by mutableStateOf<Clip?>(null)
    var viewing by mutableStateOf<FileEntry?>(null)
    var dialog by mutableStateOf<Dialog?>(null)
    var progress by mutableStateOf<Progress?>(null); private set

    val theme: AppTheme get() = AppTheme.fromKey(prefs.theme)
    val currentDir: Crumb? get() = trail.lastOrNull()

    // ---------- arranque ----------

    /** Carga preferencias, siembra datos de ejemplo la primera vez y resuelve las raíces. */
    fun start() = scope.launch {
        try {
            withContext(Dispatchers.Default) {
                var p = prefsStore.load()
                if (!p.seeded) {
                    try { SeedData.seed(store, platform.internalRoots.first().id) } catch (_: Exception) { /* sin ejemplos, la app sigue */ }
                    p = p.copy(seeded = true)
                    prefsStore.save(p)
                }
                prefs = p
            }
            refreshRoots()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            report(e)
        }
        ready = true
    }

    private fun refreshRoots() {
        roots = platform.internalRoots + prefs.roots.map { platform.resolveRoot(it.token, it.name) }
    }

    private fun updatePrefs(transform: (PrefsData) -> PrefsData) {
        prefs = transform(prefs)
        val snapshot = prefs
        scope.launch(Dispatchers.Default) { prefsStore.save(snapshot) }
    }

    // ---------- mensajes y errores ----------

    fun report(e: Throwable) = message(e.asAppException().message ?: "Error")

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    /** Ejecuta una operación en segundo plano capturando cualquier error. */
    private fun <T> background(block: suspend () -> T, onOk: (T) -> Unit = {}, onFail: (Throwable) -> Unit = ::report) =
        scope.launch {
            try { onOk(withContext(Dispatchers.Default) { block() }) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { onFail(e) }
        }

    // ---------- navegación ----------

    fun openRoot(root: RootInfo) {
        if (!root.available) {
            message(FailureKind.PermissionRevoked.titulo + ": vuelva a elegir la carpeta o quítela de la lista")
            return
        }
        trail = listOf(Crumb(root.name, root.id))
        reload()
    }

    fun openDirectory(entry: FileEntry) {
        trail = trail + Crumb(entry.name, entry.id)
        reload()
    }

    /** Salta a un tramo de las migas de pan. */
    fun goToCrumb(index: Int) {
        trail = trail.take(index + 1)
        reload()
    }

    /** Sube un nivel; en la raíz vuelve a la lista de ubicaciones. */
    fun up() {
        trail = trail.dropLast(1)
        if (trail.isEmpty()) { entries = emptyList(); listError = null } else reload()
    }

    /** Abre una carpeta guardada (favoritos/recientes) como nueva ruta. */
    fun openDirectoryDirect(item: SavedItem) {
        section = Section.Explore
        trail = listOf(Crumb(item.name, item.id))
        reload()
    }

    fun reload() {
        val dir = currentDir ?: return
        loading = true
        listError = null
        scope.launch {
            try {
                val list = withContext(Dispatchers.Default) {
                    store.list(dir.id).sortedWith(
                        compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
                    )
                }
                if (currentDir?.id == dir.id) entries = list
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                entries = emptyList()
                listError = e.asAppException()
            }
            loading = false
        }
    }

    // ---------- abrir archivos ----------

    fun open(entry: FileEntry) {
        if (entry.isDirectory) { openDirectory(entry); return }
        when (FileTypes.kindOf(entry.name)) {
            ViewerKind.Unsupported -> dialog = Dialog.Unsupported(entry)
            else -> {
                viewing = entry
                updatePrefs { it.addRecent(entry.toSaved()) }
            }
        }
    }

    /** Abre desde favoritos/recientes: se comprueba que siga existiendo y siga autorizado. */
    fun openSaved(item: SavedItem) {
        if (item.isDirectory) { openDirectoryDirect(item); return }
        background({ store.stat(item.id) }, onOk = { open(it) }, onFail = {
            report(it)
        })
    }

    // ---------- favoritos / recientes ----------

    fun isFavorite(id: String) = prefs.isFavorite(id)
    fun toggleFavorite(entry: FileEntry) = updatePrefs { it.toggleFavorite(entry.toSaved()) }
    fun toggleFavorite(item: SavedItem) = updatePrefs { it.toggleFavorite(item) }
    fun removeRecent(id: String) = updatePrefs { it.copy(recents = it.recents.filterNot { r -> r.id == id }) }
    fun clearRecents() = updatePrefs { it.copy(recents = emptyList()) }
    fun setTheme(t: AppTheme) = updatePrefs { it.copy(theme = t.key) }

    // ---------- carpetas externas ----------

    /** Lanza el selector del sistema (el diálogo explicativo se mostró antes). */
    fun pickFolder() {
        dialog = null
        scope.launch {
            try {
                val root = platform.pickFolder() ?: run { message("Selección cancelada; no se concedió acceso"); return@launch }
                val token = root.token ?: return@launch
                updatePrefs { p -> p.copy(roots = p.roots.filterNot { it.token == token } + SavedRoot(token, root.name)) }
                refreshRoots()
                message("Carpeta añadida: ${root.name}")
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { report(e) }
        }
    }

    fun removeRoot(root: RootInfo) {
        dialog = null
        updatePrefs { p -> p.copy(roots = p.roots.filterNot { it.token == root.token }) }
        if (trail.firstOrNull()?.id == root.id) { trail = emptyList(); entries = emptyList() }
        refreshRoots()
    }

    // ---------- operaciones de archivo ----------

    fun copyToClip(entry: FileEntry, move: Boolean) {
        clip = Clip(entry, move, currentDir?.id)
        message(if (move) "Listo para mover: elija la carpeta destino y pulse Pegar" else "Listo para copiar: elija la carpeta destino y pulse Pegar")
    }

    fun paste() {
        val c = clip ?: return
        val dest = currentDir ?: run { message("Abra una carpeta de destino primero"); return }
        progress = Progress(if (c.move) "Moviendo ${c.entry.name}" else "Copiando ${c.entry.name}")
        val update: (Long, Long) -> Unit = { d, t -> progress = Progress(progress?.label ?: "", d, t) }
        background({
            if (c.move) ops.move(c.entry, dest.id, c.sourceDirId, update) else ops.copy(c.entry, dest.id, update)
        }, onOk = {
            progress = null
            if (c.move) clip = null
            message(if (c.move) "Movido" else "Copiado")
            reload()
        }, onFail = { progress = null; report(it) })
    }

    fun rename(entry: FileEntry, newName: String) {
        dialog = null
        background({ ops.rename(entry, newName, currentDir?.id) }, onOk = { reload(); message("Renombrado") })
    }

    fun delete(entry: FileEntry) {
        dialog = null
        background({ ops.delete(entry) }, onOk = {
            // También se limpia de favoritos/recientes para no dejar referencias rotas.
            updatePrefs { p -> p.copy(favorites = p.favorites.filterNot { it.id == entry.id }, recents = p.recents.filterNot { it.id == entry.id }) }
            if (clip?.entry?.id == entry.id) clip = null
            reload(); message("Eliminado")
        })
    }

    fun createFolder(name: String) {
        dialog = null
        val dir = currentDir ?: return
        background({ ops.createFolder(dir.id, name) }, onOk = { reload() })
    }

    /** Importa archivos elegidos con el selector del sistema a la carpeta actual. */
    fun importFiles() {
        val dir = currentDir ?: run { message("Abra una carpeta de destino primero"); return }
        scope.launch {
            try {
                val picked = platform.pickFilesToImport()
                if (picked.isEmpty()) return@launch
                progress = Progress("Importando ${picked.size} archivo(s)")
                withContext(Dispatchers.Default) {
                    val total = picked.sumOf { it.size ?: 0L }
                    var base = 0L
                    for (f in picked) {
                        val start = base
                        ops.copy(f, dir.id) { d, _ -> progress = Progress("Importando ${f.name}", start + d, total) }
                        base += f.size ?: 0L
                    }
                }
                progress = null
                message("Importado"); reload()
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { progress = null; report(e) }
        }
    }

    fun export(entry: FileEntry) {
        dialog = null
        if (entry.isDirectory) { message("Sólo se pueden exportar archivos, no carpetas"); return }
        scope.launch {
            try { if (platform.exportFile(entry)) message("Exportado") }
            catch (e: CancellationException) { throw e } catch (e: Throwable) { report(e) }
        }
    }

    fun share(entry: FileEntry) {
        dialog = null
        try { platform.share(entry) } catch (e: Throwable) { report(e) }
    }

    fun openWith(entry: FileEntry) {
        dialog = null
        try { platform.openWith(entry) } catch (e: Throwable) { report(e) }
    }

    fun clearThumbnails() = scope.launch(Dispatchers.Default) { thumbs.clear(); message("Caché de miniaturas vaciada") }
}

fun FileEntry.toSaved() = SavedItem(id, name, isDirectory, nowMillis())
