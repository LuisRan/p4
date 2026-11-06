package mx.ipn.escom.gestor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.ipn.escom.gestor.ui.App

/** Actividad única: crea los servicios de plataforma y delega toda la UI al módulo compartido. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debe crearse aquí: registra los ActivityResult launchers antes de STARTED.
        val platform = AndroidPlatform(this, "$packageName.fileprovider")
        setContent { App(platform) }
    }
}
