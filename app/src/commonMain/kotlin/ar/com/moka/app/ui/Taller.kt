// =======================================================================
//  Taller.kt - La pantalla principal (sec. 5.2 y 5.5)
// =======================================================================
//  Version 7.11: layout adaptativo.
//    - Pantalla ancha (desktop/tablet): muelles. Arriba el editor y los
//      paneles de fase; abajo, siempre visibles, Problemas y Consola.
//    - Pantalla angosta (telefono): TODAS las zonas pasan a pestañas.
//      Asi Codigo, Tokens, Arbol, Semantica, TAC, Ejecucion, Problemas y
//      Consola conservan su contenido sin pelear por el mismo ancho.
//
//  El corte usa 840.dp, el limite Material habitual entre una ventana
//  compacta y una mediana. No pregunta por la plataforma: una ventana de
//  escritorio achicada tambien usa pestañas, y una tablet/Android ancha
//  usa muelles. Eso es justamente lo que pide la sec. 5.5.
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
// ~360-410.dp de alto: ahi la franja fija de 220.dp de Problemas/Consola
// no deja lugar al editor. Los muelles tambien exigen altura minima.
private val ALTURA_MINIMA_MUELLES = 480.dp
private val ALTURA_FRANJA_INFERIOR = 220.dp

// En modo angosto la pantalla entera se organiza como una sola fila de
// pestañas. Los nombres coinciden con los paneles de la sec. 5.3.
private val PESTANAS_ANGOSTAS =
    listOf("Codigo") + PESTANAS_FASES + listOf("Problemas", "Consola")

// Modo simple: solo Codigo y Consola, sin fases ni paneles internos del
// compilador. Pensado para quien solo quiere escribir y correr programas.
// Los errores llegan igual a la Consola (salida del pipeline).
enum class ModoTaller { COMPLETO, SIMPLE }
private val PESTANAS_SIMPLES = listOf("Codigo", "Consola")

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
    alCambiarTamanio: (Int) -> Unit
) {
    val estado by controlador.estado.collectAsState()

    ArchivoTaller(controlador, sesionArchivo, alSalir, tema, alCambiarTema, modo, alCambiarModo, tamanioFuente, alCambiarTamanio) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Angosto por ancho O por altura: landscape de telefono cae
            // en pestañas aunque su ancho parezca de tablet.
            val angosto = maxWidth < ANCHO_MINIMO_MUELLES || maxHeight < ALTURA_MINIMA_MUELLES
            val simple = modo == ModoTaller.SIMPLE
            Column(
                // 5B: Ctrl + rueda del mouse cambia el tamano de letra
                // desde cualquier panel (ver UtilidadesPlataforma).
                Modifier.fillMaxSize().zoomConRueda { delta -> alCambiarTamanio(tamanioFuente + delta) }
            ) {
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
                        modifier = Modifier.weight(1f)
                    )
                } else if (angosto) {
                    ContenidoPorPestanas(
                        estado = estado,
                        controlador = controlador,
                        modificado = sesionArchivo.hayCambios(estado.fuente),
                        pestanas = PESTANAS_ANGOSTAS,
                        simple = false,
                        tamanioFuente = tamanioFuente,
                        alCambiarTamanio = alCambiarTamanio,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ContenidoConMuelles(
                        estado = estado,
                        controlador = controlador,
                        tamanioFuente = tamanioFuente,
                        alCambiarTamanio = alCambiarTamanio,
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
@Composable
private fun DivisorVertical(onArrastre: (Float) -> Unit) {
    Box(
        Modifier.fillMaxHeight().width(9.dp)
            // 5B: cursor de flecha doble horizontal al pasar el mouse
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
            // 5B: cursor de flecha doble vertical al pasar el mouse
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

// Layout de escritorio: codigo | paneles arriba, consola | problemas
// abajo (5A: la consola va a la IZQUIERDA y los problemas a la derecha),
// con divisores arrastrables entre las cuatro secciones.
@Composable
private fun ContenidoConMuelles(
    estado: EstadoWorkbench,
    controlador: MokaControlador,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var fraccionArriba by remember { mutableFloatStateOf(0.68f) }
    var fraccionEditor by remember { mutableFloatStateOf(0.5f) }
    var fraccionConsola by remember { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val anchoPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val altoPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Column(Modifier.fillMaxSize()) {
            // ---- editor | paneles de fase ----
            Row(Modifier.fillMaxWidth().weight(fraccionArriba).padding(top = 8.dp)) {
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
                    modifier = Modifier.weight(fraccionEditor).fillMaxHeight().padding(start = 12.dp, bottom = 4.dp)
                )
                DivisorVertical { delta ->
                    fraccionEditor = (fraccionEditor + delta / anchoPx).coerceIn(FRACCION_MINIMA, FRACCION_MAXIMA)
                }
                PanelesTaller(
                    estado = estado,
                    onSeleccionar = controlador::seleccionar,
                    modifier = Modifier.weight(1f - fraccionEditor).fillMaxHeight()
                        .padding(end = 12.dp, bottom = 4.dp)
                )
            }
            DivisorHorizontal { delta ->
                fraccionArriba = (fraccionArriba + delta / altoPx).coerceIn(FRACCION_MINIMA, FRACCION_MAXIMA)
            }
            // ---- consola | problemas ----
            Row(Modifier.fillMaxWidth().weight(1f - fraccionArriba)) {
                ConsolaTaller(
                    salida = estado.salida,
                    pidiendoEntrada = estado.pidiendoEntrada,
                    terminado = estado.terminado,
                    exito = estado.exito,
                    onEnviar = controlador::enviarEntrada,
                    modifier = Modifier.weight(fraccionConsola).fillMaxHeight()
                        .padding(start = 12.dp, bottom = 8.dp)
                )
                DivisorVertical { delta ->
                    fraccionConsola = (fraccionConsola + delta / anchoPx).coerceIn(FRACCION_MINIMA, FRACCION_MAXIMA)
                }
                PanelProblemas(
                    fases = estado.fases,
                    onSeleccionar = controlador::seleccionar,
                    modifier = Modifier.weight(1f - fraccionConsola).fillMaxHeight()
                        .padding(end = 12.dp, bottom = 8.dp)
                )
            }
        }
    }
}

// Modo simple (5A): codigo arriba y consola abajo, sin pestañas, con un
// divisor arrastrable entre las dos. La consola a la vista todo el tiempo
// es lo que hace util al modo para aprender.
@Composable
private fun ContenidoSimple(
    estado: EstadoWorkbench,
    controlador: MokaControlador,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var fraccionCodigo by remember { mutableFloatStateOf(0.62f) }
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
                modifier = Modifier.fillMaxWidth().weight(fraccionCodigo)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            )
            DivisorHorizontal { delta ->
                fraccionCodigo = (fraccionCodigo + delta / altoPx).coerceIn(0.25f, FRACCION_MAXIMA)
            }
            ConsolaTaller(
                salida = estado.salida,
                pidiendoEntrada = estado.pidiendoEntrada,
                terminado = estado.terminado,
                exito = estado.exito,
                onEnviar = controlador::enviarEntrada,
                modifier = Modifier.fillMaxWidth().weight(1f - fraccionCodigo)
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            )
        }
    }
}

// Layout de pantalla angosta. Los indices 1..5 llaman al MISMO selector
// de paneles que usa el contenedor del modo ancho; no hay una segunda
// copia de Tokens/Arbol/Semantica/TAC/Ejecucion que pueda desactualizarse.
@Composable
private fun ContenidoPorPestanas(
    estado: EstadoWorkbench,
    controlador: MokaControlador,
    modificado: Boolean,
    pestanas: List<String>,
    simple: Boolean,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pestana by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pestana,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 8.dp
        ) {
            pestanas.forEachIndexed { indice, titulo ->
                // El asterisco de modificado tambien en la pestaña Codigo:
                // en modo pestañas la barra de archivo puede quedar lejos.
                val rotulo = if (indice == 0 && modificado) "$titulo *" else titulo
                Tab(
                    selected = pestana == indice,
                    onClick = { pestana = indice },
                    text = { Text(rotulo) }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            val contenido = Modifier.fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            when {
                pestana == 0 -> EditorTaller(
                    fuente = estado.fuente,
                    tokens = (estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico)?.tokens,
                    diagnosticos = estado.diagnosticos,
                    seleccion = estado.seleccion,
                    onCambio = controlador::cambiarFuente,
                    tamanioFuente = tamanioFuente,
                    alCambiarTamanio = alCambiarTamanio,
                    alDeshacer = controlador::deshacer,
                    alRehacer = controlador::rehacer,
                    modifier = contenido
                )
                // En modo simple la pestaña 1 es la Consola directamente.
                simple -> ConsolaTaller(
                    salida = estado.salida,
                    pidiendoEntrada = estado.pidiendoEntrada,
                    terminado = estado.terminado,
                    exito = estado.exito,
                    onEnviar = controlador::enviarEntrada,
                    modifier = contenido
                )
                pestana in 1..5 -> PanelFase(
                    indice = pestana - 1,
                    estado = estado,
                    onSeleccionar = controlador::seleccionar,
                    modifier = contenido
                )
                pestana == 6 -> PanelProblemas(
                    fases = estado.fases,
                    onSeleccionar = controlador::seleccionar,
                    modifier = contenido
                )
                else -> ConsolaTaller(
                    salida = estado.salida,
                    pidiendoEntrada = estado.pidiendoEntrada,
                    terminado = estado.terminado,
                    exito = estado.exito,
                    onEnviar = controlador::enviarEntrada,
                    modifier = contenido
                )
            }
        }
    }
}
