// AjustesMoka.android.kt - SharedPreferences privadas de la app.
package ar.com.moka.app.ajustes

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class AjustesAndroid(contexto: Context) : AjustesMoka {
    private val prefs = contexto.getSharedPreferences("moka_ajustes", Context.MODE_PRIVATE)
    override fun leer(clave: String): String? = prefs.getString(clave, null)
    override fun escribir(clave: String, valor: String) {
        prefs.edit().putString(clave, valor).apply()
    }
}

@Composable
actual fun recordarAjustesMoka(): AjustesMoka {
    val contexto = LocalContext.current.applicationContext
    return remember(contexto) { AjustesAndroid(contexto) }
}
