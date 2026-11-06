package mx.ipn.escom.circuito.saves

/**
 * Jerarquía de excepciones propias del sistema de guardado. Todas llevan un
 * [message] en español apto para mostrarse al usuario.
 */
sealed class SaveException(override val message: String) : Exception(message)

/** Archivo vacío o solo con espacios. */
class EmptySaveException : SaveException("El archivo está vacío.")

/** Contenido ilegible o con sintaxis inválida para el formato. */
class CorruptSaveException(message: String) : SaveException(message)

/** Faltan campos obligatorios (archivo truncado o incompleto). */
class IncompleteSaveException(val field: String) :
    SaveException("Archivo incompleto: falta el campo \"$field\".")

/** Versión de esquema más nueva que la soportada por esta app. */
class UnsupportedVersionException(val found: Int, val supported: Int) :
    SaveException("Versión de esquema $found no soportada (esta app entiende hasta la $supported).")

/** Error de E/S del sistema de archivos. */
class SaveIoException(message: String) : SaveException(message)
