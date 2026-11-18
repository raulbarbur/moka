// AjustesMoka.kt - Preferencias chicas que sobreviven al cierre.
// Hoy: tema y modo (simple/completo). Clave/valor alcanza; cada
// plataforma elige su almacenamiento.
package ar.com.moka.app.ajustes

import androidx.compose.runtime.Composable

interface AjustesMoka {
    fun leer(clave: String): String?
    fun escribir(clave: String, valor: String)
}

@Composable
expect fun recordarAjustesMoka(): AjustesMoka
