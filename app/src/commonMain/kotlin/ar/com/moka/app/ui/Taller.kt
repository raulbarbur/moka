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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun Taller(
    controlador: MokaControlador,
    sesionArchivo: SesionArchivoMoka,
    alSalir: () -> Unit,
    tema: TemaMoka,
    alCambiarTema: (TemaMoka) -> Unit
) {
    val estado by controlador.estado.collectAsState()

    ArchivoTaller(controlador, sesionArchivo, alSalir, tema, alCambiarTema) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Angosto por ancho O por altura: landscape de telefono cae
            // en pestañas aunque su ancho parezca de tablet.
            val angosto = maxWidth < ANCHO_MINIMO_MUELLES || maxHeight < ALTURA_MINIMA_MUELLES
            Column(Modifier.fillMaxSize()) {
                BarraAcciones(controlador, angosto)
                BarraDepuracion(controlador)

                // La tira de fases queda en TODOS los tamaños: en angosta
                // se vuelve desplazable para no comprimir sus cinco chips.
                BarraFases(estado.fases, compacta = angosto)

                if (angosto) {
                    ContenidoPorPestanas(
                        estado = estado,
                        controlador = controlador,
                        modificado = sesionArchivo.hayCambios(estado.fuente),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ContenidoConMuelles(
                        estado = estado,
                        controlador = controlador,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// Botones del pipeline. En pantalla angosta se omite el titulo "Moka":
// el ancho libre queda para los tres botones, que entran en un telefono
// comun de 320.dp sin necesidad de una segunda fila.
@Composable
private fun BarraAcciones(controlador: MokaControlador, angosto: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!angosto) {
            Text("Moka", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(24.dp))
        }
        Button(onClick = controlador::ejecutar) { Text("Ejecutar") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = controlador::ejecutarPasoAPaso) { Text("Paso a paso") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = controlador::detener) { Text("Detener") }
    }
}

// Layout historico de escritorio: dos muelles principales arriba y dos
// muelles fijos abajo. Se conserva igual para no cambiar la experiencia
// que ya quedo validada en las fases anteriores.
@Composable
private fun ContenidoConMuelles(
    estado: EstadoWorkbench,
    controlador: MokaControlador,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        // ---- editor | paneles de fase ----
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            EditorTaller(
                fuente = estado.fuente,
                tokens = (estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico)?.tokens,
                diagnosticos = estado.diagnosticos,
                seleccion = estado.seleccion,
                onCambio = controlador::cambiarFuente,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, bottom = 8.dp)
            )
            PanelesTaller(
                estado = estado,
                onSeleccionar = controlador::seleccionar,
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(start = 8.dp, end = 12.dp, bottom = 8.dp)
            )
        }

        // ---- problemas | consola ----
        Row(Modifier.fillMaxWidth().height(ALTURA_FRANJA_INFERIOR)) {
            PanelProblemas(
                fases = estado.fases,
                onSeleccionar = controlador::seleccionar,
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(start = 12.dp, end = 4.dp, bottom = 8.dp)
            )
            ConsolaTaller(
                salida = estado.salida,
                pidiendoEntrada = estado.pidiendoEntrada,
                terminado = estado.terminado,
                exito = estado.exito,
                onEnviar = controlador::enviarEntrada,
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(start = 4.dp, end = 12.dp, bottom = 8.dp)
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
    modifier: Modifier = Modifier
) {
    var pestana by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pestana,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 8.dp
        ) {
            PESTANAS_ANGOSTAS.forEachIndexed { indice, titulo ->
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
            when (pestana) {
                0 -> EditorTaller(
                    fuente = estado.fuente,
                    tokens = (estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico)?.tokens,
                    diagnosticos = estado.diagnosticos,
                    seleccion = estado.seleccion,
                    onCambio = controlador::cambiarFuente,
                    modifier = contenido
                )
                in 1..5 -> PanelFase(
                    indice = pestana - 1,
                    estado = estado,
                    onSeleccionar = controlador::seleccionar,
                    modifier = contenido
                )
                6 -> PanelProblemas(
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
