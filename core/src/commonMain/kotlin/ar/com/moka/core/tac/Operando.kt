// ============================================================================
// Operando.kt - Los huecos de una cuadrupla: arg1, arg2 y resultado
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/tac/Operando.kt
// (documento de diseno, seccion 4.5.1)
//
// Cada operando es una de cinco cosas:
//
//   Constante  un literal ya cocinado: 4, 3.14, "hola", verdadero, nulo
//   Temporal   t1, t2, ... resultados intermedios; la numeracion se
//              REINICIA en cada funcion (viven en el marco de la llamada)
//   Variable   una variable YA RESUELTA por el semantico (lleva su
//              SimboloVariable: la ejecucion no busca nombres, trabaja
//              con simbolos, seccion 2.14). El idUnico distingue
//              sombreados: x y x#2
//   Etiqueta   L1, L2, ... destinos de salto (tambien son cuadruplas,
//              con op = etiqueta)
//   Nombre     texto pelado para lo que no es variable ni temporal:
//              nombre de funcion en llamar, de clase en nuevo_objeto,
//              de campo en leer_campo / asignar_campo
//
// Un hueco sin contenido no es un operando "vacio": es null en la
// cuadrupla y se dibuja como "_" en el listado.
// ============================================================================

package ar.com.moka.core.tac

import ar.com.moka.core.semantico.SimboloVariable

sealed interface Operando {
    data class Constante(val valor: Valor) : Operando
    data class Temporal(val numero: Int) : Operando
    data class Variable(val simbolo: SimboloVariable) : Operando
    data class Etiqueta(val nombre: String) : Operando
    data class Nombre(val texto: String) : Operando
}
