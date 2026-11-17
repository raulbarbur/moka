// =======================================================================
//  Consola.kt — Abstraccion de entrada/salida de la maquina (sec. 4.6)
// =======================================================================
//  Interfaz minima que la MaquinaTac usa para el builtin `imprimir` (via
//  el evento Salida, no por aca) y para los builtins de lectura
//  (`leer`, `leer_entero`, `leer_flotante`). La CLI la implementa con
//  System.in/out; la GUI con un textbox suspendido hasta que el usuario
//  escribe; los tests con una cola de lineas prefijada.
// =======================================================================
package ar.com.moka.core.ejecucion

interface Consola {

    // Salida directa por si la capa superior prefiere no usar los
    // eventos Salida (la maquina emite eventos y NO llama a escribir;
    // quien consume el Flow decide si delega aca).
    fun escribir(texto: String)

    // Suspende hasta que haya una linea disponible. null = fin de
    // entrada (EOF): la maquina lo trata como cadena vacia para leer y
    // como entrada invalida para leer_entero / leer_flotante.
    suspend fun leerLinea(): String?
}
