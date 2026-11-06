import UIKit
import SwiftUI
import ComposeApp

/// Aloja el UIViewController de Compose Multiplatform (MainViewController.kt) dentro de SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea() // Compose gestiona el área segura por su cuenta
    }
}
