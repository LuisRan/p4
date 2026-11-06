import UIKit
import SwiftUI
import ComposeApp

// Envuelve el UIViewController de Compose Multiplatform (definido en commonMain/iosMain)
// dentro de SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea() // Compose maneja los insets por sí mismo
    }
}
