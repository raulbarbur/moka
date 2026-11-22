package ar.com.moka.app.ui

import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Moka
import ar.com.moka.core.semantico.Severidad
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

// Corre un programa Moka de punta a punta con el interprete real y
// devuelve lo que imprimio y los errores (de cualquier fase).
class ResultadoCorrida(val salida: String, val errores: List<String>)

object EjecutorTutorial {
    fun correr(fuente: String, entradas: List<String> = emptyList()): ResultadoCorrida {
        val pendientes = entradas.toMutableList()
        val consola = object : Consola {
            override fun escribir(texto: String) {}
            override suspend fun leerLinea(): String? = if (pendientes.isEmpty()) null else pendientes.removeAt(0)
        }
        val salida = StringBuilder()
        val errores = mutableListOf<String>()
        runBlocking {
            Moka().procesar(fuente, consola).toList().forEach { ev ->
                when (ev) {
                    is EventoPipeline.FaseCompletada ->
                        ev.diagnosticos.filter { it.severidad == Severidad.ERROR }
                            .forEach { errores.add("${it.pos.linea}:${it.pos.columna} ${it.mensaje}") }
                    is EventoPipeline.DeEjecucion -> when (val e = ev.evento) {
                        is EventoEjecucion.Salida -> salida.append(e.texto)
                        is EventoEjecucion.ErrorEjecucion -> errores.add("ejecucion: ${e.diagnostico.mensaje}")
                        else -> {}
                    }
                    else -> {}
                }
            }
        }
        return ResultadoCorrida(salida.toString(), errores)
    }
}
