package mx.ipn.escom.circuito.saves

/** Conversión mínima entre milisegundos epoch y texto ISO-8601 UTC (sin librerías). */
object IsoTime {
    private fun floorDiv(a: Long, b: Long): Long {
        var q = a / b
        if ((a % b != 0L) && ((a < 0) != (b < 0))) q--
        return q
    }

    private fun pad(v: Long, n: Int) = v.toString().padStart(n, '0')

    /** Ej.: 2026-11-05T10:30:15.000Z */
    fun format(millis: Long): String {
        val days = floorDiv(millis, 86_400_000L)
        val msOfDay = millis - days * 86_400_000L
        // Algoritmo "civil from days" de Howard Hinnant.
        val z = days + 719468
        val era = floorDiv(z, 146097)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val year = if (m <= 2) y + 1 else y
        val h = msOfDay / 3_600_000
        val mi = msOfDay / 60_000 % 60
        val s = msOfDay / 1000 % 60
        val ms = msOfDay % 1000
        return "${pad(year, 4)}-${pad(m, 2)}-${pad(d, 2)}T${pad(h, 2)}:${pad(mi, 2)}:${pad(s, 2)}.${pad(ms, 3)}Z"
    }

    private val rx = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3})\d*)?Z$""")

    /** Devuelve millis o null si el texto no es una fecha ISO UTC válida. */
    fun parse(text: String): Long? {
        val g = rx.matchEntire(text.trim())?.groupValues ?: return null
        val y = g[1].toLong()
        val m = g[2].toLong()
        val d = g[3].toLong()
        val h = g[4].toLong()
        val mi = g[5].toLong()
        val s = g[6].toLong()
        if (m !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || s > 59) return null
        val ms = if (g[7].isEmpty()) 0L else g[7].padEnd(3, '0').toLong()
        val yy = if (m <= 2) y - 1 else y
        val era = floorDiv(yy, 400)
        val yoe = yy - era * 400
        val mp = if (m > 2) m - 3 else m + 9
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146097 + doe - 719468
        return days * 86_400_000L + h * 3_600_000 + mi * 60_000 + s * 1000 + ms
    }
}
