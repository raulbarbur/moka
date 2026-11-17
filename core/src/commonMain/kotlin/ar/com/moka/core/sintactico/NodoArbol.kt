// =======================================================================
//  NodoArbol.kt — Copia propia del arbol de parseo (sec. 4.1 y 4.2)
// =======================================================================
//  El parse tree de ANTLR se copia a este tipo de datos propio dentro del
//  nucleo: la UI nunca ve tipos de ANTLR (regla de la sec. 4.2). Un nodo
//  es una regla del parser ("sentenciaSi", "expresion") con hijos, o una
//  hoja token ("SI", "imprimir"). Los nodos de error (ErrorNode y los
//  tokens de error lexico) quedan marcados para pintarlos en rojo.
// =======================================================================
package ar.com.moka.core.sintactico

import ar.com.moka.core.ast.Posicion

data class NodoArbol(
    val nombre: String,                     // regla ("sentenciaSi") o tipo de token ("SI")
    val esHoja: Boolean,
    val lexema: String? = null,             // solo hojas: texto del token
    val esError: Boolean = false,           // ErrorNode de ANTLR o token ERROR_CHAR / CADENA_ABIERTA
    val pos: Posicion? = null,
    val hijos: List<NodoArbol> = emptyList()
)
