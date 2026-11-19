// =======================================================================
//  UtilidadesPlataforma.desktop.kt - version escritorio (AWT)
// =======================================================================
//  Implementaciones "actual" de lo declarado en commonMain
//  (UtilidadesPlataforma.kt): cursores nativos de redimension para los
//  divisores. El zoom con Ctrl + rueda es comun a todas las
//  plataformas y vive en commonMain.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

// Cursores del sistema: flecha horizontal de dos puntas para la barra
// vertical (se arrastra hacia los costados) y flecha vertical de dos
// puntas para la barra horizontal. Se crean una sola vez.
private val CURSOR_COSTADOS = PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
private val CURSOR_ARRIBA_ABAJO = PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR))

internal actual fun cursorDivisorVertical(): PointerIcon = CURSOR_COSTADOS
internal actual fun cursorDivisorHorizontal(): PointerIcon = CURSOR_ARRIBA_ABAJO
