// ProyectoMoka.android.kt - Carpeta proyecto en Android via SAF: el
// usuario elige un arbol con OpenDocumentTree, la app conserva el
// permiso de lectura y busca cada paquete bajando por los nombres de
// los documentos hijos (DocumentsContract, sin dependencias nuevas).
package ar.com.moka.app.archivos

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// Continuacion del selector en curso (una a la vez: el menu no abre
// dos dialogos).
private class SeleccionCarpetaPendiente {
    var continuacion: CancellableContinuation<String?>? = null
    fun completar(ruta: String?) {
        val c = continuacion
        continuacion = null
        if (c?.isActive == true) c.resume(ruta)
    }
}

// El contexto de aplicacion lo fija la entrada (MainActivity) al crear
// el proveedor: leerTextoDeCarpeta es una funcion suelta (la llama el
// pipeline fuera de toda composicion) y necesita llegar al resolver.
private var contextoApp: Context? = null

fun registrarContextoProyecto(contexto: Context) {
    contextoApp = contexto.applicationContext
}

actual fun leerTextoDeCarpeta(carpeta: String, relativo: String): String? {
    val resolver = contextoApp?.contentResolver ?: return null
    return try {
        val uri = buscarDocumento(resolver, Uri.parse(carpeta), relativo.split('/')) ?: return null
        resolver.openInputStream(uri)?.use { entrada ->
            entrada.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
    } catch (_: Exception) {
        null
    }
}

// Baja por los tramos del nombre relativo ("util/texto.moka" -> util,
// texto.moka) comparando el nombre visible de cada documento hijo.
private fun buscarDocumento(resolver: ContentResolver, arbol: Uri, tramos: List<String>): Uri? {
    var actual = arbol
    for (tramo in tramos) {
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(
            actual, DocumentsContract.getDocumentId(actual)
        )
        var siguiente: Uri? = null
        resolver.query(
            hijos,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == tramo) {
                    siguiente = DocumentsContract.buildDocumentUriUsingTree(actual, cursor.getString(0))
                    break
                }
            }
        }
        actual = siguiente ?: return null
    }
    return actual
}

@Composable
actual fun recordarSelectorCarpeta(): SelectorCarpeta {
    val contexto = LocalContext.current.applicationContext
    val pendiente = remember { SeleccionCarpetaPendiente() }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Que el permiso de lectura sobreviva al reinicio de la app:
            // la URI guardada en ajustes sirve en la proxima sesion.
            try {
                contexto.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Algunos proveedores no ofrecen permiso persistente: la
                // carpeta igual sirve hasta que se cierre la app.
            }
        }
        pendiente.completar(uri?.toString())
    }
    DisposableEffect(Unit) {
        onDispose { pendiente.completar(null) }
    }
    return remember(lanzador) {
        object : SelectorCarpeta {
            override suspend fun elegir(): String? = suspendCancellableCoroutine { continuacion ->
                pendiente.continuacion = continuacion
                lanzador.launch(null)
            }
        }
    }
}
