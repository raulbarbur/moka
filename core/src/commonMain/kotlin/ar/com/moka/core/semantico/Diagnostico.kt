// ============================================================================
// Diagnostico.kt - Errores y advertencias, todos con la misma forma
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/Diagnostico.kt
// (documento de diseno, seccion 4.8)
//
// Todo problema que el compilador reporta - lexico, sintactico, semantico
// o de ejecucion - tiene la MISMA forma: severidad, codigo, mensaje en
// espanol y posicion. La GUI pinta la lista completa en el panel de
// problemas sin importarle que fase la produjo.
//
// ACUMULACION CON TOPE (cambio de la v2, seccion 4.1): ninguna fase frena
// en el primer error; acumula hasta TOPE_DIAGNOSTICOS (50) y ahi se
// detiene, con un ultimo diagnostico que avisa que hubo mas. Un programa
// con 300 errores no genera 300 mensajes: con los primeros alcanza para
// trabajar.
//
// NOTA DE INTEGRACION: el documento (secciones 4.7/4.8) define Diagnostico
// con un campo `fase: Fase` en el paquete errores/. Ese paquete todavia no
// existe (llega con la fachada del pipeline); cuando exista, esta clase se
// mueve ahi y se le agrega `fase` sin tocar al analizador. Por ahora la
// fase queda implicita: estos diagnosticos son todos SEM-.
// ============================================================================

package ar.com.moka.core.semantico

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.errores.Pistas

const val TOPE_DIAGNOSTICOS: Int = 50

enum class Severidad { ERROR, ADVERTENCIA }

data class Diagnostico(
    val severidad: Severidad,
    val codigo: String,      // "SEM-001" ... (lexicos LEX-, sintacticos SIN-, ejecucion EJE-)
    val mensaje: String,
    val pos: Posicion
) {
    // Sugerencia didactica opcional (errores/Pistas.kt): calculada del
    // codigo y el mensaje, no guardada, asi todos los reportes la ganan
    // sin cambiar y ningun constructor se rompe. Null si no hay pista.
    val pista: String?
        get() = Pistas.para(codigo, mensaje)
}

// Acumulador con tope: compartido por todos los puntos del analizador que
// reportan. error() y advertencia() devuelven false cuando ya no entran
// mas diagnosticos, por si el llamador quiere cortar antes.
class AcumuladorDiagnosticos {
    private val lista = mutableListOf<Diagnostico>()
    private var descartados: Int = 0
    private var avisoDeTopeAgregado = false

    val diagnosticos: List<Diagnostico> get() = lista

    // ¿Llegamos al tope? El analizador lo consulta para abandonar la
    // pasada en curso: seguir recorriendo no agregaria informacion.
    val completo: Boolean get() = lista.size >= TOPE_DIAGNOSTICOS

    fun error(codigo: String, mensaje: String, pos: Posicion) =
        agregar(Severidad.ERROR, codigo, mensaje, pos)

    fun advertencia(codigo: String, mensaje: String, pos: Posicion) =
        agregar(Severidad.ADVERTENCIA, codigo, mensaje, pos)

    private fun agregar(severidad: Severidad, codigo: String, mensaje: String, pos: Posicion): Boolean {
        if (lista.size >= TOPE_DIAGNOSTICOS) {
            descartados++
            // El diagnostico numero 50 se CANJEA por el aviso de corte:
            // es mas util saber "hay mas" que un mensaje suelto numero 50.
            if (!avisoDeTopeAgregado) {
                avisoDeTopeAgregado = true
                lista[lista.size - 1] = Diagnostico(
                    Severidad.ERROR, "SEM-000",
                    "Se alcanzo el tope de $TOPE_DIAGNOSTICOS diagnosticos; se omiten los siguientes.",
                    lista[lista.size - 1].pos
                )
            }
            return false
        }
        lista.add(Diagnostico(severidad, codigo, mensaje, pos))
        return true
    }
}
