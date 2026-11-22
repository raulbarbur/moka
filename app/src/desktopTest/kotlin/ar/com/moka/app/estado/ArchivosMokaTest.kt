// ArchivosMokaTest.kt - Contrato sencillo sin abrir dialogos nativos.
package ar.com.moka.app.estado

import ar.com.moka.app.archivos.ArchivoMoka
import ar.com.moka.app.archivos.EjemplosMoka
import ar.com.moka.app.archivos.nombreMoka
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchivosMokaTest {
    @Test
    fun nombreVacioUsaNombrePorDefecto() {
        assertEquals("sin-titulo.moka", nombreMoka("  "))
    }

    @Test
    fun agregaExtensionSinDuplicarla() {
        assertEquals("programa.moka", nombreMoka(" programa "))
        assertEquals("programa.moka", nombreMoka("programa.moka"))
        assertEquals("programa.MOKA", nombreMoka("programa.MOKA"))
    }

    @Test
    fun documentoNuevoNoTieneDestino() {
        assertNull(ArchivoMoka("nuevo.moka", "").referencia)
    }

    @Test
    fun ejemplosSonCopiasSinDestinoDeGuardado() {
        assertEquals(
            listOf(
                "hola.moka", "bucles.moka", "funciones.moka", "arreglos.moka",
                "segun.moka", "clases.moka", "entrada.moka", "fizzbuzz.moka", "consola.moka"
            ),
            EjemplosMoka.todos.map { it.nombre }
        )
        for (ejemplo in EjemplosMoka.todos) {
            assertNull(ejemplo.referencia)
            assertTrue(ejemplo.contenido.isNotBlank())
            assertTrue(ejemplo.contenido.endsWith("\n"))
        }
    }

    @Test
    fun holaConservaLaFuenteDelRepositorio() {
        assertEquals("imprimir(\"Hola, Moka!\");\n", EjemplosMoka.todos.first().contenido)
    }
}
