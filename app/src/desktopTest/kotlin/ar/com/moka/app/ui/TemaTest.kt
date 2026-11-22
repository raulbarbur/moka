// =======================================================================
//  TemaTest.kt - La paleta centralizada de Tema.kt
// =======================================================================
//  Tema.kt es el UNICO lugar donde se editan colores. El contrato que
//  importa: todo color que el lenguaje acepta en color("...") (la lista
//  canonica de ColoresConsola en core) tiene su pinta en la GUI, en los
//  dos temas. Si alguien agrega un color al lenguaje y olvida la paleta,
//  este test falla.
// =======================================================================
package ar.com.moka.app.ui

import ar.com.moka.core.ejecucion.ColoresConsola
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class TemaTest {

    @Test
    fun todoColorDelLenguajeTienePintaEnAmbosTemas() {
        for (nombre in ColoresConsola.nombres) {
            assertNotNull(colorDeConsolaMoka(nombre, oscuro = false), "Falta '$nombre' en la paleta clara")
            assertNotNull(colorDeConsolaMoka(nombre, oscuro = true), "Falta '$nombre' en la paleta oscura")
        }
    }

    @Test
    fun lasDosPaletasDeConsolaCubrenLosMismosNombres() {
        assertEquals(
            coloresConsolaMoka(oscuro = false).porNombre.keys,
            coloresConsolaMoka(oscuro = true).porNombre.keys
        )
    }

    @Test
    fun lasVariantesClaraYOscuraNoSonElMismoColor() {
        // La variante existe para leerse sobre fondos distintos: si ambas
        // son iguales, la mitad de los temas queda ilegible.
        for (nombre in ColoresConsola.nombres) {
            assertNotEquals(
                colorDeConsolaMoka(nombre, oscuro = false),
                colorDeConsolaMoka(nombre, oscuro = true),
                "'$nombre' tiene la misma pinta en claro y en oscuro"
            )
        }
    }

    @Test
    fun elEditorTieneColoresDistintosParaCadaRol() {
        for (oscuro in listOf(false, true)) {
            val c = coloresEditorMoka(oscuro)
            val roles = listOf(c.palabraClave, c.literal, c.delimitador, c.error, c.advertencia)
            assertEquals(roles.size, roles.distinct().size, "Roles repetidos en oscuro=$oscuro")
        }
    }
}
