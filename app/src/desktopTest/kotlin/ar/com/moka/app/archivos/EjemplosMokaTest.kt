// =======================================================================
//  EjemplosMokaTest.kt - Los ejemplos del menu, corridos de verdad
// =======================================================================
//  Cada ejemplo del menu Archivo > Ejemplos se corre con el interprete
//  real (EjecutorTutorial, el mismo arnes de los tutoriales) y su salida
//  se compara contra la esperada EXACTA: un ejemplo del menu no puede
//  romperse ni cambiar de salida sin que este test avise.
//
//  entrada.moka es interactivo: las respuestas del usuario se prefijan
//  ("Raul", "27") como hace ConsolaDePrueba en los tests del nucleo.
// =======================================================================
package ar.com.moka.app.archivos

import ar.com.moka.app.ui.EjecutorTutorial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EjemplosMokaTest {

    private fun ejemplo(nombre: String): ArchivoMoka =
        EjemplosMoka.todos.first { it.nombre == nombre }

    private fun corre(nombre: String, entradas: List<String> = emptyList()): String {
        val r = EjecutorTutorial.correr(ejemplo(nombre).contenido, entradas)
        assertTrue(r.errores.isEmpty(), "$nombre no deberia tener errores: ${r.errores}")
        return r.salida
    }

    @Test
    fun hola() {
        assertEquals("Hola, Moka!\n", corre("hola.moka"))
    }

    @Test
    fun bucles() {
        val esperada = (1..10).joinToString("") { "7 x $it = ${7 * it}\n" } +
                "\nFaltan 3...\nFaltan 2...\nFaltan 1...\nDespegue!\n"
        assertEquals(esperada, corre("bucles.moka"))
    }

    @Test
    fun funciones() {
        assertEquals("Hola, Moka!\nfactorial(5) = 120\n", corre("funciones.moka"))
    }

    @Test
    fun arreglos() {
        assertEquals(
            "Cantidad de notas: 5\nPromedio: 8\nNota mas alta: 10\n",
            corre("arreglos.moka")
        )
    }

    @Test
    fun segun() {
        assertEquals("Restas un punto\n", corre("segun.moka"))
    }

    @Test
    fun clases() {
        assertEquals(
            "Hola, soy Ana y tengo 20 anos.\nAna cumplio 21 anos.\n",
            corre("clases.moka")
        )
    }

    @Test
    fun entrada() {
        assertEquals(
            "Como te llamas?\nHola, Raul!\nCuantos anos tenes?\nEl ano que viene vas a tener 28.\n",
            corre("entrada.moka", entradas = listOf("Raul", "27"))
        )
    }

    @Test
    fun fizzbuzz() {
        val esperada = (1..100).joinToString("") { i ->
            when {
                i % 15 == 0 -> "FizzBuzz\n"
                i % 3 == 0 -> "Fizz\n"
                i % 5 == 0 -> "Buzz\n"
                else -> "$i\n"
            }
        }
        assertEquals(esperada, corre("fizzbuzz.moka"))
    }

    @Test
    fun consolaTerminaLimpiaSinErrores() {
        // consola.moka usa limpiar/color/pausa/titulo: su efecto son
        // eventos, no salida acumulada, asi que se verifica que corra
        // completo y que sus impresiones lleguen en orden.
        assertEquals(
            "=== Moka ===\nTexto normal.\nTexto en verde.\nTexto en rojo.\n" +
                    "Se limpia la consola en un segundo...\nConsola limpia: la salida anterior ya no esta.\n",
            corre("consola.moka")
        )
    }
}
