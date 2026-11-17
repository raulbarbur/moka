// =======================================================================
//  Artefacto.kt — Lo que cada fase produce (sec. 4.7)
// =======================================================================
//  Identico al del documento. La fase de ejecucion no tiene variante: su
//  "artefacto" es el flujo de eventos mismo (sec. 4.1), que viaja en los
//  EventoPipeline.DeEjecucion.
// =======================================================================
package ar.com.moka.core.pipeline

import ar.com.moka.core.ast.ProgramaAst
import ar.com.moka.core.lexico.Token
import ar.com.moka.core.semantico.Ambito
import ar.com.moka.core.semantico.Chequeo
import ar.com.moka.core.semantico.Tipo
import ar.com.moka.core.sintactico.NodoArbol
import ar.com.moka.core.tac.ProgramaTac

sealed interface Artefacto {
    data class Lexico(val tokens: List<Token>) : Artefacto
    // ast = null si hubo errores de sintaxis (sec. 4.7).
    data class Sintactico(val arbolParseo: NodoArbol, val ast: ProgramaAst?) : Artefacto
    data class Semantico(val raiz: Ambito, val tipos: Map<Int, Tipo>, val chequeos: List<Chequeo>) : Artefacto
    data class Tac(val programa: ProgramaTac) : Artefacto
}
