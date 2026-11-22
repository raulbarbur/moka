// =======================================================================
//  ReglasToken.kt — La regla de la sec. 3.2 que reconocio cada token
// =======================================================================
//  Tabla tipo de token -> texto de su regla en la gramatica (sec. 3.2),
//  para el conmutador "Mostrar regla" del panel de tokens. Es una copia
//  didactica de Moka.g4: las palabras clave y operadores muestran su
//  literal entre comillas; los tokens de forma variable muestran el
//  patron. Si la gramatica cambia, esta tabla se actualiza a mano (es la
//  unica copia: el ANTLR generado no expone los patrones).
// =======================================================================
package ar.com.moka.app.ui

val REGLAS_TOKEN: Map<String, String> = mapOf(
    // --- palabras clave de control ---
    "SI" to "'si'",
    "SINO" to "'sino'",
    "MIENTRAS" to "'mientras'",
    "HACER" to "'hacer'",
    "PARA" to "'para'",
    "PARA_CADA" to "'para_cada'",
    "PAQUETE" to "'paquete'",
    "USAR" to "'usar'",
    "SEGUN" to "'segun'",
    "CASO" to "'caso'",
    "DEFECTO" to "'defecto'",
    "ROMPER" to "'romper'",
    "CONTINUAR" to "'continuar'",
    "RETORNAR" to "'retornar'",
    // --- declaraciones ---
    "FUNCION" to "'funcion'",
    "CLASE" to "'clase'",
    "CONSTRUCTOR" to "'constructor'",
    "NUEVO" to "'nuevo'",
    "ESTE" to "'este'",
    "ESTATICO" to "'estatico'",
    "CONSTANTE" to "'constante'",
    // --- operadores booleanos (palabras reservadas) ---
    "Y" to "'y'",
    "O" to "'o'",
    "NO" to "'no'",
    // --- tipos ---
    "T_ENTERO" to "'entero'",
    "T_FLOTANTE" to "'flotante'",
    "T_BOOLEANO" to "'booleano'",
    "T_CARACTER" to "'caracter'",
    "T_CADENA" to "'cadena'",
    "T_VACIO" to "'vacio'",
    // --- literales constantes ---
    "VERDADERO" to "'verdadero'",
    "FALSO" to "'falso'",
    "NULO" to "'nulo'",
    // --- operadores aritmeticos ---
    "MAS" to "'+'",
    "MENOS" to "'-'",
    "POR" to "'*'",
    "DIV" to "'/'",
    "MODULO" to "'%'",
    "INCREMENTO" to "'++'",
    "DECREMENTO" to "'--'",
    // --- comparacion ---
    "MENOR" to "'<'",
    "MENOR_IGUAL" to "'<='",
    "MAYOR" to "'>'",
    "MAYOR_IGUAL" to "'>='",
    "IGUAL_IGUAL" to "'=='",
    "DISTINTO" to "'!='",
    // --- asignacion ---
    "ASIGNA" to "'='",
    "MAS_ASIGNA" to "'+='",
    "MENOS_ASIGNA" to "'-='",
    "POR_ASIGNA" to "'*='",
    "DIV_ASIGNA" to "'/='",
    "MOD_ASIGNA" to "'%='",
    // --- delimitadores ---
    "PAREN_IZQ" to "'('",
    "PAREN_DER" to "')'",
    "LLAVE_IZQ" to "'{'",
    "LLAVE_DER" to "'}'",
    "CORCHETE_IZQ" to "'['",
    "CORCHETE_DER" to "']'",
    "PUNTO_COMA" to "';'",
    "COMA" to "','",
    "PUNTO" to "'.'",
    "DOS_PUNTOS" to "':'",
    // --- de forma variable (patrones) ---
    "ENTERO_LIT" to "[0-9]+",
    "FLOTANTE_LIT" to "[0-9]+ '.' [0-9]+",
    "CARACTER_LIT" to "'a'  |  un escape como '\\n' o '\\t'",
    "CADENA_LIT" to "\"texto entre comillas\"",
    "ID" to "[\\p{L}_] [\\p{L}\\p{N}_]*",
    // --- errores lexicos ---
    "CADENA_ABIERTA" to "comilla de apertura sin cierre en la misma linea",
    "ERROR_CHAR" to ".  (cualquier caracter no reconocido)"
)

fun reglaDe(tipo: String): String = REGLAS_TOKEN[tipo] ?: "(regla no documentada)"
