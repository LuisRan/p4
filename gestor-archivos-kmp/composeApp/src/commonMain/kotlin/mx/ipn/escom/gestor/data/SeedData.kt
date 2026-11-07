package mx.ipn.escom.gestor.data

import mx.ipn.escom.gestor.core.PngEncoder

/** Datos de ejemplo del primer arranque (txt, md, json, xml, png, bin). Idempotente. */
object SeedData {
    /** true si [documentsRoot] no contiene nada (o no se puede listar): hay que (re)sembrar. */
    fun needsSeed(store: FileStore, documentsRoot: String): Boolean =
        try { store.list(documentsRoot).isEmpty() } catch (_: Exception) { true }

    /** Crea las carpetas y archivos de ejemplo que falten; nunca borra ni pisa lo existente. */
    fun seed(store: FileStore, documentsRoot: String) {
        val base = store.createDirectory(documentsRoot, "Ejemplos")
        val docs = store.createDirectory(base, "Documentos")
        val media = store.createDirectory(base, "Imagenes")

        fun w(dir: String, name: String, bytes: ByteArray) {
            if (!store.exists(dir, name)) store.writeBytes(dir, name, bytes)
        }
        w(base, "LEEME.txt", NOTE.encodeToByteArray())
        w(docs, "nota.txt", NOTE.encodeToByteArray())
        w(docs, "lee_me.md", MD.encodeToByteArray())
        w(docs, "alumnos.json", JSON.encodeToByteArray())
        w(docs, "plan.xml", XML.encodeToByteArray())
        // Imagen 480x320 generada en código: degradado guinda -> azul.
        w(media, "degradado.png", PngEncoder.encode(480, 320) { x, y ->
            val t = x / 479.0
            val r = (0x6A + (0x0B - 0x6A) * t).toInt()
            val g = (0x1B + (0x3C - 0x1B) * t).toInt()
            val b = (0x3A + (0x8C - 0x3A) * t).toInt()
            val shade = if ((x / 32 + y / 32) % 2 == 0) 0 else 24 // cuadrícula tenue para apreciar zoom/rotación
            (minOf(255, r + shade) shl 16) or (minOf(255, g + shade) shl 8) or minOf(255, b + shade)
        })
        // 2 KB deterministas: formato no soportado (muestra el diálogo abrir/compartir/exportar).
        w(base, "datos.bin", ByteArray(2048) { i -> ((i * 73 + 41) and 0xFF).toByte() })
    }

    private const val NOTE = "Gestor de archivos - IPN ESCOM\n\nEste es un archivo de texto plano.\n" +
        "Puedes copiarlo, moverlo, renombrarlo o compartirlo.\n"

    private const val MD = "# Bienvenido\n\nEste es un documento **Markdown** con *énfasis* y `código en línea`.\n\n" +
        "## Lista\n\n- Explorar carpetas\n- Ver JSON y XML con colores\n- Rotar y hacer zoom en imágenes\n\n" +
        "> Una cita de ejemplo.\n\n```\nbloque de código\n```\n"

    private const val JSON = "{\"curso\":\"Desarrollo Móvil\",\"escuela\":\"ESCOM\",\"alumnos\":[" +
        "{\"nombre\":\"Ana\",\"calificacion\":9.5,\"activo\":true}," +
        "{\"nombre\":\"Luis\",\"calificacion\":8,\"activo\":false,\"nota\":null}]}"

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- Plan de ejemplo -->" +
        "<plan id=\"1\" tipo=\"movil\"><materia clave=\"DM\">Desarrollo Móvil</materia>" +
        "<materia clave=\"BD\">Bases de Datos</materia><vacio/></plan>"
}
