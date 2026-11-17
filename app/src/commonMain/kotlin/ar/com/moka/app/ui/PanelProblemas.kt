// =======================================================================
//  PanelProblemas.kt — Lista unificada de diagnosticos (sec. 5.3, panel 7)
// =======================================================================
//  Junta los diagnosticos de TODAS las fases (cada uno ya vive en el slot
//  de su fase; aca solo se aplanan, no se recalcula nada) y los deja
//  filtrar por fase y por severidad. Tocar uno avisa la posicion al
//  controlador (seleccion): el editor subraya ese rango y los paneles de
//  fase resaltan su elemento.
//
//  Nota: Diagnostico todavia no lleva su fase adentro (quedo pendiente
//  desde la Fase 3), asi que el filtro por fase se hace preguntandole a
//  cada slot del mapa, no al diagnostico.
//
//  La fila (FilaDiagnostico) es compartida: la usa tambien la sub-pestaña
//  Errores del panel semantico.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.app.estado.EstadoFaseUi
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.pipeline.Fase
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad

@Composable
fun PanelProblemas(
    fases: Map<Fase, EstadoFaseUi>,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    var faseFiltro by remember { mutableStateOf<Fase?>(null) }
    var severidadFiltro by remember { mutableStateOf<Severidad?>(null) }

    val visibles = remember(fases, faseFiltro, severidadFiltro) {
        val base = if (faseFiltro == null) {
            Fase.entries.flatMap { fases.getValue(it).diagnosticos }
        } else {
            fases.getValue(faseFiltro!!).diagnosticos
        }
        base.filter { severidadFiltro == null || it.severidad == severidadFiltro }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("Problemas (${visibles.size})", style = MaterialTheme.typography.labelLarge)

            // Filtro por fase (con scroll por si la ventana es angosta).
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = faseFiltro == null,
                    onClick = { faseFiltro = null },
                    label = { Text("Todas") }
                )
                for (fase in Fase.entries) {
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = faseFiltro == fase,
                        onClick = { faseFiltro = fase },
                        label = { Text(nombreDe(fase)) }
                    )
                }
            }

            // Filtro por severidad.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = severidadFiltro == null,
                    onClick = { severidadFiltro = null },
                    label = { Text("Errores y advertencias") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = severidadFiltro == Severidad.ERROR,
                    onClick = { severidadFiltro = Severidad.ERROR },
                    label = { Text("Solo errores") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = severidadFiltro == Severidad.ADVERTENCIA,
                    onClick = { severidadFiltro = Severidad.ADVERTENCIA },
                    label = { Text("Solo advertencias") }
                )
            }

            if (visibles.isEmpty()) {
                Text(
                    "Sin problemas.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(visibles) { diagnostico ->
                    FilaDiagnostico(diagnostico, onSeleccionar)
                }
            }
        }
    }
}

// Una fila de diagnostico: codigo coloreado por severidad, posicion
// (linea:columna) y el mensaje en espanol. Tocarla selecciona el rango
// en el editor. Compartida con el panel semantico.
@Composable
fun FilaDiagnostico(diagnostico: Diagnostico, onSeleccionar: (Posicion) -> Unit) {
    val color = if (diagnostico.severidad == Severidad.ERROR) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Row(
        Modifier.fillMaxWidth().clickable { onSeleccionar(diagnostico.pos) }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(diagnostico.codigo, style = MaterialTheme.typography.labelMedium, color = color)
        Spacer(Modifier.width(8.dp))
        Text(
            "(${diagnostico.pos.linea}:${diagnostico.pos.columna})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(8.dp))
        Text(diagnostico.mensaje, style = MaterialTheme.typography.bodySmall)
    }
}
