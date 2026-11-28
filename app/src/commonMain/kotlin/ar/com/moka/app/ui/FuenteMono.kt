// =======================================================================
//  FuenteMono.kt - la fuente de ancho fijo de Moka (7.13)
// =======================================================================
//  Editor, consola y paneles usaban FontFamily.Monospace, que en Android
//  es "la monoespaciada que traiga el telefono": cambia segun la marca y
//  algunos caracteres (acentos, simbolos) caen a otra fuente y ocupan mas
//  o menos lugar. Para que TODOS los caracteres midan lo mismo en
//  cualquier celular, Android lleva su propia fuente adentro de la app:
//  DejaVu Sans Mono (libre), en androidMain/res/font/moka_mono.ttf.
//
//  Es "expect/actual": cada plataforma define su version.
//    - Escritorio: sigue con la monoespaciada del sistema (en Windows,
//      Consolas), que ya se ve bien.
//    - Android: la fuente incluida en la app.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.ui.text.font.FontFamily

internal expect val FuenteMono: FontFamily
