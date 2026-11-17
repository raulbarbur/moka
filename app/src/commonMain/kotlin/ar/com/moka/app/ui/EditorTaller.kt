// =======================================================================
//  EditorTaller.kt — El editor de codigo fuente (sec. 5.3, panel 1)
// =======================================================================
//  Version 7.4: ademas del subrayado de diagnosticos (7.3), resalta con
//  fondo el rango de la SELECCION compartida (sec. 5.1): tocar un token
//  en el panel, un problema en la lista o un nodo del arbol (7.5) pinta
//  su fragmento aca.
//
//  Todo se pinta con una VisualTransformation: el texto no cambia
//  (OffsetMapping.Identity), solo se le superponen estilos, asi que
//  cursor y scroll no se alteran.
//
//  Aproximacion declarada en 7.3: el subrayado es simple, no ondulado
//  (Compose no trae decoracion ondulada lista).
//
//  Siguen pendientes con paso propio: resaltado de sintaxis (7.8) y
//  abrir/guardar (7.9).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad

// Ambar para las advertencias: el tema no trae un "amarillo de warning"
// propio, y este se lee bien en tema claro y oscuro.
private val COLOR_ADVERTENCIA = Color(0xFFB8860B)

@Composable
fun EditorTaller(
    fuente: String,
    diagnosticos: List<Diagnostico>,
    seleccion: Posicion?,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorError = MaterialTheme.colorScheme.error
    val colorSeleccion = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    // Se recalcula solo cuando cambia el texto, la lista o la seleccion.
    val transformacion = remember(fuente, diagnosticos, seleccion, colorError) {
        SubrayadoDiagnosticos(diagnosticos, seleccion, colorError, COLOR_ADVERTENCIA, colorSeleccion)
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
        Column {
            Text(
                "Codigo fuente",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            BasicTextField(
                value = fuente,
                onValueChange = onCambio,
                visualTransformation = transformacion,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// Pinta: 1) fondo tenue sobre el rango seleccionado, 2) subrayado de
// color sobre el rango de cada diagnostico. Las posiciones son las del
// nucleo: linea 1-based, columna 0-based, fin inclusivo; aca se
// convierten a desplazamientos de caracter.
private class SubrayadoDiagnosticos(
    private val diagnosticos: List<Diagnostico>,
    private val seleccion: Posicion?,
    private val colorError: Color,
    private val colorAdvertencia: Color,
    private val colorSeleccion: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (diagnosticos.isEmpty() && seleccion == null) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val fuente = text.text
        val inicios = iniciosDeLinea(fuente)
        val constructor = AnnotatedString.Builder(text)

        val sel = seleccion
        if (sel != null) {
            val rango = rangoDe(inicios, fuente.length, sel)
            if (rango != null) {
                constructor.addStyle(SpanStyle(background = colorSeleccion), rango.first, rango.second)
            }
        }

        for (d in diagnosticos) {
            val rango = rangoDe(inicios, fuente.length, d.pos) ?: continue
            constructor.addStyle(
                SpanStyle(
                    color = if (d.severidad == Severidad.ERROR) colorError else colorAdvertencia,
                    textDecoration = TextDecoration.Underline
                ),
                rango.first, rango.second
            )
        }
        return TransformedText(constructor.toAnnotatedString(), OffsetMapping.Identity)
    }

    // Posicion del nucleo -> par (desde, hasta) de desplazamientos, o
    // null si quedo vacia o fuera de rango (el texto cambio despues).
    private fun rangoDe(inicios: IntArray, largo: Int, pos: Posicion): Pair<Int, Int>? {
        val desde = desplazamiento(inicios, pos.linea, pos.columna).coerceIn(0, largo)
        val hasta = (desplazamiento(inicios, pos.finLinea, pos.finColumna) + 1) // fin inclusivo
            .coerceIn(desde, largo)
        return if (hasta > desde) desde to hasta else null
    }

    // Desplazamiento de inicio de cada linea (el indice 0 es la linea 1).
    private fun iniciosDeLinea(fuente: String): IntArray {
        val inicios = mutableListOf(0)
        for (i in fuente.indices) {
            if (fuente[i] == '\n') inicios.add(i + 1)
        }
        return inicios.toIntArray()
    }

    // Linea 1-based + columna 0-based -> desplazamiento en el texto.
    // Una linea fuera de rango cae lejos, donde coerceIn lo descarta.
    private fun desplazamiento(inicios: IntArray, linea: Int, columna: Int): Int {
        if (linea < 1 || linea > inicios.size) return Int.MAX_VALUE / 2
        return inicios[linea - 1] + columna
    }
}
