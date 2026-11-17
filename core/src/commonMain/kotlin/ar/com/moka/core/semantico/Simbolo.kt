// ============================================================================
// Simbolo.kt - Lo que significa un NOMBRE en el programa
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/Simbolo.kt
// (documento de diseno, seccion 4.4)
//
// Fase 3: cuando el programador escribe `contador`, ¿que ES contador? La
// respuesta es un Simbolo: nombre + tipo + posicion + datos segun el caso.
//
// Un nombre por si solo no significa nada: `x` puede ser la variable local
// de una funcion, un campo de la clase o la global de arriba de todo. El
// analisis semantico decide A CUAL de esos simbolos se refiere cada uso
// (eso es el "ligado de nombres") y lo guarda en la tabla lateral
// Map<Int, Simbolo>: del id del nodo Nombre al simbolo resuelto.
//
// idUnico distingue variables sombreadas: si dos bloques declaran `x`, sus
// idUnico son "x" y "x#2". El TAC y la GUI las muestran distintas aunque
// tengan el mismo nombre (seccion 4.4).
//
// sealed class (no interface): los simbolos comparten implementacion
// (nombre/tipo/pos abstractos) y no hay necesidad de herencia multiple.
// ============================================================================

package ar.com.moka.core.semantico

import ar.com.moka.core.ast.Posicion

// Donde vive una variable (seccion 4.4). Cambia como se inicializa y
// desde donde se ve: una GLOBAL nace con el programa, una LOCAL con su
// bloque, un PARAMETRO con la llamada, un CAMPO con cada objeto.
enum class Categoria { GLOBAL, LOCAL, PARAMETRO, CAMPO }

sealed class Simbolo {
    abstract val nombre: String
    abstract val tipo: Tipo
    abstract val pos: Posicion
}

class SimboloVariable(
    override val nombre: String,
    override val tipo: Tipo,
    override val pos: Posicion,
    val categoria: Categoria,
    val esConstante: Boolean,
    val esEstatico: Boolean,
    val idUnico: String
) : Simbolo() {
    // La usa alguien? Si nadie la leyo al terminar el analisis, advertimos
    // ("declarada pero nunca usada", seccion 4.8). Es var: la marca la
    // hace la pasada B al resolver cada Nombre.
    var usada: Boolean = false
}

class SimboloFuncion(
    override val nombre: String,
    override val tipo: Tipo,               // el tipo de RETORNO
    override val pos: Posicion,
    val parametros: List<Tipo>,
    val esBuiltin: Boolean,
    val esEstatico: Boolean = false        // solo tiene sentido en metodos
) : Simbolo()

// La clase ES su tabla de miembros: campos, metodos y su constructor
// (null si no se declaro: hay uno vacio implicito, seccion 2.10).
// Los mapas son mutables y el constructor arranca en null porque la
// pasada A del analizador trabaja en dos tiempos: primero registra la
// clase "en cascaron" (para que las demas clases puedan nombrarla) y
// despues completa sus miembros. Nadie mas los toca.
class SimboloClase(
    override val nombre: String,
    override val pos: Posicion,
    val campos: MutableMap<String, SimboloVariable>,
    val metodos: MutableMap<String, SimboloFuncion>,
    var constructor: SimboloFuncion?
) : Simbolo() {
    override val tipo: Tipo get() = TClase(nombre)
}
