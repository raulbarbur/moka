// =======================================================================
//  PanelEjecucion.kt — El panel de ejecucion (sec. 5.3, panel 6)
// =======================================================================
//  La SALIDA del programa esta en la consola de abajo (esa es su unica
//  fuente, sec. 5.1); este panel muestra lo demas:
//    Estado (paso a paso): la pila de marcos del ultimo evento Paso, con
//      variables y temporales ya renderizados por la maquina (MarcoVista),
//      y la cuadrupla actual.
//    Resultado: fin normal con la cantidad de cuadruplas ejecutadas, o
//      cierre con errores.
//    Entrada: un aviso mientras el programa espera una linea (el campo
//      para escribirla esta en la consola).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.core.ejecucion.EventoEjecucion

private val MONO_CHICO = TextStyle(fontFamily = FuenteMono, fontSize = 12.sp)

@Composable
fun PanelEjecucion(
    pasoActual: EventoEjecucion.Paso?,
    pidiendoEntrada: Boolean,
    terminado: Boolean,
    exito: Boolean,
    cuadruplasEjecutadas: Long?,
    // 5C: tamano de letra propio del panel.
    tamanio: Int,
    modifier: Modifier = Modifier
) {
    val mono = MONO_CHICO.copy(fontSize = tamanio.sp)

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text("Ejecucion", style = MaterialTheme.typography.labelLarge)

        // ---- aviso de entrada pendiente ----
        if (pidiendoEntrada) {
            Text(
                "El programa espera una linea (campo de entrada en la consola).",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = tamanio.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ---- estado del paso a paso ----
        val paso = pasoActual
        if (paso != null) {
            Text(
                "Paso a paso: cuadrupla #${paso.pc}",
                style = MaterialTheme.typography.labelMedium,
                fontSize = tamanio.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            for (marco in paso.marcos) {
                Text("marco: ${marco.nombre}", style = mono, modifier = Modifier.padding(top = 4.dp))
                if (marco.variables.isEmpty() && marco.temporales.isEmpty()) {
                    Text("  (sin variables)", style = mono, color = MaterialTheme.colorScheme.outline)
                }
                for ((nombre, valor) in marco.variables) {
                    Text("  $nombre = $valor", style = mono)
                }
                for ((nombre, valor) in marco.temporales) {
                    Text("  $nombre = $valor", style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---- resultado ----
        if (terminado) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (exito) {
                        "Finalizado correctamente" +
                                (cuadruplasEjecutadas?.let { ", $it cuadruplas ejecutadas" } ?: "") + "."
                    } else {
                        "Finalizado con errores."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = tamanio.sp,
                    color = if (exito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        // ---- estado inicial ----
        if (paso == null && !terminado && !pidiendoEntrada) {
            Spacer(Modifier.width(0.dp))
            Text(
                "La salida del programa esta en la consola (abajo a la derecha).\n" +
                        "Con Paso a paso, aca se ven los marcos y las variables de cada cuadrupla.",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = tamanio.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

