// =======================================================================
//  EventoPipeline.kt — Eventos del pipeline (sec. 4.7)
// =======================================================================
//  Identico al del documento. El nucleo no sabe quien escucha: la UI
//  recolecta el Flow y actualiza paneles, la CLI imprime solo la salida,
//  los tests verifican artefactos. Cada FaseCompletada lleva el artefacto
//  completo de su fase y sus diagnosticos, asi cada panel se dibuja ni
//  bien termina su fase.
// =======================================================================
package ar.com.moka.core.pipeline

import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.semantico.Diagnostico

sealed interface EventoPipeline {
    data class FaseIniciada(val fase: Fase) : EventoPipeline

    data class FaseCompletada(
        val fase: Fase,
        val artefacto: Artefacto,
        val diagnosticos: List<Diagnostico>,
        val duracionMs: Long
    ) : EventoPipeline

    // La fase no corrio porque una anterior termino con errores (o porque
    // el modo no la incluye, como la ejecucion en SOLO_ANALISIS).
    data class FaseOmitida(val fase: Fase, val motivo: String) : EventoPipeline

    // Un evento de la fase de ejecucion, tal como lo emite la MaquinaTac.
    data class DeEjecucion(val evento: EventoEjecucion) : EventoPipeline

    // Fin del pipeline. exito = ninguna fase con errores y ningun error
    // de ejecucion (las advertencias no lo cambian, sec. 4.8).
    data class Terminado(val exito: Boolean) : EventoPipeline
}
