// =======================================================================
//  Configuracion.kt — Ajustes de la fachada Moka (sec. 4.7)
// =======================================================================
//  Lo que una corrida puede querer cambiar: los guardarrailes de la
//  maquina (sec. 4.6). La CLI deja todo por defecto; la GUI pone un
//  limite de cuadruplas para que un ciclo infinito no la cuelgue.
// =======================================================================
package ar.com.moka.core.pipeline

import ar.com.moka.core.paquetes.ProveedorPaquetes

data class Configuracion(
    // Limite de cuadruplas ejecutadas; null = sin limite (sec. 4.6).
    val limiteCuadruplas: Long? = null,
    // Profundidad maxima de la pila de llamadas (sec. 4.6).
    val profundidadMaxima: Int = 1000,
    // Moka 2 (P1): quien busca la fuente de un `usar paquete;`. Null
    // significa "no hay carga de paquetes" y todo usar da SEM-062.
    val proveedorPaquetes: ProveedorPaquetes? = null,
    // Moka 2 (P4): nombre del archivo principal, para que sus errores
    // digan de que archivo salieron. Null = mostrar solo linea/columna,
    // como siempre. La CLI pasa el nombre real; la GUI lo deja en null
    // (sus errores de paquetes igual muestran el nombre del paquete).
    val archivoPrincipal: String? = null
)
