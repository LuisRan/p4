package mx.ipn.escom.circuito.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Una nota: frecuencia (Hz, 0 = silencio) y duración (s). */
class Note(val freq: Double, val seconds: Double)

/**
 * Sintetiza un WAV PCM 16-bit mono (senoidal + armónico, envolvente simple
 * para evitar clicks). Todo en memoria: no hay assets.
 */
fun synthWav(notes: List<Note>, sampleRate: Int = 22050, volume: Double = 0.6): ByteArray {
    val samples = ArrayList<Int>()
    for (n in notes) {
        val count = (n.seconds * sampleRate).roundToInt()
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val pos = i.toDouble() / count
            val env = if (pos < 0.08) pos / 0.08 else exp(-3.0 * (pos - 0.08))
            val v = if (n.freq <= 0) 0.0
            else (sin(2 * PI * n.freq * t) + 0.3 * sin(4 * PI * n.freq * t)) / 1.3
            samples.add((v * env * volume * 32767).roundToInt().coerceIn(-32768, 32767))
        }
    }
    val dataLen = samples.size * 2
    val out = ByteArray(44 + dataLen)
    fun u16(off: Int, v: Int) { out[off] = v.toByte(); out[off + 1] = (v shr 8).toByte() }
    fun u32(off: Int, v: Int) { u16(off, v and 0xFFFF); u16(off + 2, v ushr 16) }
    fun str(off: Int, s: String) { s.forEachIndexed { i, c -> out[off + i] = c.code.toByte() } }
    str(0, "RIFF"); u32(4, 36 + dataLen); str(8, "WAVE"); str(12, "fmt ")
    u32(16, 16); u16(20, 1); u16(22, 1); u32(24, sampleRate); u32(28, sampleRate * 2)
    u16(32, 2); u16(34, 16); str(36, "data"); u32(40, dataLen)
    for (i in samples.indices) u16(44 + i * 2, samples[i] and 0xFFFF)
    return out
}

/** Efectos de sonido del juego. */
enum class Sfx { TAP, CONNECT, WIN, LOSE }

/** Banco de sonidos sintetizados una sola vez (perezoso). */
object SoundBank {
    private val cache = HashMap<Sfx, ByteArray>()
    fun wav(s: Sfx): ByteArray = cache.getOrPut(s) {
        when (s) {
            Sfx.TAP -> synthWav(listOf(Note(440.0, 0.06)))
            Sfx.CONNECT -> synthWav(listOf(Note(660.0, 0.07), Note(880.0, 0.10)))
            Sfx.WIN -> synthWav(listOf(Note(523.25, 0.12), Note(659.25, 0.12), Note(783.99, 0.12), Note(1046.5, 0.30)))
            Sfx.LOSE -> synthWav(listOf(Note(330.0, 0.15), Note(262.0, 0.15), Note(196.0, 0.30)))
        }
    }
}
