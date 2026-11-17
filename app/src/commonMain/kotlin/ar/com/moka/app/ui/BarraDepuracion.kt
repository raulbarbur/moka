// BarraDepuracion.kt - Controles de la compuerta de paso a paso.
package ar.com.moka.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.app.estado.MokaControlador
import ar.com.moka.app.estado.SituacionEjecucion

@Composable
fun BarraDepuracion(controlador: MokaControlador) {
    val control by controlador.control.collectAsState()
    val pausada = control.situacion == SituacionEjecucion.PAUSADA
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = controlador::siguiente, enabled = pausada) { Text("Siguiente") }
        TextButton(onClick = controlador::continuar, enabled = pausada) { Text("Continuar") }
        Text(
            when (control.situacion) {
                SituacionEjecucion.INACTIVA -> "Paso a paso pausa ANTES de ejecutar cada cuadrupla."
                SituacionEjecucion.ANALIZANDO -> "Analizando..."
                SituacionEjecucion.CORRIENDO -> "Ejecutando..."
                SituacionEjecucion.PAUSADA -> "Pausa ${control.paso}: inspecciona TAC y Ejecucion; pulsa Siguiente."
                SituacionEjecucion.ENTRADA -> "Esperando entrada en la consola (no un clic en Siguiente)."
                SituacionEjecucion.DETENIDA -> "Detenida. Los paneles conservan la ultima foto."
                SituacionEjecucion.FINALIZADA -> "Finalizada. El total de cuadruplas esta en Ejecucion."
                SituacionEjecucion.ERROR -> control.mensaje ?: "Finalizada con errores: revisa Problemas y Consola."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (control.situacion == SituacionEjecucion.ERROR) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
