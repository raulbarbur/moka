package ar.com.moka.core

import kotlin.test.Test
import kotlin.test.assertEquals
import ar.com.moka.core.generado.MokaLexer
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.test.assertFalse


/**
 * Tests del spike de la Fase 0 (seccion 7 del documento de diseno):
 * verifican que la generacion con antlr-kotlin funciona de punta a punta
 * y que las propiedades Unicode \p{L} del lexer andan en JVM.
 */
class MokaParserTest {

    @Test
    fun programaVacioParseaSinErrores() {
        val resultado = Moka.analizar("")
        assertEquals(0, resultado.errores)
        assertEquals(0, resultado.lineas)
    }

    @Test
    fun unaLineaMinimaParsea() {
        val resultado = Moka.analizar("hola;")
        assertEquals(0, resultado.errores)
        assertEquals(1, resultado.lineas)
    }

    @Test
    fun lexerDelParaTokenizaLimpio() {
        val lexer = MokaLexer(CharStreams.fromString("para (entero i = 1; i <= 10; i++) { }"))
        val tokens = CommonTokenStream(lexer)
        tokens.fill() // fuerza al lexer a consumir TODO el texto de entrada
        val nombres = tokens.tokens.map { lexer.vocabulary.getSymbolicName(it.type) }

        assertFalse(nombres.contains("ERROR_CHAR"), "hay un caracter que el lexer no reconoce")
        assertFalse(nombres.contains("CADENA_ABIERTA"), "hay una cadena sin cerrar")
        assertEquals("PARA", nombres.first())
    }

    @Test
    fun cadenaSinCerrarProduceTokenDeError() {
        val lexer = MokaLexer(CharStreams.fromString("imprimir(\"hola)"))
        val tokens = CommonTokenStream(lexer)
        tokens.fill()

        val nombres = tokens.tokens.map { lexer.vocabulary.getSymbolicName(it.type) }

        // La cadena sin cerrar tiene que salir como UN token CADENA_ABIERTA
        // (y no como una catarata de ERROR_CHAR):
        assertEquals(1, nombres.count { it == "CADENA_ABIERTA" })
    }

    @Test
    fun identificadoresConUnicodeParsean() {
        // Riesgo explicito de la seccion 6: \p{L} tiene que reconocer
        // letras con tilde y enie en todas las plataformas.
        val resultado = Moka.analizar("año;\nseñal;\nniño;")
        assertEquals(0, resultado.errores)
        assertEquals(3, resultado.lineas)
    }

    @Test
    fun lineaSinPuntoYComaReportaError() {
        val resultado = Moka.analizar("hola")
        assertEquals(1, resultado.errores)
    }
}
