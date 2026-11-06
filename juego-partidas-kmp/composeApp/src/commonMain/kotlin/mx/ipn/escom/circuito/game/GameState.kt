package mx.ipn.escom.circuito.game

/**
 * Modos de juego.
 *  - CLASICO: sin límite de movimientos; puntúa rapidez y eficiencia.
 *  - RETO: tope de movimientos; si se agota, se pierde el nivel.
 * [id] es el texto estable que se escribe en los archivos de guardado.
 */
enum class GameMode(val id: String, val label: String) {
    CLASICO("clasico", "Clásico"),
    RETO("reto", "Reto");

    companion object {
        fun fromId(id: String): GameMode? = entries.firstOrNull { it.id == id }
    }
}

/** Dificultad: define el tamaño base del tablero. */
enum class Difficulty(val id: String, val label: String, val baseSize: Int) {
    FACIL("facil", "Fácil", 4),
    NORMAL("normal", "Normal", 5),
    DIFICIL("dificil", "Difícil", 6);

    companion object {
        fun fromId(id: String): Difficulty? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Estado completo y serializable de una partida. Todo lo necesario para
 * restaurarla EXACTAMENTE (tablero, historial, puntuación, tiempo) vive aquí.
 */
class GameState(
    val mode: GameMode,
    val difficulty: Difficulty,
    val level: Int,
    val size: Int,
    val seed: Int,
    /** Índice (fila*size+col) de la loseta fuente de energía. */
    val source: Int,
    tiles: List<Int>,
    /** Tope de movimientos (0 = sin límite). */
    val moveLimit: Int,
    moves: List<Int> = emptyList(),
    var score: Int = 0,
    var elapsedSeconds: Int = 0,
    var won: Boolean = false,
) {
    /** Máscaras actuales de cada loseta (size*size). */
    val tiles: MutableList<Int> = tiles.toMutableList()

    /** Historial: índice de cada loseta girada, en orden cronológico. */
    val moves: MutableList<Int> = moves.toMutableList()

    val lost: Boolean get() = moveLimit > 0 && moves.size >= moveLimit && !won
    val finished: Boolean get() = won || lost

    /**
     * Losetas alimentadas desde la fuente (BFS). La conexión debe ser
     * recíproca: ambos lados contiguos tienen que "mirarse".
     */
    fun poweredSet(): Set<Int> {
        val powered = HashSet<Int>()
        powered.add(source)
        val queue = ArrayDeque<Int>()
        queue.add(source)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % size
            val y = i / size
            for (dir in DIRS) {
                if (tiles[i] and dir == 0) continue
                val nx = x + dx(dir)
                val ny = y + dy(dir)
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
                val j = ny * size + nx
                if (j in powered) continue
                if (tiles[j] and opposite(dir) == 0) continue
                powered.add(j)
                queue.add(j)
            }
        }
        return powered
    }

    val solved: Boolean get() = poweredSet().size == size * size

    fun copy() = GameState(
        mode, difficulty, level, size, seed, source, tiles, moveLimit, moves,
        score, elapsedSeconds, won,
    )

    /** Igualdad de contenido (usada en las pruebas de ida y vuelta). */
    fun sameAs(o: GameState) = mode == o.mode && difficulty == o.difficulty &&
        level == o.level && size == o.size && seed == o.seed && source == o.source &&
        tiles == o.tiles && moveLimit == o.moveLimit && moves == o.moves &&
        score == o.score && elapsedSeconds == o.elapsedSeconds && won == o.won
}
