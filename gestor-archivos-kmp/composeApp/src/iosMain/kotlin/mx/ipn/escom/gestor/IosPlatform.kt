@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package mx.ipn.escom.gestor

import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ipn.escom.gestor.core.AppException
import mx.ipn.escom.gestor.core.FailureKind
import mx.ipn.escom.gestor.data.FileEntry
import mx.ipn.escom.gestor.data.FileStore
import mx.ipn.escom.gestor.data.PlatformServices
import mx.ipn.escom.gestor.data.RootInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

/**
 * Servicios de iOS:
 *  - Sandbox: Documents y Application Support (sin permisos).
 *  - UIDocumentPickerViewController para carpetas/archivos; las carpetas se conservan con
 *    bookmarks (NSURL bookmarkData) y se reabren con startAccessingSecurityScopedResource().
 *  - UIActivityViewController (compartir) y UIDocumentInteractionController (abrir con).
 */
class IosPlatform : PlatformServices {
    override val fileStore: FileStore = IosFileStore()
    private val fm = NSFileManager.defaultManager

    private fun dir(kind: NSSearchPathDirectory): String {
        val p = (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String) ?: NSTemporaryDirectory()
        fm.createDirectoryAtPath(p, true, null, null)
        return p
    }

    private val documents = dir(NSDocumentDirectory)
    private val support = dir(NSApplicationSupportDirectory)
    override val privateDir: String = "$support/internal".also { fm.createDirectoryAtPath(it, true, null, null) }
    override val cacheDir: String = dir(NSCachesDirectory)

    override val internalRoots = listOf(
        RootInfo(documents, "Documentos de la app"),
        RootInfo("$support/Archivos", "Datos internos de la app").also { fm.createDirectoryAtPath(it.id, true, null, null) },
    )

    // Los delegados de UIKit son referencias débiles: hay que retenerlos hasta que respondan.
    private var delegate: PickerDelegate? = null
    private var interaction: UIDocumentInteractionController? = null

    private class PickerDelegate(val onPick: (List<NSURL>) -> Unit, val onCancel: () -> Unit) :
        NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            onPick(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
        }
        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onCancel()
    }

    private fun topController(): UIViewController {
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: throw AppException(FailureKind.Unavailable, "no hay ventana activa")
        while (vc.presentedViewController != null) vc = vc.presentedViewController!!
        return vc
    }

    private suspend fun present(picker: UIDocumentPickerViewController): List<NSURL> {
        val d = CompletableDeferred<List<NSURL>>()
        val del = PickerDelegate({ d.complete(it) }, { d.complete(emptyList()) })
        delegate = del
        picker.delegate = del
        topController().presentViewController(picker, true, null)
        return d.await().also { delegate = null }
    }

    // ----- carpetas con bookmarks -----

    override suspend fun pickFolder(): RootInfo? {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
        val url = present(picker).firstOrNull() ?: return null
        if (!url.startAccessingSecurityScopedResource()) {
            throw AppException(FailureKind.PermissionRevoked, "iOS no concedió acceso a la carpeta")
        }
        val path = url.path ?: throw AppException(FailureKind.Unavailable, "ruta no disponible")
        val token = makeBookmark(url) ?: throw AppException(FailureKind.PermissionRevoked, "no se pudo guardar el acceso a la carpeta")
        return RootInfo(path, url.lastPathComponent ?: "Carpeta", token = token, external = true)
    }

    private fun makeBookmark(url: NSURL): String? = memScoped {
        val err = alloc<ObjCObjectVar<NSError?>>()
        val data = url.bookmarkDataWithOptions(0u, null, null, err.ptr)
        data?.base64EncodedStringWithOptions(0u)
    }

    override fun resolveRoot(token: String, nameHint: String): RootInfo = memScoped {
        val data = NSData.create(base64EncodedString = token, options = 0u)
        val stale = alloc<BooleanVar>()
        val err = alloc<ObjCObjectVar<NSError?>>()
        val url = data?.let { NSURL.URLByResolvingBookmarkData(it, 0u, null, stale.ptr, err.ptr) }
        if (url == null || !url.startAccessingSecurityScopedResource() || !fm.fileExistsAtPath(url.path ?: "")) {
            RootInfo(token, nameHint, token = token, external = true, available = false)
        } else {
            // El alcance de seguridad se mantiene abierto mientras viva la app (se libera al cerrarla).
            RootInfo(url.path!!, nameHint, token = token, external = true, available = true)
        }
    }

    // ----- importar / exportar -----

    override suspend fun pickFilesToImport(): List<FileEntry> {
        // asCopy = true: iOS copia los archivos a una carpeta temporal legible sin alcance de seguridad.
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        picker.allowsMultipleSelection = true
        val urls = present(picker)
        return withContext(Dispatchers.Default) { urls.mapNotNull { it.path }.map { fileStore.stat(it) } }
    }

    override suspend fun exportFile(entry: FileEntry): Boolean {
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(entry.id)), asCopy = true)
        return present(picker).isNotEmpty()
    }

    // ----- compartir / abrir con -----

    override fun share(entry: FileEntry) {
        val top = topController()
        val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(entry.id)), applicationActivities = null)
        // iPad: la hoja debe anclarse a una vista.
        sheet.popoverPresentationController?.apply {
            sourceView = top.view
            sourceRect = CGRectMake(top.view.bounds.useContents { size.width / 2 }, top.view.bounds.useContents { size.height / 2 }, 1.0, 1.0)
        }
        top.presentViewController(sheet, true, null)
    }

    override fun openWith(entry: FileEntry) {
        val top = topController()
        val ctl = UIDocumentInteractionController.interactionControllerWithURL(NSURL.fileURLWithPath(entry.id))
        interaction = ctl
        val ok = top.view.bounds.useContents {
            ctl.presentOpenInMenuFromRect(CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0), top.view, true)
        }
        if (!ok) throw AppException(FailureKind.NotAllowed, "no hay aplicaciones que puedan abrir este archivo")
    }
}
