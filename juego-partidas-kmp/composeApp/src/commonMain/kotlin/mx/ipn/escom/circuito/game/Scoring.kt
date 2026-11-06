package mx.ipn.escom.circuito.game

import kotlin.math.roundToInt

/**
 * Puntuación (función pura y testeable).
 * base = 100 por loseta; -8 por movimiento y -1 por segundo, mínimo 10% de la
 * base. Reto multiplica x1.5 y, al ganar, se suma 50 x nivel.
 */
fun computeScore(s: GameState, won: Boolean): Int {
    val base = s.size * s.size * 100
    val raw = base - s.moves.size * 8 - s.elapsedSeconds
    var score = if (raw < base / 10) base / 10 else raw
    if (s.mode == GameMode.RETO) score = (score * 1.5).roundToInt()
    if (won) score += s.level * 50
    return score
}
