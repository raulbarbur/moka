package ar.com.moka.web

import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Modo
import ar.com.moka.core.pipeline.Moka
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Fase 0: ejecuta un programa Moka en JavaScript y vuelca todo a console.log.
// Sirve en el navegador (abrir la consola de dev) y en Node.
private class ConsolaWeb : Consola {
    override fun escribir(texto: String) = println(texto)
    override suspend fun leerLinea(): String? = null
}

private const val PROGRAMA = """
entero a = 7;
flotante b = 2.5;
imprimir("Hola, Moka en JS!");
imprimir(a * 2);
imprimir(b * 2);
para (entero i = 0; i < 3; i = i + 1) {
    imprimir("i = " + i);
}
"""

fun main() {
    MainScope().launch {
        Moka().procesar(PROGRAMA, ConsolaWeb(), Modo.EJECUTAR).collect { ev ->
            when (ev) {
                is EventoPipeline.FaseCompletada -> ev.diagnosticos.forEach { println("DIAG: $it") }
                is EventoPipeline.DeEjecucion -> {
                    val e = ev.evento
                    if (e is EventoEjecucion.Salida) print(e.texto)
                    if (e is EventoEjecucion.ErrorEjecucion) println("ERROR: ${e.diagnostico}")
                }
                is EventoPipeline.Terminado -> println("\n[fase 0] terminado, exito=${ev.exito}")
                else -> {}
            }
        }
    }
}
