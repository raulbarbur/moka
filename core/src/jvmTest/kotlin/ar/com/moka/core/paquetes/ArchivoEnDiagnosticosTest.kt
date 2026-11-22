// ============================================================================
//  ArchivoEnDiagnosticosTest.kt — Los errores dicen de que archivo salieron
//  (Moka 2, fase P4)
// ============================================================================
//  Cuando un programa usa paquetes, un error dentro de util.moka tiene
//  que poder nombrar a "util" y no confundirse con una linea del archivo
//  principal. La posicion de cada nodo/diagnostico lleva `archivo`:
//  null en el archivo principal (salvo que se configure su nombre) y el
//  nombre del paquete en el codigo cargado con `usar`.
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

private class ConsolaMudaP4 : Consola {
    override fun escribir(texto: String) {}
    override suspend fun leerLinea(): String? = null
}

class ArchivoEnDiagnosticosTest {

    private data class Corrida(val salida: String, val diagnosticos: List<Diagnostico>, val exito: Boolean)

    // Corre el principal con paquetes en memoria; nombrePrincipal imita a
    // la CLI pasando el nombre real del archivo.
    private fun correr(
        principal: String,
        paquetes: Map<String, String> = emptyMap(),
        nombrePrincipal: String? = null
    ): Corrida {
        val eventos = runBlocking {
            Moka(
                Configuracion(
                    proveedorPaquetes = ProveedorPaquetes { paquetes[it] },
                    archivoPrincipal = nombrePrincipal
                )
            ).procesar(principal, ConsolaMudaP4()).toList()
        }
        val salida = eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }
            .filterIsInstance<EventoEjecucion.Salida>()
            .joinToString("") { it.texto }
        // Los errores de ejecucion viajan dentro de DeEjecucion, no en
        // los diagnosticos de la fase: se juntan aca para el test.
        val diagnosticos = eventos.filterIsInstance<EventoPipeline.FaseCompletada>()
            .flatMap { it.diagnosticos } +
                eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
                    .map { it.evento }
                    .filterIsInstance<EventoEjecucion.ErrorEjecucion>()
                    .map { it.diagnostico }
        val exito = eventos.filterIsInstance<EventoPipeline.Terminado>().last().exito
        return Corrida(salida, diagnosticos, exito)
    }

    // ------------------------- archivo en la posicion -------------------------

    @Test
    fun errorSemanticoEnPaqueteNombraAlPaquete() {
        val util = """
            paquete util;
            funcion entero rota(entero x) {
                retornar z + 1;
            }
        """.trimIndent()
        val r = correr("usar util;\nimprimir(a_cadena(util.rota(2)));", mapOf("util" to util))
        val dePaquete = r.diagnosticos.filter { it.pos.archivo == "util" }
        assertTrue(dePaquete.isNotEmpty(), "algun diagnostico tiene que venir marcado con el paquete 'util': ${r.diagnosticos}")
        assertTrue(dePaquete.all { it.pos.linea > 0 })
    }

    @Test
    fun errorEnElPrincipalNoLlevaArchivoPorDefecto() {
        val r = correr("imprimir(z);")
        assertTrue(r.diagnosticos.isNotEmpty())
        assertTrue(r.diagnosticos.all { it.pos.archivo == null })
    }

    @Test
    fun errorEnElPrincipalLlevaElNombreConfigurado() {
        val r = correr("imprimir(z);", nombrePrincipal = "principal.moka")
        assertTrue(r.diagnosticos.isNotEmpty())
        assertTrue(r.diagnosticos.all { it.pos.archivo == "principal.moka" })
    }

    @Test
    fun errorDeSintaxisEnPaqueteSeReportaEnElUso() {
        // SEM-063 se reporta en el `usar` del principal (el paquete no se
        // llega a unir), asi que su posicion NO lleva archivo.
        val util = "paquete util;\nfuncion entero rota(entero x) { retornar x + ; }"
        val r = correr("usar util;", mapOf("util" to util))
        val sem063 = r.diagnosticos.filter { it.codigo == "SEM-063" }
        assertEquals(1, sem063.size)
        assertNull(sem063.first().pos.archivo)
    }

    @Test
    fun errorDeEjecucionEnPaqueteNombraAlPaquete() {
        val util = """
            paquete util;
            funcion entero explota(entero x) {
                retornar x / 0;
            }
        """.trimIndent()
        val r = correr("usar util;\nimprimir(a_cadena(util.explota(4)));", mapOf("util" to util))
        val eje = r.diagnosticos.filter { it.codigo.startsWith("EJE-") }
        assertTrue(eje.isNotEmpty(), "la division por cero tiene que producir un EJE-: ${r.diagnosticos}")
        assertEquals("util", eje.first().pos.archivo)
    }
}
