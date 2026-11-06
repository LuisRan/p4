package mx.ipn.escom.gestor.core

/** Tipos de fallo que la UI sabe explicar al usuario sin que la app se cierre. */
enum class FailureKind(val titulo: String) {
    Unavailable("Ruta no disponible"),
    PermissionRevoked("Permiso revocado o denegado"),
    NotFound("El archivo no existe"),
    ReadFailed("No se pudo leer el archivo"),
    WriteFailed("No se pudo escribir"),
    NoSpace("Almacenamiento insuficiente"),
    InvalidName("Nombre no válido"),
    NotAllowed("Operación no permitida"),
    Unknown("Error inesperado"),
}

/** Excepción de dominio: toda la capa de archivos lanza esto (o se convierte con [asAppException]). */
class AppException(val kind: FailureKind, detail: String? = null) :
    Exception(if (detail.isNullOrBlank()) kind.titulo else "${kind.titulo}: $detail")

/** Convierte cualquier Throwable en AppException para mostrarlo en un Snackbar/pantalla de error. */
fun Throwable.asAppException(): AppException =
    this as? AppException ?: AppException(FailureKind.Unknown, message)
