// =======================================================================
//  EstadoWorkbench.kt — El estado completo de la pantalla Taller (sec. 5.4)
// =======================================================================
//  UNA SOLA FUENTE DE VERDAD (sec. 5.1): todo lo que la UI muestra sale
//  de este objeto inmutable. Los eventos del pipeline se aplican con
//  reducir() y el analisis en vivo con conAnalisisEnVivo(); ambas son
//  FUNCIONES PURAS (estado + entrada -> estado nuevo): sin efectos, sin
//  corrutinas, faciles de testear sin correr el compilador.
//
//  Los paneles no recalculan nada: cada uno lee el slot de su fase
//  (estado.fases[fase]) y se recompone solo cuando su artefacto cambia.
// =======================================================================
package ar.com.moka.app.estado

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Fase
import ar.com.moka.core.pipeline.ResultadoAnalisis
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad

// El slot de una fase: como esta, su artefacto (si ya corrio), sus
// diagnosticos, cuanto tardo y, si se omitio, por que.
data class EstadoFaseUi(
    val estado: EstadoFase = EstadoFase.PENDIENTE,
    val artefacto: Artefacto? = null,
    val diagnosticos: List<Diagnostico> = emptyList(),
    val duracionMs: Long? = null,
    val motivoOmision: String? = null
)

// Una linea de la consola del Taller. esError la pinta en rojo (los
// errores de ejecucion tambien aparecen aca, ademas de en Problemas).
// color es el nombre canonico pedido con color("...") (ver
// ColoresConsola en core); null es el color por defecto del tema.
data class LineaConsola(
    val texto: String,
    val esError: Boolean = false,
    val color: String? = null
)

data class EstadoWorkbench(
    val fuente: String = "",
    val fases: Map<Fase, EstadoFaseUi> = Fase.entries.associateWith { EstadoFaseUi() },
    val salida: List<LineaConsola> = emptyList(),
    val pidiendoEntrada: Boolean = false,
    val pasoActual: EventoEjecucion.Paso? = null,
    val cuadruplasEjecutadas: Long? = null,   // la del evento Fin (informativa)
    val seleccion: Posicion? = null,     // sincronizacion entre paneles (sec. 5.1)
    val terminado: Boolean = false,
    val exito: Boolean = false,
    // Consola con estilo: color vigente para las proximas Salidas (lo
    // fija color()/restaurar_color()) y titulo pedido con titulo(...).
    val colorConsola: String? = null,
    val tituloConsola: String? = null
) {

    // Todos los diagnosticos de todas las fases, en orden de fase: es lo
    // que lista el panel Problemas (sec. 5.3, panel 7).
    val diagnosticos: List<Diagnostico>
        get() = Fase.entries.flatMap { fases.getValue(it).diagnosticos }

    // ------------------------- analisis en vivo -------------------------

    // Aplica el resultado de Moka.analizar() (sec. 5.2: mientras se
    // escribe). Actualiza las fases 1 a 4 y deja EJECUCION pendiente: el
    // analisis en vivo no ejecuta. La salida de la consola se conserva
    // (es historia de corridas), pero el paso a paso y el cierre ya no
    // reflejan el texto actual, asi que se limpian.
    //
    // Reparto de diagnosticos: ResultadoAnalisis los trae en una sola
    // lista y Diagnostico no lleva su fase (pendiente desde la Fase 3),
    // asi que se reparten por prefijo del codigo: LEX-, SIN-, SEM-.
    fun conAnalisisEnVivo(r: ResultadoAnalisis): EstadoWorkbench {
        fun diags(prefijo: String) = r.diagnosticos.filter { it.codigo.startsWith(prefijo) }
        fun estadoSegun(ds: List<Diagnostico>) =
            if (ds.any { it.severidad == Severidad.ERROR }) EstadoFase.CON_ERRORES else EstadoFase.COMPLETADA
        fun slot(artefacto: Artefacto?, fase: Fase, prefijo: String): EstadoFaseUi =
            if (artefacto == null) {
                EstadoFaseUi(
                    estado = EstadoFase.OMITIDA,
                    motivoOmision = r.omitidas[fase] ?: "una fase anterior termino con errores"
                )
            } else {
                val ds = diags(prefijo)
                EstadoFaseUi(estado = estadoSegun(ds), artefacto = artefacto, diagnosticos = ds)
            }
        return copy(
            fases = mapOf(
                Fase.LEXICA to slot(r.lexico, Fase.LEXICA, "LEX"),
                Fase.SINTACTICA to slot(r.sintactico, Fase.SINTACTICA, "SIN"),
                Fase.SEMANTICA to slot(r.semantico, Fase.SEMANTICA, "SEM"),
                Fase.GENERACION_TAC to slot(r.tac, Fase.GENERACION_TAC, "TAC"),
                Fase.EJECUCION to EstadoFaseUi() // pendiente: no se ejecuto
            ),
            pasoActual = null,
            terminado = false,
            exito = false
        )
    }

    // ------------------------- eventos del pipeline -------------------------

    // Estado + evento -> estado nuevo. Pura: mismo estado y mismo evento,
    // mismo resultado.
    fun reducir(evento: EventoPipeline): EstadoWorkbench = when (evento) {
        is EventoPipeline.FaseIniciada -> actualizarFase(evento.fase) {
            it.copy(estado = EstadoFase.CORRIENDO)
        }
        is EventoPipeline.FaseCompletada -> actualizarFase(evento.fase) {
            it.copy(
                estado = if (hayError(evento.diagnosticos)) EstadoFase.CON_ERRORES else EstadoFase.COMPLETADA,
                artefacto = evento.artefacto,
                diagnosticos = evento.diagnosticos,
                duracionMs = evento.duracionMs
            )
        }
        is EventoPipeline.FaseOmitida -> actualizarFase(evento.fase) {
            it.copy(estado = EstadoFase.OMITIDA, motivoOmision = evento.motivo)
        }
        is EventoPipeline.DeEjecucion -> reducirEjecucion(evento.evento)
        is EventoPipeline.Terminado -> copy(
            terminado = true,
            exito = evento.exito,
            pidiendoEntrada = false
        )
    }

    // Los eventos de la MaquinaTac, que llegan envueltos en DeEjecucion.
    private fun reducirEjecucion(evento: EventoEjecucion): EstadoWorkbench = when (evento) {
        // Cada Salida hereda el color vigente al momento de imprimirse:
        // cambiar el color no repinta lo ya escrito, como en una consola.
        is EventoEjecucion.Salida -> copy(salida = salida + LineaConsola(evento.texto, color = colorConsola))
        is EventoEjecucion.LimpiarConsola -> copy(salida = emptyList())
        is EventoEjecucion.ColorConsola -> copy(colorConsola = evento.color)
        is EventoEjecucion.TituloConsola -> copy(tituloConsola = evento.titulo)
        is EventoEjecucion.PidiendoEntrada -> copy(pidiendoEntrada = true)
        is EventoEjecucion.Paso -> copy(pasoActual = evento, pidiendoEntrada = false)
        is EventoEjecucion.ErrorEjecucion ->
            copy(
                // La pista didactica viaja en la misma linea de error,
                // en un renglon aparte e indentada (ConsolaTaller junta
                // los trozos, asi que el \n interno se respeta).
                salida = salida + LineaConsola(
                    evento.diagnostico.mensaje + (evento.diagnostico.pista?.let { "\n   Pista: $it" } ?: ""),
                    esError = true
                ),
                pidiendoEntrada = false
            ).actualizarFase(Fase.EJECUCION) {
                it.copy(
                    estado = EstadoFase.CON_ERRORES,
                    diagnosticos = it.diagnosticos + evento.diagnostico
                )
            }
        // Fin normal: la fase EJECUCION no emite FaseCompletada (no tiene
        // Artefacto, sec. 4.7), asi que el chip se cierra aca.
        is EventoEjecucion.Fin -> copy(
            pidiendoEntrada = false,
            cuadruplasEjecutadas = evento.cuadruplasEjecutadas
        ).actualizarFase(Fase.EJECUCION) {
            if (it.estado == EstadoFase.CON_ERRORES) it else it.copy(estado = EstadoFase.COMPLETADA)
        }
    }

    private fun actualizarFase(fase: Fase, cambio: (EstadoFaseUi) -> EstadoFaseUi): EstadoWorkbench =
        copy(fases = fases + (fase to cambio(fases.getValue(fase))))

    private fun hayError(diagnosticos: List<Diagnostico>): Boolean =
        diagnosticos.any { it.severidad == Severidad.ERROR }

    companion object {
        // Estado inicial de una corrida: fuente nueva, todo pendiente.
        fun vacio(fuente: String = "") = EstadoWorkbench(fuente = fuente)
    }
}
