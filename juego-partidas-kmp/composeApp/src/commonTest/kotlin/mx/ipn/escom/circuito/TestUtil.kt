package mx.ipn.escom.circuito

import mx.ipn.escom.circuito.game.Difficulty
import mx.ipn.escom.circuito.game.GameMode
import mx.ipn.escom.circuito.game.generateLevel
import mx.ipn.escom.circuito.saves.SaveGame
import mx.ipn.escom.circuito.saves.SavedSettings

/** Partida de ejemplo con historial, nombre con caracteres especiales y etiquetas. */
fun sampleSave(name: String = "Nivel 1; \"Clásico\" <a&b>\nlínea2 \\ fin"): SaveGame {
    val g = generateLevel(GameMode.RETO, Difficulty.FACIL, 2, 12345)
    val s = g.state
    s.moves.addAll(listOf(0, 5, 5, 7))
    s.score = 1580
    s.elapsedSeconds = 73
    return SaveGame(
        name, 1_793_874_615_123L, s, listOf("practica", "fav;orita", "ñandú"),
        SavedSettings(sound = false, vibration = true, difficulty = Difficulty.DIFICIL, palette = "azul"),
    )
}
