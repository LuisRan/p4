@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package mx.ipn.escom.gestor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import mx.ipn.escom.gestor.data.PlatformServices

/**
 * Raíz de la app (común a Android e iOS). Recibe los servicios de plataforma ya construidos.
 * Diseño responsivo: con ancho >= 720 dp se usa NavigationRail + cuadrícula; en estrecho,
 * NavigationBar inferior + lista. Esto cubre teléfonos, tablets y ambas orientaciones.
 */
@Composable
fun App(platform: PlatformServices) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val state = remember(platform) { AppState(platform, scope, snackbar) }
    LaunchedEffect(state) { state.start() }

    // Claro/oscuro según el sistema (se recompone solo cuando el usuario cambia el modo).
    GestorTheme(state.theme, state.appearance.isDark(isSystemInDarkTheme())) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!state.ready) {
                Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(32.dp)) }
            } else {
                Root(state)
            }
        }
    }
}

@Composable
private fun Root(state: AppState) {
    // Atrás del sistema: cierra el visor, o sube un nivel en el explorador.
    BackHandler(enabled = state.viewing != null) { state.viewing = null }
    BackHandler(enabled = state.viewing == null && state.section == Section.Explore && state.trail.isNotEmpty()) { state.up() }

    val viewing = state.viewing
    if (viewing != null) {
        ViewerScreen(state, viewing)
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        Spacer(Modifier.weight(1f))
                        Section.entries.forEach { s ->
                            NavigationRailItem(
                                selected = state.section == s, onClick = { state.section = s },
                                icon = { Text(s.glyph) }, label = { Text(s.label) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Scaffold(snackbarHost = { SnackbarHost(state.snackbar) }) { pad ->
                        SectionContent(state, wide = true, Modifier.padding(pad))
                    }
                }
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(state.snackbar) },
                    bottomBar = {
                        NavigationBar {
                            Section.entries.forEach { s ->
                                NavigationBarItem(
                                    selected = state.section == s, onClick = { state.section = s },
                                    icon = { Text(s.glyph) }, label = { Text(s.label) },
                                )
                            }
                        }
                    },
                ) { pad -> SectionContent(state, wide = false, Modifier.padding(pad)) }
            }
        }
    }
    AppDialogs(state)
}

@Composable
private fun SectionContent(state: AppState, wide: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        when (state.section) {
            Section.Explore -> ExplorerScreen(state, wide)
            Section.Recents -> LibraryScreen(state, recents = true)
            Section.Favorites -> LibraryScreen(state, recents = false)
            Section.Settings -> SettingsScreen(state)
        }
    }
}
