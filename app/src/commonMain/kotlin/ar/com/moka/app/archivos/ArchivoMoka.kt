// ArchivoMoka.kt - Contrato comun para archivos de texto UTF-8.
package ar.com.moka.app.archivos

import androidx.compose.runtime.Composable

// referencia es una ruta en desktop o una URI de SAF en Android.
// Nunca se interpreta desde commonMain. null significa documento nuevo.
data class ArchivoMoka(
    val nombre: String,
    val contenido: String,
    val referencia: String? = null
)

interface GestorArchivosMoka {
    // Cancelar un dialogo devuelve null. Los errores se propagan a la UI.
    suspend fun abrir(): ArchivoMoka?
    suspend fun guardar(contenido: String, anterior: ArchivoMoka?, como: Boolean): ArchivoMoka?
}

@Composable
expect fun recordarGestorArchivosMoka(): GestorArchivosMoka

fun nombreMoka(nombre: String): String {
    val limpio = nombre.trim().ifEmpty { "sin-titulo.moka" }
    return if (limpio.endsWith(".moka", ignoreCase = true)) limpio else "$limpio.moka"
}
