package ar.com.moka.app.estado

import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PasoAPasoTest {
    // Un hilo de UI dedicado para que las acciones y publicaciones tengan
    // el mismo orden que en Compose; el pipeline usa su Default real.
    private fun prueba(cuerpo: suspend (MokaControlador) -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ui ->
            runBlocking(ui) {
                val c = MokaControlador(ui = ui)
                try { withTimeout(10_000) { cuerpo(c) } } finally { c.liberar() }
            }
        }
    }

    private suspend fun esperar(c: MokaControlador, situacion: SituacionEjecucion): ControlEjecucion =
        withTimeout(5_000) { c.control.first { it.situacion == situacion } }

    @Test
    fun pausaAntesDeLaPrimeraInstruccionYNoAvanzaSola() = prueba { c ->
        c.cambiarFuente("imprimir(\"hola\");")
        c.ejecutarPasoAPaso()
        val control = esperar(c, SituacionEjecucion.PAUSADA)
        val paso = assertNotNull(c.estado.value.pasoActual)
        assertEquals(1L, control.paso)
        assertTrue(c.estado.value.salida.isEmpty())
        delay(650) // incluye el debounce: no debe pisar la pausa
        assertEquals(paso, c.estado.value.pasoActual)
        assertEquals(SituacionEjecucion.PAUSADA, c.control.value.situacion)
        assertFalse(c.estado.value.terminado)
    }

    @Test
    fun siguienteAvanzaUnaSolaPausa() = prueba { c ->
        c.cambiarFuente("entero x = 1; imprimir(x);")
        c.ejecutarPasoAPaso()
        esperar(c, SituacionEjecucion.PAUSADA)
        val pc = c.estado.value.pasoActual!!.pc
        c.siguiente()
        esperar(c, SituacionEjecucion.PAUSADA)
        assertEquals(2L, c.control.value.paso)
        assertTrue(pc != c.estado.value.pasoActual!!.pc)
        delay(80)
        assertEquals(2L, c.control.value.paso)
    }

    @Test
    fun continuarTerminaYConservaSalidaYContadorFinal() = prueba { c ->
        c.cambiarFuente("imprimir(\"hola\");")
        c.ejecutarPasoAPaso()
        esperar(c, SituacionEjecucion.PAUSADA)
        c.continuar()
        esperar(c, SituacionEjecucion.FINALIZADA)
        assertTrue(c.estado.value.salida.joinToString("") { it.texto }.contains("hola"))
        assertTrue(c.estado.value.cuadruplasEjecutadas!! > 0)
        assertEquals(null, c.estado.value.pasoActual)
    }

    @Test
    fun detenerEnPausaNoEjecutaLaInstruccionPendiente() = prueba { c ->
        c.cambiarFuente("imprimir(\"no debe salir\");")
        c.ejecutarPasoAPaso()
        esperar(c, SituacionEjecucion.PAUSADA)
        c.detener()
        c.siguiente() // ya no tiene efecto
        delay(80)
        assertEquals(SituacionEjecucion.DETENIDA, c.control.value.situacion)
        assertTrue(c.estado.value.salida.isEmpty())
        assertFalse(c.estado.value.pidiendoEntrada)
    }

    @Test
    fun corridaCanceladaNoPisaALaNueva() = prueba { c ->
        c.cambiarFuente("imprimir(\"viejo\");")
        c.ejecutarPasoAPaso()
        esperar(c, SituacionEjecucion.PAUSADA)
        c.cambiarFuente("imprimir(\"nuevo\");")
        c.ejecutar()
        esperar(c, SituacionEjecucion.FINALIZADA)
        delay(100)
        val salida = c.estado.value.salida.joinToString("") { it.texto }
        assertTrue(salida.contains("nuevo"))
        assertFalse(salida.contains("viejo"))
    }

    @Test
    fun editarDetieneYDescartaLaFotoDeOtraFuente() = prueba { c ->
        c.cambiarFuente("imprimir(1);")
        c.ejecutarPasoAPaso()
        esperar(c, SituacionEjecucion.PAUSADA)
        c.cambiarFuente("imprimir(2);")
        assertEquals(SituacionEjecucion.DETENIDA, c.control.value.situacion)
        assertEquals(null, c.estado.value.pasoActual)
        assertEquals("imprimir(2);", c.estado.value.fuente)
    }

    @Test
    fun errorDeAnalisisNoQuedaPausado() = prueba { c ->
        c.cambiarFuente("imprimir(noExiste);")
        c.ejecutarPasoAPaso()
        esperar(c, SituacionEjecucion.ERROR)
        assertTrue(c.estado.value.terminado)
        assertEquals(null, c.estado.value.pasoActual)
    }
    @Test
    fun analisisPendienteNoBorraUnaCorridaRapida() = prueba { c ->
        c.cambiarFuente("imprimir(123);")
        c.ejecutar()
        esperar(c, SituacionEjecucion.FINALIZADA)
        delay(650)
        assertTrue(c.estado.value.terminado)
        assertNotNull(c.estado.value.cuadruplasEjecutadas)
    }

    @Test
    fun entradaSuspendeIndependientementeDeLaCompuerta() = prueba { c ->
        c.cambiarFuente("cadena nombre = leer(); imprimir(nombre);")
        c.ejecutarPasoAPaso()
        esperar(c, SituacionEjecucion.PAUSADA)
        c.continuar()
        esperar(c, SituacionEjecucion.ENTRADA)
        assertTrue(c.estado.value.pidiendoEntrada)
        c.enviarEntrada("Raul")
        esperar(c, SituacionEjecucion.FINALIZADA)
        assertTrue(c.estado.value.salida.joinToString("") { it.texto }.contains("Raul"))
        assertFalse(c.estado.value.pidiendoEntrada)
    }

    @Test
    fun detenerMientrasEsperaEntradaCancelaLaCorrutina() = prueba { c ->
        c.cambiarFuente("cadena nombre = leer(); imprimir(nombre);")
        c.ejecutar()
        esperar(c, SituacionEjecucion.ENTRADA)
        c.detener()
        c.enviarEntrada("no")
        delay(80)
        assertEquals(SituacionEjecucion.DETENIDA, c.control.value.situacion)
        assertFalse(c.estado.value.pidiendoEntrada)
        assertTrue(c.estado.value.salida.isEmpty())
    }

}
