// =======================================================================
//  ConsolaTaller.kt — La consola del Taller (sec. 5.3, panel 6)
// =======================================================================
//  Muestra la salida del programa (las LineaConsola que reducir() fue
//  acumulando de los eventos Salida; las de error en rojo), el aviso de
//  cierre cuando el pipeline termina, y el campo de entrada que aparece
//  SOLO cuando el programa pidio una linea (evento PidiendoEntrada):
//  escribir y tocar Enviar (o apretar ENTER) llama a enviarEntrada() del
//  controlador, que despierta la leerLinea() suspendida de la maquina.
//  ENTER en escritorio y la tecla "Enviar" del teclado en Android usan
//  el MISMO camino (la accion de teclado onSend), asi que nunca se envia
//  dos veces. Una linea vacia tambien se envia: hay programas (como el
//  Buscaminas) que piden "Presione ENTER para continuar".
//
//  El scroll baja solo al agregarse lineas, para seguir la salida en
//  tiempo real.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.app.estado.LineaConsola

// Los colores del builtin color() vienen de Tema.kt (colorDeConsolaMoka):
// toda la paleta de la app se edita en UN solo archivo.

@Composable
fun ConsolaTaller(
    salida: List<LineaConsola>,
    pidiendoEntrada: Boolean,
    terminado: Boolean,
    exito: Boolean,
    onEnviar: (String) -> Unit,
    // 5C: tamano de letra propio del panel (Ctrl + rueda sobre la consola).
    tamanio: Int,
    modifier: Modifier = Modifier,
    // Titulo pedido por el programa con titulo("..."): se muestra junto
    // al encabezado. null: la consola aun no tiene titulo propio.
    titulo: String? = null
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (titulo == null) "Consola" else "Consola · $titulo",
                    style = MaterialTheme.typography.labelLarge
                )
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
                // La salida llega en trozos: imprimir manda "texto\n" e
                // imprimir_sin_salto manda "texto" a secas. Se pegan todos en
                // UN solo texto para que los saltos de linea sean los del
                // programa (antes cada trozo ocupaba su propia fila y se
                // veian lineas en blanco y cortes de mas). Los mensajes de
                // error se pintan en rojo y siempre en su propia linea.
                // Cada trozo se dibuja con el color que estaba vigente al
                // imprimirse (builtin color), salvo el error, que manda.
                val colorError = MaterialTheme.colorScheme.error
                val fondoOscuro = MaterialTheme.colorScheme.surfaceVariant.let { it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f < 0.5f }
                val texto = remember(salida, colorError, fondoOscuro) {
                    buildAnnotatedString {
                        var alInicioDeLinea = true
                        for (linea in salida) {
                            if (linea.esError) {
                                if (!alInicioDeLinea) append("\n")
                                withStyle(SpanStyle(color = colorError)) { append(linea.texto) }
                                append("\n")
                                alInicioDeLinea = true
                            } else if (linea.texto.isNotEmpty()) {
                                val tinte = linea.color?.let { colorDeConsolaMoka(it, fondoOscuro) }
                                if (tinte != null) {
                                    withStyle(SpanStyle(color = tinte)) { append(linea.texto) }
                                } else {
                                    append(linea.texto)
                                }
                                alInicioDeLinea = linea.texto.last() == '\n'
                            }
                        }
                    }
                }
                Text(
                    texto,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = tamanio.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (pidiendoEntrada) {
                var linea by remember { mutableStateOf("") }
                // El campo toma el foco solo al aparecer: se puede seguir
                // jugando o respondiendo sin volver a hacer clic en el.
                val foco = remember { FocusRequester() }
                LaunchedEffect(Unit) { foco.requestFocus() }
                // Un unico punto de envio para ENTER y para el boton.
                val enviar = { onEnviar(linea); linea = "" }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = linea,
                        onValueChange = { linea = it },
                        singleLine = true,
                        label = { Text("Entrada del programa") },
                        // ImeAction.Send: ENTER en escritorio y el boton
                        // "Enviar" del teclado en Android disparan onSend.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { enviar() }),
                        modifier = Modifier.weight(1f).focusRequester(foco)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = enviar) { Text("Enviar") }
                }
            }
        }
    }
}
