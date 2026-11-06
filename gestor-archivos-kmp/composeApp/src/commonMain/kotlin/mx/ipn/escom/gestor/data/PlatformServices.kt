package mx.ipn.escom.gestor.data

/**
 * Todo lo que depende del sistema operativo y requiere UI nativa o permisos.
 * Se implementa en androidMain (AndroidPlatform) e iosMain (IosPlatform) y se inyecta en App().
 * Las funciones suspend esperan el resultado del selector del sistema.
 */
interface PlatformServices {
    val fileStore: FileStore
    /** Espacio propio de la app (Documentos y datos internos), sin permisos. */
    val internalRoots: List<RootInfo>
    /** Directorio privado (no navegable) para prefs.json. */
    val privateDir: String
    /** Directorio de caché (miniaturas). */
    val cacheDir: String

    /** Selector de carpetas del sistema. null = el usuario canceló. Persiste el permiso. */
    suspend fun pickFolder(): RootInfo?
    /** Reabre una raíz guardada; available=false si el permiso fue revocado. */
    fun resolveRoot(token: String, nameHint: String): RootInfo
    /** Selector de archivos para importar; devuelve entradas legibles con [fileStore]. Vacío si cancela. */
    suspend fun pickFilesToImport(): List<FileEntry>
    /** "Guardar como" del sistema: copia [entry] al destino elegido. false si cancela. */
    suspend fun exportFile(entry: FileEntry): Boolean
    /** Hoja de compartir (Intent chooser / UIActivityViewController). */
    fun share(entry: FileEntry)
    /** "Abrir con..." otra aplicación. */
    fun openWith(entry: FileEntry)
}
