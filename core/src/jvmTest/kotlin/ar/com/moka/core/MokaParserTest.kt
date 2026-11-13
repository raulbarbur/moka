package ar.com.moka.core

import kotlin.test.Test
import kotlin.test.assertEquals

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
