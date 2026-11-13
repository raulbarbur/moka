package ar.com.moka.cli

import ar.com.moka.core.Moka
import java.io.File

/**
 * CLI minima de la Fase 0: parsea un archivo .moka y muestra el resumen.
 *
 * Uso:  ./gradlew :cli:run --args="ejemplos/hola.moka"
 *
 * En la Parte A del roadmap crece hasta ser `java -jar moka-cli.jar`.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Uso: moka <archivo.moka>")
        return
    }
    val fuente = File(args[0]).readText()
    println(Moka.saludo())
    println(Moka.analizar(fuente))
}
