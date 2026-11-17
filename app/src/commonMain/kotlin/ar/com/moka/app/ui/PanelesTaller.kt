// =======================================================================
//  PanelesTaller.kt — El contenedor con pestañas de los paneles de fase
// =======================================================================
//  El lado derecho del Taller (sec. 5.2): pestañas Tokens | Arbol |
//  Semantica | TAC | Ejecucion, cada una alimentada por el artefacto de
//  su fase (sec. 5.1: paneles sin estado, reciben solo su artefacto).
//  Las pestañas cuyo panel todavia no existe muestran un aviso con el
//  paso en que llegan; la estructura de pestañas ya queda.
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
import ar.com.moka.app.estado.EstadoFaseUi
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.pipeline.Fase

// Pestaña -> paso en que llega su panel (Tokens ya existe: es esta fase).
private val PESTANAS = listOf("Tokens", "Arbol", "Semantica", "TAC", "Ejecucion")
private val PASO_DE_PESTANA = listOf("7.4", "7.5", "7.6", "7.7", "7.7")

@Composable
fun PanelesTaller(
    fases: Map<Fase, EstadoFaseUi>,
    seleccion: Posicion?,
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
                    lexico = fases.getValue(Fase.LEXICA).artefacto as? Artefacto.Lexico,
                    seleccion = seleccion,
                    onSeleccionar = onSeleccionar,
                    modifier = Modifier.fillMaxSize()
                )
                else -> PanelVacio(
                    "Este panel llega en el paso ${PASO_DE_PESTANA[pestana]}.",
                    Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Aviso centrado para paneles que todavia no existen o fases que no
// corrieron. Lo comparten todos los paneles.
@Composable
fun PanelVacio(mensaje: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(mensaje, style = MaterialTheme.typography.bodyMedium)
    }
}
