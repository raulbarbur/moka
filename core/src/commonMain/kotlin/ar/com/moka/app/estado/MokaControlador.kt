// MokaControlador.kt - Pipeline, analisis en vivo y depuracion por cuadrupla.
package ar.com.moka.app.estado

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.Configuracion
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Fase
import ar.com.moka.core.pipeline.Moka
import ar.com.moka.core.pipeline.Modo
import ar.com.moka.core.paquetes.ProveedorPaquetes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SituacionEjecucion { INACTIVA, ANALIZANDO, CORRIENDO, PAUSADA, ENTRADA, DETENIDA, FINALIZADA, ERROR }

data class ControlEjecucion(
    val situacion: SituacionEjecucion = SituacionEjecucion.INACTIVA,
    // Numero de eventos Paso observados, NO pc: los saltos pueden volver atras.
    val paso: Long = 0,
    val mensaje: String? = null
) {
    val activa: Boolean get() = situacion in setOf(
        SituacionEjecucion.ANALIZANDO, SituacionEjecucion.CORRIENDO,
        SituacionEjecucion.PAUSADA, SituacionEjecucion.ENTRADA
    )
}

@OptIn(FlowPreview::class)
class MokaControlador(
    // Moka 2 (P3): quien encuentra los paquetes de los `usar`. La app
    // pasa el proveedor de su EstadoProyecto; sin el, `usar` da SEM-062.
    proveedorPaquetes: ProveedorPaquetes? = null,
    private val moka: Moka = Moka(Configuracion(limiteCuadruplas = 10_000_000, proveedorPaquetes = proveedorPaquetes)),
    // Inyectable para tests. Las acciones publicas se llaman desde la UI.
    private val ui: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val ambito = CoroutineScope(SupervisorJob() + ui)
    private val _estado = MutableStateFlow(EstadoWorkbench.vacio())
    val estado: StateFlow<EstadoWorkbench> = _estado.asStateFlow()
    private val _control = MutableStateFlow(ControlEjecucion())
    val control: StateFlow<ControlEjecucion> = _control.asStateFlow()
    private val cambiosFuente = MutableStateFlow("" to 0L)
    private var revision = 0L

    // Cada corrida posee su consola y su compuerta. Cancelarla nunca abre
    // la compuerta de una corrida posterior. Capacidad 1 evita perder el
    // clic que llega entre publicar PAUSADA y empezar receive().
    private class Corrida(var porPasos: Boolean) {
        val consola = ConsolaUi()
        val permiso = Channel<Unit>(1)
        var trabajo: Job? = null
    }
    private var actual: Corrida? = null

    init {
        ambito.launch {
            cambiosFuente.debounce(400).collectLatest { (fuente, version) ->
                // La revision viaja con la tecla, no se toma despues del
                // debounce: una corrida rapida tampoco puede ser pisada.
                if (actual != null || version != revision) return@collectLatest
                val resultado = withContext(Dispatchers.Default) { moka.analizar(fuente) }
                if (actual == null && version == revision && fuente == _estado.value.fuente) {
                    _estado.value = _estado.value.conAnalisisEnVivo(resultado)
                }
            }
        }
    }

    // P3: al cambiar la carpeta proyecto, el mismo fuente puede analizar
    // distinto (los `usar` encuentran o no sus paquetes). Fuerza una
    // pasada nueva sin tocar el texto ni el historial de deshacer.
    fun reanalizar() {
        revision++
        _estado.value = _estado.value.copy(pasoActual = null, seleccion = null)
        cambiosFuente.value = _estado.value.fuente to revision
    }

    fun cambiarFuente(nueva: String) {
        if (nueva == _estado.value.fuente) return
        registrarDeshacer(_estado.value.fuente)
        cambiarFuenteInterno(nueva)
    }

    // ---- Deshacer / rehacer del texto fuente ----
    // Historial simple con tope: cada cambio que llega a mas de 700 ms
    // del anterior abre un paso nuevo; tipear seguido se deshace de una
    // sola vez. Pensado para los botones de la barra del editor, sobre
    // todo en celular, donde no hay Ctrl+Z comodo.
    private val deshacerPila = ArrayDeque<String>()
    private val rehacerPila = ArrayDeque<String>()
    private var ultimaEdicion = 0L

    private fun registrarDeshacer(anterior: String) {
        val ahora = System.currentTimeMillis()
        if (deshacerPila.isEmpty() || ahora - ultimaEdicion > 700) {
            deshacerPila.addLast(anterior)
            if (deshacerPila.size > 200) deshacerPila.removeFirst()
        }
        ultimaEdicion = ahora
        rehacerPila.clear()
    }

    fun deshacer() {
        val anterior = deshacerPila.removeLastOrNull() ?: return
        rehacerPila.addLast(_estado.value.fuente)
        ultimaEdicion = 0
        cambiarFuenteInterno(anterior)
    }

    fun rehacer() {
        val siguiente = rehacerPila.removeLastOrNull() ?: return
        deshacerPila.addLast(_estado.value.fuente)
        ultimaEdicion = 0
        cambiarFuenteInterno(siguiente)
    }

    private fun cambiarFuenteInterno(nueva: String) {
        // No mezclamos variables/TAC de una fuente con el editor de otra.
        if (actual != null) detener()
        revision++
        _estado.value = _estado.value.copy(fuente = nueva, pasoActual = null, seleccion = null)
        cambiosFuente.value = nueva to revision
    }

    fun ejecutar() = lanzar(Modo.EJECUTAR)
    fun ejecutarPasoAPaso() = lanzar(Modo.PASO_A_PASO)

    fun siguiente() {
        val corrida = actual ?: return
        if (_control.value.situacion != SituacionEjecucion.PAUSADA) return
        // Cambiar el estado antes de enviar evita acumular clics mientras
        // todavia se ve la misma pausa. Nunca duerme ni bloquea el hilo UI.
        _control.value = _control.value.copy(situacion = SituacionEjecucion.CORRIENDO)
        corrida.permiso.trySend(Unit)
    }

    fun continuar() {
        val corrida = actual ?: return
        if (_control.value.situacion != SituacionEjecucion.PAUSADA) return
        corrida.porPasos = false
        _control.value = _control.value.copy(situacion = SituacionEjecucion.CORRIENDO)
        corrida.permiso.close() // despierta la pausa; los proximos Paso ya no esperan
    }

    fun enviarEntrada(linea: String) {
        val corrida = actual ?: return
        if (_control.value.situacion != SituacionEjecucion.ENTRADA) return
        _estado.value = _estado.value.copy(pidiendoEntrada = false)
        _control.value = _control.value.copy(situacion = SituacionEjecucion.CORRIENDO)
        corrida.consola.entregar(linea)
    }

    fun seleccionar(pos: Posicion?) {
        _estado.value = _estado.value.copy(seleccion = pos)
    }

    fun detener() {
        val corrida = actual ?: return
        actual = null // invalida publicaciones ANTES de cancelar
        revision++
        corrida.trabajo?.cancel()
        corrida.permiso.cancel()
        corrida.consola.cerrar()
        _control.value = _control.value.copy(situacion = SituacionEjecucion.DETENIDA)
        val estado = _estado.value
        _estado.value = estado.copy(
            pidiendoEntrada = false,
            fases = estado.fases.mapValues { (_, fase) ->
                if (fase.estado == EstadoFase.CORRIENDO) fase.copy(
                    estado = EstadoFase.OMITIDA, motivoOmision = "ejecucion detenida por el usuario"
                ) else fase
            }
        )
    }

    fun liberar() {
        detener()
        ambito.cancel()
    }

    private fun lanzar(modo: Modo) {
        detener()
        revision++ // descarta analisis en vivo empezados antes de esta corrida
        val corrida = Corrida(modo == Modo.PASO_A_PASO)
        actual = corrida
        val fuente = _estado.value.fuente
        _estado.value = EstadoWorkbench.vacio(fuente)
        _control.value = ControlEjecucion(SituacionEjecucion.ANALIZANDO)
        corrida.trabajo = ambito.launch {
            try {
                withContext(Dispatchers.Default) {
                    // No flowOn/buffer aca: el productor debe quedar frenado
                    // EN el emit(Paso), antes de ejecutar la instruccion.
                    moka.procesar(fuente, corrida.consola, modo).collect { evento ->
                        val esperar = withContext(ui) {
                            if (actual !== corrida) return@withContext false
                            _estado.value = _estado.value.reducir(evento)
                            when (evento) {
                                is EventoPipeline.FaseIniciada -> if (evento.fase == Fase.EJECUCION) {
                                    _control.value = _control.value.copy(situacion = SituacionEjecucion.CORRIENDO)
                                }
                                is EventoPipeline.DeEjecucion -> when (val ev = evento.evento) {
                                    is EventoEjecucion.Paso -> {
                                        if (corrida.porPasos) {
                                            _estado.value = _estado.value.copy(seleccion = ev.cuadrupla.origen)
                                            _control.value = _control.value.copy(
                                                situacion = SituacionEjecucion.PAUSADA,
                                                paso = _control.value.paso + 1
                                            )
                                        } else {
                                            // Continuar ya no es una inspeccion: no mostrar una foto
                                            // fugaz como si la maquina siguiera pausada ahi.
                                            _estado.value = _estado.value.copy(pasoActual = null)
                                        }
                                    }
                                    is EventoEjecucion.PidiendoEntrada ->
                                        _control.value = _control.value.copy(situacion = SituacionEjecucion.ENTRADA)
                                    else -> Unit
                                }
                                is EventoPipeline.Terminado -> {
                                    _control.value = _control.value.copy(
                                        situacion = if (evento.exito) SituacionEjecucion.FINALIZADA else SituacionEjecucion.ERROR
                                    )
                                    _estado.value = _estado.value.copy(pasoActual = null)
                                }
                                else -> Unit
                            }
                            evento is EventoPipeline.DeEjecucion &&
                                    evento.evento is EventoEjecucion.Paso && corrida.porPasos
                        }
                        if (esperar) corrida.permiso.receiveCatching()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (actual === corrida) {
                    _control.value = _control.value.copy(
                        situacion = SituacionEjecucion.ERROR, mensaje = e.message ?: "Fallo inesperado"
                    )
                    _estado.value = _estado.value.copy(pidiendoEntrada = false, pasoActual = null)
                }
            } finally {
                corrida.consola.cerrar()
                corrida.permiso.cancel()
                if (actual === corrida) actual = null
            }
        }
    }
}
