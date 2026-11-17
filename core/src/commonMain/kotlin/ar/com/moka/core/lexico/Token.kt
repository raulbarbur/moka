// =======================================================================
//  Token.kt — Token propio del nucleo (sec. 4.2 y 4.7)
// =======================================================================
//  Lo que la UI, la CLI y los tests reciben como Artefacto.Lexico. Es un
//  tipo DE DATOS PROPIO: la UI nunca ve el Token de ANTLR (regla de la
//  sec. 4.2). La posicion usa la misma convencion que el AST: linea
//  1-based, columna 0-based, fin inclusivo (ver ConstructorAst).
// =======================================================================
package ar.com.moka.core.lexico

import ar.com.moka.core.ast.Posicion

data class Token(
    val tipo: String,           // nombre simbolico de la gramatica: "SI", "ID", "ENTERO_LIT"
    val lexema: String,         // el texto tal como aparece en la fuente
    val categoria: CategoriaToken,
    val pos: Posicion
)
