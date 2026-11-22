// ============================================================================
// Ast.kt - Arbol de sintaxis abstracta (AST) de Moka
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/ast/Ast.kt
// (documento de diseno, seccion 4.3)
//
// Fase 2: el parse tree de ANTLR se convierte en un AST propio del proyecto.
//
// ¿Por que un AST propio y no recorrer el parse tree directamente?
// El parse tree tiene TODOS los tokens (parentesis, comas, puntos y coma) y
// mezcla detalles de gramatica con estructura del programa. El AST guarda
// solo lo que significa algo: declaraciones, sentencias y expresiones, ya
// anidadas segun la precedencia. El analisis semantico (Fase 3) y el
// generador de TAC (Fase 4) recorren este arbol varias veces.
//
// Tres decisiones de diseno que atraviesan todo el archivo:
//
// 1) SEALED (jerarquia cerrada). El lenguaje es fijo: no va a aparecer un
//    tipo nuevo de expresion sin tocar la gramatica. `sealed` le avisa al
//    compilador "estas son TODAS las variantes", y entonces un `when` sin
//    `else` es exhaustivo: si manana agregamos un nodo y olvidamos un caso,
//    el codigo NO COMPILA. El compilador es la red de seguridad.
//
// 2) INMUTABLE (todo `val`). Ninguna fase modifica el arbol. Lo que el
//    semantico calcula (el tipo de cada expresion, el simbolo de cada
//    nombre) va a TABLAS LATERALES: Map<Int, Tipo>, Map<Int, Simbolo>.
//    Asi el mismo arbol se muestra en la GUI antes y despues del analisis.
//
// 3) ID UNICO por nodo. Es la clave de esas tablas laterales y el hilo que
//    usa la GUI para resaltar "este nodo del arbol = este token = esta
//    cuadrupla". Los asigna ConstructorAst con un contador.
//
// Pureza multiplataforma: este archivo solo usa la stdlib de Kotlin
// (nada de java.*), asi compila igual para escritorio y Android.
// ============================================================================

package ar.com.moka.core.ast


// ----------------------------------------------------------------------------
// Posicion: donde esta un nodo en el codigo fuente.
// Sirve para mensajes de error ("linea 7: ...") y para que la GUI resalte
// el texto cuando tocas un nodo del arbol.
// linea/columna = primer token del nodo; finLinea/finColumna = ultimo
// caracter del ultimo token. Las columnas arrancan en 0 (convencion ANTLR).
// ----------------------------------------------------------------------------
data class Posicion(
    val linea: Int,
    val columna: Int,
    val finLinea: Int,
    val finColumna: Int
)


// ----------------------------------------------------------------------------
// Nodo: la raiz de todo lo que puede aparecer en el AST.
// Cada nodo tiene un id unico (clave de las tablas laterales) y su posicion.
// ----------------------------------------------------------------------------
sealed interface Nodo {
    val id: Int
    val pos: Posicion
}


// ----------------------------------------------------------------------------
// ProgramaAst: la raiz del arbol. Un programa Moka mezcla clases, funciones
// y sentencias sueltas (las sueltas actuan como el "main", seccion 2.2).
// Por eso la lista es de Nodo: puede tener DeclClase, DeclFuncion o Sent.
// ----------------------------------------------------------------------------
class ProgramaAst(
    override val id: Int,
    override val pos: Posicion,
    val declaraciones: List<Nodo>,
    // Moka 2 (P1): `paquete util.texto;` al inicio (null si el archivo no
    // declara paquete) y la lista de `usar ...;` en orden de aparicion.
    // Los nombres ya vienen unidos con puntos ("util.texto").
    val paquete: String? = null,
    val usos: List<UsoAst> = emptyList()
) : Nodo

// UsoAst: una declaracion `usar paquete;` del encabezado del programa.
class UsoAst(
    override val id: Int,
    override val pos: Posicion,
    val nombre: String
) : Nodo


// ----------------------------------------------------------------------------
// TipoAst: un tipo tal como se escribio en el codigo.
// `nombre` es "entero", "flotante", "booleano", "caracter", "cadena",
// "vacio" (solo como retorno) o el nombre de una clase ("Alumno"); desde
// Moka 2 P2 puede venir cualificado con su paquete ("geometria.Punto").
// `esArreglo` marca el sufijo [] (entero[], Alumno[]).
//
// Aca NO se distingue si el nombre es valido o si la clase existe: eso es
// trabajo del analisis semantico (Fase 3), que convierte TipoAst en su Tipo
// interno. El AST solo registra lo que el programador escribio.
// ----------------------------------------------------------------------------
class TipoAst(
    override val id: Int,
    override val pos: Posicion,
    val nombre: String,
    val esArreglo: Boolean
) : Nodo


// ----------------------------------------------------------------------------
// Operadores como enums. Un enum es la forma de decir "conjunto cerrado de
// valores": no existe un operador binario fuera de esta lista.
// `simbolo` es el lexema fuente: ConstructorAst resuelve el enum buscando
// por simbolo (el lexer no expone sus constantes de token, ver ConstructorAst).
// ----------------------------------------------------------------------------
enum class OpBinario(val simbolo: String) {
    MAS("+"), MENOS("-"), POR("*"), DIV("/"), MODULO("%"),
    MENOR("<"), MENOR_IGUAL("<="), MAYOR(">"), MAYOR_IGUAL(">="),
    IGUAL_IGUAL("=="), DISTINTO("!="),
    Y("y"), O("o");

    companion object {
        fun desde(simbolo: String): OpBinario =
            values().firstOrNull { it.simbolo == simbolo }
                ?: error("Operador binario desconocido: '$simbolo'")
    }
}

enum class OpUnario(val simbolo: String) {
    INCREMENTO("++"), DECREMENTO("--"),
    NEGATIVO("-"),   // el MENOS unario: -x  (distinto del MENOS binario)
    NO("no");

    companion object {
        fun desde(simbolo: String): OpUnario =
            values().firstOrNull { it.simbolo == simbolo }
                ?: error("Operador unario desconocido: '$simbolo'")
    }
}

enum class OpAsignacion(val simbolo: String) {
    ASIGNA("="), MAS_ASIGNA("+="), MENOS_ASIGNA("-="),
    POR_ASIGNA("*="), DIV_ASIGNA("/="), MOD_ASIGNA("%=");

    companion object {
        fun desde(simbolo: String): OpAsignacion =
            values().firstOrNull { it.simbolo == simbolo }
                ?: error("Operador de asignacion desconocido: '$simbolo'")
    }
}


// ============================ DECLARACIONES =================================

// Parametro de funcion, metodo o constructor. Tambien es Nodo: el semantico
// anota su simbolo en la tabla lateral como a cualquier declaracion.
class Param(
    override val id: Int,
    override val pos: Posicion,
    val nombre: String,
    val tipo: TipoAst
) : Nodo

// Funcion toplevel Y metodo de clase (misma forma). `esEstatico` solo puede
// ser true en metodos; en una funcion toplevel siempre es false.
class DeclFuncion(
    override val id: Int,
    override val pos: Posicion,
    val nombre: String,
    val retorno: TipoAst,
    val params: List<Param>,
    val cuerpo: Bloque,
    val esEstatico: Boolean
) : Nodo

// El constructor de una clase (a lo sumo uno; el semantico lo verifica).
class DeclConstructor(
    override val id: Int,
    override val pos: Posicion,
    val params: List<Param>,
    val cuerpo: Bloque
) : Nodo

// Variable: toplevel, local, o campo de clase (ahi `esEstatico` tiene
// sentido). `inicial` es null si no tiene "= ...".
class DeclVariable(
    override val id: Int,
    override val pos: Posicion,
    val nombre: String,
    val tipo: TipoAst,
    val inicial: Expr?,
    val esConstante: Boolean,
    val esEstatico: Boolean
) : Sent

// La clase separa sus miembros en tres listas en vez de una mezcla: al
// semantico y a la GUI les resulta mas comodo (sec. 4.3).
class DeclClase(
    override val id: Int,
    override val pos: Posicion,
    val nombre: String,
    val campos: List<DeclVariable>,
    val constructor: DeclConstructor?,
    val metodos: List<DeclFuncion>
) : Nodo


// ============================= SENTENCIAS ===================================

sealed interface Sent : Nodo

class Bloque(
    override val id: Int,
    override val pos: Posicion,
    val sentencias: List<Sent>
) : Sent

class SentSi(
    override val id: Int,
    override val pos: Posicion,
    val cond: Expr,
    val entonces: Sent,
    val sino: Sent?          // null si no hay rama "sino"
) : Sent

class SentMientras(
    override val id: Int,
    override val pos: Posicion,
    val cond: Expr,
    val cuerpo: Sent
) : Sent

// hacer ... mientras: el cuerpo se ejecuta AL MENOS una vez.
class SentHacer(
    override val id: Int,
    override val pos: Posicion,
    val cuerpo: Sent,
    val cond: Expr
) : Sent

// para (inicio; cond; paso) cuerpo
// inicio es una DeclVariable, una Expr (tipicamente una Asignacion) o null.
// cond y paso son opcionales: para (;;) es sintacticamente valido.
class SentPara(
    override val id: Int,
    override val pos: Posicion,
    val inicio: Nodo?,
    val cond: Expr?,
    val paso: Expr?,
    val cuerpo: Sent
) : Sent

// para_cada (entero n : notas) cuerpo
class SentParaCada(
    override val id: Int,
    override val pos: Posicion,
    val tipo: TipoAst,
    val variable: String,
    val coleccion: Expr,
    val cuerpo: Sent
) : Sent

// segun (operando) { caso 1: ... defecto: ... }
class SentSegun(
    override val id: Int,
    override val pos: Posicion,
    val operando: Expr,
    val ramas: List<RamaSegun>
) : Sent

// Cada rama del segun. La etiqueta de un caso es siempre un literal
// (LitEntero -con el signo ya aplicado si habia un MENOS-, LitCaracter o
// LitCadena); que no haya casos repetidos lo verifica el semantico.
sealed interface RamaSegun : Nodo

class RamaCaso(
    override val id: Int,
    override val pos: Posicion,
    val etiqueta: Expr,
    val sentencias: List<Sent>
) : RamaSegun

class RamaDefecto(
    override val id: Int,
    override val pos: Posicion,
    val sentencias: List<Sent>
) : RamaSegun

class SentRetornar(
    override val id: Int,
    override val pos: Posicion,
    val valor: Expr?           // null en "retornar;" pelado
) : Sent

class SentRomper(
    override val id: Int,
    override val pos: Posicion
) : Sent

class SentContinuar(
    override val id: Int,
    override val pos: Posicion
) : Sent

// Una expresion usada como sentencia: imprimir("hola");  x = 5;
class SentExpr(
    override val id: Int,
    override val pos: Posicion,
    val expr: Expr
) : Sent

// El ";" suelto. No hace nada, pero se conserva para que el arbol muestre
// exactamente lo que hay en el fuente (y la GUI lo pueda exhibir).
class SentVacia(
    override val id: Int,
    override val pos: Posicion
) : Sent


// ============================= EXPRESIONES ==================================

sealed interface Expr : Nodo

// --- Literales: ya vienen "cocinados" (sin comillas, escapes resueltos) ---
class LitEntero(
    override val id: Int,
    override val pos: Posicion,
    val valor: Long            // Long para no fallar antes de tiempo: el
    // chequeo de rango de 32 bits es del semantico
) : Expr

class LitFlotante(
    override val id: Int,
    override val pos: Posicion,
    val valor: Double
) : Expr

class LitCaracter(
    override val id: Int,
    override val pos: Posicion,
    val valor: Char
) : Expr

class LitCadena(
    override val id: Int,
    override val pos: Posicion,
    val valor: String
) : Expr

class LitBool(
    override val id: Int,
    override val pos: Posicion,
    val valor: Boolean
) : Expr

class LitNulo(
    override val id: Int,
    override val pos: Posicion
) : Expr

// --- Referencias ---
class Nombre(
    override val id: Int,
    override val pos: Posicion,
    val ident: String          // una variable, funcion o clase por nombre;
    // A QUIEN se refiere lo resuelve el semantico
) : Expr

class Este(
    override val id: Int,
    override val pos: Posicion
) : Expr

// --- Operaciones ---
// esPrefijo distingue ++x (unario prefijo) de x++ (postfijo): misma
// operacion, distinto momento del incremento. El MENOS unario y el NO
// siempre llegan con esPrefijo = true.
class Unaria(
    override val id: Int,
    override val pos: Posicion,
    val op: OpUnario,
    val operando: Expr,
    val esPrefijo: Boolean
) : Expr

class Binaria(
    override val id: Int,
    override val pos: Posicion,
    val op: OpBinario,
    val izq: Expr,
    val der: Expr
) : Expr

// destino: Nombre, AccesoMiembro o AccesoIndice (que sea un l-valor valido
// lo chequea el semantico; la gramatica acepta cualquier expresion a la
// izquierda a proposito, sec. 3.4).
class Asignacion(
    override val id: Int,
    override val pos: Posicion,
    val op: OpAsignacion,
    val destino: Expr,
    val valor: Expr
) : Expr

// callee es una Expr y no un String: se puede llamar una funcion suelta
// (Nombre), un metodo (AccesoMiembro) o hasta algo devuelto por otra
// llamada. La gramatica lo permite; el semantico lo acota.
class Llamada(
    override val id: Int,
    override val pos: Posicion,
    val callee: Expr,
    val args: List<Expr>
) : Expr

class AccesoMiembro(
    override val id: Int,
    override val pos: Posicion,
    val obj: Expr,
    val miembro: String
) : Expr

class AccesoIndice(
    override val id: Int,
    override val pos: Posicion,
    val arr: Expr,
    val indice: Expr
) : Expr

// `clase` es el nombre tal como se escribio: simple ("Punto") o
// cualificado con su paquete ("geometria.Punto", Moka 2 P2).
class NuevoObjeto(
    override val id: Int,
    override val pos: Posicion,
    val clase: String,
    val args: List<Expr>
) : Expr

class NuevoArreglo(
    override val id: Int,
    override val pos: Posicion,
    val tipoElemento: TipoAst,
    val tamano: Expr
) : Expr

class LitArreglo(
    override val id: Int,
    override val pos: Posicion,
    val elementos: List<Expr>
) : Expr

// Nota: NO hay nodo "Parentesis". Los parentesis solo sirvieron para guiar
// al parser; el anidamiento del AST ya refleja la agrupacion que pidieron.
// (1 + 2) * 3 y 1 + 2 * 3 dan arboles distintos sin necesidad de marcarlos.
