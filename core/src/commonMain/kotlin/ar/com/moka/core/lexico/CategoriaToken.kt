// =======================================================================
//  CategoriaToken.kt — Categoria de un token para la UI (sec. 5.3, panel 2)
// =======================================================================
//  Clasificacion gruesa del tipo de token: es lo que el panel de tokens
//  usa para colorear y filtrar. La asignacion de cada nombre simbolico de
//  la gramatica (SI, MAS, ENTERO_LIT...) a su categoria la hace el
//  AnalizadorLexico con tablas explicitas.
// =======================================================================
package ar.com.moka.core.lexico

enum class CategoriaToken { PALABRA_CLAVE, OPERADOR, LITERAL, IDENTIFICADOR, DELIMITADOR, ERROR }
