// =======================================================================
//  EventoEjecucion.kt - Eventos del Flow de ejecucion (sec. 4.6)
// =======================================================================
//  La MaquinaTac NO imprime ni lee directamente: emite un Flow de estos
//  eventos y quien la invoca decide que hacer (CLI: print/readLine;
//  GUI: append al textbox; tests: acumular en una lista). Los nombres y
//  el contrato son los del diagrama de la sec. 4.6 del documento de
//  disenio, mas MarcoVista, que es la vista de texto de la pila que
//  acompana al modo paso a paso.
// =======================================================================
package ar.com.moka.core.ejecucion

import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.tac.Cuadrupla

sealed class EventoEjecucion {

    // Texto producido por `imprimir` (lleva el "\n" final) y por
    // `imprimir_sin_salto` (sin "\n"). Se concatena tal cual.
    data class Salida(val texto: String) : EventoEjecucion()

    // La maquina esta por suspenderse esperando una linea (builtin
    // leer / leer_entero / leer_flotante). El prompt es null en esta
    // fase: Moka no tiene prompts en la entrada.
    data class PidiendoEntrada(val prompt: String?) : EventoEjecucion()

    // Un paso en modo paso a paso: la cuadrupla que esta por
    // ejecutarse y la vista actual de la pila de marcos.
    data class Paso(
        val pc: Int,
        val cuadrupla: Cuadrupla,
        val marcos: List<MarcoVista>,
        // Foto independiente de globales y campos estaticos YA escritos.
        // Claves: idUnico de la variable o Clase.campo para estaticos.
        // Default conserva los constructores de tres argumentos existentes.
        val globales: Map<String, String> = emptyMap()
    ) : EventoEjecucion()

    // Error en tiempo de ejecucion (codigos EJE-xxx de la sec. 2.9 y
    // 4.6). La posicion apunta a la sentencia fuente que lo origino,
    // propagada por el generador TAC.
    data class ErrorEjecucion(val diagnostico: Diagnostico) : EventoEjecucion()

    // El programa termino sin errores, con la cantidad de cuadruplas
    // ejecutadas (informativa; la GUI puede ignorarla).
    data class Fin(val cuadruplasEjecutadas: Long) : EventoEjecucion()
}

// Vista de texto de la pila de marcos para el modo paso a paso: cada
// marco con sus variables y temporales ya renderizados.
data class MarcoVista(
    val nombre: String,
    val variables: Map<String, String>,
    val temporales: Map<String, String>
)
