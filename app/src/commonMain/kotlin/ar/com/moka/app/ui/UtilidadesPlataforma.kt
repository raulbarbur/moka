// =======================================================================
//  UtilidadesPlataforma.kt - gestos y cursores multiplataforma (5B)
// =======================================================================
//  Dos piezas:
//
//    1. zoomConRueda: Ctrl + rueda del mouse cambia el tamano de letra.
//       Funciona desde CUALQUIER panel (editor, consola, problemas,
//       tokens, arbol, etc.) porque se engancha en la raiz del taller y
//       lee el evento en el pase Initial, que baja de la raiz hacia el
//       componente: con Ctrl apretado el evento se consume ahi y el
//       panel NO se desplaza mientras se hace zoom; sin Ctrl, la rueda
//       sigue su camino normal y el panel scrollea como siempre.
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput

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
