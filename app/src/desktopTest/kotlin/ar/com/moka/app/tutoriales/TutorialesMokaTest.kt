package ar.com.moka.app.ui

import ar.com.moka.app.tutoriales.LECCIONES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Corre CADA ejemplo y CADA solucion de los tutoriales con el interprete
// real: si una leccion promete una salida, tiene que ser la verdadera.
class TutorialesMokaTest {
    @Test fun losIdsSonUnicos() {
        assertEquals(LECCIONES.size, LECCIONES.map { it.id }.toSet().size)
    }

    @Test fun cadaEjemploCorreComoDice() {
        for (l in LECCIONES) {
            val r = EjecutorTutorial.correr(l.codigo, l.entradas)
            if (l.errorEsperado != null) {
                assertTrue(
                    r.errores.any { l.errorEsperado in it },
                    "${l.id}: se esperaba un error con '${l.errorEsperado}' y hubo ${r.errores}"
                )
            } else {
                assertTrue(r.errores.isEmpty(), "${l.id}: errores inesperados ${r.errores}")
            }
            if (l.salida != null) {
                assertEquals(l.salida, r.salida.trimEnd(), "${l.id}: la salida no coincide")
            }
        }
    }

    @Test fun cadaSolucionCorreSinErrores() {
        for (l in LECCIONES) {
            val r = EjecutorTutorial.correr(l.solucion, l.entradasSolucion)
            assertTrue(r.errores.isEmpty(), "${l.id}: la solucion da errores ${r.errores}")
            assertTrue(r.salida.isNotBlank(), "${l.id}: la solucion no imprime nada")
        }
    }
}
