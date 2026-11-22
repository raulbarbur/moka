// ============================================================================
//  ProyectoMokaTest.kt — Carpeta de proyecto en la app (Moka 2, fase P3)
// ============================================================================
//  El proveedor del EstadoProyecto lee los .moka de los paquetes desde la
//  carpeta elegida (convencion de la CLI: "util" -> util.moka al lado;
//  "util.texto" -> util/texto.moka en una subcarpeta). Sin carpeta, todo
//  `usar` da SEM-062 como antes de P3.
// ============================================================================
package ar.com.moka.app.archivos

import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.Configuracion
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Moka
import ar.com.moka.core.semantico.Diagnostico
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

private class ConsolaMudaProyecto : Consola {
    override fun escribir(texto: String) {}
    override suspend fun leerLinea(): String? = null
}

class ProyectoMokaTest {

    private data class Corrida(val salida: String, val diagnosticos: List<Diagnostico>, val exito: Boolean)

    private fun correr(fuente: String, proyecto: EstadoProyecto): Corrida {
        val eventos = runBlocking {
            Moka(Configuracion(proveedorPaquetes = proyecto.proveedor))
                .procesar(fuente, ConsolaMudaProyecto()).toList()
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

    private fun escribirPaquete(carpeta: Path, relativo: String, contenido: String) {
        val destino = carpeta.resolve(relativo)
        Files.createDirectories(destino.parent)
        Files.write(destino, contenido.toByteArray(Charsets.UTF_8))
    }

    private val util = """
        paquete util;
        funcion entero doble(entero x) {
            retornar x * 2;
        }
    """.trimIndent()

    // ------------------------- lectura de la carpeta -------------------------

    @Test
    fun leeElArchivoDelPaquete() {
        val carpeta = Files.createTempDirectory("moka-p3")
        escribirPaquete(carpeta, "util.moka", util)
        assertEquals(util, leerTextoDeCarpeta(carpeta.toString(), "util.moka"))
    }

    @Test
    fun paqueteConPuntosSeBuscaEnSubcarpeta() {
        val carpeta = Files.createTempDirectory("moka-p3")
        escribirPaquete(carpeta, "util/texto.moka", "paquete util.texto;")
        assertEquals("paquete util.texto;", leerTextoDeCarpeta(carpeta.toString(), "util/texto.moka"))
    }

    @Test
    fun archivoInexistenteDaNull() {
        val carpeta = Files.createTempDirectory("moka-p3")
        assertNull(leerTextoDeCarpeta(carpeta.toString(), "fantasma.moka"))
    }

    // ------------------------- el proveedor en el pipeline -------------------------

    @Test
    fun sinCarpetaTodoUsarDaSem062() {
        val proyecto = EstadoProyecto()
        val r = correr("usar util;\nimprimir(a_cadena(util.doble(21)));", proyecto)
        assertTrue(r.diagnosticos.any { it.codigo == "SEM-062" })
        assertTrue(!r.exito)
    }

    @Test
    fun conCarpetaElUsarEjecuta() {
        val carpeta = Files.createTempDirectory("moka-p3")
        escribirPaquete(carpeta, "util.moka", util)
        val proyecto = EstadoProyecto()
        proyecto.fijar(carpeta.toString())
        val r = correr("usar util;\nimprimir(a_cadena(util.doble(21)));", proyecto)
        assertEquals("42\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun cambiarDeCarpetaCambiaLoQueVeElMismoProveedor() {
        // El controlador se crea una sola vez con este proveedor: elegir
        // otra carpeta (menu Proyecto) tiene que bastar, sin reconstruir
        // nada. Dos carpetas con el mismo paquete y resultados distintos.
        val carpetaA = Files.createTempDirectory("moka-p3-a")
        val carpetaB = Files.createTempDirectory("moka-p3-b")
        escribirPaquete(carpetaA, "util.moka", util)
        escribirPaquete(carpetaB, "util.moka", util.replace("x * 2", "x * 10"))
        val proyecto = EstadoProyecto()
        proyecto.fijar(carpetaA.toString())
        assertEquals("42\n", correr("usar util;\nimprimir(a_cadena(util.doble(21)));", proyecto).salida)
        proyecto.fijar(carpetaB.toString())
        assertEquals("210\n", correr("usar util;\nimprimir(a_cadena(util.doble(21)));", proyecto).salida)
        proyecto.fijar(null)
        assertTrue(correr("usar util;\nimprimir(a_cadena(util.doble(21)));", proyecto).diagnosticos.any { it.codigo == "SEM-062" })
    }
}
