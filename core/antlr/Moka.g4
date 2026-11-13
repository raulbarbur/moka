// ============================================================================
// Moka.g4 - Gramatica del lenguaje Moka (documento de diseno, seccion 3)
//
// Fase 0: ESQUELETO. Solo alcanza para verificar que:
//   1. antlr-kotlin genera lexer y parser en Kotlin dentro de commonMain.
//   2. El runtime multiplataforma de ANTLR compila para JVM y Android.
//   3. Las propiedades Unicode \p{L} (letras con tilde, enie, etc.) funcionan
//      en los identificadores (riesgo listado en la seccion 6 del documento).
//
// Convencion (decision cerrada 4, seccion 3.1): todo literal tiene una regla
// lexica NOMBRADA y el parser referencia solo nombres de token, nunca
// literales entre comillas. Los fragment son auxiliares: no producen tokens.
// ============================================================================
grammar Moka;

// --------------------------- PARSER ---------------------------

// Un programa es una secuencia de lineas hasta el fin de archivo.
programa : linea* EOF ;

// Fase 0: la unica "sentencia" es un identificador seguido de ';'.
// (En la Fase 1 esto se reemplaza por la gramatica de expresiones real.)
linea : ID PUNTO_COMA ;

// --------------------------- LEXER ----------------------------

PUNTO_COMA : ';' ;

// Identificadores con soporte Unicode: LETRA matchea cualquier letra
// (\p{L}), asi 'anio' con enie o 'señal' son identificadores validos.
ID       : LETRA (LETRA | DIGITO)* ;
ESPACIOS : [ \t\r\n]+ -> skip ;

fragment LETRA  : [\p{L}] ;
fragment DIGITO : [0-9] ;
