package mx.ipn.escom.circuito.saves

import mx.ipn.escom.circuito.game.Difficulty
import mx.ipn.escom.circuito.game.GameMode
import mx.ipn.escom.circuito.game.GameState

/** Versión actual del esquema. El parser acepta <= esta; mayores se rechazan. */
const val SCHEMA_VERSION = 1

/** Configuración personalizada que viaja dentro del archivo de guardado. */
data class SavedSettings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val palette: String = "guinda", // "guinda" | "azul"
)

/** Contenido lógico de un guardado, independiente del formato. */
class SaveGame(
    val name: String,
    val savedAtMillis: Long,
    val state: GameState,
    val tags: List<String> = emptyList(),
    val settings: SavedSettings = SavedSettings(),
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    fun copyWith(name: String = this.name, tags: List<String> = this.tags) =
        SaveGame(name, savedAtMillis, state, tags, settings, schemaVersion)

    /**
     * Representación neutral (mapas/listas/primitivos). JSON la usa directo y
     * sirve de contrato común de validación para XML y TXT.
     */
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to schemaVersion,
        "name" to name,
        "savedAt" to IsoTime.format(savedAtMillis),
        "tags" to tags,
        "game" to linkedMapOf(
            "mode" to state.mode.id,
            "difficulty" to state.difficulty.id,
            "level" to state.level,
            "size" to state.size,
            "seed" to state.seed,
            "source" to state.source,
            "moveLimit" to state.moveLimit,
            "score" to state.score,
            "elapsedSeconds" to state.elapsedSeconds,
            "won" to state.won,
            "tiles" to state.tiles.toList(),
            "moves" to state.moves.toList(),
        ),
        "settings" to linkedMapOf(
            "sound" to settings.sound,
            "vibration" to settings.vibration,
            "difficulty" to settings.difficulty.id,
            "palette" to settings.palette,
        ),
    )

    companion object {
        /**
         * Construye y VALIDA un SaveGame desde un mapa ya parseado.
         * Lanza [SaveException] con mensaje legible ante cualquier problema.
         */
        fun fromMap(raw: Any?): SaveGame {
            val m = asMap(raw) ?: throw CorruptSaveException("La estructura raíz del archivo no es válida.")

            val version = int(m, "schemaVersion")
            if (version < 1) throw CorruptSaveException("schemaVersion inválido.")
            if (version > SCHEMA_VERSION) throw UnsupportedVersionException(version, SCHEMA_VERSION)

            val name = str(m, "name")
            val savedAt = IsoTime.parse(str(m, "savedAt"))
                ?: throw CorruptSaveException("Fecha \"savedAt\" inválida.")
            val tagsRaw = m["tags"]
            val tags: List<String> = when (tagsRaw) {
                null -> emptyList()
                is List<*> -> tagsRaw.map { it.toString() }
                else -> throw CorruptSaveException("\"tags\" debe ser una lista.")
            }

            val g = map(m, "game")
            val size = int(g, "size")
            if (size < 3 || size > 8) throw CorruptSaveException("Tamaño de tablero fuera de rango: $size.")
            val tiles = intList(g, "tiles")
            if (tiles.size != size * size) {
                throw CorruptSaveException("El tablero tiene ${tiles.size} losetas; se esperaban ${size * size}.")
            }
            if (tiles.any { it < 0 || it > 15 }) throw CorruptSaveException("Hay losetas con valores inválidos.")
            val source = int(g, "source")
            if (source < 0 || source >= size * size) throw CorruptSaveException("Posición de fuente inválida.")
            val moves = intList(g, "moves")
            if (moves.any { it < 0 || it >= size * size }) {
                throw CorruptSaveException("El historial contiene movimientos inválidos.")
            }
            val elapsed = int(g, "elapsedSeconds")
            val score = int(g, "score")
            val limit = int(g, "moveLimit")
            val level = int(g, "level")
            if (elapsed < 0 || score < 0 || limit < 0 || level < 1) {
                throw CorruptSaveException("Valores numéricos fuera de rango.")
            }

            val state = GameState(
                mode = enumOf(GameMode.fromId(str(g, "mode")), str(g, "mode"), "mode"),
                difficulty = enumOf(Difficulty.fromId(str(g, "difficulty")), str(g, "difficulty"), "difficulty"),
                level = level,
                size = size,
                seed = int(g, "seed"),
                source = source,
                tiles = tiles,
                moveLimit = limit,
                moves = moves,
                score = score,
                elapsedSeconds = elapsed,
                won = bool(g, "won"),
            )

            // La sección de ajustes es opcional: si falta, valores por defecto.
            var settings = SavedSettings()
            if (m["settings"] != null) {
                val s = map(m, "settings")
                settings = SavedSettings(
                    sound = bool(s, "sound"),
                    vibration = bool(s, "vibration"),
                    difficulty = enumOf(Difficulty.fromId(str(s, "difficulty")), str(s, "difficulty"), "settings.difficulty"),
                    palette = if (str(s, "palette") == "azul") "azul" else "guinda",
                )
            }
            return SaveGame(name, savedAt, state, tags, settings, version)
        }

        // ---- helpers de validación defensiva ----

        @Suppress("UNCHECKED_CAST")
        private fun asMap(v: Any?): Map<String, Any?>? = (v as? Map<*, *>)?.let { it as Map<String, Any?> }

        private fun req(m: Map<String, Any?>, k: String): Any = m[k] ?: throw IncompleteSaveException(k)

        private fun toIntOrNull(v: Any?): Int? = when (v) {
            is Int -> v
            is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
            is Double -> if (v == v.toLong().toDouble() && v in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) v.toInt() else null
            is String -> v.trim().toIntOrNull()
            else -> null
        }

        private fun int(m: Map<String, Any?>, k: String): Int =
            toIntOrNull(req(m, k)) ?: throw CorruptSaveException("El campo \"$k\" debe ser un entero.")

        private fun bool(m: Map<String, Any?>, k: String): Boolean = when (val v = req(m, k)) {
            is Boolean -> v
            is String -> when (v.trim()) {
                "true" -> true
                "false" -> false
                else -> throw CorruptSaveException("El campo \"$k\" debe ser true/false.")
            }
            else -> throw CorruptSaveException("El campo \"$k\" debe ser true/false.")
        }

        private fun str(m: Map<String, Any?>, k: String): String =
            req(m, k) as? String ?: throw CorruptSaveException("El campo \"$k\" debe ser texto.")

        private fun map(m: Map<String, Any?>, k: String): Map<String, Any?> =
            asMap(req(m, k)) ?: throw CorruptSaveException("El campo \"$k\" debe ser un objeto.")

        private fun intList(m: Map<String, Any?>, k: String): List<Int> {
            val v = req(m, k) as? List<*> ?: throw CorruptSaveException("El campo \"$k\" debe ser una lista.")
            return v.map { toIntOrNull(it) ?: throw CorruptSaveException("\"$k\" contiene un valor no numérico: $it.") }
        }

        private fun <T> enumOf(value: T?, text: String, field: String): T =
            value ?: throw CorruptSaveException("Valor desconocido \"$text\" en \"$field\".")
    }
}
