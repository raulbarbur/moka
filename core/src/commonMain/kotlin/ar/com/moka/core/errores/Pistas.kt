// ============================================================================
//  Pistas.kt - Sugerencias didacticas para los diagnosticos
// ============================================================================
//  Cada diagnostico puede llevar una PISTA: una sugerencia corta pensada
//  para quien esta aprendiendo, que aparece debajo del mensaje en el
//  panel Problemas, en la consola de la GUI y en la CLI. El mensaje dice
//  QUE paso; la pista sugiere COMO encararlo.
//
//  Es una propiedad calculada de Diagnostico (Diagnostico.pista): no se
//  guarda en el constructor, asi ningun punto de reporte cambia y hasta
//  los diagnosticos viejos ganan pista si su codigo la tiene.
//
//  Dos fuentes:
//    - fijas por codigo (EJE-001 -> "el divisor dio 0...", LEX-002 -> la
//      comilla de cierre);
//    - por patron del mensaje para SIN-001, el codigo comun de todos los
//      errores de sintaxis: el detalle ya traducido ("se esperaba ';'
//      antes de ...") delata el caso tipico.
// ============================================================================
package ar.com.moka.core.errores

object Pistas {

    // La pista de un diagnostico, o null si no tenemos una sugerencia
    // util (mejor callar que repetir el mensaje con otras palabras).
    fun para(codigo: String, mensaje: String): String? =
        fijas[codigo] ?: cuandoSin001(codigo, mensaje) ?: cuandoLex001(codigo, mensaje)

    // ------------------------- pistas fijas -------------------------

    private val fijas = mapOf(
        "LEX-002" to "A la cadena le falta la comilla doble de cierre: \"texto\".",
        "SEM-001" to "Revisa que el nombre este bien escrito (se distinguen mayusculas y minusculas) y que la variable o funcion este declarada antes de usarse.",
        "SEM-004" to "Un flotante no entra solo en un entero: converti con a_entero(valor), que trunca los decimales.",
        "SEM-007" to "Conta los argumentos entre parentesis y comparalos con la declaracion de la funcion.",
        "SEM-008" to "Los tipos tienen que coincidir; si hace falta, converti con a_entero(...), a_flotante(...), a_cadena(...) o a_booleano(...).",
        "SEM-013" to "Una funcion 'vacio' no devuelve nada: llamala como instruccion suelta, no dentro de una expresion.",
        "SEM-015" to "A la izquierda de un = solo puede ir una variable, un campo o un elemento de arreglo.",
        "SEM-053" to "longitud(...) sirve para arreglos y cadenas; a un entero no se le puede pedir su longitud.",
        "EJE-001" to "El divisor dio 0. Si lo escribe el usuario, validalo con un si antes de dividir.",
        "EJE-002" to "El divisor dio 0: el resto (%) tampoco puede calcularse con divisor 0.",
        "EJE-003" to "Los indices de un arreglo van de 0 a longitud - 1; el ultimo NO es longitud.",
        "EJE-004" to "La variable vale nulo: inicializala con nuevo NombreDeLaClase(...) antes de usarla.",
        "EJE-005" to "El texto no tiene el formato pedido: un entero se escribe sin puntos ni letras (42), un flotante con punto (4.2).",
        "EJE-009" to "El color se escribe entre comillas y con uno de los nombres de la lista, por ejemplo color(\"verde\").",
        "EJE-010" to "pausa espera milisegundos: pausa(500) espera medio segundo."
    )

    // ------------------------- SIN-001 por patron -------------------------

    // El detalle ya viene traducido por TraductorErrores ("se esperaba
    // ';' antes de 'imprimir'."). Cada patron apunta al error tipico del
    // curso, no a repetir la gramatica.
    private fun cuandoSin001(codigo: String, mensaje: String): String? {
        if (codigo != "SIN-001") return null
        return when {
            "se esperaba ';'" in mensaje ->
                "Cada instruccion termina con punto y coma: imprimir(\"hola\");"
            "se esperaba ')'" in mensaje ->
                "Conta los parentesis: cada '(' que abre necesita su ')' de cierre."
            "se esperaba '('" in mensaje ->
                "Las condiciones de si, mientras y para van entre parentesis: si (x > 0) { ... }"
            "se esperaba '}'" in mensaje || "se esperaba '{'" in mensaje ->
                "Cada '{' abre un bloque que se cierra con '}'; revisa que queden parejas."
            "se encontro '='" in mensaje ->
                "Para comparar se usa '==' (doble igual); '=' solo asigna valores."
            "sobra ';'" in mensaje ->
                "Ese ';' corta la instruccion antes de tiempo: un si o un mientras no llevan ';' antes de su bloque."
            else -> null
        }
    }

    // ------------------------- LEX-001 por patron -------------------------

    // "caracter no reconocido: 'X'": los clasicos son las comillas
    // tipograficas que pegan Word o las paginas web.
    private fun cuandoLex001(codigo: String, mensaje: String): String? {
        if (codigo != "LEX-001") return null
        val curvas = listOf("‘", "’", "“", "”", "`", "´")
        return if (curvas.any { it in mensaje }) {
            "Esas comillas vienen de un texto formateado (Word, una pagina): reemplazalas por la comilla recta (\") del teclado."
        } else {
            "Ese caracter no pertenece al lenguaje; las cadenas van entre comillas dobles (\") y los caracteres entre simples (')."
        }
    }
}
