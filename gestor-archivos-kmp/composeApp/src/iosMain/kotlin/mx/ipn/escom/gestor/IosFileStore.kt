@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package mx.ipn.escom.gestor

import kotlinx.cinterop.*
import mx.ipn.escom.gestor.core.AppException
import mx.ipn.escom.gestor.core.FailureKind
import mx.ipn.escom.gestor.data.FileEntry
import mx.ipn.escom.gestor.data.FileStore
import platform.Foundation.*
import platform.posix.memcpy

/**
 * FileStore de iOS sobre NSFileManager. Los ids son rutas POSIX: del sandbox de la app o de una
 * carpeta elegida con UIDocumentPicker (mientras su security-scoped URL esté activa, ver IosPlatform).
 */
class IosFileStore : FileStore {
    private val fm = NSFileManager.defaultManager

    /** Ejecuta una llamada de Foundation con NSError** y la convierte en AppException. */
    private inline fun <T> nsTry(block: (CPointer<ObjCObjectVar<NSError?>>) -> T?): T = memScoped {
        val err = alloc<ObjCObjectVar<NSError?>>()
        val result = block(err.ptr)
        if (result == null || result == false) {
            throw mapError(err.value)
        }
        result
    }

    private fun mapError(e: NSError?): AppException {
        if (e == null) return AppException(FailureKind.Unknown)
        val detail = e.localizedDescription
        return when (e.code) {
            4L, 260L -> AppException(FailureKind.NotFound, detail)               // NSFileNoSuchFile / ReadNoSuchFile
            257L, 513L -> AppException(FailureKind.PermissionRevoked, detail)    // sin permiso lectura/escritura
            640L -> AppException(FailureKind.NoSpace, detail)                    // NSFileWriteOutOfSpace
            516L -> AppException(FailureKind.InvalidName, "ya existe")           // NSFileWriteFileExists
            else -> AppException(FailureKind.ReadFailed, detail)
        }
    }

    private fun date(d: Any?): Long? = (d as? NSDate)?.let { (it.timeIntervalSince1970 * 1000).toLong() }

    override fun list(dirId: String): List<FileEntry> {
        if (!fm.fileExistsAtPath(dirId)) throw AppException(FailureKind.Unavailable, dirId.substringAfterLast('/'))
        val names = nsTry<List<*>> { fm.contentsOfDirectoryAtPath(dirId, it) }
        return names.mapNotNull { n -> (n as? String)?.takeIf { !it.startsWith(".") }?.let { stat("$dirId/$it") } }
    }

    override fun stat(id: String): FileEntry {
        if (!fm.fileExistsAtPath(id)) throw AppException(FailureKind.NotFound, id.substringAfterLast('/'))
        val attrs = nsTry<Map<Any?, *>> { fm.attributesOfItemAtPath(id, it) }
        val isDir = attrs[NSFileType] == NSFileTypeDirectory
        return FileEntry(
            id = id, name = id.trimEnd('/').substringAfterLast('/'), isDirectory = isDir,
            size = if (isDir) null else (attrs[NSFileSize] as? NSNumber)?.longLongValue,
            modified = date(attrs[NSFileModificationDate]),
            created = date(attrs[NSFileCreationDate]),
        )
    }

    override fun exists(dirId: String, name: String) = fm.fileExistsAtPath("$dirId/$name")

    override fun createDirectory(parentId: String, name: String): String {
        val path = "$parentId/$name"
        memScoped {
            val isDir = alloc<BooleanVar>()
            if (fm.fileExistsAtPath(path, isDir.ptr) && isDir.value) return path
        }
        nsTry<Boolean> { fm.createDirectoryAtPath(path, true, null, it) }
        return path
    }

    override fun copyFile(srcId: String, destDirId: String, destName: String, onBytes: (Long) -> Unit): String {
        val dest = "$destDirId/$destName"
        nsTry<Boolean> { fm.copyItemAtPath(srcId, dest, it) }
        // NSFileManager copia de una vez; se informa el tamaño completo al terminar.
        onBytes((fm.attributesOfItemAtPath(dest, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L)
        return dest
    }

    override fun delete(id: String) {
        nsTry<Boolean> { fm.removeItemAtPath(id, it) }
    }

    override fun rename(id: String, newName: String): String {
        val dest = id.substringBeforeLast('/') + "/" + newName
        if (fm.fileExistsAtPath(dest)) throw AppException(FailureKind.InvalidName, "ya existe $newName")
        nsTry<Boolean> { fm.moveItemAtPath(id, dest, it) }
        return dest
    }

    override fun readBytes(id: String, maxBytes: Int): ByteArray {
        val handle = NSFileHandle.fileHandleForReadingAtPath(id)
            ?: throw if (fm.fileExistsAtPath(id)) AppException(FailureKind.PermissionRevoked, id.substringAfterLast('/'))
            else AppException(FailureKind.NotFound, id.substringAfterLast('/'))
        try {
            val data = handle.readDataOfLength(maxBytes.toULong())
            val n = data.length.toInt()
            if (n == 0) return ByteArray(0)
            return ByteArray(n).also { out -> out.usePinned { memcpy(it.addressOf(0), data.bytes, n.toULong()) } }
        } finally {
            handle.closeFile()
        }
    }

    override fun writeBytes(dirId: String, name: String, bytes: ByteArray): String {
        val path = "$dirId/$name"
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        nsTry<Boolean> { data.writeToFile(path, NSDataWritingAtomic, it) }
        return path
    }

    override fun canWrite(dirId: String) = fm.isWritableFileAtPath(dirId)

    override fun freeSpace(dirId: String): Long? =
        (fm.attributesOfFileSystemForPath(dirId, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue

    override fun isInside(candidateId: String, ancestorId: String) =
        candidateId == ancestorId || candidateId.startsWith(ancestorId.trimEnd('/') + "/")
}
