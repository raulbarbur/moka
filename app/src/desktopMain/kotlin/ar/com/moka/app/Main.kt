package ar.com.moka.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Entrada de la app de escritorio: ./gradlew :app:run */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Moka") {
        App()
    }
}
