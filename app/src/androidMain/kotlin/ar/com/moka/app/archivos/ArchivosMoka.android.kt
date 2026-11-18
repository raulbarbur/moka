// ArchivosMoka.android.kt - SAF: no pide permisos generales de almacenamiento.
package ar.com.moka.app.archivos

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private class SeleccionPendiente {
    var continuacion: CancellableContinuation<Uri?>? = null
    fun completar(uri: Uri?) {
        val c = continuacion
        continuacion = null
        if (c?.isActive == true) c.resume(uri)
    }
}

@Composable
actual fun recordarGestorArchivosMoka(): GestorArchivosMoka {
    val contexto = LocalContext.current.applicationContext
    val abrirPendiente = remember { SeleccionPendiente() }
    val guardarPendiente = remember { SeleccionPendiente() }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        abrirPendiente.completar(it)
    }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        guardarPendiente.completar(it)
    }
    DisposableEffect(Unit) {
        onDispose {
            abrirPendiente.completar(null)
            guardarPendiente.completar(null)
        }
    }
    return remember(contexto, abrir, guardar) {
        object : GestorArchivosMoka {
            private val resolver = contexto.contentResolver

            private fun nombre(uri: Uri): String = resolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "programa.moka"

            override suspend fun abrir(): ArchivoMoka? {
                // Algunos proveedores clasifican .moka como binario y otros
                // como texto; el selector deja elegir ambos (o ver todos).
                val uri = suspendCancellableCoroutine<Uri?> { c ->
                    abrirPendiente.continuacion = c
                    c.invokeOnCancellation { abrirPendiente.continuacion = null }
                    abrir.launch(arrayOf("*/*"))
                } ?: return null
                return withContext(Dispatchers.IO) {
                    val nombre = nombre(uri)
                    // Programas .moka y tambien .txt.
                    if (!nombre.endsWith(".moka", ignoreCase = true) && !nombre.endsWith(".txt", ignoreCase = true)) {
                        throw IOException("Selecciona un archivo .moka o .txt.")
                    }
                    val contenido = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IOException("No se pudo leer el archivo.")
                    // Un proveedor puede conceder solo lectura. Abrir sigue
                    // funcionando; si luego falla Guardar, la UI ofrece
                    // Guardar como para elegir otro documento.
                    runCatching {
                        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ArchivoMoka(nombre, contenido.removePrefix("\uFEFF"), uri.toString())
                }
            }

            override suspend fun guardar(contenido: String, anterior: ArchivoMoka?, como: Boolean): ArchivoMoka? {
                val uri = if (!como && anterior?.referencia != null) Uri.parse(anterior.referencia) else {
                    suspendCancellableCoroutine<Uri?> { c ->
                        guardarPendiente.continuacion = c
                        c.invokeOnCancellation { guardarPendiente.continuacion = null }
                        guardar.launch(nombreMoka(anterior?.nombre.orEmpty()))
                    } ?: return null
                }
                return withContext(Dispatchers.IO) {
                    // wt trunca: guardar un texto mas corto no deja restos.
                    // SAF no ofrece un reemplazo atomico universal. Los
                    // errores NO se ocultan ni marcan el editor como guardado.
                    resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(contenido) }
                        ?: throw IOException("No se pudo escribir. Prueba Guardar como.")
                    ArchivoMoka(nombre(uri), contenido, uri.toString())
                }
            }
        }
    }
}
