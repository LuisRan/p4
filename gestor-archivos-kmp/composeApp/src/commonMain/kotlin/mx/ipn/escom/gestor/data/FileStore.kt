package mx.ipn.escom.gestor.data

import mx.ipn.escom.gestor.core.FileTypes

/**
 * Elemento de un directorio. [id] es opaco para la lógica común:
 *  - Android: ruta de archivo (espacio propio) o URI content:// de SAF (carpetas elegidas).
 *  - iOS: ruta POSIX (sandbox o carpeta elegida con security-scoped URL activa).
 */
data class FileEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val modified: Long? = null,
    val created: Long? = null,
    /** MIME informado por el proveedor (SAF); si falta se deduce de la extensión. */
    val providerMime: String? = null,
) {
    val mime: String? get() = if (isDirectory) null else providerMime?.takeIf { it != "application/octet-stream" } ?: FileTypes.mimeOf(name)
    val utType: String? get() = if (isDirectory) "public.folder" else FileTypes.utTypeOf(name)
}

/** Una raíz navegable: espacio propio de la app o carpeta elegida por el usuario. */
data class RootInfo(
    val id: String,
    val name: String,
    /** Dato persistible para recuperar el acceso (URI de árbol en Android, bookmark en iOS). Null = interna. */
    val token: String? = null,
    val external: Boolean = false,
    /** false si el permiso se revocó o la ubicación ya no existe. */
    val available: Boolean = true,
)

/**
 * Operaciones primitivas de archivos, implementadas por plataforma
 * (java.io + ContentResolver/SAF en Android, NSFileManager en iOS).
 * Todas son bloqueantes: se invocan desde Dispatchers.Default. Lanzan AppException.
 */
interface FileStore {
    fun list(dirId: String): List<FileEntry>
    fun stat(id: String): FileEntry
    fun exists(dirId: String, name: String): Boolean
    /** Crea la carpeta (o devuelve la existente) y devuelve su id. */
    fun createDirectory(parentId: String, name: String): String
    /** Copia un archivo (no carpeta) a [destDirId]/[destName]; [onBytes] recibe bytes copiados. Devuelve el id nuevo. */
    fun copyFile(srcId: String, destDirId: String, destName: String, onBytes: (Long) -> Unit): String
    fun delete(id: String)
    /** Renombra y devuelve el id resultante (puede cambiar en SAF). */
    fun rename(id: String, newName: String): String
    /** Lee hasta [maxBytes] bytes del inicio del archivo. */
    fun readBytes(id: String, maxBytes: Int): ByteArray
    /** Escribe (sobrescribiendo si existe) y devuelve el id. */
    fun writeBytes(dirId: String, name: String, bytes: ByteArray): String
    /** true si se puede crear contenido dentro de [dirId]. */
    fun canWrite(dirId: String): Boolean
    /** Bytes libres si la plataforma los puede calcular. */
    fun freeSpace(dirId: String): Long?
    /** true si [candidateId] es [ancestorId] o está dentro de él. */
    fun isInside(candidateId: String, ancestorId: String): Boolean
}
