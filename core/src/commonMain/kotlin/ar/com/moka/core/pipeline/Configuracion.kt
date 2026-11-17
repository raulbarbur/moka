// =======================================================================
//  Configuracion.kt — Ajustes de la fachada Moka (sec. 4.7)
// =======================================================================
//  Lo que una corrida puede querer cambiar: los guardarrailes de la
//  maquina (sec. 4.6). La CLI deja todo por defecto; la GUI pone un
//  limite de cuadruplas para que un ciclo infinito no la cuelgue.
// =======================================================================
package ar.com.moka.core.pipeline

data class Configuracion(
    // Limite de cuadruplas ejecutadas; null = sin limite (sec. 4.6).
    val limiteCuadruplas: Long? = null,
    // Profundidad maxima de la pila de llamadas (sec. 4.6).
    val profundidadMaxima: Int = 1000
)
