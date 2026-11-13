package ar.com.moka.core

import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

/**
 * Fachada del nucleo de Moka.
 *
 * Fase 0: esqueleto minimo. Solo sabe parsear el subconjunto de la gramatica
 * que existe hoy (`Moka.g4`) y devolver un resumen. La API definitiva
 * (Moka.procesar con Flow<EventoPipeline>, seccion 4.7 del documento de
 * diseno) se construye a partir de la Fase 1.
 */
object Moka {

    const val VERSION: String = "0.1.0-fase0"

    /**
     * Parsea [fuente] y devuelve cuantas lineas reconocio y cuantos errores
     * de sintaxis encontro. No ejecuta nada todavia.
     */
    fun analizar(fuente: String): ResultadoParseo {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val arbol = parser.programa()
        return ResultadoParseo(
            lineas = arbol.linea().size,
            errores = parser.numberOfSyntaxErrors,
        )
    }

    /** Mensaje corto para las pantallas "hola" de la Fase 0. */
    fun saludo(): String = "Moka $VERSION - nucleo funcionando"
}

/** Resultado del analisis de la Fase 0. */
data class ResultadoParseo(val lineas: Int, val errores: Int) {
    override fun toString(): String =
        if (errores == 0) "Parse OK: $lineas linea(s) reconocida(s)"
        else "Parse con $errores error(es) de sintaxis ($lineas linea(s) reconocidas)"
}
