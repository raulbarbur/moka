// ============================================================================
// Ambito.kt - La tabla de simbolos como ARBOL de ambitos
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/Ambito.kt
// (documento de diseno, seccion 4.4)
//
// Fase 3: Moka tiene alcance lexico por bloques (seccion 2.5): una variable
// existe desde su declaracion hasta el `}` que cierra su bloque, y los
// bloques anidados pueden sombrear nombres de afuera.
//
// ¿POR QUE UN ARBOL Y NO UNA PILA?
// Durante el analisis, el ambito "actual" es una hoja y resolver un nombre
// es subir por los padres: bloque -> funcion -> clase -> global. Eso solo
// necesitaria una pila que se descarta. Pero la GUI quiere MOSTRAR la tabla
// de simbolos completa (panel semantico, seccion 5.3), asi que cada ambito
// queda colgado de su padre: el arbol entero es el artefacto.
//
// ESPACIOS DE NOMBRES SEPARADOS (seccion 4.4): una variable `suma`, una
// funcion `suma` y una clase `suma` pueden coexistir (como en Java), asi
// que el ambito guarda tres mapas. Cuando el analizador encuentra un
// nombre, el contexto sintactico ya dice en cual buscar: `suma(...)` busca
// en funciones, `nuevo suma()` en clases, `suma` pelado en variables.
//
// RESOLVER es subir: si el nombre no esta en este ambito, se pregunta al
// padre. El primer resultado gana: por eso la variable mas interna sombrea
// a la externa.
// ============================================================================

package ar.com.moka.core.semantico

import ar.com.moka.core.ast.Posicion

enum class TipoAmbito { GLOBAL, CLASE, FUNCION, BLOQUE }

class Ambito(
    val tipo: TipoAmbito,
    val nombre: String,          // "global", "funcion sumar", "clase Alumno", "bloque"
    val padre: Ambito?,
    val pos: Posicion?           // null en el global (no corresponde a un texto)
) {
    // Variables (y campos, en el ambito CLASE). Clave: nombre fuente.
    val simbolos: MutableMap<String, SimboloVariable> = mutableMapOf()

    // Funciones y clases: en la practica solo el ambito GLOBAL y el CLASE
    // los usan (no hay funciones anidadas ni clases locales), pero la
    // estructura lo permite y la resolucion sube igual.
    val funciones: MutableMap<String, SimboloFuncion> = mutableMapOf()
    val clases: MutableMap<String, SimboloClase> = mutableMapOf()

    // El arbol completo, para la GUI. Los hijos se agregan en orden de
    // aparicion en el fuente.
    val hijos: MutableList<Ambito> = mutableListOf()

    // Crea un ambito hijo y lo cuelga de este. Es la unica forma de abrir
    // ambitos: asi el arbol queda siempre bien formado.
    fun abrir(tipo: TipoAmbito, nombre: String, pos: Posicion?): Ambito {
        val hijo = Ambito(tipo, nombre, padre = this, pos = pos)
        hijos.add(hijo)
        return hijo
    }

    // Declara una variable ACA (sin mirar padres). Devuelve false si el
    // nombre ya estaba: redeclarar en el MISMO ambito es error, y el
    // analizador es quien sabe como reportarlo (con la linea anterior).
    fun declarar(variable: SimboloVariable): Boolean {
        if (simbolos.containsKey(variable.nombre)) return false
        simbolos[variable.nombre] = variable
        return true
    }

    fun declarar(funcion: SimboloFuncion): Boolean {
        if (funciones.containsKey(funcion.nombre)) return false
        funciones[funcion.nombre] = funcion
        return true
    }

    fun declarar(clase: SimboloClase): Boolean {
        if (clases.containsKey(clase.nombre)) return false
        clases[clase.nombre] = clase
        return true
    }

    // Resolucion lexica: primero aca, despues el padre, y asi hasta el
    // global. El primer ambito que tiene el nombre gana (sombreado).
    fun resolver(nombre: String): SimboloVariable? =
        simbolos[nombre] ?: padre?.resolver(nombre)

    fun resolverFuncion(nombre: String): SimboloFuncion? =
        funciones[nombre] ?: padre?.resolverFuncion(nombre)

    fun resolverClase(nombre: String): SimboloClase? =
        clases[nombre] ?: padre?.resolverClase(nombre)

    // Todos los nombres de variables visibles desde aca (esta cadena de
    // ambitos). Lo usa el "¿Quisiste decir...?" para sugerir solo nombres
    // que el programador REALMENTE podia escribir en ese punto.
    fun nombresVisibles(): Set<String> {
        val resultado = mutableSetOf<String>()
        var actual: Ambito? = this
        while (actual != null) {
            resultado.addAll(actual.simbolos.keys)
            actual = actual.padre
        }
        return resultado
    }
}
