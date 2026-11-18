// =======================================================================
//  Tema.kt - Paleta de colores de Moka (UN solo lugar para editar)
// =======================================================================
//  Los dos esquemas siguen la paleta de IntelliJ:
//    - Claro: tema "Light" por defecto de IntelliJ.
//    - Oscuro: Darcula (#2B2B2B de fondo, paneles #3C3F41, texto #A9B7C6).
//
//  Hay dos piezas:
//    1. esquemaClaroMoka()/esquemaOscuroMoka(): el ColorScheme de toda la
//       UI (botones, paneles, dialogos). primary es el azul de accion.
//    2. ColoresEditorMoka: los colores del resaltado de sintaxis, que en
//       IntelliJ no siguen al color de accion (palabra clave azul oscuro
//       en Light, naranja en Darcula; literales siempre verdes). Llegan
//       al editor por LocalColoresEditor, asi EditorTaller no hardcodea
//       nada y un tema futuro personalizable solo toca este archivo.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------- UI clara (IntelliJ Light) ----------
private val CLARO_AZUL = Color(0xFF3574F0)    // azul de accion IntelliJ
private val CLARO_FONDO = Color(0xFFFFFFFF)
private val CLARO_PANEL = Color(0xFFF7F8FA)   // fondo de paneles IntelliJ
private val CLARO_TEXTO = Color(0xFF1D1D1D)
private val CLARO_ERROR = Color(0xFFC7222D)

// ---------- UI oscura (Darcula) ----------
private val OSCURO_AZUL = Color(0xFF548AF7)   // azul de accion en Darcula
private val OSCURO_FONDO = Color(0xFF2B2B2B)
private val OSCURO_PANEL = Color(0xFF3C3F41)
private val OSCURO_TEXTO = Color(0xFFA9B7C6)
private val OSCURO_ERROR = Color(0xFFE55765)

fun esquemaClaroMoka(): ColorScheme = lightColorScheme(
    primary = CLARO_AZUL,
    onPrimary = Color.White,
    error = CLARO_ERROR,
    background = CLARO_FONDO,
    onBackground = CLARO_TEXTO,
    surface = CLARO_FONDO,
    onSurface = CLARO_TEXTO,
    surfaceVariant = CLARO_PANEL,
    onSurfaceVariant = CLARO_TEXTO,
    outline = Color(0xFF6E6E6E)
)

fun esquemaOscuroMoka(): ColorScheme = darkColorScheme(
    primary = OSCURO_AZUL,
    onPrimary = Color.White,
    error = OSCURO_ERROR,
    background = OSCURO_FONDO,
    onBackground = OSCURO_TEXTO,
    surface = OSCURO_FONDO,
    onSurface = OSCURO_TEXTO,
    surfaceVariant = OSCURO_PANEL,
    onSurfaceVariant = OSCURO_TEXTO,
    outline = Color(0xFF8C8C8C)
)

// ---------- Resaltado del editor (colores de sintaxis IntelliJ) ----------
data class ColoresEditorMoka(
    val palabraClave: Color,
    val literal: Color,
    val delimitador: Color,
    val error: Color,
    val seleccion: Color
)

val LocalColoresEditor = staticCompositionLocalOf {
    // Valor de respaldo: solo se usa si alguien monta el editor sin App.
    ColoresEditorMoka(
        palabraClave = Color(0xFF0033B3),
        literal = Color(0xFF067D17),
        delimitador = Color(0xFF6E6E6E),
        error = CLARO_ERROR,
        seleccion = CLARO_AZUL.copy(alpha = 0.25f)
    )
}

fun coloresEditorMoka(oscuro: Boolean): ColoresEditorMoka =
    if (oscuro) {
        ColoresEditorMoka(
            palabraClave = Color(0xFFCF8E6D), // naranja Darcula
            literal = Color(0xFF6AAB73),      // verde Darcula
            delimitador = Color(0xFF8C8C8C),
            error = OSCURO_ERROR,
            seleccion = OSCURO_AZUL.copy(alpha = 0.35f)
        )
    } else {
        ColoresEditorMoka(
            palabraClave = Color(0xFF0033B3), // azul palabra clave IntelliJ
            literal = Color(0xFF067D17),      // verde IntelliJ
            delimitador = Color(0xFF6E6E6E),
            error = CLARO_ERROR,
            seleccion = CLARO_AZUL.copy(alpha = 0.25f)
        )
    }
