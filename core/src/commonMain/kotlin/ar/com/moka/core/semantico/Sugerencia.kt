// ============================================================================
// Sugerencia.kt - "¿Quisiste decir 'contador'?"
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/Sugerencia.kt
// (documento de diseno, secciones 4.4 y 4.8)
//
// Cuando un nombre no esta declarado, el error mas util no es "no existe"
// sino "no existe Y ESTE ES PARECIDO". Eso es la distancia de edicion
// (Levenshtein): cuantas inserciones, borrados o cambios de UNA letra
// separan dos cadenas. "contdor" esta a 1 de "contador": casi seguro es un
// typo. "contdor" esta a 7 de "promedio": no sugerimos nada.
//
// El algoritmo es programacion dinamica clasica: d[i][j] = distancia entre
// los primeros i caracteres de `a` y los primeros j de `b`. Solo nos
// interesan distancias chicas (<= MAXIMA_DISTANCIA), asi que se corta
// antes: si una fila entera supera el maximo, la respuesta es "lejos".
//
// Solo la stdlib de Kotlin: Array<IntArray> y minOf. Nada de librerias.
// ============================================================================

package ar.com.moka.core.semantico

// Distancia maxima para sugerir (seccion 4.4: Levenshtein <= 2). Con 1 o 2
// cubre el typo tipico; con 3 empezaria a sugerir nombres que no son.
private const val MAXIMA_DISTANCIA: Int = 2

fun distanciaEdicion(a: String, b: String): Int {
    // Truco de memoria: la fila i solo depende de la fila i-1, asi que
    // guardamos dos filas en vez de la matriz completa.
    var anterior = IntArray(b.length + 1) { it }          // fila 0: 0,1,2,...
    for (i in 1..a.length) {
        val actual = IntArray(b.length + 1)
        actual[0] = i
        var minimoFila = actual[0]
        for (j in 1..b.length) {
            val costo = if (a[i - 1] == b[j - 1]) 0 else 1
            actual[j] = minOf(
                anterior[j] + 1,           // borrar de a
                actual[j - 1] + 1,         // insertar en a
                anterior[j - 1] + costo    // igualar o cambiar
            )
            if (actual[j] < minimoFila) minimoFila = actual[j]
        }
        if (minimoFila > MAXIMA_DISTANCIA) return MAXIMA_DISTANCIA + 1
        anterior = actual
    }
    return anterior[b.length]
}

// El candidato visible mas parecido a `escrito`, o null si ninguno esta lo
// bastante cerca. Devuelve el de menor distancia; en empate, el primero
// por orden alfabetico (deterministico: el mismo programa siempre sugiere
// lo mismo).
fun sugerirNombre(escrito: String, candidatos: Collection<String>): String? =
    candidatos
        .map { it to distanciaEdicion(escrito, it) }
        .filter { (_, d) -> d in 1..MAXIMA_DISTANCIA }
        .sortedWith(compareBy({ it.second }, { it.first }))
        .firstOrNull()
        ?.first
