package mx.ipn.escom.circuito

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import mx.ipn.escom.circuito.core.AppSettings
import mx.ipn.escom.circuito.core.SoundBank
import mx.ipn.escom.circuito.core.Sfx
import mx.ipn.escom.circuito.game.*

class GameTests {
    @Test fun rotationsAreInverse() {
        for (m in 0..15) {
            assertEquals(m, rotateCcw(rotateCw(m)))
            var x = m; repeat(4) { x = rotateCw(x) }
            assertEquals(m, x)
        }
        assertEquals(E, rotateCw(N)); assertEquals(N, rotateCw(W))
    }

    @Test fun generatorIsDeterministicAndSolvable() {
        for (d in Difficulty.entries) for (level in listOf(1, 4, 7, 20)) {
            val a = generateLevel(GameMode.CLASICO, d, level, 99)
            val b = generateLevel(GameMode.CLASICO, d, level, 99)
            assertEquals(a.state.tiles, b.state.tiles)
            assertEquals(boardSizeFor(d, level), a.state.size)
            assertFalse(a.state.solved, "no debe empezar resuelto")
            // aplicar la solución original resuelve el tablero
            val s = a.state.copy()
            for (i in s.tiles.indices) s.tiles[i] = a.solution[i]
            assertTrue(s.solved)
        }
    }

    @Test fun boardSizeCapsAtEight() {
        assertEquals(4, boardSizeFor(Difficulty.FACIL, 1))
        assertEquals(5, boardSizeFor(Difficulty.FACIL, 4))
        assertEquals(8, boardSizeFor(Difficulty.DIFICIL, 100))
    }

    @Test fun solvingWithMinimumMovesWins() {
        val c = GameController(AppSettings(), null, null)
        c.startNew(GameMode.CLASICO, 1, seed = 7, difficulty = Difficulty.FACIL)
        val sol = generateLevel(GameMode.CLASICO, Difficulty.FACIL, 1, 7).solution
        var moves = 0
        for (i in c.state.tiles.indices) while (c.state.tiles[i] != sol[i]) { c.rotate(i); moves++ }
        assertTrue(c.state.won)
        assertEquals(c.minMoves, moves)
        assertTrue(c.state.score > 0)
        // tras ganar ya no acepta movimientos
        val before = c.state.moves.size
        c.rotate(0)
        assertEquals(before, c.state.moves.size)
    }

    @Test fun undoRevertsLastMove() {
        val c = GameController(AppSettings(), null, null)
        c.startNew(GameMode.CLASICO, 1, seed = 3, difficulty = Difficulty.FACIL)
        val start = c.state.tiles.toList()
        c.rotate(2); c.rotate(5)
        assertEquals(2, c.state.moves.size)
        assertTrue(c.undo()); assertTrue(c.undo())
        assertEquals(start, c.state.tiles)
        assertFalse(c.undo())
    }

    @Test fun retoLosesWhenMovesRunOut() {
        val c = GameController(AppSettings(), null, null)
        c.startNew(GameMode.RETO, 1, seed = 11, difficulty = Difficulty.FACIL)
        val limit = c.state.moveLimit
        assertEquals(moveLimitFor(c.minMoves), limit)
        // Gira solo la loseta 0 cuatro veces por ciclo: nunca resuelve salvo casualidad; basta ver el tope.
        var guard = 0
        while (!c.state.finished && guard++ < 1000) c.rotate(if (c.state.source == 0) 1 else 0)
        assertTrue(c.state.finished)
        if (!c.state.won) assertTrue(c.state.lost && c.state.moves.size == limit)
    }

    @Test fun scoringRules() {
        val g = generateLevel(GameMode.CLASICO, Difficulty.FACIL, 1, 1).state
        assertEquals(1600, computeScore(g, won = false))
        g.elapsedSeconds = 10; g.moves.addAll(listOf(1, 2))
        assertEquals(1600 - 16 - 10, computeScore(g, won = false))
        assertEquals(1600 - 26 + 50, computeScore(g, won = true))
        g.elapsedSeconds = 100000
        assertEquals(160, computeScore(g, won = false)) // mínimo 10% de la base
        val r = generateLevel(GameMode.RETO, Difficulty.FACIL, 2, 1).state
        assertEquals((r.size * r.size * 100 * 1.5).toInt(), computeScore(r, won = false))
    }

    @Test fun restoreGivesExactState() {
        val c = GameController(AppSettings(), null, null)
        c.startNew(GameMode.CLASICO, 3, seed = 5, difficulty = Difficulty.NORMAL)
        c.rotate(1); c.rotate(4); c.tick(); c.tick()
        val save = c.toSave("x", 1L)
        val c2 = GameController(AppSettings(), null, null)
        c2.restore(save)
        assertTrue(c.state.sameAs(c2.state))
        assertEquals(c.powered, c2.powered)
        assertEquals(c.minMoves, c2.minMoves)
    }

    @Test fun wavIsValid() {
        for (s in Sfx.entries) {
            val w = SoundBank.wav(s)
            assertEquals("RIFF", w.copyOfRange(0, 4).decodeToString())
            assertEquals("WAVE", w.copyOfRange(8, 12).decodeToString())
            val dataLen = (w[40].toInt() and 255) or ((w[41].toInt() and 255) shl 8) or ((w[42].toInt() and 255) shl 16)
            assertEquals(w.size - 44, dataLen)
            assertTrue(w.size > 1000)
        }
    }
}
