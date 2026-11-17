// ============================================================================
//  Moka.kt — Fachada unica del nucleo (sec. 4.7)
// ============================================================================
//  EL UNICO PUNTO DE ENTRADA del nucleo: la UI, la CLI y los tests pasan
//  todos por aca. Publicar/suscribir: procesar() emite un Flow de
//  EventoPipeline y no sabe quien escucha.
//
//  Encadenamiento (sec. 4.1): cada fase corre solo si la anterior no
//  produjo ERRORES (las advertencias no frenan); la que no corre sale como
//  FaseOmitida con el motivo. Los artefactos parciales se conservan: con
//  errores lexicos igual salen los tokens, con errores sintacticos sale el
//  arbol de parseo con sus nodos de error (pero no el AST), con errores
//  semanticos salen la tabla de simbolos y los chequeos.
//
//  Hilos (sec. 5.4): el runtime de ANTLR no es thread-safe; cada llamada
//  crea sus propias instancias de lexer y parser dentro de los analizadores
//  y corre en la corrutina del que la invoco. Nada se comparte entre
//  llamadas: la fachada no guarda estado.
// ============================================================================
package ar.com.moka.core.pipeline

import ar.com.moka.core.ast.ProgramaAst
import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.ejecucion.MaquinaTac
import ar.com.moka.core.ejecucion.ConfigEjecucion
import ar.com.moka.core.lexico.AnalizadorLexico
import ar.com.moka.core.pipeline.EventoPipeline.*
import ar.com.moka.core.semantico.AnalizadorSemantico
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.ResultadoSemantico
import ar.com.moka.core.semantico.Severidad
import ar.com.moka.core.sintactico.AnalizadorSintactico
import ar.com.moka.core.tac.GeneradorTac
import ar.com.moka.core.tac.ProgramaTac
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class Moka(private val configuracion: Configuracion = Configuracion()) {

    // Analisis completo + ejecucion. Emite un evento por fase a medida que
    // se completa, y los eventos de la maquina envueltos en DeEjecucion.
    fun procesar(fuente: String, consola: Consola, modo: Modo = Modo.EJECUTAR): Flow<EventoPipeline> = flow {
        val parcial = analizarFases(fuente)
        for (e in parcial.eventos) emit(e)

        var exito = !parcial.huboErrores
        when {
            modo == Modo.SOLO_ANALISIS ->
                emit(FaseOmitida(Fase.EJECUCION, "el modo es solo analisis"))
            parcial.tac == null ->
                emit(FaseOmitida(Fase.EJECUCION, "una fase anterior termino con errores"))
            else -> {
                emit(FaseIniciada(Fase.EJECUCION))
                val config = ConfigEjecucion(
                    profundidadMaxima = configuracion.profundidadMaxima,
                    limiteCuadruplas = configuracion.limiteCuadruplas,
                    pasoAPaso = modo == Modo.PASO_A_PASO
                )
                MaquinaTac(parcial.tac, consola, config).ejecutar().collect { ev ->
                    emit(DeEjecucion(ev))
                    if (ev is EventoEjecucion.ErrorEjecucion) exito = false
                }
                // La fase de ejecucion no emite FaseCompletada: su
                // "artefacto" es el flujo de eventos mismo (sec. 4.1) y la
                // variante no existe en Artefacto. El cierre es Terminado.
            }
        }
        emit(Terminado(exito))
    }

    // Solo analisis (fases 1 a 4), sincrono: para el analisis en vivo
    // mientras se escribe (sec. 4.7). No ejecuta el programa.
    fun analizar(fuente: String): ResultadoAnalisis {
        val parcial = analizarFases(fuente)
        var lexico: Artefacto.Lexico? = null
        var sintactico: Artefacto.Sintactico? = null
        var semantico: Artefacto.Semantico? = null
        var tac: Artefacto.Tac? = null
        val diagnosticos = mutableListOf<Diagnostico>()
        val omitidas = mutableMapOf<Fase, String>()
        for (e in parcial.eventos) {
            when (e) {
                is FaseCompletada -> {
                    diagnosticos.addAll(e.diagnosticos)
                    when (val a = e.artefacto) {
                        is Artefacto.Lexico -> lexico = a
                        is Artefacto.Sintactico -> sintactico = a
                        is Artefacto.Semantico -> semantico = a
                        is Artefacto.Tac -> tac = a
                    }
                }
                is FaseOmitida -> omitidas[e.fase] = e.motivo
                else -> {} // FaseIniciada y Terminado no aportan al resultado
            }
        }
        // La fase lexica siempre corre: nunca es null.
        return ResultadoAnalisis(requireNotNull(lexico), sintactico, semantico, tac, diagnosticos, omitidas)
    }

    // -------------------------------------------------------------------
    //  Las fases 1 a 4, sincronas (ninguna suspende: solo la ejecucion
    //  necesita la consola). Devuelve los eventos ya armados y lo que la
    //  fase 5 necesita para correr.
    // -------------------------------------------------------------------

    private fun analizarFases(fuente: String): AnalisisParcial {
        val eventos = mutableListOf<EventoPipeline>()
        var huboErrores = false
        var motivoOmision = ""

        // --- Fase 1: lexica (siempre corre) ---
        eventos.add(FaseIniciada(Fase.LEXICA))
        var marca = TimeSource.Monotonic.markNow()
        val lex = AnalizadorLexico().analizar(fuente)
        eventos.add(FaseCompletada(Fase.LEXICA, Artefacto.Lexico(lex.tokens), lex.diagnosticos, marca.elapsedNow().inWholeMilliseconds))
        if (hayErrores(lex.diagnosticos)) {
            huboErrores = true
            motivoOmision = "la fase lexica termino con errores"
        }

        // --- Fase 2: sintactica ---
        var ast: ProgramaAst? = null
        if (huboErrores) {
            eventos.add(FaseOmitida(Fase.SINTACTICA, motivoOmision))
        } else {
            eventos.add(FaseIniciada(Fase.SINTACTICA))
            marca = TimeSource.Monotonic.markNow()
            val sin = AnalizadorSintactico().analizar(fuente)
            eventos.add(FaseCompletada(Fase.SINTACTICA, Artefacto.Sintactico(sin.arbol, sin.ast), sin.diagnosticos, marca.elapsedNow().inWholeMilliseconds))
            ast = sin.ast
            if (hayErrores(sin.diagnosticos)) {
                huboErrores = true
                motivoOmision = "la fase sintactica termino con errores"
            }
        }

        // --- Fase 3: semantica ---
        var sem: ResultadoSemantico? = null
        if (huboErrores || ast == null) {
            eventos.add(FaseOmitida(Fase.SEMANTICA, if (huboErrores) motivoOmision else "la fase sintactica termino con errores"))
        } else {
            eventos.add(FaseIniciada(Fase.SEMANTICA))
            marca = TimeSource.Monotonic.markNow()
            val resultado = AnalizadorSemantico.analizar(ast)
            eventos.add(
                FaseCompletada(
                    Fase.SEMANTICA,
                    Artefacto.Semantico(resultado.raiz, resultado.tipos, resultado.chequeos),
                    resultado.diagnosticos,
                    marca.elapsedNow().inWholeMilliseconds
                )
            )
            sem = resultado
            if (hayErrores(resultado.diagnosticos)) {
                huboErrores = true
                motivoOmision = "la fase semantica termino con errores"
            }
        }

        // --- Fase 4: generacion de TAC ---
        var tac: ProgramaTac? = null
        if (huboErrores || sem == null || ast == null) {
            eventos.add(FaseOmitida(Fase.GENERACION_TAC, if (huboErrores) motivoOmision else "la fase semantica no corrio"))
        } else {
            eventos.add(FaseIniciada(Fase.GENERACION_TAC))
            marca = TimeSource.Monotonic.markNow()
            val programa = GeneradorTac.generar(ast, sem)
            eventos.add(FaseCompletada(Fase.GENERACION_TAC, Artefacto.Tac(programa), emptyList(), marca.elapsedNow().inWholeMilliseconds))
            tac = programa
        }

        return AnalisisParcial(eventos, tac, huboErrores)
    }

    private fun hayErrores(diagnosticos: List<Diagnostico>): Boolean =
        diagnosticos.any { it.severidad == Severidad.ERROR }

    private class AnalisisParcial(
        val eventos: List<EventoPipeline>,
        val tac: ProgramaTac?,
        val huboErrores: Boolean
    )
}
