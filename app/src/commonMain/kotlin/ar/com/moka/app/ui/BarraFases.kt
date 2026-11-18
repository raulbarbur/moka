// =======================================================================
//  BarraFases.kt - La barra de fases o "pipeline strip" (sec. 5.2)
// =======================================================================
//  Cinco chips, uno por fase, con su estado y su duracion. Es la
//  expresion visual de FaseIniciada / FaseCompletada / FaseOmitida:
//  esta funcion solo LEE el mapa de slots que reducir() ya actualizo.
//
//  7.11 agrega el modo compacto: en pantalla angosta los chips conservan
//  un ancho legible y la fila se desplaza horizontalmente. En pantalla
//  ancha siguen repartiendo todo el ancho, como en las fases anteriores.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.app.estado.EstadoFase
import ar.com.moka.app.estado.EstadoFaseUi
import ar.com.moka.core.pipeline.Fase
import ar.com.moka.core.semantico.Severidad

private val ANCHO_CHIP_COMPACTO = 112.dp

@Composable
fun BarraFases(fases: Map<Fase, EstadoFaseUi>, compacta: Boolean = false) {
    val desplazamiento = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (compacta) Modifier.horizontalScroll(desplazamiento) else Modifier)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (fase in Fase.entries) {
            ChipFase(
                fase,
                fases.getValue(fase),
                if (compacta) Modifier.width(ANCHO_CHIP_COMPACTO) else Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChipFase(fase: Fase, estado: EstadoFaseUi, modifier: Modifier) {
    val esquema = MaterialTheme.colorScheme
    val (fondo, contenido) = when (estado.estado) {
        EstadoFase.PENDIENTE -> esquema.surfaceVariant to esquema.onSurfaceVariant
        EstadoFase.CORRIENDO -> esquema.secondaryContainer to esquema.onSecondaryContainer
        EstadoFase.COMPLETADA -> esquema.primaryContainer to esquema.onPrimaryContainer
        EstadoFase.CON_ERRORES -> esquema.errorContainer to esquema.onErrorContainer
        EstadoFase.OMITIDA -> esquema.surfaceVariant to esquema.outline
    }
    Surface(modifier = modifier, color = fondo, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(nombreDe(fase), style = MaterialTheme.typography.labelLarge, color = contenido)
            Text(detalleDe(estado), style = MaterialTheme.typography.labelSmall, color = contenido)
        }
    }
}

// La segunda linea del chip: estado legible + duracion o cantidad de
// errores. Los simbolos (tick, cruz, guion) van con escapes Unicode
// para que el pegado no los deforme.
private fun detalleDe(estado: EstadoFaseUi): String = when (estado.estado) {
    EstadoFase.PENDIENTE -> "pendiente"
    EstadoFase.CORRIENDO -> "corriendo..."
    EstadoFase.COMPLETADA -> estado.duracionMs?.let { "\u2713 $it ms" } ?: "\u2713"
    EstadoFase.CON_ERRORES -> {
        val n = estado.diagnosticos.count { it.severidad == Severidad.ERROR }
        "\u2717 $n " + if (n == 1) "error" else "errores"
    }
    EstadoFase.OMITIDA -> "\u2013 omitida"
}

// Nombre visible de cada fase (sin tildes, como todos los mensajes de
// Moka: convencion del proyecto).
fun nombreDe(fase: Fase): String = when (fase) {
    Fase.LEXICA -> "Lexico"
    Fase.SINTACTICA -> "Sintactico"
    Fase.SEMANTICA -> "Semantica"
    Fase.GENERACION_TAC -> "TAC"
    Fase.EJECUCION -> "Ejecucion"
}
