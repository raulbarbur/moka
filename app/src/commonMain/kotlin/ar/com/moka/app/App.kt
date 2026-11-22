// =======================================================================
//  App.kt - Raiz de la UI y del tema
// =======================================================================
//  Recibe el controlador y la sesion de archivo desde la entrada de cada
//  plataforma. Eso permite que desktop/Android pregunten si el cierre es
//  seguro ANTES de destruir la ventana/Activity.
//
//  Aqui tambien vive el tema claro/oscuro. El estado usa
//  rememberSaveable: si Android recrea la Activity al girar el telefono,
//  la eleccion sobrevive. El selector se dibuja en la barra de archivo
//  (ArchivoTaller), pero el color se aplica aca, en el MaterialTheme que
//  envuelve TODO: asi ningun panel puede quedar con el tema viejo.
//
//  App sigue siendo fina: libera el controlador al salir de composicion,
//  aplica el tema y monta el Taller. La logica del pipeline vive en
//  estado/ y la del documento en archivos/SesionArchivoMoka.
// =======================================================================
package ar.com.moka.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ar.com.moka.app.ajustes.recordarAjustesMoka
import ar.com.moka.app.archivos.EstadoProyecto
import ar.com.moka.app.archivos.SesionArchivoMoka
import ar.com.moka.app.estado.MokaControlador
import ar.com.moka.app.ui.LocalColoresEditor
import ar.com.moka.app.ui.ModoTaller
import ar.com.moka.app.ui.PANEL_EDITOR
import ar.com.moka.app.ui.Taller
import ar.com.moka.app.ui.recordarEstadoPaneles
import ar.com.moka.app.ui.coloresEditorMoka
import ar.com.moka.app.ui.esquemaClaroMoka
import ar.com.moka.app.ui.esquemaOscuroMoka

// Las tres opciones del selector de tema. SISTEMA sigue al telefono o al
// escritorio; CLARO y OSCURO fuerzan la eleccion del usuario.
enum class TemaMoka { SISTEMA, CLARO, OSCURO }

@Composable
fun App(
    controlador: MokaControlador,
    sesionArchivo: SesionArchivoMoka,
    // P3: carpeta proyecto compartida entre el pipeline (su proveedor ya
    // vive en el controlador) y el menu Proyecto.
    proyecto: EstadoProyecto,
    alSalir: () -> Unit
) {
    DisposableEffect(controlador) {
        onDispose { controlador.liberar() }
    }

    // Tema y modo se leen del almacenamiento de la plataforma y se
    // guardan a cada cambio: sobreviven al cierre de la app. Los tamanos
    // de letra y la visibilidad de cada panel tambien (5C): los maneja
    // EstadoPaneles sobre el mismo almacenamiento.
    val ajustes = recordarAjustesMoka()
    var tema by rememberSaveable {
        mutableStateOf(ajustes.leer("tema")?.let { runCatching { TemaMoka.valueOf(it) }.getOrNull() } ?: TemaMoka.SISTEMA)
    }
    var modo by rememberSaveable {
        mutableStateOf(ajustes.leer("modo")?.let { runCatching { ModoTaller.valueOf(it) }.getOrNull() } ?: ModoTaller.COMPLETO)
    }
    val paneles = recordarEstadoPaneles()

    // P3: la carpeta proyecto elegida la ultima vez (ajustes); una
    // lectura en blanco cuenta como "sin carpeta".
    LaunchedEffect(Unit) {
        ajustes.leer("proyecto.carpeta")?.takeIf { it.isNotBlank() }?.let(proyecto::fijar)
    }
    val oscuro = when (tema) {
        TemaMoka.SISTEMA -> isSystemInDarkTheme()
        TemaMoka.CLARO -> false
        TemaMoka.OSCURO -> true
    }

    // Los esquemas y los colores del editor viven en ui/Tema.kt.
    MaterialTheme(colorScheme = if (oscuro) esquemaOscuroMoka() else esquemaClaroMoka()) {
        CompositionLocalProvider(LocalColoresEditor provides coloresEditorMoka(oscuro)) {
            // safeDrawing: en Android edge-to-edge la barra de estado y la
            // de navegacion no tapan la UI; en desktop no cambia nada.
            Surface(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                color = MaterialTheme.colorScheme.background
            ) {
                Taller(
                    controlador = controlador,
                    sesionArchivo = sesionArchivo,
                    alSalir = alSalir,
                    tema = tema,
                    alCambiarTema = {
                        tema = it
                        ajustes.escribir("tema", it.name)
                    },
                    modo = modo,
                    alCambiarModo = {
                        modo = it
                        ajustes.escribir("modo", it.name)
                    },
                    tamanioFuente = paneles.tamanio(PANEL_EDITOR),
                    alCambiarTamanio = { paneles.fijarTamanio(PANEL_EDITOR, it) },
                    paneles = paneles,
                    proyecto = proyecto
                )
            }
        }
    }
}
