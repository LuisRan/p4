package mx.ipn.escom.gestor.core

/** Cómo sabe la app visualizar un archivo. */
enum class ViewerKind { Text, Markdown, Json, Xml, Image, Unsupported }

/** Tabla extensión -> (MIME, UTType de Apple). Es lógica pura y común a ambas plataformas. */
object FileTypes {
    private data class T(val mime: String, val uti: String?)

    private val table = mapOf(
        "txt" to T("text/plain", "public.plain-text"),
        "log" to T("text/plain", "public.plain-text"),
        "md" to T("text/markdown", "net.daringfireball.markdown"),
        "markdown" to T("text/markdown", "net.daringfireball.markdown"),
        "csv" to T("text/csv", "public.comma-separated-values-text"),
        "json" to T("application/json", "public.json"),
        "xml" to T("application/xml", "public.xml"),
        "html" to T("text/html", "public.html"),
        "yml" to T("application/yaml", "public.yaml"),
        "yaml" to T("application/yaml", "public.yaml"),
        "ini" to T("text/plain", "public.plain-text"),
        "png" to T("image/png", "public.png"),
        "jpg" to T("image/jpeg", "public.jpeg"),
        "jpeg" to T("image/jpeg", "public.jpeg"),
        "gif" to T("image/gif", "com.compuserve.gif"),
        "webp" to T("image/webp", "org.webmproject.webp"),
        "bmp" to T("image/bmp", "com.microsoft.bmp"),
        "pdf" to T("application/pdf", "com.adobe.pdf"),
        "zip" to T("application/zip", "public.zip-archive"),
        "mp3" to T("audio/mpeg", "public.mp3"),
        "mp4" to T("video/mp4", "public.mpeg-4"),
        "bin" to T("application/octet-stream", "public.data"),
    )

    fun extension(name: String): String =
        if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""

    fun mimeOf(name: String): String? = table[extension(name)]?.mime
    fun utTypeOf(name: String): String? = table[extension(name)]?.uti

    fun kindOf(name: String): ViewerKind = when (extension(name)) {
        "txt", "log", "csv", "yml", "yaml", "ini", "html" -> ViewerKind.Text
        "md", "markdown" -> ViewerKind.Markdown
        "json" -> ViewerKind.Json
        "xml" -> ViewerKind.Xml
        "png", "jpg", "jpeg", "gif", "webp", "bmp" -> ViewerKind.Image
        else -> ViewerKind.Unsupported
    }
}
