package mx.ipn.escom.gestor.core

/**
 * Codificador PNG mínimo (RGB 8 bits, bloques deflate "stored" sin compresión) para generar
 * la imagen de ejemplo sin depender de APIs de plataforma.
 */
object PngEncoder {
    private val crcTable = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    private fun crc32(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var c = -1
        for (i in from until to) c = crcTable[(c xor data[i].toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    private fun adler32(data: ByteArray): Int {
        var a = 1; var b = 0
        for (x in data) { a = (a + (x.toInt() and 0xFF)) % 65521; b = (b + a) % 65521 }
        return (b shl 16) or a
    }

    private fun ByteArray.putInt(off: Int, v: Int) {
        this[off] = (v ushr 24).toByte(); this[off + 1] = (v ushr 16).toByte()
        this[off + 2] = (v ushr 8).toByte(); this[off + 3] = v.toByte()
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArray(12 + data.size)
        out.putInt(0, data.size)
        for (i in 0 until 4) out[4 + i] = type[i].code.toByte()
        data.copyInto(out, 8)
        out.putInt(8 + data.size, crc32(out, 4, 8 + data.size))
        return out
    }

    /** [pixel] devuelve 0xRRGGBB para (x, y). */
    fun encode(width: Int, height: Int, pixel: (Int, Int) -> Int): ByteArray {
        val raw = ByteArray(height * (1 + width * 3))
        var p = 0
        for (y in 0 until height) {
            raw[p++] = 0 // filtro "None"
            for (x in 0 until width) {
                val c = pixel(x, y)
                raw[p++] = (c shr 16).toByte(); raw[p++] = (c shr 8).toByte(); raw[p++] = c.toByte()
            }
        }
        // zlib: cabecera 78 01 + bloques stored de hasta 65535 bytes + Adler-32.
        val z = ArrayList<Byte>()
        z.add(0x78); z.add(0x01)
        var off = 0
        while (off < raw.size) {
            val n = minOf(65535, raw.size - off)
            z.add(if (off + n >= raw.size) 1 else 0)
            z.add(n.toByte()); z.add((n shr 8).toByte())
            z.add(n.inv().toByte()); z.add((n.inv() shr 8).toByte())
            for (i in off until off + n) z.add(raw[i])
            off += n
        }
        val ad = ByteArray(4).also { it.putInt(0, adler32(raw)) }
        ad.forEach { z.add(it) }
        val ihdr = ByteArray(13).also {
            it.putInt(0, width); it.putInt(4, height)
            it[8] = 8; it[9] = 2 // 8 bits, RGB
        }
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return sig + chunk("IHDR", ihdr) + chunk("IDAT", z.toByteArray()) + chunk("IEND", ByteArray(0))
    }
}
