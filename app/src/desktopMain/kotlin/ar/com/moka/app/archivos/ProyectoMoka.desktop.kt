// ProyectoMoka.desktop.kt - Carpeta proyecto en desktop: rutas comunes
// y un JFileChooser de solo directorios (AWT FileDialog no elige
// carpetas de forma confiable fuera de macOS).
package ar.com.moka.app.archivos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

actual fun leerTextoDeCarpeta(carpeta: String, relativo: String): String? {
    val archivo = File(carpeta, relativo)
    if (!archivo.isFile) return null
    return try {
        archivo.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    } catch (_: Exception) {
        null
    }
}

@Composable
actual fun recordarSelectorCarpeta(): SelectorCarpeta = remember {
    object : SelectorCarpeta {
        override suspend fun elegir(): String? = suspendCancellableCoroutine { continuacion ->
            // AWT/Swing exige su propio hilo (EDT), igual que los
            // dialogos de archivo de ArchivosMoka.desktop.
            SwingUtilities.invokeLater {
                if (!continuacion.isActive) return@invokeLater
                val selector = JFileChooser().apply {
                    dialogTitle = "Elegir la carpeta del proyecto"
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isAcceptAllFileFilterUsed = false
                }
                val resultado = selector.showOpenDialog(null)
                val elegida = if (resultado == JFileChooser.APPROVE_OPTION) {
                    selector.selectedFile?.absolutePath
                } else {
                    null
                }
                if (continuacion.isActive) continuacion.resume(elegida)
            }
        }
    }
}
