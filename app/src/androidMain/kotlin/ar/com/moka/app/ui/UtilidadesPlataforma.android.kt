// =======================================================================
//  UtilidadesPlataforma.android.kt - version Android
// =======================================================================
//  Implementaciones "actual" de lo declarado en commonMain
//  (UtilidadesPlataforma.kt). En Android no hay cursor de mouse en el
//  uso comun, asi que los divisores dejan el cursor por defecto. El
//  zoom con Ctrl + rueda es comun y vive en commonMain (con un mouse
//  y teclado fisicos en Android funciona igual).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.ui.input.pointer.PointerIcon

internal actual fun cursorDivisorVertical(): PointerIcon = PointerIcon.Default
internal actual fun cursorDivisorHorizontal(): PointerIcon = PointerIcon.Default
