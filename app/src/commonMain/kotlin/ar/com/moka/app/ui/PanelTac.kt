// =======================================================================
//  PanelTac.kt — El panel de codigo de 3 direcciones (sec. 5.3, panel 5)
// =======================================================================
//  Dos vistas conmutables:
//    Tabla:  una fila por cuadrupla (#, op, arg1, arg2, resultado; los
//            operandos se dibujan con ProgramaTac.textoDe, el mismo
//            render del nucleo). Cabeceras por funcion (inicio_func),
//            etiquetas resaltadas, saltos con flecha al indice destino.
//    Texto:  la notacion "(op, arg1, arg2, res)" tal como la imprime
//            ProgramaTac.comoTexto().
//
//  Sincronizacion (sec. 5.1): tocar una cuadrupla selecciona su posicion
//  origen y el editor resalta la linea que la genero; al reves, las
//  cuadruplas cuya linea origen es la de la seleccion se marcan con un
//  fondo tenue. En modo paso a paso, la cuadrupla actual (pc) va con
//  fondo fuerte.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.tac.Cuadrupla
import ar.com.moka.core.tac.Operacion
import ar.com.moka.core.tac.Operando
import ar.com.moka.core.tac.ProgramaTac

private val MONO_CHICO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable
fun PanelTac(
    tac: Artefacto.Tac?,
    pasoActual: EventoEjecucion.Paso?,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    // 5C: tamano de letra propio del panel.
    tamanio: Int,
    modifier: Modifier = Modifier
) {
    if (tac == null) {
        PanelVacio("Todavia no corrio la generacion de TAC.", modifier)
        return
    }
    val programa = tac.programa
    val mono = MONO_CHICO.copy(fontSize = tamanio.sp)
    var vistaTexto by remember(programa) { mutableStateOf(false) }

    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TAC (${programa.cuadruplas.size} cuadruplas)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            FilterChip(selected = !vistaTexto, onClick = { vistaTexto = false }, label = { Text("Tabla") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = vistaTexto, onClick = { vistaTexto = true }, label = { Text("Notacion textual") })
        }

        if (vistaTexto) {
            Text(
                programa.comoTexto(),
                style = mono,
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
                items(programa.cuadruplas, key = { it.indice }) { c ->
                    FilaCuadrupla(
                        programa = programa,
                        c = c,
                        esActual = pasoActual?.pc == c.indice,
                        esDeLaSeleccion = seleccion != null && c.origen?.linea == seleccion.linea,
                        onSeleccionar = onSeleccionar,
                        estilo = mono
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaCuadrupla(
    programa: ProgramaTac,
    c: Cuadrupla,
    esActual: Boolean,
    esDeLaSeleccion: Boolean,
    onSeleccionar: (Posicion) -> Unit,
    estilo: TextStyle
) {
    // Cabecera de funcion: no es una instruccion, marca el comienzo.
    if (c.op == Operacion.INICIO_FUNC) {
        Text(
            "funcion ${programa.textoDe(c.arg1)}",
            style = estilo,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 3.dp, horizontal = 4.dp)
        )
        return
    }

    val fondo = when {
        esActual -> MaterialTheme.colorScheme.secondaryContainer       // paso a paso: la actual
        esDeLaSeleccion -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val colorEtiqueta = c.op == Operacion.ETIQUETA
    val fila = Modifier.fillMaxWidth()
        .background(fondo)
        .let { base ->
            val origen = c.origen
            if (origen != null) base.clickable { onSeleccionar(origen) } else base
        }
        .padding(vertical = 1.dp, horizontal = 4.dp)

    Row(fila, verticalAlignment = Alignment.CenterVertically) {
        Text("${c.indice}", modifier = Modifier.width(36.dp), style = estilo, color = MaterialTheme.colorScheme.outline)
        Text(
            c.op.simbolo,
            modifier = Modifier.width(88.dp),
            style = estilo,
            color = if (colorEtiqueta) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )
        Text(programa.textoDe(c.arg1), modifier = Modifier.weight(1f), style = estilo, maxLines = 1)
        Text(programa.textoDe(c.arg2), modifier = Modifier.weight(1f), style = estilo, maxLines = 1)
        // Saltos: la etiqueta destino con flecha al indice que le toca.
        Text(resultadoDe(programa, c), modifier = Modifier.weight(1f), style = estilo, maxLines = 1)
    }
}

// El resultado de una cuadrupla; en saltos (ir_a / si_falso /
// si_verdadero) agrega la flecha al indice de la etiqueta destino.
private fun resultadoDe(programa: ProgramaTac, c: Cuadrupla): String {
    val texto = programa.textoDe(c.resultado)
    val esSalto = c.op == Operacion.IR_A || c.op == Operacion.SI_FALSO || c.op == Operacion.SI_VERDADERO
    if (!esSalto) return texto
    val destino = (c.resultado as? Operando.Etiqueta)?.let { programa.etiquetas[it.nombre] }
    return if (destino != null) "$texto \u2192 $destino" else texto
}
