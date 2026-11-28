// FuenteMono.android.kt - Android usa DejaVu Sans Mono, incluida en la app
// (app/src/androidMain/res/font/moka_mono.ttf), igual en todos los telefonos.
package ar.com.moka.app.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import ar.com.moka.app.R

internal actual val FuenteMono: FontFamily = FontFamily(Font(R.font.moka_mono))
