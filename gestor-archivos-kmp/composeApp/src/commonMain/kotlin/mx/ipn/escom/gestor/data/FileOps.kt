package mx.ipn.escom.gestor.data

import mx.ipn.escom.gestor.core.AppException
import mx.ipn.escom.gestor.core.FailureKind

/**
 * Lógica común de copiar/mover/renombrar/eliminar, con validaciones previas:
 * nombre válido, destino escribible, espacio suficiente, sin copiar una carpeta dentro de sí misma
 * y sin pisar archivos existentes ("a (1).txt").
 */
class FileOps(private val store: FileStore) {

    fun validateName(name: String) {
        val n = name.trim()
        if (n.isEmpty()) throw AppException(FailureKind.InvalidName, "el nombre está vacío")
        if (n == "." || n == "..") throw AppException(FailureKind.InvalidName, "no se permite '$n'")
        if (n.any { it == '/' || it == '\\' || it == ':' || it.code < 32 }) {
            throw AppException(FailureKind.InvalidName, "contiene caracteres no permitidos (/ \\ :)")
        }
        if (n.length > 255) throw AppException(FailureKind.InvalidName, "supera 255 caracteres")
    }

    /** Devuelve [name] o "base (1).ext", "base (2).ext"... si ya existe en el destino. */
    fun uniqueName(dirId: String, name: String): String {
        if (!store.exists(dirId, name)) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var i = 1
        while (store.exists(dirId, "$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    /** Bytes totales de un árbol (para la barra de progreso). */
    fun totalSize(entry: FileEntry, depth: Int = 0): Long {
        if (!entry.isDirectory) return entry.size ?: 0L
        if (depth > MAX_DEPTH) return 0L
        return store.list(entry.id).sumOf { totalSize(it, depth + 1) }
    }

    private fun checkDestination(entry: FileEntry, destDirId: String) {
        if (!store.canWrite(destDirId)) {
            throw AppException(FailureKind.NotAllowed, "el destino no admite escritura")
        }
        if (entry.isDirectory && store.isInside(destDirId, entry.id)) {
            throw AppException(FailureKind.NotAllowed, "no se puede copiar una carpeta dentro de sí misma")
        }
        val needed = totalSize(entry)
        val free = store.freeSpace(destDirId)
        if (free != null && needed > free) throw AppException(FailureKind.NoSpace, "se necesitan $needed B y hay $free B")
    }

    /** Copia [entry] a [destDirId]; [progress] recibe (copiados, total). Devuelve el id nuevo. */
    fun copy(entry: FileEntry, destDirId: String, progress: (Long, Long) -> Unit = { _, _ -> }): String {
        checkDestination(entry, destDirId)
        val total = totalSize(entry)
        var done = 0L
        fun rec(e: FileEntry, dest: String, depth: Int): String {
            if (depth > MAX_DEPTH) throw AppException(FailureKind.NotAllowed, "estructura demasiado profunda")
            val name = uniqueName(dest, e.name)
            if (!e.isDirectory) {
                return store.copyFile(e.id, dest, name) { b -> done += b; progress(done, total) }
            }
            val newDir = store.createDirectory(dest, name)
            for (child in store.list(e.id)) rec(child, newDir, depth + 1)
            return newDir
        }
        return rec(entry, destDirId, 0)
    }

    /** Mueve = copiar + eliminar origen (sólo si la copia terminó bien). */
    fun move(entry: FileEntry, destDirId: String, sourceDirId: String?, progress: (Long, Long) -> Unit = { _, _ -> }): String {
        if (sourceDirId != null && sourceDirId == destDirId) {
            throw AppException(FailureKind.NotAllowed, "el origen y el destino son la misma carpeta")
        }
        val newId = copy(entry, destDirId, progress)
        store.delete(entry.id)
        return newId
    }

    fun rename(entry: FileEntry, newName: String, parentId: String?): String {
        validateName(newName)
        val n = newName.trim()
        if (n == entry.name) return entry.id
        if (parentId != null && store.exists(parentId, n)) {
            throw AppException(FailureKind.InvalidName, "ya existe '$n' en esta carpeta")
        }
        return store.rename(entry.id, n)
    }

    fun createFolder(parentId: String, name: String): String {
        validateName(name)
        if (!store.canWrite(parentId)) throw AppException(FailureKind.NotAllowed, "la carpeta no admite escritura")
        if (store.exists(parentId, name.trim())) throw AppException(FailureKind.InvalidName, "ya existe '${name.trim()}'")
        return store.createDirectory(parentId, name.trim())
    }

    fun delete(entry: FileEntry) = store.delete(entry.id)

    companion object { const val MAX_DEPTH = 32 }
}
