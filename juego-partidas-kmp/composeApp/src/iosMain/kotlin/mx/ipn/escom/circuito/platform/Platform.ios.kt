@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package mx.ipn.escom.circuito.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import mx.ipn.escom.circuito.saves.SaveIoException
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypeText
import platform.UniformTypeIdentifiers.UTTypeXML
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

/** Convierte texto UTF-8 a NSData. */
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/** Archivos privados en Documents/<dir>/<nombre> del contenedor de la app. */
private class IosFileStore : FileStore {
    private val fm = NSFileManager.defaultManager
    private val root: String = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String)

    private fun dir(d: String): String {
        val p = "$root/$d"
        if (!fm.fileExistsAtPath(p)) fm.createDirectoryAtPath(p, true, null, null)
        return p
    }

    override fun list(dir: String): List<String> =
        (fm.contentsOfDirectoryAtPath(dir(dir), null) ?: emptyList<Any?>()).map { it as String }

    override fun read(dir: String, name: String): String? {
        val p = "${dir(dir)}/$name"
        if (!fm.fileExistsAtPath(p)) return null
        return NSString.stringWithContentsOfFile(p, NSUTF8StringEncoding, null)
    }

    override fun write(dir: String, name: String, content: String) {
        // atomically = true: Foundation escribe a un temporal y renombra.
        val ok = NSString.create(string = content).writeToFile("${dir(dir)}/$name", true, NSUTF8StringEncoding, null)
        if (!ok) throw SaveIoException("No se pudo escribir \"$name\".")
    }

    override fun delete(dir: String, name: String): Boolean =
        fm.removeItemAtPath("${dir(dir)}/$name", null)
}

actual fun createFileStore(): FileStore = IosFileStore()

/** View controller visible más al frente (para presentar pickers y hojas de compartir). */
private fun topViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
    val window: UIWindow? = scene?.windows?.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        ?: UIApplication.sharedApplication.keyWindow
    var vc = window?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

private fun writeTemp(name: String, content: String): NSURL? {
    val path = NSTemporaryDirectory() + name
    val ok = NSString.create(string = content).writeToFile(path, true, NSUTF8StringEncoding, null)
    return if (ok) NSURL.fileURLWithPath(path) else null
}

/** Delegado del UIDocumentPicker (se mantiene referenciado hasta que termina). */
private class PickerDelegate(
    private val onPick: (NSURL?) -> Unit,
    private val release: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPick(didPickDocumentsAtURLs.firstOrNull() as? NSURL); release()
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPick(null); release()
    }
}

private class IosPlatform : PlatformServices {
    private val players = HashMap<String, AVAudioPlayer>()
    private val delegates = HashSet<PickerDelegate>()
    private var sessionReady = false

    override fun playWav(id: String, wav: ByteArray) {
        try {
            if (!sessionReady) {
                AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
                sessionReady = true
            }
            val p = players.getOrPut(id) {
                AVAudioPlayer(data = wav.toNSData(), error = null).also { it.prepareToPlay() }
            }
            p.currentTime = 0.0
            p.play()
        } catch (e: Throwable) { /* el sonido nunca debe romper el juego */ }
    }

    override fun haptic(kind: Haptic) {
        when (kind) {
            Haptic.SELECT -> UISelectionFeedbackGenerator().apply { prepare() }.selectionChanged()
            Haptic.STRONG -> UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
    }

    private fun present(picker: UIDocumentPickerViewController, onPick: (NSURL?) -> Unit) {
        // La referencia fuerte evita que el delegado se libere (UIKit solo guarda una débil).
        lateinit var wrapped: PickerDelegate
        wrapped = PickerDelegate(onPick) { delegates.remove(wrapped) }
        delegates.add(wrapped)
        picker.delegate = wrapped
        topViewController()?.presentViewController(picker, true, null) ?: onPick(null)
    }

    /** Exportar: copia temporal + UIDocumentPicker en modo exportación. */
    override fun exportText(fileName: String, mime: String, content: String, onResult: (Boolean) -> Unit) {
        val url = writeTemp(fileName, content) ?: return onResult(false)
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        present(picker) { onResult(it != null) }
    }

    /** Compartir: UIActivityViewController con el archivo temporal. */
    override fun shareText(fileName: String, mime: String, content: String) {
        val url = writeTemp(fileName, content) ?: return
        val vc = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        // En iPad el estilo popover exige un ancla; se presenta como hoja modal para evitarlo.
        vc.modalPresentationStyle = UIModalPresentationPageSheet
        val top = topViewController()
        top?.presentViewController(vc, true, null)
    }

    override fun importText(onResult: (ImportedFile?) -> Unit) {
        val types: List<UTType> = listOfNotNull(UTTypeJSON, UTTypeXML, UTTypeText, UTTypeData)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        present(picker) { url ->
            if (url == null) onResult(null)
            else {
                val text = NSString.stringWithContentsOfFile(url.path ?: "", NSUTF8StringEncoding, null) ?: ""
                onResult(ImportedFile(url.lastPathComponent ?: "importado", text))
            }
        }
    }
}

@Composable
actual fun rememberPlatformServices(): PlatformServices = remember { IosPlatform() }

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}
