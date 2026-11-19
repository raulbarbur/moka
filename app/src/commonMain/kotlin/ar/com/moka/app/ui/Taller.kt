// =======================================================================
//  Taller.kt - La pantalla principal (sec. 5.2 y 5.5)
// =======================================================================
//  Version 7.12: zoom y visibilidad POR PANEL (5C).
//    - Ctrl + rueda cambia el tamano de letra del panel que esta bajo
//      el mouse (editor, consola, problemas o el panel de fase visible),
//      ya no el de todos a la vez. Cada panel recuerda su tamano en
//      EstadoPaneles, que lo guarda en los ajustes de la plataforma.
//    - El menu Ver -> Paneles prende y apaga paneles sueltos. Los
//      layouts de abajo se arman con lo que queda visible: si no hay
//      paneles de fase visibles el editor ocupa todo el ancho, y si se
//      apagan consola y problemas la franja de abajo desaparece.
//
//  Layout adaptativo (sin cambios): pantalla ancha con muelles (editor
//  y paneles arriba; consola y problemas abajo) y pantalla angosta con
//  TODAS las zonas en pestañas. El corte usa 840.dp de ancho y 480.dp
//  de alto, los limites Material habituales. No pregunta por la
//  plataforma: una ventana achicada tambien usa pestañas.
//
//  Este composable NO decide ejecucion ni analisis: observa el StateFlow
//  del controlador y reparte pedazos de estado. Toda la logica del
//  pipeline sigue en estado/ (sec. 5.1, una sola fuente de verdad).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ar.com.moka.app.TemaMoka
import ar.com.moka.app.archivos.SesionArchivoMoka
import ar.com.moka.app.estado.EstadoWorkbench
import ar.com.moka.app.estado.MokaControlador
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.pipeline.Fase

// Umbral de modo: menos que esto se considera angosto. Es un valor de
// ancho logico, no de pixeles fisicos, asi que sirve en ambas plataformas.
private val ANCHO_MINIMO_MUELLES = 840.dp
// Un telefono en horizontal puede superar los 840.dp de ancho con solo
// ~360-410.dp de alto: ahi la franja fija de Problemas/Consola no deja
// lugar al editor. Los muelles tambien exigen altura minima.
private val ALTURA_MINIMA_MUELLES = 480.dp

// Modo simple: solo Codigo y Consola, sin fases ni paneles internos del
// compilador. Pensado para quien solo quiere escribir y correr programas.
// Los errores llegan igual a la Consola (salida del pipeline).
enum class ModoTaller { COMPLETO, SIMPLE }

@Composable
fun Taller(
    controlador: MokaControlador,
    sesionArchivo: SesionArchivoMoka,
    alSalir: () -> Unit,
    tema: TemaMoka,
    alCambiarTema: (TemaMoka) -> Unit,
    modo: ModoTaller,
    alCambiarModo: (ModoTaller) -> Unit,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    paneles: EstadoPaneles
) {
    val estado by controlador.estado.collectAsState()

    ArchivoTaller(controlador, sesionArchivo, alSalir, tema, alCambiarTema, modo, alCambiarModo, tamanioFuente, alCambiarTamanio, paneles) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Angosto por ancho O por altura: landscape de telefono cae
            // en pestañas aunque su ancho parezca de tablet.
            val angosto = maxWidth < ANCHO_MINIMO_MUELLES || maxHeight < ALTURA_MINIMA_MUELLES
            val simple = modo == ModoTaller.SIMPLE
            Column(Modifier.fillMaxSize()) {
                BarraAcciones(controlador, angosto || simple, simple)
                if (!simple) {
                    BarraDepuracion(controlador)

                    // La tira de fases queda en TODOS los tamaños: en
                    // angosta se vuelve desplazable para no comprimir sus
                    // cinco chips.
                    BarraFases(estado.fases, compacta = angosto)
                }

                if (simple) {
                    ContenidoSimple(
                        estado = estado,
                        controlador = controlador,
                        tamanioFuente = tamanioFuente,
                        alCambiarTamanio = alCambiarTamanio,
                        paneles = paneles,
                        modifier = Modifier.weight(1f)
                    )
                } else if (angosto) {
                    ContenidoPorPestanas(
                        estado = estado,
                        controlador = controlador,
                        modificado = sesionArchivo.hayCambios(estado.fuente),
                        simple = false,
                        tamanioFuente = tamanioFuente,
                        alCambiarTamanio = alCambiarTamanio,
                        paneles = paneles,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ContenidoConMuelles(
                        estado = estado,
                        controlador = controlador,
                        tamanioFuente = tamanioFuente,
                        alCambiarTamanio = alCambiarTamanio,
                        paneles = paneles,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// Botones del pipeline. En pantalla angosta se omite el titulo "Moka":
// el ancho libre queda para los botones, que entran en un telefono
// comun de 320.dp sin necesidad de una segunda fila.
// En modo simple tampoco aparece "Paso a paso": es una herramienta de
// depuracion ligada a los paneles de TAC y Ejecucion, que ese modo no
// muestra; dejarlo sugeriria una funcion sin donde observarla.
@Composable
private fun BarraAcciones(controlador: MokaControlador, angosto: Boolean, simple: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!angosto) {
            Text("Moka", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(24.dp))
        }
        Button(onClick = controlador::ejecutar) { Text("Ejecutar") }
        if (!simple) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = controlador::ejecutarPasoAPaso) { Text("Paso a paso") }
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = controlador::detener) { Text("Detener") }
    }
}

// Divisores arrastrables (5A). Cada uno reporta el delta en pixeles y
// quien lo contiene lo convierte en fraccion; asi las cuatro secciones
// (codigo, paneles, consola, problemas) se redimensionan a gusto. La
// franja fina del medio es la zona de agarre: 9.dp totales.
// 5B: cursor de flecha doble al pasar el mouse (horizontal en el divisor
// vertical, vertical en el horizontal).
@Composable
private fun DivisorVertical(onArrastre: (Float) -> Unit) {
    Box(
        Modifier.fillMaxHeight().width(9.dp)
            .pointerHoverIcon(cursorDivisorVertical())
            .pointerInput(Unit) { detectDragGestures { cambio, delta -> cambio.consume(); onArrastre(delta.x) } }
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxHeight().width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun DivisorHorizontal(onArrastre: (Float) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(9.dp)
            .pointerHoverIcon(cursorDivisorHorizontal())
            .pointerInput(Unit) { detectDragGestures { cambio, delta -> cambio.consume(); onArrastre(delta.y) } }
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant))
    }
}

// Limites de las fracciones: ninguna seccion desaparece del todo, siempre
// queda un borde para volver a agarrar el divisor.
private const val FRACCION_MINIMA = 0.06f
private const val FRACCION_MAXIMA = 0.94f

// Zoom del editor con Ctrl + rueda: es el mismo tamano que mueven los
// items A+/A- del menu Editar.
private fun Modifier.zoomEditor(tamanioFuente: Int, alCambiarTamanio: (Int) -> Unit): Modifier =
    zoomConRueda { delta -> alCambiarTamanio(tamanioFuente + delta) }

// Zoom de un panel con Ctrl + rueda: cambia solo ESE panel (5C).
private fun Modifier.zoomPanel(paneles: EstadoPaneles, panel: String): Modifier =
    zoomConRueda { delta -> paneles.cambiarTamanio(panel, delta) }

// Layout de escritorio: codigo | paneles arriba, consola | problemas
// abajo (5A: la consola va a la IZQUIERDA y los problemas a la derecha),
// con divisores arrastrables entre las cuatro secciones.
// 5C: cada seccion puede estar apagada desde Ver -> Paneles. Sin paneles
// de fase visibles el editor toma todo el ancho; sin consola NI problemas
// la franja de abajo (y su divisor) desaparecen.
@Composable
private fun ContenidoConMuelles(
    estado: EstadoWorkbench,
    controlador: MokaControlador,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    paneles: EstadoPaneles,
    modifier: Modifier = Modifier
) {
    var fraccionArriba by remember { mutableFloatStateOf(0.68f) }
    var fraccionEditor by remember { mutableFloatStateOf(0.5f) }
    var fraccionConsola by remember { mutableFloatStateOf(0.5f) }

    val hayPanelesFase = PANELES_CON_FASE.any(paneles::visible)
    val hayConsola = paneles.visible(PANEL_CONSOLA)
    val hayProblemas = paneles.visible(PANEL_PROBLEMAS)
    val hayAbajo = hayConsola || hayProblemas

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val anchoPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val altoPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Column(Modifier.fillMaxSize()) {
            // ---- editor | paneles de fase ----
            Row(
                Modifier.fillMaxWidth()
                    .weight(if (hayAbajo) fraccionArriba else 1f)
                    .padding(top = 8.dp)
            ) {
                EditorTaller(
                    fuente = estado.fuente,
                    tokens = (estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico)?.tokens,
                    diagnosticos = estado.diagnosticos,
                    seleccion = estado.seleccion,
                    onCambio = controlador::cambiarFuente,
                    tamanioFuente = tamanioFuente,
                    alCambiarTamanio = alCambiarTamanio,
                    alDeshacer = controlador::deshacer,
                    alRehacer = controlador::rehacer,
                    modifier = Modifier.weight(if (hayPanelesFase) fraccionEditor else 1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = if (hayPanelesFase) 0.dp else 12.dp, bottom = 4.dp)
                        .zoomEditor(tamanioFuente, alCambiarTamanio)
                )
                if (hayPanelesFase) {
                    DivisorVertical { delta ->
                        fraccionEditor = (fraccionEditor + delta / anchoPx).coerceIn(FRACCION_MINIMA, FRACCION_MAXIMA)
                    }
                    PanelesTaller(
                        estado = estado,
                        onSeleccionar = controlador::seleccionar,
                        paneles = paneles,
                        modifier = Modifier.weight(1f - fraccionEditor).fillMaxHeight()
                            .padding(end = 12.dp, bottom = 4.dp)
                    )
                }
            }
            if (hayAbajo) {
                DivisorHorizontal { delta ->
                    fraccionArriba = (fraccionArriba + delta / altoPx).coerceIn(FRACCION_MINIMA, FRACCION_MAXIMA)
                }
                // ---- consola | problemas ----
                Row(Modifier.fillMaxWidth().weight(1f - fraccionArriba)) {
                    if (hayConsola) {
                        ConsolaTaller(
                            salida = estado.salida,
                            pidiendoEntrada = estado.pidiendoEntrada,
                            terminado = estado.terminado,
                            exito = estado.exito,
                            onEnviar = controlador::enviarEntrada,
                            tamanio = paneles.tamanio(PANEL_CONSOLA),
                            modifier = Modifier.weight(if (hayProblemas) fraccionConsola else 1f)
                                .fillMaxHeight()
                                .padding(start = 12.dp, end = if (hayProblemas) 0.dp else 12.dp, bottom = 8.dp)
                                .zoomPanel(paneles, PANEL_CONSOLA)
                        )
                    }
                    if (hayConsola && hayProblemas) {
                        DivisorVertical { delta ->
                            fraccionConsola = (fraccionConsola + delta / anchoPx).coerceIn(FRACCION_MINIMA, FRACCION_MAXIMA)
                        }
                    }
                    if (hayProblemas) {
                        PanelProblemas(
                            fases = estado.fases,
                            onSeleccionar = controlador::seleccionar,
                            tamanio = paneles.tamanio(PANEL_PROBLEMAS),
                            modifier = Modifier.weight(if (hayConsola) 1f - fraccionConsola else 1f)
                                .fillMaxHeight()
                                .padding(start = if (hayConsola) 0.dp else 12.dp, end = 12.dp, bottom = 8.dp)
                                .zoomPanel(paneles, PANEL_PROBLEMAS)
                        )
                    }
                }
            }
        }
    }
}

// Modo simple (5A): codigo arriba y consola abajo, sin pestañas, con un
// divisor arrastrable entre las dos. La consola a la vista todo el tiempo
// es lo que hace util al modo para aprender. Si la consola se apaga desde
// Ver -> Paneles, queda solo el editor.
@Composable
private fun ContenidoSimple(
    estado: EstadoWorkbench,
    controlador: MokaControlador,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    paneles: EstadoPaneles,
    modifier: Modifier = Modifier
) {
    var fraccionCodigo by remember { mutableFloatStateOf(0.62f) }
    val hayConsola = paneles.visible(PANEL_CONSOLA)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val altoPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Column(Modifier.fillMaxSize()) {
            EditorTaller(
                fuente = estado.fuente,
                tokens = (estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico)?.tokens,
                diagnosticos = estado.diagnosticos,
                seleccion = estado.seleccion,
                onCambio = controlador::cambiarFuente,
                tamanioFuente = tamanioFuente,
                alCambiarTamanio = alCambiarTamanio,
                alDeshacer = controlador::deshacer,
                alRehacer = controlador::rehacer,
                modifier = Modifier.fillMaxWidth()
                    .weight(if (hayConsola) fraccionCodigo else 1f)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (hayConsola) 0.dp else 8.dp)
                    .zoomEditor(tamanioFuente, alCambiarTamanio)
            )
            if (hayConsola) {
                DivisorHorizontal { delta ->
                    fraccionCodigo = (fraccionCodigo + delta / altoPx).coerceIn(0.25f, FRACCION_MAXIMA)
                }
                ConsolaTaller(
                    salida = estado.salida,
                    pidiendoEntrada = estado.pidiendoEntrada,
                    terminado = estado.terminado,
                    exito = estado.exito,
                    onEnviar = controlador::enviarEntrada,
                    tamanio = paneles.tamanio(PANEL_CONSOLA),
                    modifier = Modifier.fillMaxWidth().weight(1f - fraccionCodigo)
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                        .zoomPanel(paneles, PANEL_CONSOLA)
                )
            }
        }
    }
}

// Layout de pantalla angosta. Las pestañas se arman con los paneles
// VISIBLES (5C): apagar un panel desde Ver -> Paneles saca su pestaña.
// El contenido de cada pestaña de fase es el MISMO PanelFase que usa el
// muelle derecho del modo ancho; no hay una segunda copia que pueda
// desactualizarse.
@Composable
private fun ContenidoPorPestanas(
    estado: EstadoWorkbench,
    controlador: MokaControlador,
    modificado: Boolean,
    simple: Boolean,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    paneles: EstadoPaneles,
    modifier: Modifier = Modifier
) {
    // Pares (id de panel, titulo de la pestaña).
    val pestanasVisibles: List<Pair<String, String>> = buildList {
        add(PANEL_EDITOR to "Codigo")
        if (!simple) {
            for (id in PANELES_CON_FASE) {
                if (paneles.visible(id)) add(id to tituloDePanel(id))
            }
            if (paneles.visible(PANEL_PROBLEMAS)) add(PANEL_PROBLEMAS to "Problemas")
        }
        if (paneles.visible(PANEL_CONSOLA)) add(PANEL_CONSOLA to "Consola")
    }

    var pestana by remember { mutableIntStateOf(0) }
    // Si se apaga el panel de la pestaña activa, el indice guardado puede
    // quedar afuera de la lista: se acota sin tocar el estado.
    val activa = pestana.coerceIn(0, pestanasVisibles.lastIndex)

    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = activa,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 8.dp
        ) {
            pestanasVisibles.forEachIndexed { indice, par ->
                // El asterisco de modificado tambien en la pestaña Codigo:
                // en modo pestañas la barra de archivo puede quedar lejos.
                val rotulo = if (indice == 0 && modificado) "${par.second} *" else par.second
                Tab(
                    selected = activa == indice,
                    onClick = { pestana = indice },
                    text = { Text(rotulo) }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            val contenido = Modifier.fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            when (val id = pestanasVisibles[activa].first) {
                PANEL_EDITOR -> EditorTaller(
                    fuente = estado.fuente,
                    tokens = (estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico)?.tokens,
                    diagnosticos = estado.diagnosticos,
                    seleccion = estado.seleccion,
                    onCambio = controlador::cambiarFuente,
                    tamanioFuente = tamanioFuente,
                    alCambiarTamanio = alCambiarTamanio,
                    alDeshacer = controlador::deshacer,
                    alRehacer = controlador::rehacer,
                    modifier = contenido.zoomEditor(tamanioFuente, alCambiarTamanio)
                )
                PANEL_CONSOLA -> ConsolaTaller(
                    salida = estado.salida,
                    pidiendoEntrada = estado.pidiendoEntrada,
                    terminado = estado.terminado,
                    exito = estado.exito,
                    onEnviar = controlador::enviarEntrada,
                    tamanio = paneles.tamanio(PANEL_CONSOLA),
                    modifier = contenido.zoomPanel(paneles, PANEL_CONSOLA)
                )
                PANEL_PROBLEMAS -> PanelProblemas(
                    fases = estado.fases,
                    onSeleccionar = controlador::seleccionar,
                    tamanio = paneles.tamanio(PANEL_PROBLEMAS),
                    modifier = contenido.zoomPanel(paneles, PANEL_PROBLEMAS)
                )
                else -> PanelFase(
                    panelId = id,
                    estado = estado,
                    onSeleccionar = controlador::seleccionar,
                    paneles = paneles,
                    modifier = contenido.zoomPanel(paneles, id)
                )
            }
        }
    }
}
