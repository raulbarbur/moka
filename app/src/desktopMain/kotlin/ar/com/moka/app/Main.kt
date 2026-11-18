package ar.com.moka.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ar.com.moka.app.archivos.SesionArchivoMoka
import ar.com.moka.app.estado.MokaControlador

/** Entrada de la app de escritorio: ./gradlew :app:run */
fun main() = application {
    val controlador = remember { MokaControlador() }
    val sesionArchivo = remember { SesionArchivoMoka() }

    Window(
        onCloseRequest = {
            // Si devuelve false, ya dejo marcada la confirmacion visible.
            if (sesionArchivo.pedirCierre(controlador.estado.value.fuente)) exitApplication()
        },
        title = "Moka"
    ) {
        App(
            controlador = controlador,
            sesionArchivo = sesionArchivo,
            alSalir = { exitApplication() }
        )
    }
}
