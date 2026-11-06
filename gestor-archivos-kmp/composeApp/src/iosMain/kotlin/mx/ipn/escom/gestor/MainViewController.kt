package mx.ipn.escom.gestor

import androidx.compose.ui.window.ComposeUIViewController
import mx.ipn.escom.gestor.ui.App
import platform.UIKit.UIViewController

private val platformServices by lazy { IosPlatform() }

/** Punto de entrada que llama ContentView.swift: aloja toda la UI compartida en un UIViewController. */
fun MainViewController(): UIViewController = ComposeUIViewController { App(platformServices) }
