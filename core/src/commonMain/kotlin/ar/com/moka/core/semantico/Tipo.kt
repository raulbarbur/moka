// ============================================================================
// Tipo.kt - Tipos INTERNOS del analisis semantico
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/Tipo.kt
// (documento de diseno, seccion 4.4)
//
// Fase 3: el AST guarda los tipos TAL COMO SE ESCRIBIERON (TipoAst: un
// nombre y un flag de arreglo). Aca esta el sistema de tipos con el que
// piensa el compilador.
//
// ¿POR QUE DOS REPRESENTACIONES?
// TipoAst es sintaxis: "el programador escribio `Alumno[]`". Tipo es
// semantica: "existe la clase Alumno, esto es una referencia a un arreglo
// de Alumnos". La conversion de uno a otro ES un chequeo (¿la clase
// existe?), asi que no pueden ser la misma cosa.
//
// TError es el tipo comodin: cuando una expresion ya tiene un error, su
// tipo queda TError y todas las reglas lo dejan pasar en silencio. Sin
// TError, un solo error ("x no esta declarada") generaria una cascada de
// errores falsos ("no se puede sumar ...", "no se puede asignar ...")
// que ahogan al mensaje que importa.
//
// Igualdad estructural gratis: al ser objects y data classes, `==` compara
// por contenido (TArreglo(TEntero) == TArreglo(TEntero)). No hay que
// escribir ningun comparador.
// ============================================================================

package ar.com.moka.core.semantico

sealed interface Tipo {

    // Como se muestra el tipo en mensajes y en la GUI: "entero", "cadena",
    // "entero[]", "Alumno". Es el nombre del lenguaje, no el de Kotlin.
    val descripcion: String

    // Tipos numericos: los unicos que participan en aritmetica.
    val esNumerico: Boolean get() = this is TEntero || this is TFlotante

    // Tipos referencia (seccion 2.3): cadena, arreglos y clases pueden ser
    // nulo; los primitivos no.
    val esReferencia: Boolean
        get() = this is TCadena || this is TArreglo || this is TClase || this is TNulo
}

object TEntero : Tipo {
    override val descripcion: String get() = "entero"
}

object TFlotante : Tipo {
    override val descripcion: String get() = "flotante"
}

object TBooleano : Tipo {
    override val descripcion: String get() = "booleano"
}

object TCaracter : Tipo {
    override val descripcion: String get() = "caracter"
}

object TCadena : Tipo {
    override val descripcion: String get() = "cadena"
}

object TVacio : Tipo {
    override val descripcion: String get() = "vacio"
}

object TNulo : Tipo {
    override val descripcion: String get() = "nulo"
}

data class TArreglo(val elemento: Tipo) : Tipo {
    override val descripcion: String get() = elemento.descripcion + "[]"
}

data class TClase(val nombre: String) : Tipo {
    override val descripcion: String get() = nombre
}

// El tipo de una expresion que YA fallo. Todas las reglas lo aceptan:
// su unico trabajo es no generar mas ruido.
object TError : Tipo {
    override val descripcion: String get() = "<error>"
}

// ¿Puede `valor` guardarse en un lugar de tipo `destino`? Es LA regla de
// asignabilidad del lenguaje (seccion 4.4, tabla de tipos):
//   - mismo tipo: siempre;
//   - entero -> flotante: conversion implicita (el TAC inserta `itof`);
//   - nulo -> cualquier referencia;
//   - TError de cualquier lado: se deja pasar (ya se reporto el error);
//   - flotante -> entero: NUNCA implicita (necesita a_entero).
fun esAsignable(destino: Tipo, valor: Tipo): Boolean = when {
    destino is TError || valor is TError -> true
    destino == valor -> true
    destino is TFlotante && valor is TEntero -> true
    valor is TNulo && destino.esReferencia -> true
    else -> false
}

// El tipo de `a OP b` cuando ambos son numericos: flotante si alguno lo es
// (promocion entero -> flotante, seccion 2.3).
fun tipoNumericoComun(a: Tipo, b: Tipo): Tipo =
    if (a is TFlotante || b is TFlotante) TFlotante else TEntero
