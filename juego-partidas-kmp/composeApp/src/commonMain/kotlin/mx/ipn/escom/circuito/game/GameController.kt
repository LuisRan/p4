package mx.ipn.escom.circuito.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import mx.ipn.escom.circuito.core.AppSettings
import mx.ipn.escom.circuito.core.GameFeedback
import mx.ipn.escom.circuito.core.PlayerProgress
import mx.ipn.escom.circuito.core.Sfx
import mx.ipn.escom.circuito.saves.SaveGame

/**
 * Lógica de la partida en curso. No conoce la UI: la UI lee [rev] (estado de
 * Compose que se incrementa en cada cambio) para recomponerse.
 * [feedback] y [progress] pueden ser null en pruebas unitarias.
 */
class GameController(
    private val settings: AppSettings,
    private val progress: PlayerProgress?,
    private val feedback: GameFeedback?,
    /** Autoguardado: se invoca tras cada cambio relevante. */
    private val onChanged: ((GameController) -> Unit)? = null,
) {
    lateinit var state: GameState
        private set
    var powered: Set<Int> = emptySet()
        private set
    var minMoves: Int = 0
        private set
    var started = false
        private set

    /** Contador observable; cambia con cada modificación del estado. */
    var rev by mutableIntStateOf(0)
        private set

    /** La UI lanza partículas/animaciones en la loseta [index]. */
    var onTileEffect: ((index: Int, connected: Boolean, undo: Boolean) -> Unit)? = null
    var onFinished: ((won: Boolean) -> Unit)? = null

    /** Nueva partida del modo y nivel dados. [seed] opcional (pruebas). */
    fun startNew(mode: GameMode, level: Int, seed: Int? = null, difficulty: Difficulty = settings.difficulty) {
        val g = generateLevel(mode, difficulty, level, seed ?: Random.nextInt(1 shl 30))
        state = g.state
        minMoves = g.minMoves
        afterLoad()
    }

    /** Restaura exactamente una partida guardada. */
    fun restore(save: SaveGame) {
        state = save.state.copy()
        minMoves = try {
            val g = generateLevel(state.mode, state.difficulty, state.level, state.seed)
            if (g.state.size == state.size) g.minMoves else state.size * state.size
        } catch (e: Exception) { state.size * state.size }
        afterLoad()
    }

    private fun afterLoad() {
        started = true
        powered = state.poweredSet()
        rev++
    }

    /** Un segundo de juego (lo llama el temporizador de la UI). */
    fun tick() {
        if (!started || state.finished) return
        state.elapsedSeconds++
        state.score = computeScore(state, won = false)
        rev++
    }

    /** Gira la loseta [i] 90° en sentido horario. */
    fun rotate(i: Int) {
        if (!started || state.finished || i < 0 || i >= state.tiles.size) return
        state.tiles[i] = rotateCw(state.tiles[i])
        state.moves.add(i)
        applyMove(i, undo = false)
    }

    /** Deshace el último giro; el historial se acorta. */
    fun undo(): Boolean {
        if (!started || state.finished || state.moves.isEmpty()) return false
        val i = state.moves.removeAt(state.moves.lastIndex)
        state.tiles[i] = rotateCcw(state.tiles[i])
        applyMove(i, undo = true)
        return true
    }

    private fun applyMove(i: Int, undo: Boolean) {
        val before = powered
        powered = state.poweredSet()
        state.score = computeScore(state, won = false)
        val gained = powered.size > before.size
        feedback?.play(if (gained) Sfx.CONNECT else Sfx.TAP)
        feedback?.haptic()
        onTileEffect?.invoke(i, gained && i in powered, undo)

        if (state.solved) {
            state.won = true
            state.score = computeScore(state, won = true)
            finish(true)
        } else if (state.lost) finish(false)
        rev++
        onChanged?.invoke(this)
    }

    private fun finish(won: Boolean) {
        progress?.recordResult(state)
        feedback?.play(if (won) Sfx.WIN else Sfx.LOSE)
        feedback?.haptic(strong = true)
        onFinished?.invoke(won)
    }

    /** Empaqueta el estado actual para guardarlo. */
    fun toSave(name: String, nowMillis: Long, tags: List<String> = emptyList()) =
        SaveGame(name, nowMillis, state.copy(), tags, settings.snapshot())
}
