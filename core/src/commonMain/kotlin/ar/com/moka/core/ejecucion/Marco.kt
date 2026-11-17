// =======================================================================
//  Marco.kt — Marco de pila de la maquina TAC (sec. 4.6)
// =======================================================================
//  Un marco por llamada activa: guarda las variables de la funcion (por
//  idUnico, lo que le da el sombreado de ambitos gratis), sus
//  temporales, la direccion de retorno y el temporal del llamador que
//  recibe el resultado. `capturarRetorno` lo usa la maquina para las
//  llamadas internas que ella misma dispara (por ejemplo el a_cadena()
//  definido por el usuario al imprimir un objeto).
// =======================================================================
package ar.com.moka.core.ejecucion

import ar.com.moka.core.tac.Valor

class Marco(
    // Nombre de la unidad ("factorial", "Alumno.constructor").
    val nombre: String,
    // Indice de la cuadrupla siguiente a la llamada en el llamador.
    val pcRetorno: Int,
    // Temporal del llamador que recibe el valor de retorno; null en
    // llamadas a unidades vacio o cuyo resultado se descarta.
    val destinoRetorno: Int?,
    // idUnico -> valor actual.
    val variables: MutableMap<String, Valor> = mutableMapOf(),
    // numero de temporal -> valor actual.
    val temporales: MutableMap<Int, Valor> = mutableMapOf(),
    // true cuando la maquina apilo este marco para capturar su
    // resultado (a_cadena interno); el valor queda en valorCapturado
    // en vez de escribirse en un temporal del llamador.
    val capturarRetorno: Boolean = false
) {
    var valorCapturado: Valor? = null
}
