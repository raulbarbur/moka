// ============================================================================
// Chequeo.kt - El registro de COMO se verifico cada regla de tipos
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/Chequeo.kt
// (documento de diseno, seccion 4.4)
//
// El panel "Chequeos de tipo" de la GUI no muestra errores: muestra el
// TRABAJO del analizador. Cada vez que se aplica una regla de tipos se
// anota un renglon con la notacion del curso:
//
//     texto         regla                                    ok
//     "i < 10"      "relacional: entero < entero -> booleano"  true
//     "a + 1.5"     "aditiva: entero + flotante -> flotante"   true
//     "x = \"hola\""  "asignacion a 'entero': se obtuvo cadena"  false
//
// ¿PARA QUE, SI LOS ERRORES YA ESTAN EN LOS DIAGNOSTICOS?
// Porque el proyecto es didactico: ver los chequeos QUE PASARON es tan
// valioso como ver los que fallaron. Es la traza del razonamiento del
// compilador, escrita con la misma notacion de la clase de tipos.
//
// tipoEsperado / tipoObtenido son anulables: algunas reglas ("la variable
// existe") no comparan tipos, solo verifican un nombre.
// ============================================================================

package ar.com.moka.core.semantico

import ar.com.moka.core.ast.Posicion

data class Chequeo(
    val pos: Posicion,
    val texto: String,            // la construccion chequeada, renderizada ("i < 10")
    val regla: String,            // la regla aplicada ("relacional: entero < entero -> booleano")
    val tipoEsperado: Tipo?,
    val tipoObtenido: Tipo?,
    val ok: Boolean
)
