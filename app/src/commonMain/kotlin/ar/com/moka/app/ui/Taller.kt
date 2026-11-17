// =======================================================================
//  Taller.kt - La pantalla principal (sec. 5.2)
// =======================================================================
//  Version 7.9: ArchivoTaller agrega archivos, ejemplos y atajos.
//  El contenido del Taller conserva el resaltado de sintaxis de 7.8.
//
//  Este composable NO tiene logica: observa el StateFlow del controlador
//  y le reparte a cada parte su pedazo de estado (sec. 5.1: una sola
//  fuente de verdad). Toda la decision de "que cambio" ya la tomo
//  reducir() en estado/.
// =======================================================================
package ar.com.moka.app.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.app.estado.MokaControlador
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.pipeline.Fase

@Composable
fun Taller(controlador: MokaControlador) {
    val estado by controlador.estado.collectAsState()

    ArchivoTaller(controlador) {
        Column(Modifier.fillMaxSize()) {
            // ---- barra superior: acciones del pipeline ----
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

            // ---- mitad de arriba: editor | paneles de fase ----
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

            // ---- franja inferior: problemas | consola (sec. 5.2) ----
            Row(Modifier.fillMaxWidth().height(220.dp)) {
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
}
