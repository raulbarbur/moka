// ============================================================================
// Operacion.kt - Catalogo de operaciones del TAC (codigo de 3 direcciones)
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/tac/Operacion.kt
// (documento de diseno, seccion 4.5.2)
//
// Cada cuadrupla tiene UNA operacion. El enum es el conjunto cerrado: no
// existe una instruccion del TAC fuera de esta lista. `simbolo` es como se
// muestra en el listado (el panel TAC de la GUI y los golden files).
//
// Tres grupos:
//   - las que calculan un valor (=, aritmeticas, neg, no, relacionales,
//     itof, concat, lecturas de memoria),
//   - las que mueven el control (etiqueta, ir_a, si_falso, si_verdadero,
//     llamar, retornar, inicio_func, fin_func),
//   - las que escriben memoria (asignar_campo, asignar_indice): en esas dos
//     el campo `resultado` lleva el VALOR que se almacena, porque una
//     escritura no produce un valor nuevo (convencion clasica, sec. 4.5.2).
//
// y / o NO son operaciones: el cortocircuito se traduce a saltos
// (si_falso / si_verdadero), asi el operando derecho ni se evalua cuando
// el izquierdo ya decidio (seccion 2.14).
// ============================================================================

package ar.com.moka.core.tac

enum class Operacion(val simbolo: String) {
    // copia: (=, origen, _, destino)
    ASIGNAR("="),

    // aritmetica: ambos operandos del mismo tipo; la conversion entero ->
    // flotante ya se materializo con ITOF donde hacia falta
    MAS("+"), MENOS("-"), POR("*"), DIV("/"), MODULO("%"),

    // unarias
    NEG("neg"),           // menos unario
    NO("no"),             // negacion logica

    // comparaciones: el resultado es booleano
    MENOR("<"), MENOR_IGUAL("<="), MAYOR(">"), MAYOR_IGUAL(">="),
    IGUAL("=="), DISTINTO("!="),

    // conversiones y cadenas
    ITOF("itof"),         // entero -> flotante (la promocion que anoto el semantico)
    CONCAT("concat"),     // cadena + cadena; el generador antepone a_cadena si hace falta

    // control de flujo
    ETIQUETA("etiqueta"), // marca un destino de salto: (etiqueta, _, _, L)
    IR_A("ir_a"),         // salto incondicional
    SI_FALSO("si_falso"), // salta si arg1 es falso
    SI_VERDADERO("si_verdadero"),

    // llamadas y funciones
    PARAM("param"),               // apila un argumento para la proxima llamada
    LLAMAR("llamar"),             // (llamar, funcion, n, t o _): consume los n ultimos param
    RETORNAR("retornar"),         // termina la funcion actual
    INICIO_FUNC("inicio_func"),   // (inicio_func, nombre, nro de parametros, _)
    FIN_FUNC("fin_func"),

    // objetos y arreglos
    NUEVO_OBJETO("nuevo_objeto"),   // (nuevo_objeto, clase, _, t)
    NUEVO_ARREGLO("nuevo_arreglo"), // (nuevo_arreglo, tipoElemento, tamano, t)
    LEER_CAMPO("leer_campo"),       // (leer_campo, objeto, campo, t)
    ASIGNAR_CAMPO("asignar_campo"), // (asignar_campo, objeto, campo, valor)
    LEER_INDICE("leer_indice"),     // (leer_indice, arreglo, indice, t)
    ASIGNAR_INDICE("asignar_indice")
}
