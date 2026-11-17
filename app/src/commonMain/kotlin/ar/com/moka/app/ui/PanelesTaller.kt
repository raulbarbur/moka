// =======================================================================
//  PanelesTaller.kt — El contenedor con pestañas de los paneles de fase
// =======================================================================
//  El lado derecho del Taller (sec. 5.2): pestañas Tokens | Arbol |
//  Semantica | TAC | Ejecucion. Cada panel recibe solo el artefacto de
//  su fase y lo poco mas que necesita del estado (sec. 5.1: paneles sin
//  estado). Desde 7.7 existen las cinco.
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
import androidx.compose.material3.TabRow
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

private val PESTANAS = listOf("Tokens", "Arbol", "Semantica", "TAC", "Ejecucion")

@Composable
fun PanelesTaller(
    estado: EstadoWorkbench,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    var pestana by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = pestana, modifier = Modifier.fillMaxWidth()) {
                PESTANAS.forEachIndexed { indice, titulo ->
                    Tab(
                        selected = pestana == indice,
                        onClick = { pestana = indice },
                        text = { Text(titulo) }
                    )
                }
            }
            when (pestana) {
                0 -> PanelTokens(
                    lexico = estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico,
                    seleccion = estado.seleccion,
                    onSeleccionar = onSeleccionar,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> PanelArbol(
                    sintactico = estado.fases.getValue(Fase.SINTACTICA).artefacto as? Artefacto.Sintactico,
                    seleccion = estado.seleccion,
                    onSeleccionar = onSeleccionar,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> {
                    val slotSemantica = estado.fases.getValue(Fase.SEMANTICA)
                    PanelSemantico(
                        semantico = slotSemantica.artefacto as? Artefacto.Semantico,
                        diagnosticos = slotSemantica.diagnosticos,
                        seleccion = estado.seleccion,
                        onSeleccionar = onSeleccionar,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                3 -> PanelTac(
                    tac = estado.fases.getValue(Fase.GENERACION_TAC).artefacto as? Artefacto.Tac,
                    pasoActual = estado.pasoActual,
                    seleccion = estado.seleccion,
                    onSeleccionar = onSeleccionar,
                    modifier = Modifier.fillMaxSize()
                )
                else -> PanelEjecucion(
                    pasoActual = estado.pasoActual,
                    pidiendoEntrada = estado.pidiendoEntrada,
                    terminado = estado.terminado,
                    exito = estado.exito,
                    cuadruplasEjecutadas = estado.cuadruplasEjecutadas,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Aviso centrado para fases que no corrieron. Lo comparten los paneles.
@Composable
fun PanelVacio(mensaje: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(mensaje, style = MaterialTheme.typography.bodyMedium)
    }
}
