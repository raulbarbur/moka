// SesionArchivoMokaTest.kt - Reglas de cierre seguro del documento.
package ar.com.moka.app.archivos

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SesionArchivoMokaTest {
    @Test
    fun documentoLimpioPuedeCerrar() {
        val sesion = SesionArchivoMoka()
        assertTrue(sesion.pedirCierre(""))
        assertFalse(sesion.cierrePendiente)
    }

    @Test
    fun cambiosSinGuardarPidenConfirmacion() {
        val sesion = SesionArchivoMoka()
        sesion.marcarGuardado(ArchivoMoka("a.moka", "uno", "a.moka"), "uno")

        assertFalse(sesion.pedirCierre("dos"))
        assertTrue(sesion.cierrePendiente)

        sesion.cancelarCierre()
        assertFalse(sesion.cierrePendiente)
    }

    @Test
    fun guardarVuelveAPermitirElCierre() {
        val sesion = SesionArchivoMoka()
        sesion.marcarGuardado(ArchivoMoka("a.moka", "uno", "a.moka"), "uno")

        assertFalse(sesion.pedirCierre("dos"))
        sesion.cancelarCierre()
        sesion.marcarGuardado(ArchivoMoka("a.moka", "dos", "a.moka"), "dos")

        assertTrue(sesion.pedirCierre("dos"))
        assertFalse(sesion.cierrePendiente)
    }

    @Test
    fun unaOperacionDeArchivoEnCursoNoSeCierraEnSilencio() {
        val sesion = SesionArchivoMoka()
        sesion.comenzarOperacion()

        assertFalse(sesion.pedirCierre(""))
        assertTrue(sesion.cierrePendiente)

        sesion.terminarOperacion()
        sesion.cancelarCierre()
        assertTrue(sesion.pedirCierre(""))
    }

    @Test
    fun unEjemploCargadoCuentaComoCopiaSinGuardar() {
        val sesion = SesionArchivoMoka()
        val ejemplo = ArchivoMoka("hola.moka", "imprimir(1);")

        sesion.cargar(ejemplo)

        assertTrue(sesion.hayCambios(ejemplo.contenido))
        assertFalse(sesion.pedirCierre(ejemplo.contenido))
    }
}
