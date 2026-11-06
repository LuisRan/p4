package mx.ipn.escom.circuito.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import mx.ipn.escom.circuito.game.Difficulty
import mx.ipn.escom.circuito.game.GameMode
import mx.ipn.escom.circuito.game.GameState
import mx.ipn.escom.circuito.platform.FileStore
import mx.ipn.escom.circuito.saves.SaveFormat
import mx.ipn.escom.circuito.saves.SavedSettings

/** Lectura/escritura tolerante de pequeños JSON de configuración. */
private class JsonDoc(private val store: FileStore?, private val name: String) {
    fun read(): JsonObject? = try {
        store?.read("data", name)?.let { Json.parseToJsonElement(it) as? JsonObject }
    } catch (e: Exception) { null } // archivo dañado: se usan valores por defecto

    fun write(map: Map<String, JsonElement>) {
        try { store?.write("data", name, Json.encodeToString(JsonElement.serializer(), JsonObject(map))) }
        catch (e: Exception) { /* no crítico */ }
    }
}

private fun JsonObject.bool(k: String, d: Boolean) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: d
private fun JsonObject.int(k: String, d: Int = 0) = (this[k] as? JsonPrimitive)?.intOrNull ?: d
private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

/** Ajustes del usuario (persisten en data/settings.json). Observable por Compose. */
class AppSettings(store: FileStore? = null) {
    private val doc = JsonDoc(store, "settings.json")

    var sound by mutableStateOf(true); private set
    var vibration by mutableStateOf(true); private set
    var difficulty by mutableStateOf(Difficulty.NORMAL); private set
    var palette by mutableStateOf("guinda"); private set   // "guinda" | "azul"
    var format by mutableStateOf(SaveFormat.JSON); private set
    var autosave by mutableStateOf(true); private set
    var animations by mutableStateOf(true); private set
    var particles by mutableStateOf(true); private set
    var glow by mutableStateOf(true); private set

    init {
        doc.read()?.let { m ->
            sound = m.bool("sound", sound)
            vibration = m.bool("vibration", vibration)
            autosave = m.bool("autosave", autosave)
            animations = m.bool("animations", animations)
            particles = m.bool("particles", particles)
            glow = m.bool("glow", glow)
            difficulty = m.str("difficulty")?.let { Difficulty.fromId(it) } ?: difficulty
            format = m.str("format")?.let { SaveFormat.fromExtension(it) } ?: format
            palette = if (m.str("palette") == "azul") "azul" else "guinda"
        }
    }

    fun update(
        sound: Boolean = this.sound, vibration: Boolean = this.vibration,
        difficulty: Difficulty = this.difficulty, palette: String = this.palette,
        format: SaveFormat = this.format, autosave: Boolean = this.autosave,
        animations: Boolean = this.animations, particles: Boolean = this.particles,
        glow: Boolean = this.glow,
    ) {
        this.sound = sound; this.vibration = vibration; this.difficulty = difficulty
        this.palette = palette; this.format = format; this.autosave = autosave
        this.animations = animations; this.particles = particles; this.glow = glow
        doc.write(
            mapOf(
                "sound" to JsonPrimitive(sound), "vibration" to JsonPrimitive(vibration),
                "difficulty" to JsonPrimitive(difficulty.id), "palette" to JsonPrimitive(palette),
                "format" to JsonPrimitive(format.extension), "autosave" to JsonPrimitive(autosave),
                "animations" to JsonPrimitive(animations), "particles" to JsonPrimitive(particles),
                "glow" to JsonPrimitive(glow),
            ),
        )
    }

    fun snapshot() = SavedSettings(sound, vibration, difficulty, palette)
    fun apply(s: SavedSettings) = update(sound = s.sound, vibration = s.vibration, difficulty = s.difficulty, palette = s.palette)
}

/** Progreso global del jugador (data/progress.json). */
class PlayerProgress(store: FileStore? = null) {
    private val doc = JsonDoc(store, "progress.json")

    var totalScore by mutableIntStateOf(0); private set
    var bestScore by mutableIntStateOf(0); private set
    var gamesWon by mutableIntStateOf(0); private set
    var gamesLost by mutableIntStateOf(0); private set
    var totalSeconds by mutableIntStateOf(0); private set
    var bestLevelClasico by mutableIntStateOf(0); private set
    var bestLevelReto by mutableIntStateOf(0); private set

    init {
        doc.read()?.let { m ->
            totalScore = m.int("totalScore"); bestScore = m.int("bestScore")
            gamesWon = m.int("gamesWon"); gamesLost = m.int("gamesLost")
            totalSeconds = m.int("totalSeconds")
            bestLevelClasico = m.int("bestLevelClasico"); bestLevelReto = m.int("bestLevelReto")
        }
    }

    fun bestLevel(m: GameMode) = if (m == GameMode.CLASICO) bestLevelClasico else bestLevelReto

    /** Siguiente nivel a jugar en un modo. */
    fun nextLevel(m: GameMode) = bestLevel(m) + 1

    /** Registra el resultado de una partida terminada. */
    fun recordResult(s: GameState) {
        totalSeconds += s.elapsedSeconds
        if (s.won) {
            gamesWon++
            totalScore += s.score
            if (s.score > bestScore) bestScore = s.score
            if (s.mode == GameMode.CLASICO) { if (s.level > bestLevelClasico) bestLevelClasico = s.level }
            else if (s.level > bestLevelReto) bestLevelReto = s.level
        } else gamesLost++
        doc.write(
            mapOf(
                "totalScore" to JsonPrimitive(totalScore), "bestScore" to JsonPrimitive(bestScore),
                "gamesWon" to JsonPrimitive(gamesWon), "gamesLost" to JsonPrimitive(gamesLost),
                "totalSeconds" to JsonPrimitive(totalSeconds),
                "bestLevelClasico" to JsonPrimitive(bestLevelClasico),
                "bestLevelReto" to JsonPrimitive(bestLevelReto),
            ),
        )
    }
}
