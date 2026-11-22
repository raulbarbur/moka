// ============================================================================
//  InstruccionesConsolaTest.kt - Builtins de consola con estilo
// ============================================================================
//  Cubre limpiar(), color(...), restaurar_color(), pausa(ms) y titulo(...):
//    - los eventos que la maquina emite (LimpiarConsola, ColorConsola,
//      TituloConsola) y que pausa no emita ninguno;
//    - los errores de ejecucion EJE-009 (color desconocido con nombre
//      computado) y EJE-010 (pausa negativa);
//    - los errores de compilacion SEM-058/059/060 (tipo del argumento),
//      SEM-007 (cantidad) y SEM-061 (color literal desconocido).
//  Mismo arnés que MaquinaTacTest: pipeline real, de la fuente al Flow.
// ============================================================================
package ar.com.moka.core.ejecucion

import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import ar.com.moka.core.semantico.AnalizadorSemantico
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import ar.com.moka.core.sintactico.ConstructorAst
import ar.com.moka.core.tac.GeneradorTac
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream

class InstruccionesConsolaTest {

    // Pipeline completo sin errores semanticos esperados.
    private fun ejecutar(fuente: String): List<EventoEjecucion> {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val ast = ConstructorAst.construir(parser.programa())
        val sem = AnalizadorSemantico.analizar(ast)
        val errores = sem.diagnosticos.filter { it.severidad == Severidad.ERROR }
        assertTrue(errores.isEmpty(), "Errores semanticos inesperados: ${errores.map { it.codigo + ": " + it.mensaje }}")
        val tac = GeneradorTac.generar(ast, sem)
        return runBlocking { MaquinaTac(tac, ConsolaDePrueba(), ConfigEjecucion()).ejecutar().toList() }
    }

    // Solo el semantico (para los errores SEM- el programa no se ejecuta).
    private fun diagnosticosDe(fuente: String): List<Diagnostico> {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val ast = ConstructorAst.construir(parser.programa())
        return AnalizadorSemantico.analizar(ast).diagnosticos
    }

    private fun errorDe(eventos: List<EventoEjecucion>) =
        eventos.filterIsInstance<EventoEjecucion.ErrorEjecucion>().firstOrNull()

    // --------------------------- eventos ---------------------------

    @Test
    fun limpiarEmiteLimpiarConsola() {
        val eventos = ejecutar("""limpiar();""")
        assertTrue(eventos.any { it is EventoEjecucion.LimpiarConsola }, "Falta LimpiarConsola: $eventos")
        assertIs<EventoEjecucion.Fin>(eventos.last())
    }

    @Test
    fun colorEmiteElNombreCanonico() {
        // Mayusculas y espacios se normalizan al nombre canonico.
        val eventos = ejecutar("""color(" Rojo ");""")
        val color = eventos.filterIsInstance<EventoEjecucion.ColorConsola>().single()
        assertEquals("rojo", color.color)
    }

    @Test
    fun restaurarColorEmiteColorNulo() {
        val eventos = ejecutar(
            """
            color("verde");
            restaurar_color();
            """.trimIndent()
        )
        val colores = eventos.filterIsInstance<EventoEjecucion.ColorConsola>()
        assertEquals(listOf("verde", null), colores.map { it.color })
    }

    @Test
    fun tituloEmiteTituloConsola() {
        val eventos = ejecutar("""titulo("Mi juego");""")
        val titulo = eventos.filterIsInstance<EventoEjecucion.TituloConsola>().single()
        assertEquals("Mi juego", titulo.titulo)
    }

    @Test
    fun elColorViajaAntesQueLaSalida() {
        // La GUI tiñe cada Salida con el ultimo ColorConsola visto; el
        // orden de los eventos es el contrato.
        val eventos = ejecutar(
            """
            color("azul");
            imprimir("en azul");
            """.trimIndent()
        )
        val orden = eventos.map {
            when (it) {
                is EventoEjecucion.ColorConsola -> "color"
                is EventoEjecucion.Salida -> "salida"
                else -> "otro"
            }
        }
        assertEquals(listOf("color", "salida", "otro"), orden)
    }

    @Test
    fun pausaNoEmiteEventosYElProgramaSigue() {
        val eventos = ejecutar(
            """
            imprimir("antes");
            pausa(1);
            imprimir("despues");
            """.trimIndent()
        )
        val salidas = eventos.filterIsInstance<EventoEjecucion.Salida>().map { it.texto }
        assertEquals(listOf("antes\n", "despues\n"), salidas)
        assertNull(errorDe(eventos))
        assertIs<EventoEjecucion.Fin>(eventos.last())
    }

    // ---------------------- errores de ejecucion ----------------------

    @Test
    fun colorComputadoDesconocidoDaEje009() {
        val eventos = ejecutar(
            """
            cadena c = "violeta";
            color(c);
            """.trimIndent()
        )
        val error = errorDe(eventos)
        assertNotNull(error, "Se esperaba un ErrorEjecucion: $eventos")
        assertEquals("EJE-009", error.diagnostico.codigo)
        assertTrue("violeta" in error.diagnostico.mensaje, "El mensaje nombra el color pedido: ${error.diagnostico.mensaje}")
        assertTrue("rojo" in error.diagnostico.mensaje, "El mensaje lista los colores validos: ${error.diagnostico.mensaje}")
    }

    @Test
    fun pausaNegativaDaEje010() {
        val eventos = ejecutar("""pausa(-5);""")
        val error = errorDe(eventos)
        assertNotNull(error, "Se esperaba un ErrorEjecucion: $eventos")
        assertEquals("EJE-010", error.diagnostico.codigo)
    }

    // ---------------------- errores de compilacion ----------------------

    private fun codigos(fuente: String): List<String> =
        diagnosticosDe(fuente).filter { it.severidad == Severidad.ERROR }.map { it.codigo }

    @Test
    fun colorConTipoDistintoDeCadenaDaSem058() {
        assertEquals(listOf("SEM-058"), codigos("""color(5);"""))
    }

    @Test
    fun tituloConTipoDistintoDeCadenaDaSem059() {
        assertEquals(listOf("SEM-059"), codigos("""titulo(verdadero);"""))
    }

    @Test
    fun pausaConTipoDistintoDeEnteroDaSem060() {
        assertEquals(listOf("SEM-060"), codigos("""pausa("cien");"""))
    }

    @Test
    fun colorLiteralDesconocidoDaSem061() {
        val codigos = codigos("""color("violeta");""")
        assertEquals(listOf("SEM-061"), codigos)
    }

    @Test
    fun limpiarConArgumentosDaSem007() {
        assertEquals(listOf("SEM-007"), codigos("""limpiar(1);"""))
    }

    @Test
    fun pausaSinArgumentosDaSem007() {
        assertEquals(listOf("SEM-007"), codigos("""pausa();"""))
    }

    @Test
    fun tituloConVariableCadenaEsValido() {
        assertEquals(emptyList(), codigos(
            """
            cadena t = "Hola";
            titulo(t);
            """.trimIndent()
        ))
    }
}
