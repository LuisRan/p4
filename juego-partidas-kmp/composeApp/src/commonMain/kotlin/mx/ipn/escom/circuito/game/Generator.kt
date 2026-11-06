package mx.ipn.escom.circuito.game

import kotlin.random.Random

/** Resultado de generar un nivel: estado inicial, mínimo de giros y solución. */
class GeneratedLevel(val state: GameState, val minMoves: Int, val solution: List<Int>)

/** Tamaño del tablero: crece una casilla cada 3 niveles, con tope en 8. */
fun boardSizeFor(d: Difficulty, level: Int): Int = (d.baseSize + (level - 1) / 3).coerceIn(3, 8)

/** Tope de movimientos del modo Reto: 1.5 x mínimo + 3. */
fun moveLimitFor(minMoves: Int): Int = (minMoves * 3 + 1) / 2 + 3

/**
 * Genera un nivel de forma determinista a partir de [seed].
 *
 * Algoritmo: árbol de expansión aleatorio (Prim) sobre la cuadrícula; cada
 * arista se vuelve una conexión recíproca entre dos losetas. Luego se gira
 * cada loseta un número aleatorio de veces. Siempre tiene solución porque el
 * estado de partida se obtiene girando una solución.
 */
fun generateLevel(mode: GameMode, difficulty: Difficulty, level: Int, seed: Int): GeneratedLevel {
    val size = boardSizeFor(difficulty, level)
    val rng = Random(seed)
    val n = size * size
    val solution = IntArray(n)
    val source = (size / 2) * size + size / 2

    val inTree = BooleanArray(n)
    val frontier = ArrayList<IntArray>() // pares (desde, dirección)
    fun addFrontier(i: Int) {
        val x = i % size
        val y = i / size
        for (dir in DIRS) {
            val nx = x + dx(dir)
            val ny = y + dy(dir)
            if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
            if (!inTree[ny * size + nx]) frontier.add(intArrayOf(i, dir))
        }
    }
    inTree[source] = true
    addFrontier(source)
    while (frontier.isNotEmpty()) {
        val e = frontier.removeAt(rng.nextInt(frontier.size))
        val from = e[0]
        val dir = e[1]
        val to = (from / size + dy(dir)) * size + (from % size + dx(dir))
        if (inTree[to]) continue
        inTree[to] = true
        solution[from] = solution[from] or dir
        solution[to] = solution[to] or opposite(dir)
        addFrontier(to)
    }

    // Mezcla; se repite si por casualidad el tablero empieza resuelto.
    val tiles = IntArray(n)
    var minMoves: Int
    val probe = GameState(mode, difficulty, level, size, seed, source, List(n) { 0 }, 0)
    do {
        minMoves = 0
        for (i in 0 until n) {
            var m = solution[i]
            repeat(rng.nextInt(4)) { m = rotateCw(m) }
            tiles[i] = m
            minMoves += turnsBetween(m, solution[i])
        }
        for (i in 0 until n) probe.tiles[i] = tiles[i]
    } while (probe.solved)

    val state = GameState(
        mode, difficulty, level, size, seed, source, tiles.toList(),
        if (mode == GameMode.RETO) moveLimitFor(minMoves) else 0,
    )
    return GeneratedLevel(state, minMoves, solution.toList())
}
