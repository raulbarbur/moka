// =======================================================================
//  ArbolGrafico.kt - Vista grafica del AST con Canvas
// =======================================================================
//  Extra posterior a 7.11. El outline textual sigue existiendo; esta
//  vista dibuja el AST como un diagrama de cajas para ver la estructura
//  de una mirada. Solo recibe ElementoVista (la misma vista generica que
//  alimenta OutlineArbol): no conoce clases del AST ni de ANTLR.
//
//  Interaccion:
//    - arrastrar: mover (pan)
//    - pinza o rueda del mouse: zoom alrededor del punto tocado
//    - botones + / -: zoom alrededor del centro
//    - Reencuadrar: vuelve a mostrar el arbol completo
//    - tocar un nodo: publica su Posicion, como hace el outline
//
//  Todo el dibujo y el layout viven aca; no se agregan dependencias ni
//  estado al controlador. Las coordenadas internas son pixeles del
//  Canvas y se transforman a pantalla con escala + desplazamiento.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.core.ast.Posicion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val ESTILO_NODO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
private const val ESPACIO_ENTRE_HERMANOS = 24f
private const val ESPACIO_ENTRE_NIVELES = 44f
private const val MARGEN_ARBOL = 28f
private const val RADIO_NODO = 8f
private const val ESCALA_MINIMA = 0.05f
private const val ESCALA_MAXIMA = 4f

// Nodo ya medido y posicionado. x/y son el CENTRO horizontal y el borde
// superior, porque eso hace sencillo centrar padres sobre sus hijos.
private class CajaNodo(
    val texto: String,
    val pos: Posicion?,
    val esError: Boolean,
    val medicion: TextLayoutResult,
    val ancho: Float,
    val alto: Float,
    val hijos: List<CajaNodo>
) {
    var x = 0f
    var y = 0f
    var anchoSubarbol = 0f

    val izquierda: Float get() = x - ancho / 2f
    val derecha: Float get() = x + ancho / 2f
}

private class LayoutGrafico(
    val nodos: List<CajaNodo>,
    val ancho: Float,
    val alto: Float
)

@Composable
fun ArbolGrafico(
    raiz: ElementoVista,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    val medidorTexto = rememberTextMeasurer()
    val esquema = MaterialTheme.colorScheme
    val layout = remember(raiz, medidorTexto) { construirLayout(raiz, medidorTexto) }

    var tamano by remember { mutableStateOf(IntSize.Zero) }
    var escala by remember(layout) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(layout) { mutableStateOf(Offset.Zero) }
    var encuadreInicial by remember(layout) { mutableStateOf(false) }

    // Reencuadre manual e inicial. Si el usuario ya movio la camara, un
    // cambio de tamaño de ventana NO le pisa su encuadre.
    fun reencuadrar() {
        if (tamano.width <= 0 || tamano.height <= 0) return
        val disponible = Size(tamano.width.toFloat(), tamano.height.toFloat())
        val nuevaEscala = min(disponible.width / layout.ancho, disponible.height / layout.alto)
            .coerceIn(ESCALA_MINIMA, 1f)
        escala = nuevaEscala
        desplazamiento = Offset(
            (disponible.width - layout.ancho * nuevaEscala) / 2f,
            (disponible.height - layout.alto * nuevaEscala) / 2f
        )
    }

    LaunchedEffect(layout, tamano) {
        if (!encuadreInicial && tamano.width > 0 && tamano.height > 0) {
            reencuadrar()
            encuadreInicial = true
        }
    }

    // Mantiene fijo el punto del arbol que esta bajo el foco del zoom.
    fun zoomAlrededor(foco: Offset, factor: Float) {
        val anterior = escala
        val nueva = (anterior * factor).coerceIn(ESCALA_MINIMA, ESCALA_MAXIMA)
        if (nueva == anterior) return
        val focoEnArbol = Offset(
            (foco.x - desplazamiento.x) / anterior,
            (foco.y - desplazamiento.y) / anterior
        )
        escala = nueva
        desplazamiento = Offset(
            foco.x - focoEnArbol.x * nueva,
            foco.y - focoEnArbol.y * nueva
        )
    }

    Box(modifier.onSizeChanged { tamano = it }) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(layout) {
                    detectTapGestures { toque ->
                        val punto = Offset(
                            (toque.x - desplazamiento.x) / escala,
                            (toque.y - desplazamiento.y) / escala
                        )
                        val tocado = layout.nodos.firstOrNull { nodo ->
                            punto.x >= nodo.izquierda && punto.x <= nodo.derecha &&
                                    punto.y >= nodo.y && punto.y <= nodo.y + nodo.alto
                        }
                        tocado?.pos?.let(onSeleccionar)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroide, pan, zoom, _ ->
                        zoomAlrededor(centroide, zoom)
                        desplazamiento = Offset(
                            desplazamiento.x + pan.x,
                            desplazamiento.y + pan.y
                        )
                    }
                }
                .pointerInput(Unit) {
                    // Raw events multiplataforma: Android no emite Scroll y
                    // este bucle simplemente sigue esperando; desktop si.
                    awaitPointerEventScope {
                        while (true) {
                            val evento = awaitPointerEvent()
                            if (evento.type != PointerEventType.Scroll) continue
                            val cambio = evento.changes.firstOrNull() ?: continue
                            val delta = cambio.scrollDelta.y
                            if (delta != 0f) {
                                // Rueda hacia arriba acerca; hacia abajo aleja.
                                zoomAlrededor(cambio.position, if (delta < 0f) 1.12f else 1f / 1.12f)
                                cambio.consume()
                            }
                        }
                    }
                }
        ) {
            withTransform({
                translate(desplazamiento.x, desplazamiento.y)
                scale(escala, escala, Offset.Zero)
            }) {
                dibujarAristas(layout, esquema.outline)
                dibujarNodos(layout, seleccion, esquema)
            }
        }

        // Controles chicos, encima del Canvas. En pantalla angosta pueden
        // desplazarse horizontalmente en vez de tapar todo el diagrama.
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    zoomAlrededor(
                        Offset(tamano.width / 2f, tamano.height / 2f),
                        1f / 1.25f
                    )
                }) { Text("-") }
                Text("${(escala * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = {
                    zoomAlrededor(
                        Offset(tamano.width / 2f, tamano.height / 2f),
                        1.25f
                    )
                }) { Text("+") }
                TextButton(onClick = ::reencuadrar) { Text("Reencuadrar") }
            }
        }

        Text(
            "Arrastra para mover. Pellizca o usa la rueda para zoom. Toca un nodo para ir al codigo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
        )
    }
}

// ----------------------------- construccion -----------------------------

private fun construirLayout(raiz: ElementoVista, medidor: TextMeasurer): LayoutGrafico {
    val raizCaja = medirArbol(raiz, medidor)
    calcularAnchos(raizCaja)
    val alturasPorNivel = alturasPorNivel(raizCaja)
    val ysPorNivel = mutableMapOf<Int, Float>()
    var y = MARGEN_ARBOL
    for (nivel in 0..alturasPorNivel.keys.maxOf { it }) {
        ysPorNivel[nivel] = y
        y += alturasPorNivel.getValue(nivel) + ESPACIO_ENTRE_NIVELES
    }
    ubicar(raizCaja, MARGEN_ARBOL, 0, ysPorNivel)

    val nodos = mutableListOf<CajaNodo>()
    recolectar(raizCaja, nodos)
    return LayoutGrafico(
        nodos = nodos,
        ancho = raizCaja.anchoSubarbol + MARGEN_ARBOL * 2,
        alto = y - ESPACIO_ENTRE_NIVELES + MARGEN_ARBOL
    )
}

private fun medirArbol(elemento: ElementoVista, medidor: TextMeasurer): CajaNodo {
    val medicion = medidor.measure(elemento.texto, style = ESTILO_NODO, maxLines = 1)
    return CajaNodo(
        texto = elemento.texto,
        pos = elemento.pos,
        esError = elemento.esError,
        medicion = medicion,
        ancho = medicion.size.width + 24f,
        alto = medicion.size.height + 14f,
        hijos = elemento.hijos.map { medirArbol(it, medidor) }
    )
}

// Ancho reservado por cada subarbol: sus hijos o su propia caja, lo que
// sea mayor. Evita que ramas vecinas se pisen.
private fun calcularAnchos(nodo: CajaNodo): Float {
    for (hijo in nodo.hijos) calcularAnchos(hijo)
    val anchoHijos = if (nodo.hijos.isEmpty()) 0f
    else nodo.hijos.fold(0f) { total, hijo -> total + hijo.anchoSubarbol } +
            ESPACIO_ENTRE_HERMANOS * (nodo.hijos.size - 1)
    nodo.anchoSubarbol = max(nodo.ancho, anchoHijos)
    return nodo.anchoSubarbol
}

private fun alturasPorNivel(raiz: CajaNodo): Map<Int, Float> {
    val alturas = mutableMapOf<Int, Float>()
    fun visitar(nodo: CajaNodo, nivel: Int) {
        alturas[nivel] = max(alturas[nivel] ?: 0f, nodo.alto)
        nodo.hijos.forEach { visitar(it, nivel + 1) }
    }
    visitar(raiz, 0)
    return alturas
}

private fun ubicar(
    nodo: CajaNodo,
    izquierdaDisponible: Float,
    nivel: Int,
    ysPorNivel: Map<Int, Float>
) {
    nodo.y = ysPorNivel.getValue(nivel)
    if (nodo.hijos.isEmpty()) {
        nodo.x = izquierdaDisponible + nodo.ancho / 2f
        return
    }

    val anchoHijos = nodo.hijos.fold(0f) { total, hijo -> total + hijo.anchoSubarbol } +
            ESPACIO_ENTRE_HERMANOS * (nodo.hijos.size - 1)
    var izquierda = izquierdaDisponible + (nodo.anchoSubarbol - anchoHijos) / 2f
    for (hijo in nodo.hijos) {
        ubicar(hijo, izquierda, nivel + 1, ysPorNivel)
        izquierda += hijo.anchoSubarbol + ESPACIO_ENTRE_HERMANOS
    }
    // El padre queda centrado sobre el conjunto de sus hijos.
    nodo.x = (nodo.hijos.first().x + nodo.hijos.last().x) / 2f
}

private fun recolectar(nodo: CajaNodo, salida: MutableList<CajaNodo>) {
    salida += nodo
    nodo.hijos.forEach { recolectar(it, salida) }
}

// ------------------------------- dibujo --------------------------------

private fun DrawScope.dibujarAristas(layout: LayoutGrafico, color: Color) {
    for (nodo in layout.nodos) {
        for (hijo in nodo.hijos) {
            // Codo ortogonal: baja desde el padre, cruza al nivel del
            // hijo y entra por su borde superior. Asi se lee el parentesco
            // incluso cuando una rama es muy ancha.
            val mediaY = (nodo.y + nodo.alto + hijo.y) / 2f
            drawLine(color, Offset(nodo.x, nodo.y + nodo.alto), Offset(nodo.x, mediaY), 2f)
            drawLine(color, Offset(nodo.x, mediaY), Offset(hijo.x, mediaY), 2f)
            drawLine(color, Offset(hijo.x, mediaY), Offset(hijo.x, hijo.y), 2f)
        }
    }
}

private fun DrawScope.dibujarNodos(
    layout: LayoutGrafico,
    seleccion: Posicion?,
    esquema: ColorScheme
) {
    for (nodo in layout.nodos) {
        val seleccionado = nodo.pos != null && nodo.pos == seleccion
        val fondo = when {
            seleccionado -> esquema.primaryContainer
            nodo.esError -> esquema.errorContainer
            else -> esquema.surface
        }
        val borde = when {
            seleccionado -> esquema.primary
            nodo.esError -> esquema.error
            else -> esquema.outline
        }
        val texto = when {
            seleccionado -> esquema.onPrimaryContainer
            nodo.esError -> esquema.onErrorContainer
            else -> esquema.onSurface
        }
        val origen = Offset(nodo.izquierda, nodo.y)
        val tamano = Size(nodo.ancho, nodo.alto)
        drawRoundRect(fondo, origen, tamano, CornerRadius(RADIO_NODO, RADIO_NODO))
        drawRoundRect(
            borde,
            origen,
            tamano,
            CornerRadius(RADIO_NODO, RADIO_NODO),
            style = Stroke(width = if (seleccionado) 3f else 1.5f)
        )
        drawText(
            textLayoutResult = nodo.medicion,
            color = texto,
            topLeft = Offset(nodo.izquierda + 12f, nodo.y + 7f)
        )
    }
}
