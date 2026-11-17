// ============================================================================
// Valor.kt - Los VALORES que pisan el TAC
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/tac/Valor.kt
// (documento de diseno, secciones 4.5.1 y 4.6)
//
// Un Valor es un dato de Moka ya "cocinado": un numero, un texto, nulo.
// En ESTA fase (generacion) solo aparecen como constantes de las
// cuadruplas: el 4 de `entero a = 4`, el "hola" de imprimir("hola").
// En la Fase 5 la MaquinaTac los usa para todo el estado de la ejecucion
// (variables, temporales, heap).
//
// ¿Por que vive en tac/ y no en ejecucion/ como sugiere el arbol de la
// seccion 4.2? Porque Operando.Constante lo necesita AHORA: si viviera en
// ejecucion/, el paquete tac/ dependeria de uno que todavia no existe.
// Cuando llegue la Fase 5, MaquinaTac lo importa de aca sin mover nada.
//
// VArreglo y VObjeto son de REFERENCIA (clases comunes, no data class):
// dos arreglos con los mismos elementos NO son el mismo arreglo. El ==
// de Moka sobre referencias compara identidad, como en Java (sec. 2.6).
// ============================================================================

package ar.com.moka.core.tac

import ar.com.moka.core.semantico.SimboloClase
import ar.com.moka.core.semantico.Tipo

sealed interface Valor {
    data class VEntero(val v: Int) : Valor
    data class VFlotante(val v: Double) : Valor
    data class VBooleano(val v: Boolean) : Valor
    data class VCaracter(val v: Char) : Valor
    data class VCadena(val v: String) : Valor
    object VNulo : Valor
    // El `id` de heap lo asigna la MaquinaTac al crearlos: es el que se
    // muestra en "Alumno@3" cuando un objeto se imprime sin a_cadena()
    // propio (sec. 2.6). Default 0 para no romper nada de la Fase 4.
    class VArreglo(val tipoElemento: Tipo, val datos: Array<Valor>, val id: Int = 0) : Valor
    class VObjeto(val clase: SimboloClase, val campos: MutableMap<String, Valor>, val id: Int = 0) : Valor
}
