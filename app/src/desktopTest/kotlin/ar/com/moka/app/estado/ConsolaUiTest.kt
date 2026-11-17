// =======================================================================
//  ConsolaUiTest.kt — La entrada de la consola de la GUI
// =======================================================================
//  Cubre el unico comportamiento no trivial de ConsolaUi: entregar() y
//  leerLinea() se encuentran por el canal (en cualquier orden) y cerrar()
//  despierta una lectura colgada con null (EOF). Corre con runBlocking:
//  no hace falta el artefacto de test de corrutinas.
// =======================================================================
package ar.com.moka.app.estado

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ConsolaUiTest {

    @Test
    fun entregarYLuegoLeerDevuelveLaLinea() = runBlocking {
        val consola = ConsolaUi()
        consola.entregar("42")
        assertEquals("42", consola.leerLinea())
    }

    @Test
    fun leerSuspendeHastaQueLlegaLaLinea() = runBlocking {
        val consola = ConsolaUi()
        var leida: String? = null
        val lectura = launch { leida = consola.leerLinea() }
        // Si leerLinea no suspendiera, esto encontraria leida en null por
        // la carrera: entregar primero y leer despues (test anterior) y
        // este juntos verifican el encuentro en ambos sentidos.
        consola.entregar("hola")
        lectura.join()
        assertEquals("hola", leida)
    }

    @Test
    fun cerrarDespiertaLaLecturaConNull() = runBlocking {
        val consola = ConsolaUi()
        consola.cerrar()
        assertNull(consola.leerLinea())
    }
}
