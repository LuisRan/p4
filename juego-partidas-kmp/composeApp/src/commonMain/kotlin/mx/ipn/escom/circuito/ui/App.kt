package mx.ipn.escom.circuito.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import mx.ipn.escom.circuito.core.AppSettings
import mx.ipn.escom.circuito.core.GameFeedback
import mx.ipn.escom.circuito.core.PlayerProgress
import mx.ipn.escom.circuito.core.nowMillis
import mx.ipn.escom.circuito.game.GameController
import mx.ipn.escom.circuito.game.GameMode
import mx.ipn.escom.circuito.platform.PlatformServices
import mx.ipn.escom.circuito.platform.PlatformBackHandler
import mx.ipn.escom.circuito.platform.createFileStore
import mx.ipn.escom.circuito.platform.rememberPlatformServices
import mx.ipn.escom.circuito.saves.SaveGame
import mx.ipn.escom.circuito.saves.SaveRepository

/** Pantallas de la app (navegación simple basada en pila). */
sealed interface Screen {
    data object Home : Screen
    data object Game : Screen
    data object Saves : Screen
    data object Settings : Screen
    class Raw(val title: String, val content: String) : Screen
}

/** Dependencias compartidas por todas las pantallas (inyección manual). */
class AppEnv(
    val repo: SaveRepository,
    val settings: AppSettings,
    val progress: PlayerProgress,
    val platform: PlatformServices,
    val controller: GameController,
)

/** Raíz de la app multiplataforma. */
@Composable
fun App() {
    val platform = rememberPlatformServices()
    val env = remember {
        val store = createFileStore()
        val repo = SaveRepository(store)
        val settings = AppSettings(store)
        val progress = PlayerProgress(store)
        val feedback = GameFeedback(settings, platform)
        val controller = GameController(settings, progress, feedback) { c ->
            // Autoguardado tras cada movimiento (si el usuario lo permite).
            runCatching {
                if (c.state.finished) repo.clearAuto()
                else if (settings.autosave) repo.writeAuto(c.toSave("autosave", nowMillis()))
            }
        }
        AppEnv(repo, settings, progress, platform, controller)
    }

    CircuitoTheme(env.settings.palette, env.settings.appearance) {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
        var gameKey by remember { mutableIntStateOf(0) } // cambia con cada partida nueva/cargada

        fun go(s: Screen) { stack.add(s) }
        fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
        fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
        fun startNew(mode: GameMode, level: Int) {
            env.controller.startNew(mode, level)
            gameKey++
            if (env.settings.autosave) runCatching { env.repo.writeAuto(env.controller.toSave("autosave", nowMillis())) }
            go(Screen.Game)
        }
        fun openSave(save: SaveGame) {
            env.controller.restore(save)
            env.settings.apply(save.settings)
            gameKey++
            // Si veníamos de la pantalla de partidas, volvemos al juego encima del inicio.
            home(); go(Screen.Game)
        }

        PlatformBackHandler(enabled = stack.size > 1) { back() }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Surface(Modifier.fillMaxSize().padding(pad), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    when (val s = stack.last()) {
                        Screen.Home -> HomeScreen(
                            env, notify,
                            onPlay = ::startNew,
                            onContinue = {
                                val auto = env.repo.readAuto()
                                if (auto == null || auto.state.finished) notify("No hay partida para continuar.")
                                else openSave(auto)
                            },
                            onSaves = { go(Screen.Saves) },
                            onSettings = { go(Screen.Settings) },
                        )
                        Screen.Game -> GameScreen(
                            env, gameKey, notify,
                            onExit = ::home,
                            onNext = { mode, level -> startNew(mode, level).also { stack.removeAt(stack.lastIndex - 1) } },
                            onSaves = { go(Screen.Saves) },
                            onSettings = { go(Screen.Settings) },
                            onRaw = { t, c -> go(Screen.Raw(t, c)) },
                        )
                        Screen.Saves -> SavesScreen(
                            env, notify, onBack = ::back, onOpen = ::openSave,
                            onRaw = { t, c -> go(Screen.Raw(t, c)) },
                        )
                        Screen.Settings -> SettingsScreen(env, onBack = ::back)
                        is Screen.Raw -> RawViewerScreen(s.title, s.content, onBack = ::back)
                    }
                }
            }
        }
    }
}
