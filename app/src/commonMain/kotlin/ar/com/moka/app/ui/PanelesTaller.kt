// =======================================================================
//  PanelesTaller.kt - Paneles de fase y su selector compartido
// =======================================================================
//  Contenedor del lado derecho en pantalla ANCHA (sec. 5.2): pestañas
//  Tokens | Arbol | Semantica | TAC | Ejecucion. Cada panel recibe solo
//  el artefacto de su fase y lo poco mas que necesita del estado.
//
//  5C: las pestañas se arman con los paneles de fase VISIBLES (el menu
//  Ver -> Paneles puede apagar algunos) y el Ctrl + rueda se engancha
//  aca, sobre el panel de la pestaña activa: el zoom cambia el tamano
//  de letra de ESE panel, no el de todos. Cada panel recibe su tamano
//  desde EstadoPaneles.
//
//  El contenido sigue saliendo de PanelFase(), que comparte con el modo
//  angosto de Taller. Asi las variantes ancha/angosta no duplican reglas
//  ni pueden quedar con comportamientos distintos.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.app.estado.EstadoWorkbench
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.pipeline.Fase

@Composable
fun PanelesTaller(
    estado: EstadoWorkbench,
    onSeleccionar: (Posicion) -> Unit,
    paneles: EstadoPaneles,
    modifier: Modifier = Modifier
) {
    val idsVisibles = PANELES_CON_FASE.filter(paneles::visible)
    // El llamador (Taller) ya evita montar este contenedor cuando no hay
    // ninguno visible; la guarda queda por las dudas.
    if (idsVisibles.isEmpty()) return

    var pestana by remember { mutableIntStateOf(0) }
    // Si se apaga el panel de la pestaña activa, el indice guardado puede
    // quedar afuera: se acota sin tocar el estado.
    val activa = pestana.coerceIn(0, idsVisibles.lastIndex)
    val idActivo = idsVisibles[activa]

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = activa, modifier = Modifier.fillMaxWidth()) {
                idsVisibles.forEachIndexed { indice, id ->
                    Tab(
                        selected = activa == indice,
                        onClick = { pestana = indice },
                        text = { Text(tituloDePanel(id)) }
                    )
                }
            }
            PanelFase(
                panelId = idActivo,
                estado = estado,
                onSeleccionar = onSeleccionar,
                paneles = paneles,
                // 5C: Ctrl + rueda aca cambia el tamano del panel que se
                // esta viendo (el de la pestaña activa), no el de todos.
                modifier = Modifier.fillMaxSize()
                    .zoomConRueda { delta -> paneles.cambiarTamanio(idActivo, delta) }
                    // Pinza con dos dedos: lo mismo que la rueda, para el celular.
                    .zoomConPinza(paneles.tamanio(idActivo)) { nuevo -> paneles.fijarTamanio(idActivo, nuevo) }
            )
        }
    }
}

// Unico mapa id de panel -> contenido. Lo comparten:
//  - el muelle derecho del modo ancho (PanelesTaller), y
//  - las pestañas de fase del modo angosto (Taller).
@Composable
fun PanelFase(
    panelId: String,
    estado: EstadoWorkbench,
    onSeleccionar: (Posicion) -> Unit,
    paneles: EstadoPaneles,
    modifier: Modifier = Modifier
) {
    when (panelId) {
        PANEL_TOKENS -> PanelTokens(
            lexico = estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico,
            seleccion = estado.seleccion,
            onSeleccionar = onSeleccionar,
            tamanio = paneles.tamanio(PANEL_TOKENS),
            modifier = modifier
        )
        PANEL_ARBOL -> PanelArbol(
            sintactico = estado.fases.getValue(Fase.SINTACTICA).artefacto as? Artefacto.Sintactico,
            seleccion = estado.seleccion,
            onSeleccionar = onSeleccionar,
            tamanio = paneles.tamanio(PANEL_ARBOL),
            modifier = modifier
        )
        PANEL_SEMANTICA -> {
            val slotSemantica = estado.fases.getValue(Fase.SEMANTICA)
            PanelSemantico(
                semantico = slotSemantica.artefacto as? Artefacto.Semantico,
                diagnosticos = slotSemantica.diagnosticos,
                seleccion = estado.seleccion,
                onSeleccionar = onSeleccionar,
                tamanio = paneles.tamanio(PANEL_SEMANTICA),
                modifier = modifier
            )
        }
        PANEL_TAC -> PanelTac(
            tac = estado.fases.getValue(Fase.GENERACION_TAC).artefacto as? Artefacto.Tac,
            pasoActual = estado.pasoActual,
            seleccion = estado.seleccion,
            onSeleccionar = onSeleccionar,
            tamanio = paneles.tamanio(PANEL_TAC),
            modifier = modifier
        )
        else -> PanelEjecucion(
            pasoActual = estado.pasoActual,
            pidiendoEntrada = estado.pidiendoEntrada,
            terminado = estado.terminado,
            exito = estado.exito,
            cuadruplasEjecutadas = estado.cuadruplasEjecutadas,
            tamanio = paneles.tamanio(PANEL_EJECUCION),
            modifier = modifier
        )
    }
}

// Aviso centrado para fases que no corrieron. Lo comparten los paneles.
@Composable
fun PanelVacio(mensaje: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(mensaje, style = MaterialTheme.typography.bodyMedium)
    }
}

