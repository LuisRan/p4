package mx.ipn.escom.circuito.game

/**
 * Utilidades de bajo nivel para las losetas de "Circuito Guinda".
 * Cada loseta es una máscara de 4 bits con los lados que tienen conexión:
 * N=1, E=2, S=4, O=8.
 */
const val N = 1
const val E = 2
const val S = 4
const val W = 8

val DIRS = intArrayOf(N, E, S, W)

/** Gira la máscara 90° en sentido horario (N->E->S->O->N). */
fun rotateCw(mask: Int): Int = ((mask shl 1) or (mask shr 3)) and 15

/** Gira 90° en sentido antihorario (lo usa "deshacer"). */
fun rotateCcw(mask: Int): Int = ((mask shr 1) or (mask shl 3)) and 15

/** Número de conexiones de la loseta. */
fun bitCount(mask: Int): Int = (0..3).count { mask and (1 shl it) != 0 }

/**
 * Mínimo de giros horarios (0..3) para convertir [from] en [to], o -1 si no
 * son la misma forma. Considera simetrías (cruz: 0 giros, recta: máx. 1).
 */
fun turnsBetween(from: Int, to: Int): Int {
    var m = from
    for (k in 0..3) {
        if (m == to) return k
        m = rotateCw(m)
    }
    return -1
}

/** Desplazamiento en x de cada dirección. */
fun dx(dir: Int): Int = when (dir) { E -> 1; W -> -1; else -> 0 }

/** Desplazamiento en y de cada dirección (y crece hacia abajo). */
fun dy(dir: Int): Int = when (dir) { S -> 1; N -> -1; else -> 0 }

/** Dirección opuesta. */
fun opposite(dir: Int): Int = when (dir) { N -> S; E -> W; S -> N; else -> E }
