// ============================================================================
//  PaquetesTest.kt — Tests de la carga de paquetes (Moka 2, fase P1)
// ============================================================================
//  Cubre el camino completo con el proveedor en memoria: `usar` + llamada
//  cualificada ejecutada de verdad, y todos los errores de carga
//  (SEM-062 a SEM-068) con su mensaje.
// ============================================================================
package ar.com.moka.core.paquetes

import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.Configuracion
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Moka
import ar.com.moka.core.semantico.Diagnostico
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

private class ConsolaMuda : Consola {
    override fun escribir(texto: String) {}
    override suspend fun leerLinea(): String? = null
}

class PaquetesTest {

    private data class Corrida(val salida: String, val diagnosticos: List<Diagnostico>, val exito: Boolean)

    // Corre el programa principal con los paquetes dados (nombre -> fuente).
    private fun correr(principal: String, paquetes: Map<String, String> = emptyMap(), sinProveedor: Boolean = false): Corrida {
        val proveedor = if (sinProveedor) null else ProveedorPaquetes { paquetes[it] }
        val eventos = runBlocking {
            Moka(Configuracion(proveedorPaquetes = proveedor)).procesar(principal, ConsolaMuda()).toList()
        }
        val salida = eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }
            .filterIsInstance<EventoEjecucion.Salida>()
            .joinToString("") { it.texto }
        val diagnosticos = eventos.filterIsInstance<EventoPipeline.FaseCompletada>()
            .flatMap { it.diagnosticos }
        val exito = eventos.filterIsInstance<EventoPipeline.Terminado>().last().exito
        return Corrida(salida, diagnosticos, exito)
    }

    private val util = """
        paquete util;
        funcion entero doble(entero x) {
            retornar x * 2;
        }
        funcion entero cuadruple(entero x) {
            retornar doble(doble(x));
        }
    """.trimIndent()

    // ------------------------- caminos felices -------------------------

    @Test
    fun llamadaCualificadaEjecuta() {
        val r = correr(
            """
            usar util;
            imprimir(a_cadena(util.doble(21)));
            """.trimIndent(),
            mapOf("util" to util)
        )
        assertEquals("42\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun dentroDelPaqueteLasFuncionesSeLlamanSinCualificar() {
        val r = correr(
            """
            usar util;
            imprimir(a_cadena(util.cuadruple(3)));
            """.trimIndent(),
            mapOf("util" to util)
        )
        assertEquals("12\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun dosPaquetesConLaMismaFuncionNoChocan() {
        val r = correr(
            """
            usar a;
            usar b;
            imprimir(a_cadena(a.sumar(1, 2)) + " " + a_cadena(b.sumar(1, 2)));
            """.trimIndent(),
            mapOf(
                "a" to "paquete a;\nfuncion entero sumar(entero x, entero z) {\n retornar x + z;\n}",
                "b" to "paquete b;\nfuncion entero sumar(entero x, entero z) {\n retornar (x + z) * 10;\n}"
            )
        )
        assertEquals("3 30\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun paqueteQueUsaOtroPaquete() {
        val r = correr(
            """
            usar a;
            imprimir(a_cadena(a.arranque()));
            """.trimIndent(),
            mapOf(
                "a" to "paquete a;\nusar b;\nfuncion entero arranque() {\n retornar b.base() + 1;\n}",
                "b" to "paquete b;\nfuncion entero base() {\n retornar 41;\n}"
            )
        )
        assertEquals("42\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun clasesDelPaqueteFuncionanDentroDelPaquete() {
        val r = correr(
            """
            usar geo;
            imprimir(a_cadena(geo.norma2()));
            """.trimIndent(),
            mapOf(
                "geo" to """
                paquete geo;
                clase Punto {
                    entero x;
                    entero fila;
                    constructor(entero px, entero py) {
                        este.x = px;
                        este.fila = py;
                    }
                    funcion entero modulo2() {
                        retornar este.x * este.x + este.fila * este.fila;
                    }
                }
                funcion entero norma2() {
                    Punto p = nuevo Punto(3, 4);
                    retornar p.modulo2();
                }
                """.trimIndent()
            )
        )
        assertEquals("25\n", r.salida)
        assertTrue(r.exito)
    }

    // --------------------------- errores -------------------------------

    private fun codigos(r: Corrida) = r.diagnosticos.map { it.codigo }

    @Test
    fun paqueteNoEncontrado() {
        val r = correr("usar util;\nimprimir(a_cadena(1));")
        assertTrue("SEM-062" in codigos(r), codigos(r).toString())
        assertTrue(!r.exito)
    }

    @Test
    fun sinProveedorTodoUsoFalla() {
        val r = correr("usar util;", sinProveedor = true)
        assertTrue("SEM-062" in codigos(r), codigos(r).toString())
    }

    @Test
    fun encabezadoFaltanteODistinto() {
        val r1 = correr("usar util;", mapOf("util" to "funcion entero f() {\n retornar 1;\n}"))
        assertTrue("SEM-064" in codigos(r1), codigos(r1).toString())
        val r2 = correr("usar util;", mapOf("util" to "paquete otro;\nfuncion entero f() {\n retornar 1;\n}"))
        assertTrue("SEM-064" in codigos(r2), codigos(r2).toString())
    }

    @Test
    fun cicloDePaquetes() {
        val r = correr(
            "usar a;",
            mapOf(
                "a" to "paquete a;\nusar b;\nfuncion entero f() {\n retornar 1;\n}",
                "b" to "paquete b;\nusar a;\nfuncion entero g() {\n retornar 2;\n}"
            )
        )
        assertTrue("SEM-065" in codigos(r), codigos(r).toString())
    }

    @Test
    fun paqueteConCodigoSuelto() {
        val r = correr("usar util;", mapOf("util" to "paquete util;\nimprimir(a_cadena(1));"))
        assertTrue("SEM-066" in codigos(r), codigos(r).toString())
    }

    @Test
    fun funcionInexistenteEnElPaquete() {
        val r = correr("usar util;\nimprimir(a_cadena(util.triple(1)));", mapOf("util" to util))
        assertTrue("SEM-067" in codigos(r), codigos(r).toString())
    }

    @Test
    fun paqueteCargadoPeroNoUsado() {
        val r = correr(
            "usar a;\nimprimir(a_cadena(b.base()));",
            mapOf(
                "a" to "paquete a;\nusar b;\nfuncion entero arranque() {\n retornar b.base();\n}",
                "b" to "paquete b;\nfuncion entero base() {\n retornar 41;\n}"
            )
        )
        assertTrue("SEM-068" in codigos(r), codigos(r).toString())
    }

    @Test
    fun usarDuplicadoSeCargaUnaVez() {
        val r = correr(
            "usar util;\nusar util;\nimprimir(a_cadena(util.doble(5)));",
            mapOf("util" to util)
        )
        assertEquals("10\n", r.salida)
        assertTrue(r.exito)
    }
}
