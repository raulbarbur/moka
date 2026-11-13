package ar.com.moka.core

import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

/**
 * Fachada del nucleo de Moka.
 *
 * Fase 1: la gramatica ya es la completa (seccion 3 del documento de
 * diseno), asi que analizar() reconoce programas Moka reales: clases,
 * funciones y sentencias sueltas. La API definitiva (Moka.procesar con
 * Flow<EventoPipeline>, seccion 4.7) se construye a partir de la Fase 2,
 * cuando exista el AST.
 */
object Moka {

    const val VERSION: String = "0.2.0-fase1"

    /**
     * Parsea [fuente] y devuelve cuantos elementos toplevel reconocio
     * (clases + funciones + sentencias sueltas) y cuantos errores de
     * sintaxis encontro. No ejecuta nada todavia.
     */
    fun analizar(fuente: String): ResultadoParseo {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val arbol = parser.programa()
        return ResultadoParseo(
            lineas = arbol.declaracionClase().size +
                    arbol.declaracionFuncion().size +
                    arbol.sentencia().size,
            errores = parser.numberOfSyntaxErrors,
        )
    }

    /** Mensaje corto para las pantallas "hola" de la Fase 0. */
    fun saludo(): String = "Moka $VERSION - nucleo funcionando"
}

/** Resultado del analisis sintactico. */
data class ResultadoParseo(val lineas: Int, val errores: Int) {
    override fun toString(): String =
        if (errores == 0) "Parse OK: $lineas elemento(s) toplevel reconocido(s)"
        else "Parse con $errores error(es) de sintaxis ($lineas elemento(s) toplevel reconocidos)"
}
