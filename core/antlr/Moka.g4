// ============================================================================
// Moka.g4 - Gramatica completa del lenguaje Moka (documento de diseno, sec. 3)
//
// Fase 1: lexico (seccion 3.2) + sintaxis (seccion 3.3). Ya no es el
// esqueleto de la Fase 0: el parser entiende el lenguaje entero.
//
// Convencion (decision cerrada, seccion 3.1): todo literal tiene una regla
// lexica NOMBRADA y el parser referencia solo nombres de token, nunca
// literales entre comillas. Los fragment son auxiliares: no producen tokens.
// ============================================================================
grammar Moka;

// =========================== PARSER ===========================
// Un programa es una mezcla de clases, funciones y sentencias sueltas.
// Lo suelto actua como el "main" implicito (seccion 2.2).

programa
    : encabezadoPaquete? declaracionUso* (declaracionClase | declaracionFuncion | sentencia)* EOF
    ;

// --- Paquetes (Moka 2, P1) ---
// `paquete util;` al inicio declara a que paquete pertenece el archivo y
// `usar util;` pide otro paquete para llamar sus funciones como
// util.sumar(...). Un nombre de paquete puede tener puntos: util.texto.
encabezadoPaquete : PAQUETE nombrePaquete PUNTO_COMA ;
declaracionUso    : USAR nombrePaquete PUNTO_COMA ;
nombrePaquete     : ID (PUNTO ID)* ;

// --- Declaraciones ---

declaracionFuncion
    : FUNCION tipoRetorno ID PAREN_IZQ parametros? PAREN_DER bloque
    ;

declaracionClase
    : CLASE ID LLAVE_IZQ miembroClase* LLAVE_DER
    ;

// Las etiquetas (# miembroCampo, ...) generan contextos y metodos de
// visitor separados: visitMiembroCampo, visitMiembroMetodo, etc. Eso hace
// el constructor del AST mucho mas legible (seccion 3.4 del documento).
miembroClase
    : ESTATICO? declaracionVariable                                              # miembroCampo
    | CONSTRUCTOR PAREN_IZQ parametros? PAREN_DER bloque                         # miembroConstructor
    | ESTATICO? FUNCION tipoRetorno ID PAREN_IZQ parametros? PAREN_DER bloque    # miembroMetodo
    ;

parametros : parametro (COMA parametro)* ;
parametro  : tipo ID ;

declaracionVariable
    : CONSTANTE? tipo ID (ASIGNA expresion)? PUNTO_COMA
    ;

// --- Tipos ---
// tipoBase incluye ID: el nombre de una clase es un tipo (Alumno a = ...).
// Desde Moka 2 P2 el ID puede venir cualificado con su paquete
// (geometria.Punto p = ...), con la misma forma que nombrePaquete.

tipoRetorno : T_VACIO | tipo ;
tipo        : tipoBase (CORCHETE_IZQ CORCHETE_DER)? ;
tipoBase    : T_ENTERO | T_FLOTANTE | T_BOOLEANO | T_CARACTER | T_CADENA | ID (PUNTO ID)* ;

// --- Sentencias ---

bloque : LLAVE_IZQ sentencia* LLAVE_DER ;

sentencia
    : bloque
    | declaracionVariable
    | sentenciaSi
    | sentenciaMientras
    | sentenciaHacer
    | sentenciaPara
    | sentenciaParaCada
    | sentenciaSegun
    | sentenciaRetornar
    | sentenciaRomper
    | sentenciaContinuar
    | sentenciaExpresion
    | sentenciaVacia
    ;

// Dangling else: ante la ambiguedad, ANTLR elige consumir el SINO, o sea
// lo asocia al SI mas interno. Exactamente la semantica de Java (sec. 3.4).
sentenciaSi
    : SI PAREN_IZQ expresion PAREN_DER sentencia (SINO sentencia)?
    ;

sentenciaMientras
    : MIENTRAS PAREN_IZQ expresion PAREN_DER sentencia
    ;

sentenciaHacer
    : HACER sentencia MIENTRAS PAREN_IZQ expresion PAREN_DER PUNTO_COMA
    ;

sentenciaPara
    : PARA PAREN_IZQ inicioPara expresion? PUNTO_COMA expresion? PAREN_DER sentencia
    ;

// La declaracion de variable ya consume su propio PUNTO_COMA; por eso el
// inicio del para tiene forma especial y no lleva otro ; explicito.
inicioPara
    : declaracionVariable
    | expresion? PUNTO_COMA
    ;

sentenciaParaCada
    : PARA_CADA PAREN_IZQ tipo ID DOS_PUNTOS expresion PAREN_DER sentencia
    ;

sentenciaSegun
    : SEGUN PAREN_IZQ expresion PAREN_DER LLAVE_IZQ (ramaCaso | ramaDefecto)* LLAVE_DER
    ;

ramaCaso    : CASO etiquetaCaso DOS_PUNTOS sentencia* ;
ramaDefecto : DEFECTO DOS_PUNTOS sentencia* ;
etiquetaCaso
    : MENOS? ENTERO_LIT
    | CARACTER_LIT
    | CADENA_LIT
    ;

sentenciaRetornar   : RETORNAR expresion? PUNTO_COMA ;
sentenciaRomper     : ROMPER PUNTO_COMA ;
sentenciaContinuar  : CONTINUAR PUNTO_COMA ;
sentenciaExpresion  : expresion PUNTO_COMA ;
sentenciaVacia      : PUNTO_COMA ;

// --- Expresiones ---
// Regla recursiva a izquierda: ANTLR la reescribe solo y lee la precedencia
// del ORDEN de las alternativas (mas abajo = MENOS precedencia). O sea:
// la asignacion es lo ultimo que se resuelve y el postfijo lo primero.
// Cada etiqueta genera su visitXxx en el visitor (accesoIndice, llamada...).

expresion
    : primario                                                       # primarioExpr
    | expresion PUNTO ID                                             # accesoMiembro
    | expresion CORCHETE_IZQ expresion CORCHETE_DER                  # accesoIndice
    | expresion PAREN_IZQ argumentos? PAREN_DER                      # llamada
    | expresion op=(INCREMENTO | DECREMENTO)                         # postfijo
    | op=(INCREMENTO | DECREMENTO | MENOS | NO) expresion            # unario
    | expresion op=(POR | DIV | MODULO) expresion                    # multiplicativa
    | expresion op=(MAS | MENOS) expresion                           # aditiva
    | expresion op=(MENOR | MENOR_IGUAL | MAYOR | MAYOR_IGUAL) expresion   # relacional
    | expresion op=(IGUAL_IGUAL | DISTINTO) expresion                # igualdad
    | expresion Y expresion                                          # conjuncion
    | expresion O expresion                                          # disyuncion
    | <assoc=right> expresion
        op=(ASIGNA | MAS_ASIGNA | MENOS_ASIGNA | POR_ASIGNA | DIV_ASIGNA | MOD_ASIGNA)
        expresion                                                    # asignacion
    ;

argumentos : expresion (COMA expresion)* ;

primario
    : ENTERO_LIT                                                     # litEntero
    | FLOTANTE_LIT                                                   # litFlotante
    | CARACTER_LIT                                                   # litCaracter
    | CADENA_LIT                                                     # litCadena
    | VERDADERO                                                      # litVerdadero
    | FALSO                                                          # litFalso
    | NULO                                                           # litNulo
    | ESTE                                                           # refEste
    | ID                                                             # refNombre
    | PAREN_IZQ expresion PAREN_DER                                  # parentesis
    | NUEVO ID (PUNTO ID)* PAREN_IZQ argumentos? PAREN_DER           # nuevoObjeto
    | NUEVO tipoBase CORCHETE_IZQ expresion CORCHETE_DER             # nuevoArreglo
    | CORCHETE_IZQ expresion (COMA expresion)* CORCHETE_DER          # litArreglo
    ;

// Detalle de diseno: la gramatica acepta cualquier expresion a la izquierda
// de "=" (hola + 2 = 3). Que sea un l-valor valido se verifica en el
// analisis SEMANTICO, donde el mensaje de error en espanol es mucho mejor
// que el generico del parser (seccion 3.4 del documento).

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
PAQUETE     : 'paquete' ;
USAR        : 'usar' ;

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
