// ArchivoTaller.kt - Acciones de archivo, ejemplos y atajos del Taller.
// La logica de compilacion sigue en MokaControlador; los destinos de
// archivo quedan aqui, fuera de EstadoWorkbench y del nucleo.
package ar.com.moka.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import ar.com.moka.app.archivos.ArchivoMoka
import ar.com.moka.app.archivos.EjemplosMoka
import ar.com.moka.app.archivos.recordarGestorArchivosMoka
import ar.com.moka.app.estado.MokaControlador
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ArchivoTaller(controlador: MokaControlador, contenido: @Composable () -> Unit) {
    val estado by controlador.estado.collectAsState()
    // Las operaciones pueden suspenderse mientras el usuario escribe.
    // Una lectura de esta propiedad siempre obtiene la fuente mas nueva.
    val fuenteActual by rememberUpdatedState(estado.fuente)
    val gestor = recordarGestorArchivosMoka()
    val ambito = rememberCoroutineScope()
    var archivo by remember { mutableStateOf<ArchivoMoka?>(null) }
    var ultimaGuardada by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }
    var menuEjemplos by remember { mutableStateOf(false) }
    var pendiente by remember { mutableStateOf<ArchivoMoka?>(null) }
    val modificado = estado.fuente != ultimaGuardada

    fun cargar(nuevo: ArchivoMoka) {
        controlador.detener()
        controlador.seleccionar(null)
        controlador.cambiarFuente(nuevo.contenido)
        archivo = nuevo
        // Un ejemplo es una COPIA nueva, no un archivo ya guardado.
        ultimaGuardada = if (nuevo.referencia != null) nuevo.contenido else ""
        mensaje = null
        pendiente = null
    }

    fun ofrecerCarga(nuevo: ArchivoMoka) {
        // Se comprueba DESPUES del selector: tambien protege lo escrito
        // mientras el selector de archivos estaba abierto.
        if (fuenteActual != ultimaGuardada) pendiente = nuevo else cargar(nuevo)
    }

    fun abrir() {
        if (ocupado || pendiente != null) return
        ocupado = true
        mensaje = null
        ambito.launch {
            try {
                gestor.abrir()?.let(::ofrecerCarga)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                esError = true
                mensaje = "No se pudo abrir: ${e.message ?: "error de lectura"}"
            } finally {
                ocupado = false
            }
        }
    }

    fun guardar(como: Boolean) {
        if (ocupado || pendiente != null) return
        // El documento guardado es esta foto, no el texto que pueda haber
        // cuando termina la escritura. Si se sigue escribiendo, el * queda.
        val fuente = fuenteActual
        val anterior = archivo
        ocupado = true
        mensaje = null
        ambito.launch {
            try {
                val guardado = gestor.guardar(fuente, anterior, como)
                if (guardado != null) {
                    archivo = guardado
                    ultimaGuardada = fuente
                    esError = false
                    mensaje = "Guardado: ${guardado.nombre}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                esError = true
                mensaje = "No se pudo guardar: ${e.message ?: "error de escritura"}. Prueba Guardar como."
            } finally {
                ocupado = false
            }
        }
    }

    Column(Modifier.fillMaxSize().onPreviewKeyEvent { evento ->
        if (evento.type != KeyEventType.KeyDown || !evento.isCtrlPressed) false else {
            when (evento.key) {
                Key.S -> { guardar(como = false); true }
                Key.Enter -> {
                    if (!ocupado && pendiente == null) controlador.ejecutar()
                    true
                }
                else -> false
            }
        }
    }) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = ::abrir, enabled = !ocupado) { Text("Abrir") }
            TextButton(onClick = { guardar(false) }, enabled = !ocupado) { Text("Guardar") }
            TextButton(onClick = { guardar(true) }, enabled = !ocupado) { Text("Guardar como") }
            Box {
                TextButton(onClick = { menuEjemplos = true }, enabled = !ocupado) { Text("Ejemplos") }
                DropdownMenu(expanded = menuEjemplos, onDismissRequest = { menuEjemplos = false }) {
                    for (ejemplo in EjemplosMoka.todos) {
                        DropdownMenuItem(
                            text = { Text(ejemplo.nombre) },
                            onClick = {
                                menuEjemplos = false
                                ofrecerCarga(ejemplo)
                            }
                        )
                    }
                }
            }
            Text(
                (archivo?.nombre ?: "sin-titulo.moka") + (if (modificado) " *" else "") +
                        (if (ocupado) " (esperando archivo...)" else ""),
                style = MaterialTheme.typography.labelLarge
            )
        }
        mensaje?.let { texto ->
            Text(
                texto,
                color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f)) { contenido() }
    }

    pendiente?.let { nuevo ->
        AlertDialog(
            onDismissRequest = { pendiente = null },
            title = { Text("Hay cambios sin guardar") },
            text = { Text("Reemplazar el codigo actual por ${nuevo.nombre}? Si quieres conservarlo, cancela y pulsa Guardar primero.") },
            confirmButton = { TextButton(onClick = { cargar(nuevo) }) { Text("Descartar y abrir") } },
            dismissButton = { TextButton(onClick = { pendiente = null }) { Text("Cancelar") } }
        )
    }
}
