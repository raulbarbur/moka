// =======================================================================
//  ConsolaUi.kt — La Consola que la GUI le presta a la MaquinaTac
// =======================================================================
//  Mitad del contrato solamente: la SALIDA no pasa por aca (la maquina la
//  emite como eventos Salida y el estado la acumula ahi, sec. 5.1: una
//  sola fuente de verdad). Lo que si pasa por aca es la ENTRADA: cuando
//  el programa ejecuta leer / leer_entero / leer_flotante, la maquina
//  llama a leerLinea() y se suspende hasta que el usuario escribe una
//  linea en el campo de la consola y el controlador llama a entregar().
//
//  Un Channel sin tope desacopla los dos lados: entregar() nunca se
//  bloquea aunque el usuario escriba de mas, y leerLinea() espera sin
//  consumir CPU. cerrar() despierta una leerLinea() colgada con null
//  (EOF): es lo que pasa cuando se detiene la ejecucion.
// =======================================================================
package ar.com.moka.app.estado

import ar.com.moka.core.ejecucion.Consola
import kotlinx.coroutines.channels.Channel

class ConsolaUi : Consola {

    private val entrada = Channel<String>(Channel.UNLIMITED)

    // Sin efecto a proposito: la salida visible llega como evento
    // EventoEjecucion.Salida -> DeEjecucion -> reducir -> estado.salida.
    override fun escribir(texto: String) = Unit

    // Suspende hasta que haya una linea. null = canal cerrado (EOF): la
    // maquina lo trata como cadena vacia (ver Consola en el nucleo).
    override suspend fun leerLinea(): String? = entrada.receiveCatching().getOrNull()

    // El campo de entrada de la consola entrega lo que escribio el
    // usuario. Nunca se bloquea (canal sin tope).
    fun entregar(linea: String) {
        entrada.trySend(linea)
    }

    // Detener la ejecucion: despierta una leerLinea() en espera.
    fun cerrar() {
        entrada.close()
    }
}
