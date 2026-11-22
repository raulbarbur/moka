// ============================================================================
//  AnalizadorLexico.kt — Fase 1 del pipeline: texto -> tokens (sec. 4.1, 4.8.1)
// ============================================================================
//  Envuelve al MokaLexer generado por ANTLR y entrega dos cosas:
//    tokens:       la lista de tokens PROPIOS (sin el EOF), ya clasificados
//                  por categoria; comentarios y espacios no llegan porque la
//                  gramatica los descarta con -> skip (sec. 3.2)
//    diagnosticos: un diagnostico LEX- por cada token de error, con el
//                  formato de la sec. 4.8.1:
//                  LEX-001 "Error lexico en linea L, columna C: caracter no
//                           reconocido '@'."
//                  LEX-002 "Error lexico en linea L, columna C: cadena sin
//                           cerrar."
//  La gramatica garantiza que todo caracter raro termina en un token
//  ERROR_CHAR o CADENA_ABIERTA, asi que el lexer nunca imprime nada por
//  consola; igual le sacamos el escuchador por defecto por las dudas.
// ============================================================================
package ar.com.moka.core.lexico

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.Token as TokenAntlr

class AnalizadorLexico {

    fun analizar(fuente: String): ResultadoLexico {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        lexer.removeErrorListeners() // los errores llegan como tokens (sec. 3.2)
        val stream = CommonTokenStream(lexer)
        stream.fill()

        val tokens = mutableListOf<Token>()
        val diagnosticos = mutableListOf<Diagnostico>()
        for (t in stream.tokens) {
            if (t.type == TokenAntlr.EOF) break
            val tipo = lexer.vocabulary.getSymbolicName(t.type) ?: "DESCONOCIDO"
            val categoria = categoriaDe(tipo)
            val lexema = t.text ?: ""
            val pos = Posicion(
                linea = t.line,
                columna = t.charPositionInLine,
                finLinea = t.line,
                finColumna = t.charPositionInLine + lexema.length - 1
            )
            tokens.add(Token(tipo, lexema, categoria, pos))
            // Sec. 4.8.1: el diagnostico nace del token de error.
            when (tipo) {
                "ERROR_CHAR" -> diagnosticos.add(
                    Diagnostico(
                        Severidad.ERROR, "LEX-001",
                        "Error lexico en linea ${t.line}, columna ${t.charPositionInLine}: caracter no reconocido '$lexema'.",
                        pos
                    )
                )
                "CADENA_ABIERTA" -> diagnosticos.add(
                    Diagnostico(
                        Severidad.ERROR, "LEX-002",
                        "Error lexico en linea ${t.line}, columna ${t.charPositionInLine}: cadena sin cerrar.",
                        pos
                    )
                )
            }
        }
        return ResultadoLexico(tokens, diagnosticos)
    }

    // Clasificacion por nombre simbolico (sec. 3.2 de la gramatica).
    private fun categoriaDe(tipo: String): CategoriaToken = when (tipo) {
        in PALABRAS_CLAVE -> CategoriaToken.PALABRA_CLAVE
        in OPERADORES -> CategoriaToken.OPERADOR
        in LITERALES -> CategoriaToken.LITERAL
        in DELIMITADORES -> CategoriaToken.DELIMITADOR
        "ID" -> CategoriaToken.IDENTIFICADOR
        else -> CategoriaToken.ERROR
    }

    companion object {
        private val PALABRAS_CLAVE = setOf(
            "SI", "SINO", "MIENTRAS", "HACER", "PARA", "PARA_CADA", "SEGUN",
            "CASO", "DEFECTO", "ROMPER", "CONTINUAR", "RETORNAR", "FUNCION",
            "CLASE", "CONSTRUCTOR", "NUEVO", "ESTE", "ESTATICO", "CONSTANTE",
            "PAQUETE", "USAR",
            "Y", "O", "NO",
            "T_ENTERO", "T_FLOTANTE", "T_BOOLEANO", "T_CARACTER", "T_CADENA", "T_VACIO",
            "VERDADERO", "FALSO", "NULO"
        )
        private val OPERADORES = setOf(
            "MAS", "MENOS", "POR", "DIV", "MODULO", "INCREMENTO", "DECREMENTO",
            "MENOR", "MENOR_IGUAL", "MAYOR", "MAYOR_IGUAL", "IGUAL_IGUAL", "DISTINTO",
            "ASIGNA", "MAS_ASIGNA", "MENOS_ASIGNA", "POR_ASIGNA", "DIV_ASIGNA", "MOD_ASIGNA"
        )
        private val LITERALES = setOf("ENTERO_LIT", "FLOTANTE_LIT", "CARACTER_LIT", "CADENA_LIT")
        private val DELIMITADORES = setOf(
            "PAREN_IZQ", "PAREN_DER", "LLAVE_IZQ", "LLAVE_DER",
            "CORCHETE_IZQ", "CORCHETE_DER", "PUNTO_COMA", "COMA", "PUNTO", "DOS_PUNTOS"
        )
    }
}

// Lo que la fase 1 entrega al pipeline (Artefacto.Lexico + diagnosticos).
data class ResultadoLexico(
    val tokens: List<Token>,
    val diagnosticos: List<Diagnostico>
)
