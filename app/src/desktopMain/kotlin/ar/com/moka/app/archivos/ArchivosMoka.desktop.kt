// ArchivosMoka.desktop.kt - Dialogos AWT y lectura/escritura fuera de la UI.
package ar.com.moka.app.archivos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
actual fun recordarGestorArchivosMoka(): GestorArchivosMoka = remember { ArchivosDesktop() }

private class ArchivosDesktop : GestorArchivosMoka {
    override suspend fun abrir(): ArchivoMoka? {
        val archivo = elegir(FileDialog.LOAD, "Abrir programa Moka", null) ?: return null
        return withContext(Dispatchers.IO) {
            ArchivoMoka(archivo.name, archivo.readText(Charsets.UTF_8).removePrefix("\uFEFF"), archivo.absolutePath)
        }
    }

    override suspend fun guardar(contenido: String, anterior: ArchivoMoka?, como: Boolean): ArchivoMoka? {
        val existente = anterior?.referencia?.let(::File)
        val destino = if (!como && existente != null) existente else {
            elegir(FileDialog.SAVE, "Guardar programa Moka", anterior) ?: return null
        }
        withContext(Dispatchers.IO) {
            // Escribimos primero un temporal en la misma carpeta. Un fallo
            // antes de moverlo no deja el programa anterior truncado.
            val ruta = destino.toPath().toAbsolutePath()
            val temporal = Files.createTempFile(ruta.parent, ".moka-", ".tmp")
            try {
                Files.write(temporal, contenido.toByteArray(Charsets.UTF_8))
                try {
                    Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporal)
            }
        }
        return ArchivoMoka(destino.name, contenido, destino.absolutePath)
    }

    private suspend fun elegir(modo: Int, titulo: String, anterior: ArchivoMoka?): File? =
        suspendCancellableCoroutine { continuacion ->
            // AWT exige su propio hilo (EDT); no usamos el hilo de Compose
            // ni bloqueamos un hilo de corrutinas esperando el dialogo.
            SwingUtilities.invokeLater {
                if (!continuacion.isActive) return@invokeLater
                val dialogo = FileDialog(null as Frame?, titulo, modo)
                try {
                    dialogo.file = if (modo == FileDialog.SAVE) nombreMoka(anterior?.nombre.orEmpty()) else "*.moka"
                    anterior?.referencia?.let { dialogo.directory = File(it).parent }
                    // Abrir acepta .moka y .txt; Guardar sigue forzando .moka (nombreMoka).
                    dialogo.setFilenameFilter { _, nombre ->
                        nombre.endsWith(".moka", ignoreCase = true) || nombre.endsWith(".txt", ignoreCase = true)
                    }
                    continuacion.invokeOnCancellation { SwingUtilities.invokeLater { dialogo.dispose() } }
                    dialogo.isVisible = true
                    val elegido = dialogo.file?.let { nombre ->
                        File(dialogo.directory, if (modo == FileDialog.SAVE) nombreMoka(nombre) else nombre)
                    }
                    // Tambien cubre la extension agregada por nosotros:
                    // el dialogo nativo pudo preguntar por otro nombre.
                    val confirmado = if (modo == FileDialog.SAVE && elegido?.exists() == true) {
                        JOptionPane.showConfirmDialog(
                            null, "El archivo ${elegido.name} ya existe. Reemplazarlo?", "Guardar Moka",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                        ) == JOptionPane.YES_OPTION
                    } else true
                    if (continuacion.isActive) continuacion.resume(if (confirmado) elegido else null)
                } catch (e: Exception) {
                    if (continuacion.isActive) continuacion.resumeWithException(e)
                } finally {
                    dialogo.dispose()
                }
            }
        }
}
