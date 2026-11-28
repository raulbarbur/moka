// =======================================================================
//  UtilidadesPlataforma.kt - gestos y cursores multiplataforma (5B)
// =======================================================================
//  Dos piezas:
//
//    1. zoomConRueda: Ctrl + rueda del mouse cambia el tamano de letra.
//       En 5B era UN zoom global enganchado en la raiz del taller; desde
//       5C se engancha por panel (Taller lo pone en el editor, la
//       consola, los problemas y el panel de fase visible) y cada uno
//       cambia solo su propio tamano (EstadoPaneles). La mecanica es la
//       misma: lee el evento en el pase Initial, que baja de la raiz
//       hacia el componente; con Ctrl apretado el evento se consume ahi
//       y el panel NO se desplaza mientras se hace zoom; sin Ctrl, la
//       rueda sigue su camino normal y el panel scrollea como siempre.
//       (Reemplaza al atajo Ctrl+'+'/Ctrl+'-', que en escritorio
//       ensuciaba el texto con el caracter tipeado.)
//
//    2. cursorDivisorVertical/Horizontal: el cursor de "flecha doble"
//       al pasar el mouse por un divisor. Eso SI depende de la
//       plataforma (en escritorio es un cursor nativo de AWT), asi que
//       se declara con "expect" y cada plataforma tiene su version con
//       "actual": desktopMain/UtilidadesPlataforma.desktop.kt y
//       androidMain/UtilidadesPlataforma.android.kt.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

// Ctrl + rueda: rueda hacia adelante agranda, hacia atras achica.
// alZoom recibe +1 o -1; quien llama lo convierte en el tamano nuevo.
// rememberUpdatedState: el bloque de pointerInput arranca una sola vez;
// sin esto seguiria llamando al primer alZoom, con el tamano viejo.
internal fun Modifier.zoomConRueda(alZoom: (Int) -> Unit): Modifier = composed {
    val zoomActual = rememberUpdatedState(alZoom)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val evento = awaitPointerEvent(PointerEventPass.Initial)
                if (evento.type == PointerEventType.Scroll && evento.keyboardModifiers.isCtrlPressed) {
                    val delta = evento.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (delta != 0f) {
                        zoomActual.value(if (delta < 0f) 1 else -1)
                        // Consumir para que el panel bajo el mouse no se
                        // desplace mientras se hace zoom.
                        evento.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}

// Cursor de la plataforma para una barra divisora: la barra VERTICAL se
// arrastra hacia los costados (flecha <->) y la HORIZONTAL hacia arriba
// y abajo (flecha de dos puntas vertical).
internal expect fun cursorDivisorVertical(): PointerIcon
internal expect fun cursorDivisorHorizontal(): PointerIcon


// Pinza con dos dedos (7.13): separar los dedos agranda la letra, juntarlos
// la achica, igual que en el arbol grafico.
//
// Como funciona:
//   - Se mira el evento en el pase Initial (antes que los hijos). Con UN
//     dedo no se toca nada: el scroll y el arrastre siguen como siempre.
//   - Con DOS o mas dedos, calculateZoom() dice cuanto se separaron desde
//     el evento anterior (1.0 = igual, 1.1 = 10% mas separados). Se
//     multiplican en "acumulado" y el tamano nuevo es el de partida por
//     ese acumulado (siempre desde el tamano que tenia al empezar el gesto,
//     asi no se desvia).
//   - Mientras hay pinza se consumen los cambios para que el panel no se
//     desplace ni se mueva el cursor de texto.
//   - alCambiar recibe el tamano NUEVO ya redondeado; quien llama lo acota
//     (EstadoPaneles.fijarTamanio y alCambiarTamanio ya lo hacen).
internal fun Modifier.zoomConPinza(tamanioActual: Int, alCambiar: (Int) -> Unit): Modifier = composed {
    val tamanio = rememberUpdatedState(tamanioActual)
    val cambiar = rememberUpdatedState(alCambiar)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val partida = tamanio.value.toFloat()
            var acumulado = 1f
            do {
                val evento = awaitPointerEvent(PointerEventPass.Initial)
                if (evento.changes.size >= 2) {
                    val zoom = evento.calculateZoom()
                    if (zoom != 1f) {
                        acumulado *= zoom
                        val nuevo = (partida * acumulado).roundToInt()
                        if (nuevo != tamanio.value) cambiar.value(nuevo)
                        evento.changes.forEach { it.consume() }
                    }
                }
            } while (evento.changes.any { it.pressed })
        }
    }
}
