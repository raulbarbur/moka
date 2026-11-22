// ArchivoTaller.kt - Acciones de archivo, ejemplos, atajos y cierre seguro.
// La logica de compilacion sigue en MokaControlador; el documento abierto
// vive en SesionArchivoMoka para que desktop/Android puedan pedir cierre.
package ar.com.moka.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import ar.com.moka.app.TemaMoka
import ar.com.moka.app.ajustes.recordarAjustesMoka
import ar.com.moka.app.archivos.EstadoProyecto
import ar.com.moka.app.archivos.recordarSelectorCarpeta
import ar.com.moka.app.archivos.ArchivoMoka
import ar.com.moka.app.archivos.EjemplosMoka
import ar.com.moka.app.archivos.SesionArchivoMoka
import ar.com.moka.app.archivos.recordarGestorArchivosMoka
import ar.com.moka.app.estado.MokaControlador
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ArchivoTaller(
    controlador: MokaControlador,
    sesion: SesionArchivoMoka,
    alSalir: () -> Unit,
    tema: TemaMoka,
    alCambiarTema: (TemaMoka) -> Unit,
    modo: ModoTaller,
    alCambiarModo: (ModoTaller) -> Unit,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    // 5C: tamanos y visibilidad por panel (menu Ver -> Paneles).
    paneles: EstadoPaneles,
    // P3: carpeta proyecto para los `usar` (menu Proyecto).
    proyecto: EstadoProyecto,
    contenido: @Composable () -> Unit
) {
    val estado by controlador.estado.collectAsState()
    // Las operaciones pueden suspenderse mientras el usuario escribe.
    // Una lectura de esta propiedad siempre obtiene la fuente mas nueva.
    val fuenteActual by rememberUpdatedState(estado.fuente)
    val gestor = recordarGestorArchivosMoka()
    val ambito = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }
    val ajustes = recordarAjustesMoka()
    var acercaDe by remember { mutableStateOf(false) }
    // P3: selector de la carpeta proyecto (dialogo de cada plataforma).
    val selectorCarpeta = recordarSelectorCarpeta()
    // Tutoriales: se abren desde Ayuda y reemplazan al taller. El indice
    // de la leccion (-1 = lista) y si estan abiertos sobreviven a rotar.
    var tutorialesAbiertos by rememberSaveable { mutableStateOf(false) }
    var leccionActual by rememberSaveable { mutableIntStateOf(-1) }
    var vistas by remember {
        mutableStateOf(ajustes.leer("tutoriales_vistos").orEmpty().split(',').filter { it.isNotBlank() }.toSet())
    }
    val busqueda = LocalBusquedaEditor.current
    val opciones = LocalOpcionesEditor.current
    var pendiente by remember { mutableStateOf<ArchivoMoka?>(null) }
    val modificado = sesion.hayCambios(estado.fuente)

    fun cargar(nuevo: ArchivoMoka) {
        controlador.detener()
        controlador.seleccionar(null)
        controlador.cambiarFuente(nuevo.contenido)
        sesion.cargar(nuevo)
        mensaje = null
        pendiente = null
        tutorialesAbiertos = false
    }

    fun ofrecerCarga(nuevo: ArchivoMoka) {
        // Se comprueba DESPUES del selector: tambien protege lo escrito
        // mientras el selector de archivos estaba abierto.
        if (sesion.hayCambios(fuenteActual)) pendiente = nuevo else cargar(nuevo)
    }

    fun abrir() {
        if (sesion.ocupado || pendiente != null) return
        sesion.comenzarOperacion()
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
                sesion.terminarOperacion()
            }
        }
    }

    fun guardar(como: Boolean) {
        if (sesion.ocupado || pendiente != null) return
        // El documento guardado es esta foto, no el texto que pueda haber
        // cuando termina la escritura. Si se sigue escribiendo, el * queda.
        val fuente = fuenteActual
        val anterior = sesion.archivo
        sesion.comenzarOperacion()
        mensaje = null
        ambito.launch {
            try {
                val guardado = gestor.guardar(fuente, anterior, como)
                if (guardado != null) {
                    sesion.marcarGuardado(guardado, fuente)
                    esError = false
                    mensaje = "Guardado: ${guardado.nombre}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                esError = true
                mensaje = "No se pudo guardar: ${e.message ?: "error de escritura"}. Prueba Guardar como."
            } finally {
                sesion.terminarOperacion()
            }
        }
    }

    // Lote B: guardar y, solo si se guardo de verdad, salir. Si el
    // documento no tiene destino todavia, se abre "Guardar como"; si el
    // usuario cancela ese selector, NO se sale: vuelve a la app.
    fun guardarYSalir() {
        if (sesion.ocupado) return
        val fuente = fuenteActual
        val anterior = sesion.archivo
        sesion.comenzarOperacion()
        mensaje = null
        ambito.launch {
            try {
                val guardado = gestor.guardar(fuente, anterior, como = anterior?.referencia == null)
                if (guardado != null) {
                    sesion.marcarGuardado(guardado, fuente)
                    sesion.confirmarCierre()
                    alSalir()
                } else {
                    // Selector cancelado: se desiste de cerrar.
                    sesion.cancelarCierre()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                esError = true
                mensaje = "No se pudo guardar: ${e.message ?: "error de escritura"}. No se cerro."
                sesion.cancelarCierre()
            } finally {
                sesion.terminarOperacion()
            }
        }
    }

    // P3: elegir/olvidar la carpeta del proyecto. Elegirla re-analiza
    // el fuente actual: los `usar` pueden empezar a resolver (o dejar
    // de resolver) sin tocar el texto.
    fun elegirCarpetaProyecto() {
        if (sesion.ocupado) return
        ambito.launch {
            try {
                val ruta = selectorCarpeta.elegir() ?: return@launch
                proyecto.fijar(ruta)
                ajustes.escribir("proyecto.carpeta", ruta)
                controlador.reanalizar()
                esError = false
                mensaje = "Carpeta del proyecto: $ruta"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                esError = true
                mensaje = "No se pudo elegir la carpeta: ${e.message ?: "error"}"
            }
        }
    }

    fun olvidarCarpetaProyecto() {
        proyecto.fijar(null)
        ajustes.escribir("proyecto.carpeta", "")
        controlador.reanalizar()
        esError = false
        mensaje = "Sin carpeta de proyecto: los 'usar' ya no cargan paquetes."
    }

    Column(Modifier.fillMaxSize().onPreviewKeyEvent { evento ->
        if (evento.type != KeyEventType.KeyDown || !evento.isCtrlPressed) false else {
            when (evento.key) {
                Key.S -> { guardar(como = false); true }
                Key.F -> { busqueda.activa = !busqueda.activa; true }
                Key.Enter -> {
                    if (!sesion.ocupado && pendiente == null) controlador.ejecutar()
                    true
                }
                else -> false
            }
        }
    }) {
        // Los menus agrupan todo lo que antes eran botones sueltos. En
        // pantalla angosta (telefono en vertical) el nombre del archivo
        // baja a una segunda linea; en ancha queda al lado de los menus.
        val grupos = gruposDeMenu(
            ocupado = sesion.ocupado,
            alAbrir = ::abrir,
            alGuardar = { guardar(false) },
            alGuardarComo = { guardar(true) },
            alGuardarYSalir = ::guardarYSalir,
            alElegirEjemplo = ::ofrecerCarga,
            alDeshacer = controlador::deshacer,
            alRehacer = controlador::rehacer,
            busquedaActiva = busqueda.activa,
            alAlternarBusqueda = { busqueda.activa = !busqueda.activa },
            autocompletado = opciones.autocompletado,
            alAlternarAutocompletado = {
                opciones.autocompletado = !opciones.autocompletado
                ajustes.escribir("autocompletado", if (opciones.autocompletado) "si" else "no")
            },
            tamanioFuente = tamanioFuente,
            alCambiarTamanio = alCambiarTamanio,
            paneles = paneles,
            tema = tema,
            alCambiarTema = alCambiarTema,
            modo = modo,
            alCambiarModo = alCambiarModo,
            alAbrirAcercaDe = { acercaDe = true },
            alAbrirTutoriales = { tutorialesAbiertos = true },
            carpetaProyecto = proyecto.carpeta,
            alElegirCarpeta = ::elegirCarpetaProyecto,
            alOlvidarCarpeta = ::olvidarCarpetaProyecto
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 600.dp) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    BarraMenus(grupos)
                    Box(Modifier.padding(horizontal = 8.dp)) { TextoArchivo(sesion, modificado) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BarraMenus(grupos)
                    Box(Modifier.padding(horizontal = 12.dp)) { TextoArchivo(sesion, modificado) }
                }
            }
        }
        mensaje?.let { texto ->
            Text(
                texto,
                color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            if (tutorialesAbiertos) {
                PantallaTutoriales(
                    vistas = vistas,
                    indice = leccionActual,
                    alCambiarIndice = { leccionActual = it },
                    alMarcarVista = { id ->
                        if (id !in vistas) {
                            vistas = vistas + id
                            ajustes.escribir("tutoriales_vistos", vistas.joinToString(","))
                        }
                    },
                    alCargar = { nombre, codigo -> ofrecerCarga(ArchivoMoka(nombre, codigo)) },
                    alCerrar = { tutorialesAbiertos = false }
                )
            } else {
                contenido()
            }
        }
    }

    if (acercaDe) {
        AlertDialog(
            onDismissRequest = { acercaDe = false },
            title = { Text("Moka") },
            text = { Text("Interprete didactico del lenguaje Moka: escribe un programa, ejecutalo y mira cada fase del compilador (tokens, arbol, semantica, TAC y ejecucion).") },
            confirmButton = { TextButton(onClick = { acercaDe = false }) { Text("Cerrar") } }
        )
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

    // Cierre pedido por la X de desktop o por Atras en Android. El Lote A
    // evita perder el trabajo; "Guardar y salir" queda para el Lote B.
    if (sesion.cierrePendiente) {
        AlertDialog(
            onDismissRequest = sesion::cancelarCierre,
            title = { Text("Cerrar Moka?") },
            text = {
                Text(
                    when {
                        sesion.ocupado && modificado ->
                            "Hay una operacion de archivo en curso y cambios sin guardar. Espera o cancela."
                        sesion.ocupado ->
                            "Hay una operacion de archivo en curso. Espera a que termine antes de cerrar."
                        else ->
                            "Hay cambios sin guardar. Si sales ahora, se descartan."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = ::guardarYSalir, enabled = !sesion.ocupado) {
                    Text("Guardar y salir")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            sesion.confirmarCierre()
                            alSalir()
                        },
                        enabled = !sesion.ocupado
                    ) { Text("Salir sin guardar") }
                    TextButton(onClick = sesion::cancelarCierre) { Text("Cancelar") }
                }
            }
        )
    }
}

// ---- Modelo de menus ----------------------------------------------------
// Se arma en un solo lugar; la barra solo lo dibuja.

private fun gruposDeMenu(
    ocupado: Boolean,
    alAbrir: () -> Unit,
    alGuardar: () -> Unit,
    alGuardarComo: () -> Unit,
    alGuardarYSalir: () -> Unit,
    alElegirEjemplo: (ArchivoMoka) -> Unit,
    alDeshacer: () -> Unit,
    alRehacer: () -> Unit,
    busquedaActiva: Boolean,
    alAlternarBusqueda: () -> Unit,
    autocompletado: Boolean,
    alAlternarAutocompletado: () -> Unit,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    paneles: EstadoPaneles,
    tema: TemaMoka,
    alCambiarTema: (TemaMoka) -> Unit,
    modo: ModoTaller,
    alCambiarModo: (ModoTaller) -> Unit,
    alAbrirAcercaDe: () -> Unit,
    alAbrirTutoriales: () -> Unit,
    carpetaProyecto: String?,
    alElegirCarpeta: () -> Unit,
    alOlvidarCarpeta: () -> Unit
): List<GrupoMenu> {
    val archivo = GrupoMenu(
        "Archivo",
        buildList {
            add(ItemMenu("Abrir", habilitado = !ocupado, accion = alAbrir))
            add(ItemMenu("Guardar", atajo = "Ctrl+S", habilitado = !ocupado, accion = alGuardar))
            add(ItemMenu("Guardar como", habilitado = !ocupado, accion = alGuardarComo))
            add(ItemMenu("Guardar y salir", habilitado = !ocupado, accion = alGuardarYSalir))
            add(SeparadorMenu)
            add(RotuloMenu("Ejemplos"))
            for (ejemplo in EjemplosMoka.todos) {
                add(ItemMenu(ejemplo.nombre, habilitado = !ocupado) { alElegirEjemplo(ejemplo) })
            }
        }
    )
    val editar = GrupoMenu(
        "Editar",
        listOf(
            ItemMenu("Deshacer", accion = alDeshacer),
            ItemMenu("Rehacer", accion = alRehacer),
            SeparadorMenu,
            ItemMenu("Buscar y reemplazar", atajo = "Ctrl+F", marcado = busquedaActiva, accion = alAlternarBusqueda),
            ItemMenu("Autocompletado", marcado = autocompletado, accion = alAlternarAutocompletado),
            SeparadorMenu,
            // No cierran el menu: agrandar/achicar se toca varias veces.
            ItemMenu("Letra mas grande (A+)", habilitado = tamanioFuente < 28, cierraAlAccionar = false) { alCambiarTamanio(tamanioFuente + 2) },
            ItemMenu("Letra mas chica (A-)", habilitado = tamanioFuente > 10, cierraAlAccionar = false) { alCambiarTamanio(tamanioFuente - 2) }
        )
    )
    val ver = GrupoMenu(
        "Ver",
        buildList {
            add(RotuloMenu("Tema"))
            add(ItemMenu("Sistema", marcado = tema == TemaMoka.SISTEMA) { alCambiarTema(TemaMoka.SISTEMA) })
            add(ItemMenu("Claro", marcado = tema == TemaMoka.CLARO) { alCambiarTema(TemaMoka.CLARO) })
            add(ItemMenu("Oscuro", marcado = tema == TemaMoka.OSCURO) { alCambiarTema(TemaMoka.OSCURO) })
            add(SeparadorMenu)
            add(RotuloMenu("Modo"))
            add(ItemMenu("Completo", marcado = modo == ModoTaller.COMPLETO) { alCambiarModo(ModoTaller.COMPLETO) })
            add(ItemMenu("Simple (solo editor y consola)", marcado = modo == ModoTaller.SIMPLE) { alCambiarModo(ModoTaller.SIMPLE) })
            add(SeparadorMenu)
            // 5C: prender/apagar paneles sueltos. No cierran el menu:
            // acomodar la vista suele ser de a varios toques.
            add(RotuloMenu("Paneles"))
            for (id in PANELES_CONMUTABLES) {
                add(ItemMenu(tituloDePanel(id), marcado = paneles.visible(id), cierraAlAccionar = false) { paneles.alternarVisible(id) })
            }
        }
    )
    val ayuda = GrupoMenu(
        "Ayuda",
        listOf(
            ItemMenu("Tutoriales", accion = alAbrirTutoriales),
            SeparadorMenu,
            ItemMenu("Acerca de Moka", accion = alAbrirAcercaDe)
        )
    )
    // P3: la carpeta donde viven los .moka de los paquetes (`usar`).
    val proyecto = GrupoMenu(
        "Proyecto",
        buildList {
            add(ItemMenu("Elegir carpeta del proyecto...", habilitado = !ocupado, accion = alElegirCarpeta))
            if (carpetaProyecto != null) {
                add(SeparadorMenu)
                add(RotuloMenu("Carpeta actual"))
                add(ItemMenu(carpetaProyecto, habilitado = false) {})
                add(ItemMenu("Olvidar carpeta", habilitado = !ocupado, accion = alOlvidarCarpeta))
            } else {
                add(SeparadorMenu)
                add(RotuloMenu("Sin carpeta: 'usar' no carga paquetes"))
            }
        }
    )
    return buildList {
        add(archivo)
        add(editar)
        add(ver)
        add(proyecto)
        add(ayuda)
    }
}

@Composable
private fun TextoArchivo(sesion: SesionArchivoMoka, modificado: Boolean) {
    Text(
        (sesion.archivo?.nombre ?: "sin-titulo.moka") + (if (modificado) " *" else "") +
                (if (sesion.ocupado) " (esperando archivo...)" else ""),
        style = MaterialTheme.typography.labelLarge
    )
}
