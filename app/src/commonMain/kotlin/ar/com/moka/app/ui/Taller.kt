// =======================================================================
//  Taller.kt — La pantalla principal (sec. 5.2)
// =======================================================================
//  Esqueleto de la version 7.2: barra superior con Ejecutar / Paso a
//  paso / Detener, la barra de fases (un chip por fase del pipeline), el
//  editor de codigo a la izquierda, el hueco de los paneles a la derecha
//  (los paneles de verdad llegan en 7.4 a 7.7) y la consola abajo.
//
//  Este composable NO tiene logica: observa el StateFlow del controlador
//  y le reparte a cada parte su pedazo de estado (sec. 5.1: una sola
//  fuente de verdad). Toda la decision de "que cambio" ya la tomo
//  reducir() en estado/.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.app.estado.MokaControlador

@Composable
fun Taller(controlador: MokaControlador) {
    val estado by controlador.estado.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // ---- barra superior: acciones (Abrir/Guardar llegan en 7.9) ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Moka", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(24.dp))
            Button(onClick = controlador::ejecutar) { Text("Ejecutar") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = controlador::ejecutarPasoAPaso) { Text("Paso a paso") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = controlador::detener) { Text("Detener") }
        }

        // ---- barra de fases: la foto del pipeline en cada momento ----
        BarraFases(estado.fases)

        // ---- mitad de arriba: editor | paneles ----
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            EditorTaller(
                fuente = estado.fuente,
                onCambio = controlador::cambiarFuente,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, bottom = 8.dp)
            )
            PanelLateralPlaceholder(
                Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 12.dp, bottom = 8.dp)
            )
        }

        // ---- consola abajo ----
        ConsolaTaller(
            salida = estado.salida,
            pidiendoEntrada = estado.pidiendoEntrada,
            terminado = estado.terminado,
            exito = estado.exito,
            onEnviar = controlador::enviarEntrada,
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}

// Hueco del lado derecho mientras no existen los paneles. Desaparece en
// 7.4, cuando el primer panel ocupe este lugar.
@Composable
private fun PanelLateralPlaceholder(modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                "Paneles de fases (tokens, arbol, semantica, TAC):\nllegan en los pasos 7.4 a 7.7.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
