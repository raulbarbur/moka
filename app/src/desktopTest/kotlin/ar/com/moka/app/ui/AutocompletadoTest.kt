package ar.com.moka.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutocompletadoTest {
    private fun sugerir(texto: String): List<String> {
        // El cursor se marca con '|' dentro del texto de prueba.
        val cursor = texto.indexOf('|')
        return Autocompletado.sugerir(texto.removeRange(cursor, cursor + 1), cursor)
    }

    @Test fun sugierePalabraClave() {
        val r = sugerir("mien|")
        assertEquals(listOf("mientras"), r)
    }

    @Test fun conMasDeUnaCoincidenciaPrimeroPalabrasClave() {
        val r = sugerir("entero valor;\nen|")
        assertEquals("entero", r.first())
    }

    @Test fun sugiereVariablesDelPrograma() {
        val r = sugerir("entero contador = 0;\nentero total = 0;\ncon|")
        assertTrue("contador" in r, r.toString())
        // Las palabras clave (constante, continuar...) van antes que la variable.
        assertEquals("contador", r.last())
        assertTrue("constante" in r)
    }

    @Test fun noSeSugiereASiMismo() {
        // 'contador' aparece una vez, justo donde se escribe.
        assertEquals(emptyList(), sugerir("contador|"))
    }

    @Test fun incluyeFuncionesIncorporadas() {
        assertEquals(listOf("imprimir", "imprimir_sin_salto"), sugerir("imp|"))
    }

    @Test fun necesitaDosLetras() {
        assertEquals(emptyList(), sugerir("m|"))
    }

    @Test fun noSugiereDentroDeCadena() {
        assertEquals(emptyList(), sugerir("imprimir(\"hola mien|"))
    }

    @Test fun noSugiereDentroDeComentario() {
        assertEquals(emptyList(), sugerir("// mien|"))
        assertEquals(emptyList(), sugerir("/* hacer mien| */"))
    }

    @Test fun despuesDeLaCadenaVuelveASugerir() {
        assertEquals(listOf("mientras"), sugerir("imprimir(\"hola\"); mien|"))
    }

    @Test fun noSugiereSiElCursorEstaEnMedioDeUnaPalabra() {
        assertEquals(emptyList(), sugerir("mie|ntras"))
    }

    @Test fun noSugiereNumeros() {
        assertNull(Autocompletado.prefijoEn("123", 3))
        assertEquals(emptyList(), sugerir("x = 12|"))
    }

    @Test fun maximoSeisSugerencias() {
        val texto = "aa1 aa2 aa3 aa4 aa5 aa6 aa7 aa8 aa|"
        assertEquals(6, sugerir(texto).size)
    }

    @Test fun aceptarReemplazaElPrefijoYMueveElCursor() {
        val fuente = "mien"
        val prefijo = Autocompletado.prefijoEn(fuente, 4)!!
        val (nuevo, cursor) = Autocompletado.aceptar(fuente, prefijo, "mientras")
        assertEquals("mientras", nuevo)
        assertEquals(8, cursor)
    }

    @Test fun aceptarEnElMedioConservaElResto() {
        val fuente = "si (x) { imp }"
        val cursorEn = fuente.indexOf(" }")
        val prefijo = Autocompletado.prefijoEn(fuente, cursorEn)!!
        val (nuevo, cursor) = Autocompletado.aceptar(fuente, prefijo, "imprimir")
        assertEquals("si (x) { imprimir }", nuevo)
        assertEquals(cursorEn + 5, cursor)
    }

    @Test fun lasPalabrasClaveSalenDelResaltado() {
        assertTrue("mientras" in Autocompletado.PALABRAS_CLAVE)
        assertTrue("entero" in Autocompletado.PALABRAS_CLAVE)
        assertTrue("+" !in Autocompletado.PALABRAS_CLAVE)
    }
}
