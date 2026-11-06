package mx.ipn.escom.circuito

import androidx.compose.ui.window.ComposeUIViewController
import mx.ipn.escom.circuito.ui.App

/** Punto de entrada que llama el proyecto Xcode (iosApp/ContentView.swift). */
fun MainViewController() = ComposeUIViewController { App() }
