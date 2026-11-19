// AjustesMoka.kt - Preferencias chicas que sobreviven al cierre.
// Hoy: tema, modo (simple/completo), autocompletado y, desde 5C, el
// tamano de letra y la visibilidad de cada panel (claves "tam.<id>" y
// "panel.<id>", ver ui/EstadoPaneles.kt). Clave/valor alcanza; cada
// plataforma elige su almacenamiento.
package ar.com.moka.app.ajustes

import androidx.compose.runtime.Composable

interface AjustesMoka {
    fun leer(clave: String): String?
    fun escribir(clave: String, valor: String)
}

@Composable
expect fun recordarAjustesMoka(): AjustesMoka
