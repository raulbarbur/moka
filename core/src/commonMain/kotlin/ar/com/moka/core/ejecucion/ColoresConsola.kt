// =======================================================================
//  ColoresConsola.kt - Colores del builtin color() de la consola
// =======================================================================
//  Lista canonica de los colores que acepta color("..."), compartida por
//  las tres capas que hablan de colores:
//    - el semantico (valida color("rojoo") con literal en compilacion);
//    - la maquina (valida el nombre cuando se computa en ejecucion);
//    - la GUI y la CLI, que traducen el nombre a Color de Compose o a
//      codigo ANSI (cada una con su tabla propia, aca solo viven los
//      nombres, asi core no depende de plataforma).
//  Agregar un color es tocar esta lista y las dos tablas de salida.
// =======================================================================
package ar.com.moka.core.ejecucion

object ColoresConsola {

    // Nombres validos, en el orden en que se listan en los mensajes.
    val nombres: List<String> = listOf(
        "rojo", "verde", "azul", "amarillo", "celeste",
        "magenta", "naranja", "rosa", "gris", "blanco"
    )

    // Lista legible para los mensajes de error: "rojo, verde, ...".
    val lista: String = nombres.joinToString(", ")

    // Normaliza y valida: acepta mayusculas y espacios a los costados
    // (color(" Rojo ") vale), y devuelve el nombre canonico en
    // minusculas. Si el color no existe devuelve null y quien llama
    // reporta SEM-061 (compilacion) o EJE-009 (ejecucion).
    fun normalizar(nombre: String): String? {
        val canonico = nombre.trim().lowercase()
        return if (canonico in nombres) canonico else null
    }
}
