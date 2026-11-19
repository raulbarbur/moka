// =======================================================================
//  ConsolaTaller.kt — La consola del Taller (sec. 5.3, panel 6)
// =======================================================================
//  Muestra la salida del programa (las LineaConsola que reducir() fue
//  acumulando de los eventos Salida; las de error en rojo), el aviso de
//  cierre cuando el pipeline termina, y el campo de entrada que aparece
//  SOLO cuando el programa pidio una linea (evento PidiendoEntrada):
//  escribir y tocar Enviar llama a enviarEntrada() del controlador, que
//  despierta la leerLinea() suspendida de la maquina.
//
//  El scroll baja solo al agregarse lineas, para seguir la salida en
//  tiempo real.
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.app.estado.LineaConsola

@Composable
fun ConsolaTaller(
    salida: List<LineaConsola>,
    pidiendoEntrada: Boolean,
    terminado: Boolean,
    exito: Boolean,
    onEnviar: (String) -> Unit,
    // 5C: tamano de letra propio del panel (Ctrl + rueda sobre la consola).
    tamanio: Int,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Consola", style = MaterialTheme.typography.labelLarge)
                if (terminado) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (exito) "== Programa finalizado correctamente. =="
                        else "== Programa finalizado con errores. ==",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (exito) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            val desplazamiento = rememberScrollState()
            // Sigue la salida: cada linea nueva baja el scroll al final.
            LaunchedEffect(salida.size) { desplazamiento.animateScrollTo(desplazamiento.maxValue) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(desplazamiento)) {
                for (linea in salida) {
                    Text(
                        linea.texto,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = tamanio.sp),
                        color = if (linea.esError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (pidiendoEntrada) {
                var linea by remember { mutableStateOf("") }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = linea,
                        onValueChange = { linea = it },
                        singleLine = true,
                        label = { Text("Entrada del programa") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onEnviar(linea); linea = "" }) { Text("Enviar") }
                }
            }
        }
    }
}
