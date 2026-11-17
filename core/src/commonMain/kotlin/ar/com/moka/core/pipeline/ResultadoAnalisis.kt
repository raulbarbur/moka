// =======================================================================
//  ResultadoAnalisis.kt — Resultado sincrono de Moka.analizar (sec. 4.7)
// =======================================================================
//  El analisis en vivo de la GUI (mientras se escribe) no puede suspender
//  ni recolectar un Flow por cada tecla: analizar() corre las fases 1 a 4
//  de forma sincrona y devuelve este paquete con los artefactos que se
//  pudieron producir (null los de las fases omitidas), todos los
//  diagnosticos juntos y el motivo de cada omision.
// =======================================================================
package ar.com.moka.core.pipeline

import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad

data class ResultadoAnalisis(
    val lexico: Artefacto.Lexico,                 // siempre esta (es la primera fase)
    val sintactico: Artefacto.Sintactico?,
    val semantico: Artefacto.Semantico?,
    val tac: Artefacto.Tac?,
    val diagnosticos: List<Diagnostico>,          // de todas las fases, en orden
    val omitidas: Map<Fase, String>               // fase -> motivo de la omision
) {
    // Sin errores y con TAC generado: el programa esta listo para ejecutar.
    val exito: Boolean
        get() = tac != null && diagnosticos.none { it.severidad == Severidad.ERROR }
}
