// =======================================================================
//  ProyectoMoka.kt - Carpeta de proyecto: paquetes en la app (Moka 2, P3)
// =======================================================================
//  P1 trajo `paquete`/`usar` y el ResolvedorPaquetes, pero la app pasaba
//  un proveedor null y todo `usar` daba SEM-062. P3 le da a la app lo
//  que la CLI ya tenia: una carpeta donde viven los .moka de los
//  paquetes. El usuario la elige con el menu Proyecto y la eleccion
//  queda guardada en los ajustes (clave "proyecto.carpeta").
//
//  Convencion de busqueda (la misma de la CLI): el paquete "util" vive
//  en <carpeta>/util.moka; con puntos, "util.texto" es la subcarpeta:
//  <carpeta>/util/texto.moka.
//
//  Piezas:
//   - EstadoProyecto: la carpeta actual como estado de Compose (el menu
//     la muestra) + el proveedor que le pasa el controlador al pipeline.
//   - leerTextoDeCarpeta: lectura de la plataforma (ruta en desktop,
//     URI de SAF en Android).
//   - SelectorCarpeta / recordarSelectorCarpeta: el dialogo de cada
//     plataforma, suspendido como el resto de los selectores de archivo.
// =======================================================================
package ar.com.moka.app.archivos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ar.com.moka.core.paquetes.ProveedorPaquetes

class EstadoProyecto {
    // Ruta de la carpeta elegida (desktop) o URI del arbol (Android).
    // null: sin proyecto; todo `usar` da SEM-062 como antes de P3.
    var carpeta by mutableStateOf<String?>(null)
        private set

    fun fijar(ruta: String?) {
        carpeta = ruta
    }

    // El pipeline llama al proveedor en cada analisis que tenga `usar`,
    // asi que lee la carpeta VIGENTE: cambiar de carpeta no pide
    // reconstruir ni el proveedor ni el controlador.
    val proveedor = ProveedorPaquetes { paquete ->
        carpeta?.let { leerTextoDeCarpeta(it, paquete.replace('.', '/') + ".moka") }
    }
}

// Lee un archivo de texto UTF-8 dentro de la carpeta proyecto. null si
// no existe o no se puede leer: el resolvedor lo reporta como SEM-062.
expect fun leerTextoDeCarpeta(carpeta: String, relativo: String): String?

// Dialogo para elegir la carpeta. Cancelar devuelve null; los errores se
// propagan a la UI como en el gestor de archivos.
interface SelectorCarpeta {
    suspend fun elegir(): String?
}

@Composable
expect fun recordarSelectorCarpeta(): SelectorCarpeta
