// =======================================================================
//  Autocompletado.kt - Sugerencias de palabras mientras se escribe
// =======================================================================
//  Logica PURA (sin Compose) para que se pueda probar sin pantalla.
//  Sugiere, para la palabra que se esta escribiendo al lado del cursor:
//    1. palabras clave y tipos del lenguaje (salen de REGLAS_TOKEN, la
//       misma lista que usa el resaltado: no hay una segunda copia);
//    2. funciones incorporadas (imprimir, leer, longitud...);
//    3. nombres que ya aparecen en el programa (variables, funciones,
//       clases), SIN mirar la semantica: asi funciona aunque el programa
//       todavia tenga errores.
//  No sugiere dentro de cadenas, caracteres ni comentarios.
// =======================================================================
package ar.com.moka.app.ui

// Un pedido de completado: el prefijo escrito y donde empieza.
data class PrefijoActual(val inicio: Int, val texto: String)

object Autocompletado {
    const val MINIMO_LETRAS = 2
    const val MAXIMO_SUGERENCIAS = 6

    // Funciones incorporadas del lenguaje (AnalizadorSemantico.registrarBuiltins).
    val FUNCIONES_INCORPORADAS: List<String> = listOf(
        "imprimir", "imprimir_sin_salto", "leer", "leer_entero", "leer_flotante",
        "longitud", "a_entero", "a_flotante", "a_cadena", "a_booleano",
        "limpiar", "color", "restaurar_color", "pausa", "titulo"
    )

    // Palabras reservadas: el lexema entre comillas simples de REGLAS_TOKEN
    // ('si' -> si). Solo las que son palabras (no operadores como '+').
    val PALABRAS_CLAVE: List<String> by lazy {
        REGLAS_TOKEN.values
            .map { it.trim('\'') }
            .filter { palabra -> palabra.isNotEmpty() && palabra.all { c -> c.isLetter() || c == '_' } }
            .distinct()
    }

    private fun esLetraDeNombre(c: Char) = c.isLetterOrDigit() || c == '_'

    // La palabra pegada a la izquierda del cursor. Null si el cursor no
    // esta al final de una palabra, si la palabra es un numero, si el
    // texto sigue pegado a la derecha, o si cae dentro de una cadena o
    // de un comentario.
    fun prefijoEn(fuente: String, cursor: Int): PrefijoActual? {
        if (cursor < 0 || cursor > fuente.length) return null
        if (cursor < fuente.length && esLetraDeNombre(fuente[cursor])) return null
        var inicio = cursor
        while (inicio > 0 && esLetraDeNombre(fuente[inicio - 1])) inicio--
        if (inicio == cursor) return null
        val texto = fuente.substring(inicio, cursor)
        if (!(texto[0].isLetter() || texto[0] == '_')) return null
        if (enLiteralOComentario(fuente, inicio)) return null
        return PrefijoActual(inicio, texto)
    }

    // Lista ordenada de sugerencias (maximo 6). Vacia si no corresponde.
    fun sugerir(fuente: String, cursor: Int): List<String> {
        val prefijo = prefijoEn(fuente, cursor) ?: return emptyList()
        if (prefijo.texto.length < MINIMO_LETRAS) return emptyList()
        val resultado = LinkedHashSet<String>()
        fun agregar(candidatos: Iterable<String>) {
            for (c in candidatos) {
                // Sin repetir y sin ofrecer lo que ya esta escrito completo.
                if (c.startsWith(prefijo.texto) && c != prefijo.texto) resultado.add(c)
            }
        }
        agregar(PALABRAS_CLAVE)
        agregar(FUNCIONES_INCORPORADAS)
        agregar(nombresDelPrograma(fuente, prefijo).sorted())
        return resultado.take(MAXIMO_SUGERENCIAS)
    }

    // Reemplaza el prefijo por la palabra elegida. Devuelve el texto nuevo
    // y donde queda el cursor (justo despues de la palabra).
    fun aceptar(fuente: String, prefijo: PrefijoActual, palabra: String): Pair<String, Int> {
        val fin = prefijo.inicio + prefijo.texto.length
        val nuevo = fuente.substring(0, prefijo.inicio) + palabra + fuente.substring(fin)
        return nuevo to (prefijo.inicio + palabra.length)
    }

    // Nombres del programa: todo lo que parece un identificador fuera de
    // cadenas y comentarios, menos la palabra que se esta escribiendo.
    private fun nombresDelPrograma(fuente: String, actual: PrefijoActual): Set<String> {
        val nombres = LinkedHashSet<String>()
        var i = 0
        while (i < fuente.length) {
            val c = fuente[i]
            when {
                c == '"' || c == '\'' -> i = saltarLiteral(fuente, i)
                c == '/' && i + 1 < fuente.length && fuente[i + 1] == '/' -> i = saltarLineaComentario(fuente, i)
                c == '/' && i + 1 < fuente.length && fuente[i + 1] == '*' -> i = saltarBloque(fuente, i)
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < fuente.length && esLetraDeNombre(fuente[j])) j++
                    // La palabra que el usuario esta escribiendo no se sugiere a si misma.
                    if (i != actual.inicio) nombres.add(fuente.substring(i, j))
                    i = j
                }
                else -> i++
            }
        }
        return nombres
    }

    // true si el desplazamiento 'hasta' cae dentro de una cadena, un
    // caracter o un comentario (se recorre desde el principio).
    private fun enLiteralOComentario(fuente: String, hasta: Int): Boolean {
        var i = 0
        while (i < fuente.length && i < hasta) {
            val c = fuente[i]
            val siguiente = when {
                c == '"' || c == '\'' -> saltarLiteral(fuente, i)
                c == '/' && i + 1 < fuente.length && fuente[i + 1] == '/' -> saltarLineaComentario(fuente, i)
                c == '/' && i + 1 < fuente.length && fuente[i + 1] == '*' -> saltarBloque(fuente, i)
                else -> i + 1
            }
            // Si el literal o comentario empezo antes del cursor y termina
            // despues (o no termina), el cursor esta adentro.
            if (siguiente > hasta && i < hasta && siguiente != i + 1) return true
            i = siguiente
        }
        return false
    }

    // Cadena/caracter: termina con la misma comilla o al fin de la linea
    // (el lexer tampoco admite saltos de linea dentro). Respeta '\'.
    private fun saltarLiteral(fuente: String, desde: Int): Int {
        val comilla = fuente[desde]
        var i = desde + 1
        while (i < fuente.length && fuente[i] != '\n') {
            if (fuente[i] == '\\') { i += 2; continue }
            if (fuente[i] == comilla) return i + 1
            i++
        }
        return minOf(i, fuente.length).coerceAtLeast(desde + 1)
    }

    private fun saltarLineaComentario(fuente: String, desde: Int): Int {
        var i = desde
        while (i < fuente.length && fuente[i] != '\n') i++
        return i
    }

    private fun saltarBloque(fuente: String, desde: Int): Int {
        val fin = fuente.indexOf("*/", desde + 2)
        return if (fin < 0) fuente.length else fin + 2
    }
}
