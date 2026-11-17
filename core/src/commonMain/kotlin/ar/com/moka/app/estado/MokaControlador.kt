// =======================================================================
//  MokaControlador.kt — El "view model" del Taller (sec. 5.4)
// =======================================================================
//  Une el pipeline con la UI sin que ninguno conozca al otro:
//
//    fuente (editor) --ejecutar()--> Moka.procesar() --Flow--> reducir()
//        |                                                      |
//        +--(debounce 400 ms)--> Moka.analizar() -> conAnalisisEnVivo()
//                                                                   |
//    paneles  <-------- StateFlow<EstadoWorkbench> <----------------+
//
//  Decisiones (sec. 5.4):
//  - El pipeline y los analisis corren en Dispatchers.Default; la UI
//    solo OBSERVA el StateFlow en el hilo principal.
//  - Una corrida nueva CANCELA la anterior (trabajo?.cancel()).
//  - Analisis en vivo (sec. 5.2): cada cambio del editor entra a un
//    SharedFlow con debounce de 400 ms; collectLatest cancela el
//    analisis anterior si se sigue escribiendo. Mientras hay una
//    EJECUCION en curso (corriendo = true), el resultado del analisis
//    en vivo se DESCARTA al llegar: no debe pisar los chips de la
//    corrida. (La bandera es una lectura simple entre corrutinas del
//    mismo scope: el peor caso es descartar un analisis que se repetira
//    con la proxima tecla.)
//  - Es una clase propia con su CoroutineScope (no el ViewModel de
//    androidx del bosquejo del doc): mismo comportamiento en desktop y
//    Android sin agregar dependencias.
//  - La GUI pone limite de 10 millones de cuadruplas (sec. 4.6).
// =======================================================================
package ar.com.moka.app.estado

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.pipeline.Configuracion
import ar.com.moka.core.pipeline.Moka
import ar.com.moka.core.pipeline.Modo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MokaControlador(
    private val moka: Moka = Moka(Configuracion(limiteCuadruplas = 10_000_000))
) {

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _estado = MutableStateFlow(EstadoWorkbench.vacio())
    val estado: StateFlow<EstadoWorkbench> = _estado.asStateFlow()

    private var consola = ConsolaUi()
    private var trabajo: Job? = null
    private var corriendo = false

    private val cambiosFuente = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        ambito.launch {
            cambiosFuente.debounce(400).collectLatest { fuente ->
                val resultado = moka.analizar(fuente)
                _estado.update { if (corriendo) it else it.conAnalisisEnVivo(resultado) }
            }
        }
    }

    // El editor avisa cada cambio de texto: actualiza la fuente y agenda
    // el analisis en vivo (400 ms despues de la ultima tecla).
    fun cambiarFuente(nueva: String) {
        _estado.update { it.copy(fuente = nueva) }
        cambiosFuente.tryEmit(nueva)
    }

    fun ejecutar() = lanzar(Modo.EJECUTAR)

    fun ejecutarPasoAPaso() = lanzar(Modo.PASO_A_PASO)

    // El campo de entrada de la consola manda la linea a la maquina, que
    // esta suspendida en leerLinea().
    fun enviarEntrada(linea: String) = consola.entregar(linea)

    // Un panel avisa que se toco un elemento con posicion: queda como la
    // seleccion compartida que sincroniza paneles y editor (sec. 5.1).
    fun seleccionar(pos: Posicion?) {
        _estado.update { it.copy(seleccion = pos) }
    }

    // Boton Detener: cancela la corrutina y despierta una posible
    // leerLinea() colgada. El estado queda como estaba (se ve hasta donde
    // llego la salida).
    fun detener() {
        trabajo?.cancel()
        corriendo = false
        consola.cerrar()
        _estado.update { it.copy(pidiendoEntrada = false) }
    }

    // El dueno lo llama al destruir la pantalla: cancela todo y cierra el
    // scope. Despues de liberar() el controlador no se usa mas.
    fun liberar() {
        detener()
        ambito.cancel()
    }

    private fun lanzar(modo: Modo) {
        trabajo?.cancel()
        consola.cerrar()
        val consolaCorrida = ConsolaUi()
        consola = consolaCorrida
        val fuente = _estado.value.fuente
        _estado.value = EstadoWorkbench.vacio(fuente)
        corriendo = true
        trabajo = ambito.launch {
            try {
                moka.procesar(fuente, consolaCorrida, modo).collect { evento ->
                    _estado.update { it.reducir(evento) }
                }
            } finally {
                corriendo = false
            }
        }
    }
}
