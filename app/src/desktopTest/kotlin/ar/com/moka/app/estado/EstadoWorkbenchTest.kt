// =======================================================================
//  EstadoWorkbenchTest.kt — Tests de reducir(), la funcion pura del estado
// =======================================================================
//  reducir() es funcion pura (estado + evento -> estado nuevo), asi que
//  se testea sin correr el compilador ni la UI: se fabrican los eventos a
//  mano y se verifica el estado resultante. Si la GUI "no muestra" algo,
//  el bug esta aca o en el panel, y este test lo separa.
// =======================================================================
package ar.com.moka.app.estado

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.ejecucion.MarcoVista
import ar.com.moka.core.lexico.CategoriaToken
import ar.com.moka.core.lexico.Token
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Fase
import ar.com.moka.core.pipeline.ResultadoAnalisis
import ar.com.moka.core.semantico.Ambito
import ar.com.moka.core.semantico.TipoAmbito
import ar.com.moka.core.sintactico.NodoArbol
import ar.com.moka.core.tac.ProgramaTac
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import ar.com.moka.core.tac.Cuadrupla
import ar.com.moka.core.tac.Operacion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EstadoWorkbenchTest {

    // ------------------------- fabrica de datos -------------------------

    private val pos = Posicion(1, 0, 1, 1)

    private fun token() = Token("ENTERO_LIT", "42", CategoriaToken.LITERAL, pos)

    private fun diagnostico(severidad: Severidad) =
        Diagnostico(severidad, "LEX-001", "mensaje de prueba", pos)

    private fun completada(
        fase: Fase,
        diagnosticos: List<Diagnostico> = emptyList()
    ) = EventoPipeline.FaseCompletada(fase, Artefacto.Lexico(listOf(token())), diagnosticos, 7)

    private fun cuadrupla() = Cuadrupla(0, Operacion.ASIGNAR, null, null, null, null)

    // ------------------------------ tests ------------------------------

    @Test
    fun vacioTieneLasCincoFasesPendientes() {
        val estado = EstadoWorkbench.vacio()
        assertEquals(5, estado.fases.size)
        assertTrue(estado.fases.values.all { it.estado == EstadoFase.PENDIENTE })
        assertFalse(estado.terminado)
        assertFalse(estado.pidiendoEntrada)
    }

    @Test
    fun faseIniciadaMarcaCorriendo() {
        val estado = EstadoWorkbench.vacio().reducir(EventoPipeline.FaseIniciada(Fase.LEXICA))
        assertEquals(EstadoFase.CORRIENDO, estado.fases.getValue(Fase.LEXICA).estado)
        assertEquals(EstadoFase.PENDIENTE, estado.fases.getValue(Fase.SINTACTICA).estado)
    }

    @Test
    fun faseCompletadaGuardaArtefactoDiagnosticoYDuracion() {
        val estado = EstadoWorkbench.vacio().reducir(completada(Fase.LEXICA))
        val slot = estado.fases.getValue(Fase.LEXICA)
        assertEquals(EstadoFase.COMPLETADA, slot.estado)
        assertEquals(Artefacto.Lexico(listOf(token())), slot.artefacto)
        assertEquals(7, slot.duracionMs)
    }

    @Test
    fun faseCompletadaConErrorMarcaConErrores() {
        val estado = EstadoWorkbench.vacio().reducir(completada(Fase.LEXICA, listOf(diagnostico(Severidad.ERROR))))
        assertEquals(EstadoFase.CON_ERRORES, estado.fases.getValue(Fase.LEXICA).estado)
        assertEquals(1, estado.diagnosticos.size)
    }

    @Test
    fun faseCompletadaConSoloAdvertenciasQuedaCompletada() {
        val estado = EstadoWorkbench.vacio().reducir(completada(Fase.SEMANTICA, listOf(diagnostico(Severidad.ADVERTENCIA))))
        assertEquals(EstadoFase.COMPLETADA, estado.fases.getValue(Fase.SEMANTICA).estado)
    }

    @Test
    fun faseOmitidaGuardaElMotivo() {
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.FaseOmitida(Fase.SEMANTICA, "la fase sintactica termino con errores"))
        val slot = estado.fases.getValue(Fase.SEMANTICA)
        assertEquals(EstadoFase.OMITIDA, slot.estado)
        assertEquals("la fase sintactica termino con errores", slot.motivoOmision)
    }

    @Test
    fun salidaSeAcumulaEnOrden() {
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("Hola, ")))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("Moka!\n")))
        assertEquals(listOf(LineaConsola("Hola, "), LineaConsola("Moka!\n")), estado.salida)
    }

    @Test
    fun pidiendoEntradaSeEnciendeYSeApaga() {
        val pidiendo = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.PidiendoEntrada(null)))
        assertTrue(pidiendo.pidiendoEntrada)
        val respondida = pidiendo.reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Fin(12)))
        assertFalse(respondida.pidiendoEntrada)
    }

    @Test
    fun pasoActualizaElPasoActual() {
        val paso = EventoEjecucion.Paso(3, cuadrupla(), listOf(MarcoVista("principal", mapOf("x" to "1"), emptyMap())))
        val estado = EstadoWorkbench.vacio().reducir(EventoPipeline.DeEjecucion(paso))
        assertEquals(paso, estado.pasoActual)
    }

    @Test
    fun errorDeEjecucionMarcaLaFaseYAgregaLineaRoja() {
        val diag = Diagnostico(Severidad.ERROR, "EJE-001", "Error de ejecucion: division por cero.", pos)
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.FaseIniciada(Fase.EJECUCION))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.ErrorEjecucion(diag)))
        assertEquals(EstadoFase.CON_ERRORES, estado.fases.getValue(Fase.EJECUCION).estado)
        assertEquals(listOf(diag), estado.fases.getValue(Fase.EJECUCION).diagnosticos)
        // La linea roja lleva el mensaje y, si el codigo tiene, la pista
        // didactica en un renglon indentado (EJE-001 la tiene).
        val textoEsperado = diag.mensaje + (diag.pista?.let { "\n   Pista: $it" } ?: "")
        assertEquals(LineaConsola(textoEsperado, esError = true), estado.salida.last())
        assertTrue(estado.salida.last().texto.contains("Pista:"))
    }

    @Test
    fun finMarcaLaEjecucionCompletadaPeroNoPisaUnError() {
        val sinError = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.FaseIniciada(Fase.EJECUCION))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Fin(20)))
        assertEquals(EstadoFase.COMPLETADA, sinError.fases.getValue(Fase.EJECUCION).estado)

        val diag = Diagnostico(Severidad.ERROR, "EJE-001", "boom", pos)
        val conError = sinError
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.ErrorEjecucion(diag)))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Fin(21)))
        assertEquals(EstadoFase.CON_ERRORES, conError.fases.getValue(Fase.EJECUCION).estado)
    }

    @Test
    fun terminadoSeteaLasBanderas() {
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.PidiendoEntrada(null)))
            .reducir(EventoPipeline.Terminado(exito = true))
        assertTrue(estado.terminado)
        assertTrue(estado.exito)
        assertFalse(estado.pidiendoEntrada)
    }

    @Test
    fun secuenciaCompletaDeUnProgramaConErrorSintactico() {
        // Lo que el pipeline emite para "entero x = 1 imprimir(x);":
        // lexica ok, sintactica con SIN-001, las otras tres omitidas.
        val estado = EstadoWorkbench.vacio("entero x = 1 imprimir(x);")
            .reducir(EventoPipeline.FaseIniciada(Fase.LEXICA))
            .reducir(completada(Fase.LEXICA))
            .reducir(EventoPipeline.FaseIniciada(Fase.SINTACTICA))
            .reducir(completada(Fase.SINTACTICA, listOf(diagnostico(Severidad.ERROR))))
            .reducir(EventoPipeline.FaseOmitida(Fase.SEMANTICA, "la fase sintactica termino con errores"))
            .reducir(EventoPipeline.FaseOmitida(Fase.GENERACION_TAC, "la fase sintactica termino con errores"))
            .reducir(EventoPipeline.FaseOmitida(Fase.EJECUCION, "una fase anterior termino con errores"))
            .reducir(EventoPipeline.Terminado(exito = false))

        assertEquals(EstadoFase.COMPLETADA, estado.fases.getValue(Fase.LEXICA).estado)
        assertEquals(EstadoFase.CON_ERRORES, estado.fases.getValue(Fase.SINTACTICA).estado)
        assertEquals(EstadoFase.OMITIDA, estado.fases.getValue(Fase.SEMANTICA).estado)
        assertEquals(EstadoFase.OMITIDA, estado.fases.getValue(Fase.GENERACION_TAC).estado)
        assertEquals(EstadoFase.OMITIDA, estado.fases.getValue(Fase.EJECUCION).estado)
        assertTrue(estado.terminado)
        assertFalse(estado.exito)
        assertTrue(estado.salida.isEmpty())
        assertNull(estado.pasoActual)
        assertEquals(1, estado.diagnosticos.size)
    }

    // ------------------------- analisis en vivo -------------------------

    private fun resultadoSinErrores() = ResultadoAnalisis(
        lexico = Artefacto.Lexico(listOf(token())),
        sintactico = Artefacto.Sintactico(NodoArbol("programa", esHoja = false), ast = null),
        semantico = Artefacto.Semantico(Ambito(TipoAmbito.GLOBAL, "global", null, null), emptyMap(), emptyList()),
        tac = Artefacto.Tac(ProgramaTac(emptyList(), emptyMap(), emptyMap(), emptyMap())),
        diagnosticos = emptyList(),
        omitidas = emptyMap()
    )

    @Test
    fun analisisEnVivoCompletoMarcaLasCuatroFases() {
        val estado = EstadoWorkbench.vacio().conAnalisisEnVivo(resultadoSinErrores())
        for (fase in listOf(Fase.LEXICA, Fase.SINTACTICA, Fase.SEMANTICA, Fase.GENERACION_TAC)) {
            assertEquals(EstadoFase.COMPLETADA, estado.fases.getValue(fase).estado, fase.name)
        }
        assertEquals(EstadoFase.PENDIENTE, estado.fases.getValue(Fase.EJECUCION).estado)
    }

    @Test
    fun analisisEnVivoConErrorLexicoOmiteElResto() {
        // Error en la fase 1: solo el lexico corre; las otras se omiten
        // con su motivo y no traen diagnosticos (en el flujo real una
        // fase omitida nunca produjo nada).
        val r = ResultadoAnalisis(
            lexico = Artefacto.Lexico(listOf(token())),
            sintactico = null,
            semantico = null,
            tac = null,
            diagnosticos = listOf(Diagnostico(Severidad.ERROR, "LEX-001", "caracter no reconocido", pos)),
            omitidas = mapOf(Fase.SINTACTICA to "la fase lexica termino con errores")
        )
        val estado = EstadoWorkbench.vacio().conAnalisisEnVivo(r)
        assertEquals(EstadoFase.CON_ERRORES, estado.fases.getValue(Fase.LEXICA).estado)
        assertEquals(1, estado.fases.getValue(Fase.LEXICA).diagnosticos.size)
        assertEquals(EstadoFase.OMITIDA, estado.fases.getValue(Fase.SINTACTICA).estado)
        assertEquals("la fase lexica termino con errores", estado.fases.getValue(Fase.SINTACTICA).motivoOmision)
        assertEquals(1, estado.diagnosticos.size)
    }

    @Test
    fun analisisEnVivoReparteDiagnosticosPorPrefijo() {
        // Todo corre y hay una advertencia semantica: cae en el slot de
        // SEMANTICA (prefijo SEM-), que queda COMPLETADA igual.
        val r = resultadoSinErrores().let {
            ResultadoAnalisis(
                lexico = it.lexico, sintactico = it.sintactico, semantico = it.semantico, tac = it.tac,
                diagnosticos = listOf(Diagnostico(Severidad.ADVERTENCIA, "SEM-018", "variable nunca usada", pos)),
                omitidas = emptyMap()
            )
        }
        val estado = EstadoWorkbench.vacio().conAnalisisEnVivo(r)
        assertEquals(EstadoFase.COMPLETADA, estado.fases.getValue(Fase.SEMANTICA).estado)
        assertEquals(1, estado.fases.getValue(Fase.SEMANTICA).diagnosticos.size)
        assertEquals(0, estado.fases.getValue(Fase.LEXICA).diagnosticos.size)
        assertEquals(1, estado.diagnosticos.size)
    }

    @Test
    fun analisisEnVivoLimpiaEjecucionPeroConservaLaSalida() {
        val corrido = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.FaseIniciada(Fase.EJECUCION))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("Hola\n")))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Fin(10)))
            .reducir(EventoPipeline.Terminado(exito = true))
        val vivo = corrido.conAnalisisEnVivo(resultadoSinErrores())
        assertEquals(EstadoFase.PENDIENTE, vivo.fases.getValue(Fase.EJECUCION).estado)
        assertNull(vivo.pasoActual)
        assertFalse(vivo.terminado)
        assertEquals(1, vivo.salida.size) // la consola conserva la historia
    }

    // ---------------------- consola con estilo ----------------------

    @Test
    fun limpiarConsolaVaciaLaSalidaAcumulada() {
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("antes\n")))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.LimpiarConsola))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("despues\n")))
        assertEquals(listOf("despues\n"), estado.salida.map { it.texto })
    }

    @Test
    fun cadaSalidaHeredaElColorVigenteAlImprimirse() {
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("normal\n")))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.ColorConsola("verde")))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("en verde\n")))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.ColorConsola(null)))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("normal otra vez\n")))
        // Cambiar el color no repinta lo ya escrito.
        assertEquals(listOf(null, "verde", null), estado.salida.map { it.color })
    }

    @Test
    fun limpiarConservaElColorVigente() {
        // Como en una consola real: borrar la pantalla no cambia la tinta.
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.ColorConsola("rojo")))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.LimpiarConsola))
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.Salida("sigue rojo\n")))
        assertEquals("rojo", estado.salida.single().color)
    }

    @Test
    fun tituloConsolaSeGuardaEnElEstado() {
        val estado = EstadoWorkbench.vacio()
            .reducir(EventoPipeline.DeEjecucion(EventoEjecucion.TituloConsola("Mi juego")))
        assertEquals("Mi juego", estado.tituloConsola)
    }
}
