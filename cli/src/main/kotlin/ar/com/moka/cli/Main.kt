// ============================================================================
//  Main.kt — CLI de Moka: ejecuta un .moka en la terminal (sec. 4.2)
// ============================================================================
//  Uso:
//    ./gradlew :cli:run --args="ejemplos/hola.moka"
//    ./gradlew :cli:run --args="--solo-analisis ejemplos/hola.moka"
//    ./gradlew :cli:run --args="--paso-a-paso ejemplos/hola.moka"
//    java -jar moka-cli.jar programa.moka
//
//  Recolecta el mismo Flow<EventoPipeline> que la GUI (sec. 4.7) e imprime
//  SOLO la salida del programa por stdout; los diagnosticos van por stderr
//  con su fase y posicion. Exit code: 0 si Terminado(exito=true), 1 si no.
//  Este modulo es JVM: aca SI esta permitido java.* (la regla de pureza de
//  la sec. 4.2 es solo para core/commonMain).
// ============================================================================
package ar.com.moka.cli

import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Modo
import ar.com.moka.core.pipeline.Moka
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

// Consola de terminal: la salida ya viaja por los eventos Salida (la
// maquina no llama a escribir), asi que escribir queda para quien la use
// directo; la entrada lee de stdin, suspendida hasta tener una linea.
class ConsolaCli : Consola {
    override fun escribir(texto: String) = print(texto)
    override suspend fun leerLinea(): String? = readlnOrNull()
}

fun main(args: Array<String>) {
    var modo = Modo.EJECUTAR
    var ruta: String? = null
    for (arg in args) {
        when (arg) {
            "--solo-analisis" -> modo = Modo.SOLO_ANALISIS
            "--paso-a-paso" -> modo = Modo.PASO_A_PASO
            else -> ruta = arg
        }
    }
    if (ruta == null) {
        System.err.println("Uso: moka [--solo-analisis | --paso-a-paso] <archivo.moka>")
        exitProcess(2)
    }
    val archivo = File(ruta)
    if (!archivo.isFile) {
        System.err.println("No se encontro el archivo: $ruta")
        exitProcess(2)
    }
    val fuente = archivo.readText()

    val exito = runBlocking {
        var ok = true
        Moka().procesar(fuente, ConsolaCli(), modo).collect { evento ->
            when (evento) {
                is EventoPipeline.FaseCompletada ->
                    evento.diagnosticos.forEach { System.err.println(formatear(it)) }
                is EventoPipeline.FaseOmitida ->
                    System.err.println("[${evento.fase}] omitida: ${evento.motivo}")
                is EventoPipeline.DeEjecucion -> when (val ev = evento.evento) {
                    is EventoEjecucion.Salida -> print(ev.texto)
                    is EventoEjecucion.ErrorEjecucion -> System.err.println(formatear(ev.diagnostico))
                    is EventoEjecucion.Paso -> imprimirPaso(ev)
                    // Consola con estilo, en ANSI estandar: limpiar borra
                    // la pantalla y vuelve al origen; color fija el color
                    // de primer plano (null: restaurar); titulo cambia el
                    // titulo de la ventana de la terminal.
                    EventoEjecucion.LimpiarConsola -> print("\u001B[2J\u001B[H")
                    is EventoEjecucion.ColorConsola ->
                        print(ev.color?.let { "\u001B[${ansiDe(it)}m" } ?: "\u001B[0m")
                    is EventoEjecucion.TituloConsola -> print("\u001B]0;${ev.titulo}\u0007")
                    else -> {} // PidiendoEntrada y Fin no necesitan salida en la CLI
                }
                is EventoPipeline.Terminado -> ok = evento.exito
                else -> {} // FaseIniciada: la CLI no la muestra
            }
        }
        ok
    }
    exitProcess(if (exito) 0 else 1)
}

// Un diagnostico por linea: fase y posicion primero (sec. 4.8). Si hay
// pista didactica (errores/Pistas.kt) va en un segundo renglon.
private fun formatear(d: Diagnostico): String {
    val nivel = if (d.severidad == Severidad.ERROR) "Error" else "Advertencia"
    val base = "$nivel [${d.codigo}] en linea ${d.pos.linea}, columna ${d.pos.columna}: ${d.mensaje}"
    return d.pista?.let { "$base\n  Pista: $it" } ?: base
}

// Los nombres de ColoresConsola (core) a codigos ANSI de primer plano.
private fun ansiDe(nombre: String): String = when (nombre) {
    "rojo" -> "31"
    "verde" -> "32"
    "amarillo" -> "33"
    "azul" -> "34"
    "magenta" -> "35"
    "celeste" -> "36"
    "gris" -> "90"
    "blanco" -> "97"
    "naranja" -> "38;5;208"
    "rosa" -> "38;5;213"
    else -> "0"
}

// En modo paso a paso se ve cada cuadrupla con el marco actual (sec. 4.6).
private fun imprimirPaso(paso: EventoEjecucion.Paso) {
    val marco = paso.marcos.lastOrNull()
    val variables = marco?.variables?.entries?.joinToString(", ") { (k, v) -> "$k=$v" } ?: ""
    println("  paso ${paso.pc}: ${paso.cuadrupla.op.simbolo}  [${marco?.nombre ?: "?"}: $variables]")
}
