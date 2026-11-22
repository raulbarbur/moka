// =======================================================================
//  Tema.kt - Paleta de colores de Moka (UN solo lugar para editar)
// =======================================================================
//  COMO PERSONALIZAR MOKA: todos los colores viven en este archivo, en
//  tres grupos. Cambia los valores del grupo que quieras y recompila;
//  ningun otro archivo hardcodea colores.
//
//    1. UI general (botones, paneles, dialogos): las constantes
//       CLARO_* / OSCURO_* de abajo, que arman el ColorScheme.
//    2. Resaltado del editor: coloresEditorMoka() (palabra clave,
//       literal, delimitador, error, advertencia, seleccion).
//    3. Consola con color(): coloresConsolaMoka() da la variante clara y
//       oscura de cada color del builtin color("...").
//
//  Los dos esquemas siguen la paleta de IntelliJ:
//    - Claro: tema "Light" por defecto de IntelliJ.
//    - Oscuro (5B, a pedido): el tema oscuro actual de Android Studio
//      (New UI): fondo #1E1F22, paneles #242528, texto #BCBEC4. Antes
//      era Darcula clasico (#2B2B2B), que quedaba claro al lado del
//      entorno.
//
//  ColoresEditorMoka y ColoresConsolaMoka no siguen al color de accion:
//  en IntelliJ la sintaxis tiene sus propios colores (palabra clave azul
//  oscuro en Light, naranja en Darcula; literales siempre verdes). El
//  editor los recibe por LocalColoresEditor y la consola llama a
//  colorDeConsolaMoka(), asi ninguno hardcodea nada.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------- UI clara (IntelliJ Light, sobria) ----------
// En IntelliJ los botones no son azules grandes: son planos y neutros,
// y el color se reserva para ESTADO (error rojo, ok sutil). Por eso
// primary es un gris pizarra y no el azul brillante por defecto.
// Ojo: en Material el mismo "primary" pinta el fondo de los botones
// rellenos y el texto de los TextButton, asi que el gris elegido tiene
// que leerse bien en ambos roles.
private val CLARO_AZUL = Color(0xFF4B5563)      // acento sobrio (gris pizarra)
private val CLARO_FONDO = Color(0xFFFFFFFF)
private val CLARO_PANEL = Color(0xFFF7F8FA)   // fondo de paneles IntelliJ
private val CLARO_TEXTO = Color(0xFF1D1D1D)
private val CLARO_ERROR = Color(0xFFC7222D)
private val CLARO_OK = Color(0xFFDCE8DC)      // chip completada: verde muy sutil
private val CLARO_CORRIENDO = Color(0xFFDFE1E5)
private val CLARO_ERROR_FONDO = Color(0xFFF7D8DB)

// ---------- UI oscura (Android Studio New UI oscuro, 5B) ----------
// Mas oscuro que el Darcula clasico: #1E1F22 es el fondo del editor y
// de la terminal en el Android Studio actual, #242528 el de los paneles
// laterales (Project, Device Manager) y #393B40 las lineas divisorias.
private val OSCURO_AZUL = Color(0xFF9AA0A6)   // acento sobrio (gris claro)
private val OSCURO_FONDO = Color(0xFF1E1F22)
private val OSCURO_PANEL = Color(0xFF242528)
private val OSCURO_TEXTO = Color(0xFFBCBEC4)
private val OSCURO_ERROR = Color(0xFFE55765)
private val OSCURO_OK = Color(0xFF39503A)     // verde apagado
private val OSCURO_CORRIENDO = Color(0xFF2B2D30)
private val OSCURO_ERROR_FONDO = Color(0xFF5C3238)
private val OSCURO_LINEA = Color(0xFF393B40)  // divisores y bordes finos

fun esquemaClaroMoka(): ColorScheme = lightColorScheme(
    primary = CLARO_AZUL,
    onPrimary = Color.White,
    // Los chips de fase usan estos contenedores: completada = ok sutil,
    // corriendo = gris, error = rojo suave. Pendiente/omitida = panel.
    primaryContainer = CLARO_OK,
    onPrimaryContainer = CLARO_TEXTO,
    secondaryContainer = CLARO_CORRIENDO,
    onSecondaryContainer = CLARO_TEXTO,
    error = CLARO_ERROR,
    errorContainer = CLARO_ERROR_FONDO,
    onErrorContainer = Color(0xFF5F1218),
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
    onPrimary = Color(0xFF1D1D1D),
    primaryContainer = OSCURO_OK,
    onPrimaryContainer = OSCURO_TEXTO,
    secondaryContainer = OSCURO_CORRIENDO,
    onSecondaryContainer = OSCURO_TEXTO,
    error = OSCURO_ERROR,
    errorContainer = OSCURO_ERROR_FONDO,
    onErrorContainer = Color(0xFFF2B8BD),
    background = OSCURO_FONDO,
    onBackground = OSCURO_TEXTO,
    surface = OSCURO_FONDO,
    onSurface = OSCURO_TEXTO,
    surfaceVariant = OSCURO_PANEL,
    onSurfaceVariant = OSCURO_TEXTO,
    outline = Color(0xFF8C8C8C),
    // Las lineas finas (divisores arrastrables entre secciones) usan
    // outlineVariant: con el gris New UI quedan visibles pero sutiles.
    outlineVariant = OSCURO_LINEA
)

// ---------- Resaltado del editor (colores de sintaxis IntelliJ) ----------
data class ColoresEditorMoka(
    val palabraClave: Color,
    val literal: Color,
    val delimitador: Color,
    val error: Color,
    val advertencia: Color,
    val seleccion: Color
)

val LocalColoresEditor = staticCompositionLocalOf {
    // Valor de respaldo: solo se usa si alguien monta el editor sin App.
    ColoresEditorMoka(
        palabraClave = Color(0xFF0033B3),
        literal = Color(0xFF067D17),
        delimitador = Color(0xFF6E6E6E),
        error = CLARO_ERROR,
        advertencia = Color(0xFFB8860B),
        seleccion = CLARO_AZUL.copy(alpha = 0.25f)
    )
}

fun coloresEditorMoka(oscuro: Boolean): ColoresEditorMoka =
    if (oscuro) {
        ColoresEditorMoka(
            palabraClave = Color(0xFFCF8E6D), // naranja palabra clave
            literal = Color(0xFF6AAB73),      // verde literales
            delimitador = Color(0xFF8C8C8C),
            error = OSCURO_ERROR,
            advertencia = Color(0xFFBBB529),  // ambar Darcula
            seleccion = OSCURO_AZUL.copy(alpha = 0.35f)
        )
    } else {
        ColoresEditorMoka(
            palabraClave = Color(0xFF0033B3), // azul palabra clave IntelliJ
            literal = Color(0xFF067D17),      // verde IntelliJ
            delimitador = Color(0xFF6E6E6E),
            error = CLARO_ERROR,
            advertencia = Color(0xFFB8860B),  // ambar oscuro legible en blanco
            seleccion = CLARO_AZUL.copy(alpha = 0.25f)
        )
    }

// ---------- Consola: los colores del builtin color("...") ----------
// Los nombres validos los define ColoresConsola en core; aca solo esta
// su pinta. Cada color tiene dos variantes porque el fondo de la consola
// cambia con el tema: un amarillo de terminal clasica desaparece sobre
// fondo claro. Un nombre sin entrada sale con el color por defecto.
data class ColoresConsolaMoka(
    val porNombre: Map<String, Color>
)

fun coloresConsolaMoka(oscuro: Boolean): ColoresConsolaMoka =
    if (oscuro) {
        ColoresConsolaMoka(
            mapOf(
                "rojo" to Color(0xFFFF8A80),
                "verde" to Color(0xFFA5D6A7),
                "azul" to Color(0xFF90CAF9),
                "amarillo" to Color(0xFFFFF59D),
                "celeste" to Color(0xFF80DEEA),
                "magenta" to Color(0xFFE1BEE7),
                "naranja" to Color(0xFFFFCC80),
                "rosa" to Color(0xFFF8BBD0),
                "gris" to Color(0xFFB0BEC5),
                "blanco" to Color(0xFFFFFFFF)
            )
        )
    } else {
        ColoresConsolaMoka(
            mapOf(
                "rojo" to Color(0xFFC62828),
                "verde" to Color(0xFF2E7D32),
                "azul" to Color(0xFF1565C0),
                "amarillo" to Color(0xFF8D6E00),
                "celeste" to Color(0xFF00838F),
                "magenta" to Color(0xFF7B1FA2),
                "naranja" to Color(0xFFEF6C00),
                "rosa" to Color(0xFFAD1457),
                "gris" to Color(0xFF546E7A),
                "blanco" to Color(0xFF212121)
            )
        )
    }

// Traduccion nombre -> Color, lo unico que usa la consola.
fun colorDeConsolaMoka(nombre: String, oscuro: Boolean): Color? =
    coloresConsolaMoka(oscuro).porNombre[nombre]
