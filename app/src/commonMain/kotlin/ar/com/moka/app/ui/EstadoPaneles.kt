// =======================================================================
//  EstadoPaneles.kt - tamano de letra y visibilidad POR PANEL (5C)
// =======================================================================
//  Hasta 5B habia UN solo tamano de letra (el del editor) y un zoom
//  global con Ctrl + rueda que lo cambiaba desde cualquier panel.
//  Desde 5C cada panel tiene el suyo: la consola, los problemas y cada
//  panel de fase (tokens, arbol, semantica, TAC, ejecucion) recuerdan
//  su tamano por separado, y Ctrl + rueda cambia SOLO el panel que
//  esta bajo el mouse. El editor conserva el suyo (el mismo que ya
//  movian los items A+/A- del menu Editar, que siguen siendo del
//  editor).
//
//  Tambien la VISIBILIDAD es por panel: el menu Ver -> Paneles deja
//  prender y apagar cada uno (tokens, arbol, semantica, TAC, ejecucion,
//  consola, problemas). El editor no se apaga: sin el no hay taller.
//
//  Todo se guarda en los ajustes de la plataforma (claves "tam.<id>" y
//  "panel.<id>"), asi que sobrevive al cierre de la app, igual que el
//  tema y el modo. Antes el tamano del editor solo sobrevivia a una
//  rotacion de pantalla.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import ar.com.moka.app.ajustes.AjustesMoka
import ar.com.moka.app.ajustes.recordarAjustesMoka

// Ids estables de cada zona del taller. Se usan como clave de ajustes,
// asi que NO se renombran: renombrar uno pierde el ajuste guardado.
const val PANEL_EDITOR = "editor"
const val PANEL_CONSOLA = "consola"
const val PANEL_PROBLEMAS = "problemas"
const val PANEL_TOKENS = "tokens"
const val PANEL_ARBOL = "arbol"
const val PANEL_SEMANTICA = "semantica"
const val PANEL_TAC = "tac"
const val PANEL_EJECUCION = "ejecucion"

// Los paneles de fase, en el orden de siempre (el pipeline, sec. 5.3).
val PANELES_CON_FASE = listOf(PANEL_TOKENS, PANEL_ARBOL, PANEL_SEMANTICA, PANEL_TAC, PANEL_EJECUCION)

// Los que el menu Ver -> Paneles deja prender y apagar: todos menos el
// editor, que es fijo.
val PANELES_CONMUTABLES = PANELES_CON_FASE + listOf(PANEL_CONSOLA, PANEL_PROBLEMAS)

// Nombre visible de cada panel (sin tildes, convencion del proyecto).
fun tituloDePanel(id: String): String = when (id) {
    PANEL_EDITOR -> "Codigo"
    PANEL_CONSOLA -> "Consola"
    PANEL_PROBLEMAS -> "Problemas"
    PANEL_TOKENS -> "Tokens"
    PANEL_ARBOL -> "Arbol"
    PANEL_SEMANTICA -> "Semantica"
    PANEL_TAC -> "TAC"
    PANEL_EJECUCION -> "Ejecucion"
    else -> id
}

// Tamano inicial de cada panel: el que ya tenia su texto antes de 5C,
// asi la primera corrida se ve igual que la version anterior.
private val TAMANIO_BASE = mapOf(
    PANEL_EDITOR to 14,
    PANEL_CONSOLA to 13,
    PANEL_PROBLEMAS to 13,
    PANEL_TOKENS to 12,
    PANEL_ARBOL to 12,
    PANEL_SEMANTICA to 12,
    PANEL_TAC to 12,
    PANEL_EJECUCION to 12
)

const val TAMANIO_MINIMO = 8
const val TAMANIO_MAXIMO = 28

// Titular del estado: dos mapas observables (cambiar uno recompone solo
// lo que lo lee) con lectura/escritura de ajustes en cada cambio.
class EstadoPaneles(private val ajustes: AjustesMoka) {

    private val tamanios = mutableStateMapOf<String, Int>()
    private val visibilidad = mutableStateMapOf<String, Boolean>()

    init {
        // Se restauran los ajustes guardados. Un valor roto (texto que
        // no es numero, un tamano fuera de rango) se ignora y queda el
        // tamano base.
        for (id in TAMANIO_BASE.keys) {
            ajustes.leer("tam.$id")?.toIntOrNull()?.let { tamanios[id] = it.coerceIn(TAMANIO_MINIMO, TAMANIO_MAXIMO) }
            ajustes.leer("panel.$id")?.let { visibilidad[id] = it == "si" }
        }
    }

    fun tamanio(panel: String): Int = tamanios[panel] ?: TAMANIO_BASE.getValue(panel)

    fun fijarTamanio(panel: String, nuevo: Int) {
        val acotado = nuevo.coerceIn(TAMANIO_MINIMO, TAMANIO_MAXIMO)
        tamanios[panel] = acotado
        ajustes.escribir("tam.$panel", acotado.toString())
    }

    // El gesto de zoom manda +1/-1; aca se convierte en tamano nuevo.
    fun cambiarTamanio(panel: String, delta: Int) = fijarTamanio(panel, tamanio(panel) + delta)

    // Por defecto todo panel arranca visible.
    fun visible(panel: String): Boolean = visibilidad[panel] ?: true

    fun alternarVisible(panel: String) {
        val nuevo = !visible(panel)
        visibilidad[panel] = nuevo
        ajustes.escribir("panel.$panel", if (nuevo) "si" else "no")
    }
}

@Composable
fun recordarEstadoPaneles(): EstadoPaneles {
    val ajustes = recordarAjustesMoka()
    return remember { EstadoPaneles(ajustes) }
}
