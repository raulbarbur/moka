// ============================================================================
// Moka.g4 - Gramatica del lenguaje Moka (documento de diseno, seccion 3)
//
// Fase 1: lexico completo (todos los tokens nombrados de la seccion 3.2,
// con sus errores lexicos). El parser sigue siendo el esqueleto de la
// Fase 0: las reglas sintacticas de verdad (expresiones, declaracion de
// variables, imprimir) son el siguiente paso, seccion 3.3 del documento.
//
// Convencion (decision cerrada, seccion 3.1): todo literal tiene una regla
// lexica NOMBRADA y el parser referencia solo nombres de token, nunca
// literales entre comillas. Los fragment son auxiliares: no producen tokens.
// ============================================================================
grammar Moka;

// =========================== PARSER ===========================
// Esqueleto de la Fase 0. Sigue compilando con el lexico completo:
// "hola;" sigue siendo ID PUNTO_COMA. Se reemplaza en la Fase 1.

programa : linea* EOF ;

linea    : ID PUNTO_COMA ;

// ============================ LEXER ============================

// --- Palabras clave de control, funciones y clases ---
// Van ANTES de ID. Regla de ANTLR: gana el lexema MAS LARGO; ante empate,
// gana la regla declarada primero. Asi "si" es la keyword SI y "silla"
// es un ID (5 letras ganan contra 2). Y "para_cada" gana contra "para"
// por longitud, aunque PARA este declarada antes.
SI          : 'si' ;
SINO        : 'sino' ;
MIENTRAS    : 'mientras' ;
HACER       : 'hacer' ;
PARA        : 'para' ;
PARA_CADA   : 'para_cada' ;   // un solo token con guion bajo: evita una secuencia de dos keywords
SEGUN       : 'segun' ;
CASO        : 'caso' ;
DEFECTO     : 'defecto' ;
ROMPER      : 'romper' ;
CONTINUAR   : 'continuar' ;
RETORNAR    : 'retornar' ;
FUNCION     : 'funcion' ;
CLASE       : 'clase' ;
CONSTRUCTOR : 'constructor' ;
NUEVO       : 'nuevo' ;
ESTE        : 'este' ;
ESTATICO    : 'estatico' ;
CONSTANTE   : 'constante' ;

// --- Operadores con nombre ---
// Decision del lenguaje: los operadores logicos son palabras, no simbolos.
// Efecto colateral: "y", "o" y "no" son palabras reservadas, no pueden
// usarse como nombres de variable.
Y  : 'y' ;
O  : 'o' ;
NO : 'no' ;

// --- Nombres de tipo ---
T_ENTERO   : 'entero' ;
T_FLOTANTE : 'flotante' ;
T_BOOLEANO : 'booleano' ;
T_CARACTER : 'caracter' ;
T_CADENA   : 'cadena' ;
T_VACIO    : 'vacio' ;        // solo para tipo de retorno de funciones

// --- Literales con nombre fijo ---
VERDADERO : 'verdadero' ;
FALSO     : 'falso' ;
NULO      : 'nulo' ;

// --- Operadores aritmeticos ---
// Los de dos caracteres (++, --) se resuelven solos por lexema mas largo:
// "a++" nunca sale como MAS MAS.
MAS        : '+' ;
MENOS      : '-' ;   // un -7 sale como MENOS seguido de ENTERO_LIT: el signo
                     // es el operador unario, no parte del literal
POR        : '*' ;
DIV        : '/' ;   // "// comentario" gana por longitud, nunca DIV DIV
MODULO     : '%' ;
INCREMENTO : '++' ;
DECREMENTO : '--' ;

// --- Operadores relacionales ---
MENOR       : '<' ;
MENOR_IGUAL : '<=' ;
MAYOR       : '>' ;
MAYOR_IGUAL : '>=' ;
IGUAL_IGUAL : '==' ;
DISTINTO    : '!=' ;

// --- Asignacion ---
ASIGNA       : '=' ;
MAS_ASIGNA   : '+=' ;
MENOS_ASIGNA : '-=' ;
POR_ASIGNA   : '*=' ;
DIV_ASIGNA   : '/=' ;
MOD_ASIGNA   : '%=' ;

// --- Delimitadores y puntuacion ---
PAREN_IZQ    : '(' ;
PAREN_DER    : ')' ;
LLAVE_IZQ    : '{' ;
LLAVE_DER    : '}' ;
CORCHETE_IZQ : '[' ;
CORCHETE_DER : ']' ;
PUNTO_COMA   : ';' ;
COMA         : ',' ;
PUNTO        : '.' ;
DOS_PUNTOS   : ':' ;   // para "caso X:" en el segun

// --- Literales con forma variable ---
// Ojo con el orden conceptual: para "3.14", FLOTANTE_LIT matchea 4 chars
// y ENTERO_LIT solo 1, asi que gana FLOTANTE_LIT por longitud aunque
// ENTERO_LIT este declarada antes. No acepta ni "5." ni ".5" ni "1e5":
// decision del documento, se pueden agregar despues.
ENTERO_LIT   : DIGITO+ ;
FLOTANTE_LIT : DIGITO+ '.' DIGITO+ ;
CARACTER_LIT : '\'' ( ~['\\\r\n] | ESCAPE ) '\'' ;   // un caracter o un escape: 'a', '\n'
CADENA_LIT   : '"' ( ~["\\\r\n] | ESCAPE )* '"' ;    // cero o mas: "hola", ""

fragment DIGITO : [0-9] ;
fragment ESCAPE : '\\' [ntr"'\\0] ;   // \n \t \r \" \' \\ \0

// --- Identificadores (letras Unicode: acepta tildes, enie, etc.) ---
// Verificado en la Fase 0: \p{L} (letra) y \p{N} (numero) funcionan en el
// runtime Kotlin de ANTLR, en JVM y en Android.
ID : [\p{L}_] [\p{L}\p{N}_]* ;

// --- Se descartan: comentarios y espacios ---
// -> skip: no producen token, el parser nunca los ve. Si la GUI quisiera
// resaltarlos algun dia, se cambian a -> channel(HIDDEN) sin tocar nada mas.
COMENTARIO_LINEA  : '//' ~[\r\n]*  -> skip ;
COMENTARIO_BLOQUE : '/*' .*? '*/'  -> skip ;   // .*? no codicioso: frena en el primer */
ESPACIO           : [ \t\r\n]+     -> skip ;

// --- Errores lexicos como tokens (para reportarlos sin perderlos) ---
// VAN AL FINAL: solo ganan cuando ninguna otra regla matchea. El lexer
// nunca se traga un error en silencio: produce un token que el pipeline
// convierte en diagnostico en espanol ("Cadena sin cerrar", "Caracter no
// reconocido: '@'") y la GUI marca en rojo.
CADENA_ABIERTA : '"' ( ~["\\\r\n] | ESCAPE )* ;   // comilla sin cerrar en la linea
ERROR_CHAR     : . ;                              // cualquier caracter no reconocido