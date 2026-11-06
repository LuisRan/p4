package mx.ipn.escom.gestor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import mx.ipn.escom.gestor.core.AppException
import mx.ipn.escom.gestor.core.FailureKind
import mx.ipn.escom.gestor.core.FileTypes
import mx.ipn.escom.gestor.data.FileEntry
import mx.ipn.escom.gestor.data.FileStore
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * FileStore de Android. Un id es:
 *  - una ruta absoluta (espacio propio: filesDir/cacheDir) -> java.io.File, o
 *  - un URI "content://" de Storage Access Framework (carpetas elegidas) -> ContentResolver +
 *    DocumentsContract (lista de hijos con una sola consulta, crear/renombrar/borrar documentos).
 * No se usan permisos de almacenamiento globales: sólo URIs concedidos por el selector.
 */
class AndroidFileStore(private val context: Context) : FileStore {
    private val resolver = context.contentResolver

    private fun isContent(id: String) = id.startsWith("content://")

    // ------------------------------------------------------------------ SAF helpers

    /** URI de documento (con segmento /document/) a partir de un URI de árbol o de documento-en-árbol. */
    private fun docUri(id: String): Uri {
        val uri = Uri.parse(id)
        if (uri.pathSegments.contains("document")) return uri
        return if (uri.pathSegments.contains("tree"))
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        else uri
    }

    private fun isTree(uri: Uri) = uri.pathSegments.contains("tree")

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
    )

    private fun cursorEntry(c: android.database.Cursor, treeBase: Uri?, fallbackUri: Uri?): FileEntry {
        val docId = c.getString(0)
        val mime = c.getString(2)
        val isDir = mime == Document.MIME_TYPE_DIR
        val id = if (treeBase != null) DocumentsContract.buildDocumentUriUsingTree(treeBase, docId).toString() else fallbackUri.toString()
        return FileEntry(
            id = id, name = c.getString(1) ?: docId.substringAfterLast('/'), isDirectory = isDir,
            size = if (c.isNull(3)) null else c.getLong(3).takeIf { it >= 0 },
            modified = if (c.isNull(4)) null else c.getLong(4).takeIf { it > 0 },
            providerMime = if (isDir) null else mime,
        )
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: AppException) {
        throw e
    } catch (e: SecurityException) {
        throw AppException(FailureKind.PermissionRevoked, "el permiso sobre la carpeta ya no está vigente")
    } catch (e: FileNotFoundException) {
        throw AppException(FailureKind.NotFound, e.message)
    } catch (e: IOException) {
        if (e.message?.contains("ENOSPC", true) == true || e.message?.contains("No space", true) == true)
            throw AppException(FailureKind.NoSpace, e.message)
        throw AppException(FailureKind.ReadFailed, e.message)
    } catch (e: IllegalArgumentException) {
        throw AppException(FailureKind.Unavailable, e.message)
    } catch (e: UnsupportedOperationException) {
        throw AppException(FailureKind.NotAllowed, "el proveedor no admite esta operación")
    }

    // ------------------------------------------------------------------ FileStore

    override fun list(dirId: String): List<FileEntry> = guard {
        if (!isContent(dirId)) {
            val dir = File(dirId)
            if (!dir.exists()) throw AppException(FailureKind.Unavailable, dir.name)
            val files = dir.listFiles() ?: throw AppException(FailureKind.PermissionRevoked, "no se puede leer ${dir.name}")
            files.map { fileEntry(it) }
        } else {
            val base = Uri.parse(dirId)
            val docId = if (base.pathSegments.contains("document")) DocumentsContract.getDocumentId(base) else DocumentsContract.getTreeDocumentId(base)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(base, docId)
            val out = ArrayList<FileEntry>()
            val cursor = resolver.query(children, projection, null, null, null)
                ?: throw AppException(FailureKind.Unavailable, "el proveedor no respondió")
            cursor.use { while (it.moveToNext()) out.add(cursorEntry(it, base, null)) }
            out
        }
    }

    private fun fileEntry(f: File) = FileEntry(
        id = f.absolutePath, name = f.name, isDirectory = f.isDirectory,
        size = if (f.isFile) f.length() else null, modified = f.lastModified().takeIf { it > 0 },
    )

    override fun stat(id: String): FileEntry = guard {
        if (!isContent(id)) {
            val f = File(id)
            if (!f.exists()) throw AppException(FailureKind.NotFound, f.name)
            fileEntry(f)
        } else {
            val uri = docUri(id)
            val cursor = resolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) return@guard cursorEntry(it, if (isTree(Uri.parse(id))) Uri.parse(id) else null, Uri.parse(id))
            }
            throw AppException(FailureKind.NotFound, "el documento ya no existe o no hay permiso")
        }
    }

    override fun exists(dirId: String, name: String): Boolean = guard {
        if (!isContent(dirId)) File(dirId, name).exists()
        else list(dirId).any { it.name == name }
    }

    override fun createDirectory(parentId: String, name: String): String = guard {
        if (!isContent(parentId)) {
            val d = File(parentId, name)
            if (!d.isDirectory && !d.mkdirs()) throw AppException(FailureKind.WriteFailed, "no se pudo crear $name")
            d.absolutePath
        } else {
            list(parentId).firstOrNull { it.name == name && it.isDirectory }?.id
                ?: DocumentsContract.createDocument(resolver, docUri(parentId), Document.MIME_TYPE_DIR, name)?.let { treeId(parentId, it) }
                ?: throw AppException(FailureKind.NotAllowed, "no se pudo crear la carpeta")
        }
    }

    /** Convierte el URI devuelto por createDocument (ya incluye /tree/) en id; si no, lo usa tal cual. */
    private fun treeId(parentId: String, created: Uri): String = created.toString()

    private fun openIn(id: String): InputStream = guard {
        if (isContent(id)) resolver.openInputStream(docUri(id)) ?: throw AppException(FailureKind.ReadFailed, "no se pudo abrir")
        else File(id).inputStream()
    }

    /** Crea el archivo destino y devuelve (id, flujo de salida). */
    private fun createOut(dirId: String, name: String, overwrite: Boolean): Pair<String, OutputStream> = guard {
        if (!isContent(dirId)) {
            val f = File(dirId, name)
            f.absolutePath to f.outputStream()
        } else {
            val existing = if (overwrite) list(dirId).firstOrNull { it.name == name && !it.isDirectory } else null
            val uri: Uri
            val id: String
            if (existing != null) { id = existing.id; uri = docUri(id) }
            else {
                val mime = FileTypes.mimeOf(name) ?: "application/octet-stream"
                uri = DocumentsContract.createDocument(resolver, docUri(dirId), mime, name)
                    ?: throw AppException(FailureKind.NotAllowed, "el destino no admite crear archivos")
                id = uri.toString()
            }
            id to (resolver.openOutputStream(uri, "wt") ?: throw AppException(FailureKind.WriteFailed, "no se pudo abrir el destino"))
        }
    }

    override fun copyFile(srcId: String, destDirId: String, destName: String, onBytes: (Long) -> Unit): String = guard {
        val (newId, out) = createOut(destDirId, destName, overwrite = false)
        try {
            openIn(srcId).use { input ->
                out.use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
        } catch (e: Throwable) {
            // No dejar un archivo a medias si falló (p. ej. sin espacio).
            try { delete(newId) } catch (_: Exception) {}
            throw e
        }
        newId
    }

    override fun delete(id: String) = guard {
        if (!isContent(id)) {
            val f = File(id)
            if (!f.exists()) throw AppException(FailureKind.NotFound, f.name)
            if (!f.deleteRecursively()) throw AppException(FailureKind.NotAllowed, "no se pudo eliminar ${f.name}")
        } else {
            if (!DocumentsContract.deleteDocument(resolver, docUri(id))) throw AppException(FailureKind.NotAllowed, "el proveedor no permitió eliminar")
        }
    }

    override fun rename(id: String, newName: String): String = guard {
        if (!isContent(id)) {
            val f = File(id)
            val dest = File(f.parentFile, newName)
            if (dest.exists()) throw AppException(FailureKind.InvalidName, "ya existe $newName")
            if (!f.renameTo(dest)) throw AppException(FailureKind.NotAllowed, "no se pudo renombrar")
            dest.absolutePath
        } else {
            DocumentsContract.renameDocument(resolver, docUri(id), newName)?.toString()
                ?: throw AppException(FailureKind.NotAllowed, "el proveedor no permite renombrar")
        }
    }

    override fun readBytes(id: String, maxBytes: Int): ByteArray = guard {
        openIn(id).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (total < maxBytes) {
                val n = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (n < 0) break
                out.write(buf, 0, n); total += n
            }
            out.toByteArray()
        }
    }

    override fun writeBytes(dirId: String, name: String, bytes: ByteArray): String = guard {
        val (id, out) = createOut(dirId, name, overwrite = true)
        out.use { it.write(bytes) }
        id
    }

    override fun canWrite(dirId: String): Boolean = guard {
        if (!isContent(dirId)) File(dirId).let { it.isDirectory && it.canWrite() }
        else {
            val c = resolver.query(docUri(dirId), arrayOf(Document.COLUMN_FLAGS), null, null, null)
            c?.use { it.moveToFirst() && (it.getInt(0) and Document.FLAG_DIR_SUPPORTS_CREATE) != 0 } ?: false
        }
    }

    override fun freeSpace(dirId: String): Long? =
        if (isContent(dirId)) null else try { File(dirId).usableSpace } catch (_: Exception) { null }

    override fun isInside(candidateId: String, ancestorId: String): Boolean {
        if (candidateId == ancestorId) return true
        return if (isContent(candidateId) && isContent(ancestorId)) {
            // Los ids de documento hijos son "<padre>/<nombre>" (codificados como %2F en el URI).
            val a = ancestorId.removeSuffix("/")
            candidateId.startsWith("$a%2F") || candidateId.startsWith("$a/")
        } else if (!isContent(candidateId) && !isContent(ancestorId)) {
            candidateId.startsWith(ancestorId.trimEnd('/') + "/")
        } else false
    }
}
