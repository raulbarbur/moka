// =======================================================================
//  VistaArbol.kt — De los arboles del nucleo a una vista generica
// =======================================================================
//  El panel sintactico (sec. 5.3, panel 3) muestra DOS arboles: el de
//  parseo (NodoArbol) y el AST (ProgramaAst). Para no escribir la misma
//  UI dos veces, ambos se convierten a ElementoVista: texto + posicion +
//  hijos. El outline (OutlineArbol.kt) solo conoce ese tipo.
//
//  La vista del AST es la del doc: "DeclVariable, Binaria(+), SentSi":
//  cada nodo muestra su tipo y su dato principal; los hijos son sus
//  partes. SentExpr se saltea (solo envuelve una expresion) para que el
//  arbol quede compacto.
// =======================================================================
package ar.com.moka.app.ui

import ar.com.moka.core.ast.*
import ar.com.moka.core.sintactico.NodoArbol

data class ElementoVista(
    val texto: String,
    val pos: Posicion?,
    val hijos: List<ElementoVista> = emptyList(),
    val esError: Boolean = false
)

// ------------------------- arbol de parseo -------------------------

fun vistaDeParseo(nodo: NodoArbol): ElementoVista = ElementoVista(
    texto = if (nodo.esHoja) "${nodo.nombre}  \"${nodo.lexema}\"" else nodo.nombre,
    pos = nodo.pos,
    hijos = nodo.hijos.map(::vistaDeParseo),
    esError = nodo.esError
)

// ------------------------------- AST -------------------------------

private fun vistaDe(tipo: TipoAst): ElementoVista =
    ElementoVista("Tipo(${tipo.nombre}${if (tipo.esArreglo) "[]" else ""})", tipo.pos)

private fun lista(etiqueta: String, hijos: List<ElementoVista>, pos: Posicion?): ElementoVista =
    ElementoVista(etiqueta, pos, hijos)

fun vistaDeAst(nodo: Nodo): ElementoVista = when (nodo) {
    is ProgramaAst -> lista("Programa", nodo.declaraciones.map(::vistaDeAst), nodo.pos)

    is TipoAst -> vistaDe(nodo)

    is Param -> ElementoVista("Param(${nodo.nombre})", nodo.pos, listOf(vistaDe(nodo.tipo)))

    is DeclFuncion -> ElementoVista(
        "Funcion(${nodo.nombre})" + if (nodo.esEstatico) " [estatico]" else "",
        nodo.pos,
        listOf(vistaDe(nodo.retorno)) +
                nodo.params.map(::vistaDeAst) +
                vistaDeAst(nodo.cuerpo)
    )

    is DeclConstructor -> ElementoVista(
        "Constructor",
        nodo.pos,
        nodo.params.map(::vistaDeAst) + vistaDeAst(nodo.cuerpo)
    )

    is DeclVariable -> {
        val marcas = buildList {
            if (nodo.esConstante) add("constante")
            if (nodo.esEstatico) add("estatico")
        }
        ElementoVista(
            "Variable(${nodo.nombre})" + if (marcas.isNotEmpty()) " [${marcas.joinToString(", ")}]" else "",
            nodo.pos,
            listOf(vistaDe(nodo.tipo)) + listOfNotNull(nodo.inicial?.let(::vistaDeAst))
        )
    }

    is DeclClase -> ElementoVista(
        "Clase(${nodo.nombre})",
        nodo.pos,
        nodo.campos.map(::vistaDeAst) +
                listOfNotNull(nodo.constructor?.let(::vistaDeAst)) +
                nodo.metodos.map(::vistaDeAst)
    )

    is Bloque -> lista("Bloque", nodo.sentencias.map(::vistaDeAst), nodo.pos)

    is SentSi -> ElementoVista(
        "Si", nodo.pos,
        listOf(vistaDeAst(nodo.cond), vistaDeAst(nodo.entonces)) +
                listOfNotNull(nodo.sino?.let(::vistaDeAst))
    )

    is SentMientras -> ElementoVista("Mientras", nodo.pos, listOf(vistaDeAst(nodo.cond), vistaDeAst(nodo.cuerpo)))

    is SentHacer -> ElementoVista("Hacer-mientras", nodo.pos, listOf(vistaDeAst(nodo.cuerpo), vistaDeAst(nodo.cond)))

    is SentPara -> ElementoVista(
        "Para", nodo.pos,
        listOfNotNull(nodo.inicio?.let(::vistaDeAst), nodo.cond?.let(::vistaDeAst), nodo.paso?.let(::vistaDeAst)) +
                vistaDeAst(nodo.cuerpo)
    )

    is SentParaCada -> ElementoVista(
        "ParaCada(${nodo.variable})", nodo.pos,
        listOf(vistaDe(nodo.tipo), vistaDeAst(nodo.coleccion), vistaDeAst(nodo.cuerpo))
    )

    is SentSegun -> ElementoVista("Segun", nodo.pos, listOf(vistaDeAst(nodo.operando)) + nodo.ramas.map(::vistaDeAst))

    is RamaCaso -> lista("Caso", listOf(vistaDeAst(nodo.etiqueta)) + nodo.sentencias.map(::vistaDeAst), nodo.pos)

    is RamaDefecto -> lista("Defecto", nodo.sentencias.map(::vistaDeAst), nodo.pos)

    is SentRetornar -> ElementoVista("Retornar", nodo.pos, listOfNotNull(nodo.valor?.let(::vistaDeAst)))

    is SentRomper -> ElementoVista("Romper", nodo.pos)

    is SentContinuar -> ElementoVista("Continuar", nodo.pos)

    // Solo envuelve una expresion: se saltea para que el arbol quede compacto.
    is SentExpr -> vistaDeAst(nodo.expr)

    is SentVacia -> ElementoVista("Sentencia vacia (;)", nodo.pos)

    is LitEntero -> ElementoVista("Entero(${nodo.valor})", nodo.pos)
    is LitFlotante -> ElementoVista("Flotante(${nodo.valor})", nodo.pos)
    is LitCaracter -> ElementoVista("Caracter('${nodo.valor}')", nodo.pos)
    is LitCadena -> ElementoVista("Cadena(\"${nodo.valor}\")", nodo.pos)
    is LitBool -> ElementoVista("Booleano(${if (nodo.valor) "verdadero" else "falso"})", nodo.pos)
    is LitNulo -> ElementoVista("Nulo", nodo.pos)

    is Nombre -> ElementoVista("Nombre(${nodo.ident})", nodo.pos)
    is Este -> ElementoVista("Este", nodo.pos)

    is Unaria -> ElementoVista(
        "Unaria(${nodo.op.simbolo}) ${if (nodo.esPrefijo) "prefijo" else "postfijo"}",
        nodo.pos,
        listOf(vistaDeAst(nodo.operando))
    )

    is Binaria -> ElementoVista(
        "Binaria(${nodo.op.simbolo})", nodo.pos,
        listOf(vistaDeAst(nodo.izq), vistaDeAst(nodo.der))
    )

    is Asignacion -> ElementoVista(
        "Asignacion(${nodo.op.simbolo})", nodo.pos,
        listOf(vistaDeAst(nodo.destino), vistaDeAst(nodo.valor))
    )

    is Llamada -> ElementoVista("Llamada", nodo.pos, listOf(vistaDeAst(nodo.callee)) + nodo.args.map(::vistaDeAst))

    is AccesoMiembro -> ElementoVista("Miembro(.${nodo.miembro})", nodo.pos, listOf(vistaDeAst(nodo.obj)))

    is AccesoIndice -> ElementoVista(
        "Indice[]", nodo.pos,
        listOf(vistaDeAst(nodo.arr), vistaDeAst(nodo.indice))
    )

    is NuevoObjeto -> ElementoVista("Nuevo(${nodo.clase})", nodo.pos, nodo.args.map(::vistaDeAst))

    is NuevoArreglo -> ElementoVista(
        "NuevoArreglo", nodo.pos,
        listOf(vistaDe(nodo.tipoElemento), vistaDeAst(nodo.tamano))
    )

    is LitArreglo -> ElementoVista("Arreglo[${nodo.elementos.size}]", nodo.pos, nodo.elementos.map(::vistaDeAst))
}

// Cantidad total de nodos: para decidir la expansion inicial del outline
// (un arbol chico se muestra abierto; uno grande, solo la raiz).
fun contarNodos(nodos: List<ElementoVista>): Int =
    nodos.size + nodos.sumOf { contarNodos(it.hijos) }
