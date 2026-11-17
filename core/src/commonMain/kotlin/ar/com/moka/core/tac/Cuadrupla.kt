// ============================================================================
// Cuadrupla.kt - UNA instruccion del codigo de 3 direcciones
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/tac/Cuadrupla.kt
// (documento de diseno, seccion 4.5.1)
//
//     (op, arg1, arg2, resultado)
//
// Se llama "de tres direcciones" porque referencia como maximo tres
// lugares: dos fuentes (arg1, arg2) y un destino (resultado). Los huecos
// que una operacion no usa son null y se dibujan "_".
//
// Dos detalles de modelado (seccion 4.5.1):
//
// 1) Las ETIQUETAS son cuadruplas: (etiqueta, _, _, L1). Los saltos las
//    refieren por nombre simbolico; la MaquinaTac arma la tabla
//    etiqueta -> indice antes de ejecutar.
//
// 2) En las dos escrituras de memoria (asignar_campo, asignar_indice) el
//    campo `resultado` lleva el VALOR que se almacena, porque una
//    escritura no produce un valor nuevo:
//        (asignar_indice, v, 1, 7)     significa   v[1] = 7
//
// `origen` es la posicion del codigo fuente que genero la cuadrupla: es
// el hilo que usa la GUI para resaltar "esta linea del programa = estas
// cuadruplas", y la MaquinaTac para informar donde fallo un error de
// ejecucion (seccion 4.6).
// ============================================================================

package ar.com.moka.core.tac

import ar.com.moka.core.ast.Posicion

data class Cuadrupla(
    val indice: Int,
    val op: Operacion,
    val arg1: Operando?,
    val arg2: Operando?,
    val resultado: Operando?,
    val origen: Posicion?
)
