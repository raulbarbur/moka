// GlobalesPasoTest.kt - Fotos previas a la instruccion, no mapas vivos.
package ar.com.moka.core.ejecucion

import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Modo
import ar.com.moka.core.pipeline.Moka
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class GlobalesPasoTest {
    private fun pasos(fuente: String): List<EventoEjecucion.Paso> = runBlocking {
        val consola = object : Consola {
            override fun escribir(texto: String) = Unit
            override suspend fun leerLinea(): String? = null
        }
        val eventos = Moka().procesar(fuente, consola, Modo.PASO_A_PASO).toList()
        assertTrue((eventos.last() as EventoPipeline.Terminado).exito)
        eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }.filterIsInstance<EventoEjecucion.Paso>()
    }

    @Test
    fun globalCambiaSinModificarLasFotosAnteriores() {
        val fotos = pasos("entero x = 1; x = 3; imprimir(x);")
        assertTrue(fotos.first().globales.isEmpty()) // todavia no se asigno x
        val antes = fotos.first { "1" in it.globales.values }
        val despues = fotos.first { "3" in it.globales.values }
        assertEquals(antes.globales.keys, despues.globales.keys)
        assertTrue("1" in antes.globales.values) // sigue siendo 1 tras terminar
        assertFalse("3" in antes.globales.values)
        assertTrue("3" in despues.globales.values)
    }

    @Test
    fun camposEstaticosUsanNombreDeClaseYCampo() {
        val fotos = pasos("""
            clase Contador { estatico entero total; }
            Contador.total = 7;
            imprimir(Contador.total);
        """.trimIndent())
        assertTrue(fotos.any { it.globales["Contador.total"] == "7" })
        assertTrue(fotos.first().globales.isEmpty())
    }

    @Test
    fun localQuedaEnSuMarcoNoEnGlobales() {
        val fotos = pasos("""
            funcion entero doble(entero n) {
                entero local = n * 2;
                retornar local;
            }
            imprimir(doble(4));
        """.trimIndent())
        assertTrue(fotos.any { foto -> foto.marcos.any { "8" in it.variables.values } })
        assertTrue(fotos.all { it.globales.isEmpty() })
    }
}
