// ============================================================================
//  AnalizadorSintactico.kt — Fase 2 del pipeline: texto -> arbol + AST
// ============================================================================
//  Envuelve al MokaLexer/MokaParser generados y entrega tres cosas:
//    arbol:        el arbol de parseo copiado a NodoArbol (tipo propio:
//                  la UI nunca ve ANTLR, sec. 4.2), con los nodos de error
//                  marcados
//    ast:          el AST propio, o NULL si hubo errores de sintaxis
//                  (sec. 4.7: el visitor del ConstructorAst asume un arbol
//                  sano; con errores no se lo invoca)
//    diagnosticos: los SIN- traducidos al espanol por el TraductorErrores,
//                  con el formato de la sec. 4.8.2
//  El parser se configura sin el escuchador por defecto (que imprimiria en
//  ingles por consola) y con el nuestro, que acumula (sec. 4.1: los errores
//  se acumulan por fase; ANTLR ademas se recupera y sigue parseando, asi
//  que el arbol sale completo igual).
// ============================================================================
package ar.com.moka.core.sintactico

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.ast.ProgramaAst
import ar.com.moka.core.errores.TraductorErrores
import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer
import org.antlr.v4.kotlinruntime.Token as TokenAntlr
import org.antlr.v4.kotlinruntime.tree.ErrorNode
import org.antlr.v4.kotlinruntime.tree.ParseTree
import org.antlr.v4.kotlinruntime.tree.TerminalNode

class AnalizadorSintactico {

    fun analizar(fuente: String, primerId: Int = 1): ResultadoSintactico {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        lexer.removeErrorListeners() // los errores lexicos llegan como tokens (sec. 3.2)
        val parser = MokaParser(CommonTokenStream(lexer))
        val escuchador = EscuchadorErrores()
        parser.removeErrorListeners()
        parser.addErrorListener(escuchador)

        val arbolAntlr = parser.programa()
        // La raiz es siempre una regla (ParserRuleContext), asi que no puede
        // salir nula: copiarRaiz la copia directo, sin el caso "desconocido".
        val arbol = copiarRaiz(arbolAntlr, parser, lexer)
        val diagnosticos = escuchador.diagnosticos
        // Sec. 4.7: ast = null si hubo errores.
        val ast = if (diagnosticos.isEmpty()) ConstructorAst.construir(arbolAntlr, primerId) else null
        return ResultadoSintactico(arbol, ast, diagnosticos)
    }

    // ------------------------- copia del arbol -------------------------

    // Copia de la raiz (una regla): nunca nula. Los hijos desconocidos o las
    // hojas EOF se descartan en copiarNodo/hoja.
    private fun copiarRaiz(ctx: ParserRuleContext, parser: MokaParser, lexer: MokaLexer): NodoArbol = NodoArbol(
        nombre = parser.ruleNames[ctx.ruleIndex],
        esHoja = false,
        pos = posDe(ctx),
        hijos = (ctx.children ?: emptyList()).mapNotNull { copiarNodo(it, parser, lexer) }
    )

    private fun copiarNodo(nodo: ParseTree, parser: MokaParser, lexer: MokaLexer): NodoArbol? = when (nodo) {
        is ErrorNode -> hoja(nodo.symbol, lexer, forzarError = true)
        is TerminalNode -> hoja(nodo.symbol, lexer, forzarError = false)
        is ParserRuleContext -> copiarRaiz(nodo, parser, lexer)
        else -> null // no deberia pasar: el arbol solo tiene reglas y terminales
    }

    // Una hoja por token. El EOF no se muestra (ruido para la UI): null.
    private fun hoja(token: TokenAntlr, lexer: MokaLexer, forzarError: Boolean): NodoArbol? {
        if (token.type == TokenAntlr.EOF) return null
        val tipo = lexer.vocabulary.getSymbolicName(token.type) ?: "DESCONOCIDO"
        val lexema = token.text ?: ""
        return NodoArbol(
            nombre = tipo,
            esHoja = true,
            lexema = lexema,
            esError = forzarError || tipo == "ERROR_CHAR" || tipo == "CADENA_ABIERTA",
            pos = Posicion(token.line, token.charPositionInLine, token.line, token.charPositionInLine + lexema.length - 1)
        )
    }

    // Misma convencion que ConstructorAst: linea 1-based, columna 0-based,
    // fin inclusivo.
    private fun posDe(ctx: ParserRuleContext): Posicion {
        val ini = ctx.start
        val fin = ctx.stop
        return Posicion(
            linea = ini?.line ?: 0,
            columna = ini?.charPositionInLine ?: 0,
            finLinea = fin?.line ?: (ini?.line ?: 0),
            finColumna = if (fin != null) {
                fin.charPositionInLine + (fin.text?.length ?: 1) - 1
            } else {
                ini?.charPositionInLine ?: 0
            }
        )
    }

    // Acumula los errores de sintaxis ya traducidos (sec. 4.8.2).
    private class EscuchadorErrores : BaseErrorListener() {
        val diagnosticos = mutableListOf<Diagnostico>()

        override fun syntaxError(
            recognizer: Recognizer<*, *>,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException?
        ) {
            diagnosticos.add(
                Diagnostico(
                    Severidad.ERROR, "SIN-001",
                    TraductorErrores.traducir(msg, line, charPositionInLine),
                    Posicion(line, charPositionInLine, line, charPositionInLine)
                )
            )
        }
    }
}

// Lo que la fase 2 entrega al pipeline (Artefacto.Sintactico + diagnosticos).
data class ResultadoSintactico(
    val arbol: NodoArbol,
    val ast: ProgramaAst?,
    val diagnosticos: List<Diagnostico>
)
