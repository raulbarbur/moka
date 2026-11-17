// ============================================================================
//  MokaPipelineTest.kt — Tests de la fachada Moka y el pipeline (sec. 4.7)
// ============================================================================
//  Verifica el contrato publico del nucleo: el orden de los eventos, los
//  artefactos por fase, la omision en cascada cuando una fase tiene
//  errores (conservando los artefactos parciales, sec. 4.1), que las
//  advertencias NO frenan, los modos (solo analisis / paso a paso), el
//  analizar() sincrono, y la integracion de punta a punta con FizzBuzz.
//
//  Vive en jvmTest porque recolectar el Flow usa runBlocking.
// ============================================================================
package ar.com.moka.core.pipeline

import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.ConsolaDePrueba
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.lexico.CategoriaToken
import ar.com.moka.core.semantico.Severidad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class MokaPipelineTest {

    private fun procesar(
        fuente: String,
        modo: Modo = Modo.EJECUTAR,
        consola: Consola = ConsolaDePrueba()
    ): List<EventoPipeline> =
        runBlocking { Moka().procesar(fuente, consola, modo).toList() }

    private fun nombreDe(e: EventoPipeline): String = when (e) {
        is EventoPipeline.FaseIniciada -> "inicia ${e.fase}"
        is EventoPipeline.FaseCompletada -> "completa ${e.fase}"
        is EventoPipeline.FaseOmitida -> "omite ${e.fase}"
        is EventoPipeline.DeEjecucion -> "ejecucion"
        is EventoPipeline.Terminado -> "terminado ${e.exito}"
    }

    private fun nombresDe(eventos: List<EventoPipeline>): List<String> = eventos.map { nombreDe(it) }

    private fun completada(eventos: List<EventoPipeline>, fase: Fase): EventoPipeline.FaseCompletada =
        eventos.filterIsInstance<EventoPipeline.FaseCompletada>().single { it.fase == fase }

    private fun salidaDe(eventos: List<EventoPipeline>): String =
        eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }.filterIsInstance<EventoEjecucion.Salida>()
            .joinToString("") { it.texto }

    // ===================================================================
    //  Camino feliz
    // ===================================================================

    @Test
    fun programaValidoEmiteLasFasesEnOrden() {
        val eventos = procesar("entero x = 1;\nimprimir(x);")
        assertEquals(
            listOf(
                "inicia LEXICA", "completa LEXICA",
                "inicia SINTACTICA", "completa SINTACTICA",
                "inicia SEMANTICA", "completa SEMANTICA",
                "inicia GENERACION_TAC", "completa GENERACION_TAC",
                "inicia EJECUCION",
                "ejecucion", "ejecucion", // Salida + Fin
                "terminado true"
            ),
            nombresDe(eventos)
        )
        assertEquals("1\n", salidaDe(eventos))
    }

    @Test
    fun artefactosTienenContenido() {
        val eventos = procesar("entero x = 1;\nimprimir(x);")
        val lex = completada(eventos, Fase.LEXICA).artefacto as Artefacto.Lexico
        assertTrue(lex.tokens.isNotEmpty())
        assertEquals("T_ENTERO", lex.tokens.first().tipo)
        val sin = completada(eventos, Fase.SINTACTICA).artefacto as Artefacto.Sintactico
        assertEquals("programa", sin.arbolParseo.nombre)
        assertNotNull(sin.ast)
        val sem = completada(eventos, Fase.SEMANTICA).artefacto as Artefacto.Semantico
        assertTrue(sem.raiz.funciones.containsKey("imprimir"))
        val tac = completada(eventos, Fase.GENERACION_TAC).artefacto as Artefacto.Tac
        assertTrue(tac.programa.cuadruplas.isNotEmpty())
    }

    @Test
    fun cadaFaseCompletadaLlevaDuracion() {
        val eventos = procesar("imprimir(1);")
        val completadas = eventos.filterIsInstance<EventoPipeline.FaseCompletada>()
        assertEquals(4, completadas.size)
        assertTrue(completadas.all { it.duracionMs >= 0 })
    }

    // ===================================================================
    //  Omision en cascada (sec. 4.1)
    // ===================================================================

    @Test
    fun errorLexicoOmiteTodasLasDemasFases() {
        val eventos = procesar("entero @ = 1;")
        val lex = completada(eventos, Fase.LEXICA)
        assertEquals(1, lex.diagnosticos.size)
        assertEquals("LEX-001", lex.diagnosticos[0].codigo)
        assertTrue(lex.diagnosticos[0].mensaje.contains("caracter no reconocido '@'"))
        // El artefacto parcial se conserva: el token de error esta y viene marcado.
        val tokens = (lex.artefacto as Artefacto.Lexico).tokens
        assertTrue(tokens.any { it.categoria == CategoriaToken.ERROR && it.lexema == "@" })
        assertEquals(
            listOf("omite SINTACTICA", "omite SEMANTICA", "omite GENERACION_TAC", "omite EJECUCION", "terminado false"),
            nombresDe(eventos).drop(2)
        )
    }

    @Test
    fun cadenaSinCerrarEsLex002() {
        val eventos = procesar("imprimir(\"hola);")
        val lex = completada(eventos, Fase.LEXICA)
        assertTrue(lex.diagnosticos.any { it.codigo == "LEX-002" && it.mensaje.contains("cadena sin cerrar") })
    }

    @Test
    fun errorSintacticoOmiteDesdeLaSemanticaPeroConservaElArbol() {
        val eventos = procesar("entero x = ;")
        val sin = completada(eventos, Fase.SINTACTICA)
        assertTrue(sin.diagnosticos.any { it.codigo == "SIN-001" })
        val artefacto = sin.artefacto as Artefacto.Sintactico
        assertEquals("programa", artefacto.arbolParseo.nombre) // el arbol sale igual (sec. 4.1)
        assertNull(artefacto.ast)                              // pero el AST no se construye (sec. 4.7)
        assertEquals(
            listOf("omite SEMANTICA", "omite GENERACION_TAC", "omite EJECUCION", "terminado false"),
            nombresDe(eventos).drop(4)
        )
    }

    @Test
    fun mensajeSintacticoEstaEnEspanol() {
        val eventos = procesar("entero x = 1 imprimir(x);")
        val sin = completada(eventos, Fase.SINTACTICA)
        val mensaje = sin.diagnosticos.first().mensaje
        assertTrue(mensaje.startsWith("Error de sintaxis en linea 1, columna "), mensaje)
        assertTrue(mensaje.contains("';'"), mensaje)
        assertTrue(mensaje.contains("imprimir"), mensaje)
        assertFalse(mensaje.contains("missing"), mensaje)
        assertFalse(mensaje.contains("expecting"), mensaje)
    }

    @Test
    fun errorSemanticoOmiteTacYEjecucionPeroConservaElArtefacto() {
        val eventos = procesar("entero x = verdadero;")
        val sem = completada(eventos, Fase.SEMANTICA)
        assertTrue(sem.diagnosticos.any { it.severidad == Severidad.ERROR })
        val artefacto = sem.artefacto as Artefacto.Semantico
        assertTrue(artefacto.chequeos.isNotEmpty()) // se ven los chequeos fallidos (sec. 4.1)
        assertEquals(
            listOf("omite GENERACION_TAC", "omite EJECUCION", "terminado false"),
            nombresDe(eventos).drop(6)
        )
    }

    @Test
    fun advertenciasNoFrenanElPipeline() {
        val eventos = procesar("entero sinUso = 3;\nimprimir(\"hola\");")
        val sem = completada(eventos, Fase.SEMANTICA)
        assertTrue(sem.diagnosticos.any { it.codigo == "SEM-018" && it.severidad == Severidad.ADVERTENCIA })
        assertEquals("hola\n", salidaDe(eventos))
        assertEquals("terminado true", nombresDe(eventos).last())
    }

    // ===================================================================
    //  Modos
    // ===================================================================

    @Test
    fun modoSoloAnalisisOmiteLaEjecucion() {
        val eventos = procesar("imprimir(1);", modo = Modo.SOLO_ANALISIS)
        assertTrue(eventos.none { it is EventoPipeline.DeEjecucion })
        // 8 eventos de fases 1-4, despues la omision y el cierre.
        assertEquals("omite EJECUCION", nombresDe(eventos)[8])
        assertEquals("terminado true", nombresDe(eventos).last())
    }

    @Test
    fun pasoAPasoEmitePasos() {
        val eventos = procesar("entero x = 1;\nimprimir(x);", modo = Modo.PASO_A_PASO)
        val pasos = eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }.filterIsInstance<EventoEjecucion.Paso>()
        assertTrue(pasos.isNotEmpty())
        assertEquals("1\n", salidaDe(eventos))
        assertEquals("terminado true", nombresDe(eventos).last())
    }

    @Test
    fun errorDeEjecucionTerminaConExitoFalso() {
        val eventos = procesar("imprimir(1 / 0);")
        val errores = eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }.filterIsInstance<EventoEjecucion.ErrorEjecucion>()
        assertEquals(1, errores.size)
        assertEquals("EJE-001", errores[0].diagnostico.codigo)
        assertEquals("terminado false", nombresDe(eventos).last())
    }

    @Test
    fun entradaPorConsolaFuncionaATravesDeLaFachada() {
        val eventos = procesar("imprimir(leer_entero() + 1);", consola = ConsolaDePrueba(listOf("7")))
        assertEquals("8\n", salidaDe(eventos))
        val pidiendo = eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }.filterIsInstance<EventoEjecucion.PidiendoEntrada>()
        assertEquals(1, pidiendo.size)
    }

    // ===================================================================
    //  analizar() sincrono (sec. 4.7)
    // ===================================================================

    @Test
    fun analizarSincronoDevuelveTodosLosArtefactos() {
        val r = Moka().analizar("entero x = 1;\nimprimir(x);")
        assertTrue(r.exito)
        assertTrue(r.lexico.tokens.isNotEmpty())
        assertNotNull(r.sintactico)
        assertNotNull(r.semantico)
        assertNotNull(r.tac)
        assertTrue(r.omitidas.isEmpty())
        assertTrue(r.diagnosticos.none { it.severidad == Severidad.ERROR })
    }

    @Test
    fun analizarSincronoConErrorSemanticoOmiteElTac() {
        val r = Moka().analizar("entero x = verdadero;")
        assertFalse(r.exito)
        assertNotNull(r.semantico)
        assertNull(r.tac)
        assertTrue(r.omitidas.containsKey(Fase.GENERACION_TAC))
        assertTrue(r.diagnosticos.any { it.severidad == Severidad.ERROR })
    }

    // ===================================================================
    //  Clasificacion de tokens (sec. 5.3, panel 2)
    // ===================================================================

    @Test
    fun tokensVienenClasificadosPorCategoria() {
        val r = Moka().analizar("si (verdadero) { entero x = 42; }")
        val porTipo = r.lexico.tokens.associate { it.tipo to it.categoria }
        assertEquals(CategoriaToken.PALABRA_CLAVE, porTipo["SI"])
        assertEquals(CategoriaToken.PALABRA_CLAVE, porTipo["T_ENTERO"])
        assertEquals(CategoriaToken.DELIMITADOR, porTipo["PAREN_IZQ"])
        assertEquals(CategoriaToken.DELIMITADOR, porTipo["LLAVE_DER"])
        assertEquals(CategoriaToken.IDENTIFICADOR, porTipo["ID"])
        assertEquals(CategoriaToken.OPERADOR, porTipo["ASIGNA"])
        assertEquals(CategoriaToken.LITERAL, porTipo["ENTERO_LIT"])
    }

    // ===================================================================
    //  Integracion de punta a punta (sec. 4.9)
    // ===================================================================

    @Test
    fun fizzBuzzDePuntaAPunta() {
        val fuente = """
            para (entero i = 1; i <= 15; i = i + 1) {
                si (i % 15 == 0) {
                    imprimir("FizzBuzz");
                } sino {
                    si (i % 3 == 0) {
                        imprimir("Fizz");
                    } sino {
                        si (i % 5 == 0) {
                            imprimir("Buzz");
                        } sino {
                            imprimir(i);
                        }
                    }
                }
            }
        """.trimIndent()
        val eventos = procesar(fuente)
        assertEquals(
            "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz\n",
            salidaDe(eventos)
        )
        assertEquals("terminado true", nombresDe(eventos).last())
    }
}
