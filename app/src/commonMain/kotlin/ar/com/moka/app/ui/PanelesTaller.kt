// =======================================================================
//  PanelesTaller.kt - Paneles de fase y su selector compartido
// =======================================================================
//  Contenedor del lado derecho en pantalla ANCHA (sec. 5.2): pestañas
//  Tokens | Arbol | Semantica | TAC | Ejecucion. Cada panel recibe solo
//  el artefacto de su fase y lo poco mas que necesita del estado.
//
//  Desde 7.11, el contenido esta extraido en PanelFase(). El modo
//  angosto de Taller usa esa misma funcion para mostrar cada panel como
//  una pestaña de pantalla completa. Asi las variantes ancha/angosta no
//  duplican reglas ni pueden quedar con comportamientos distintos.
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

// internal: Taller la usa para armar las pestañas grandes del modo angosto.
internal val PESTANAS_FASES = listOf("Tokens", "Arbol", "Semantica", "TAC", "Ejecucion")

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
            PrimaryTabRow(selectedTabIndex = pestana, modifier = Modifier.fillMaxWidth()) {
                PESTANAS_FASES.forEachIndexed { indice, titulo ->
                    Tab(
                        selected = pestana == indice,
                        onClick = { pestana = indice },
                        text = { Text(titulo) }
                    )
                }
            }
            PanelFase(
                indice = pestana,
                estado = estado,
                onSeleccionar = onSeleccionar,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Unico mapa pestaña -> panel. Lo comparten:
//  - el muelle derecho del modo ancho (PanelesTaller), y
//  - las pestañas 1..5 del modo angosto (Taller).
@Composable
fun PanelFase(
    indice: Int,
    estado: EstadoWorkbench,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    when (indice) {
        0 -> PanelTokens(
            lexico = estado.fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico,
            seleccion = estado.seleccion,
            onSeleccionar = onSeleccionar,
            modifier = modifier
        )
        1 -> PanelArbol(
            sintactico = estado.fases.getValue(Fase.SINTACTICA).artefacto as? Artefacto.Sintactico,
            seleccion = estado.seleccion,
            onSeleccionar = onSeleccionar,
            modifier = modifier
        )
        2 -> {
            val slotSemantica = estado.fases.getValue(Fase.SEMANTICA)
            PanelSemantico(
                semantico = slotSemantica.artefacto as? Artefacto.Semantico,
                diagnosticos = slotSemantica.diagnosticos,
                seleccion = estado.seleccion,
                onSeleccionar = onSeleccionar,
                modifier = modifier
            )
        }
        3 -> PanelTac(
            tac = estado.fases.getValue(Fase.GENERACION_TAC).artefacto as? Artefacto.Tac,
            pasoActual = estado.pasoActual,
            seleccion = estado.seleccion,
            onSeleccionar = onSeleccionar,
            modifier = modifier
        )
        else -> PanelEjecucion(
            pasoActual = estado.pasoActual,
            pidiendoEntrada = estado.pidiendoEntrada,
            terminado = estado.terminado,
            exito = estado.exito,
            cuadruplasEjecutadas = estado.cuadruplasEjecutadas,
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
