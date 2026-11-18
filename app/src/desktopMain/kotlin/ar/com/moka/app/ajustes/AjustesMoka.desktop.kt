// AjustesMoka.desktop.kt - Un properties en ~/.moka/ajustes.properties.
package ar.com.moka.app.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import java.util.Properties

private class AjustesDesktop : AjustesMoka {
    private val archivo = File(System.getProperty("user.home"), ".moka/ajustes.properties")
    private val props = Properties().apply {
        if (archivo.isFile) runCatching { archivo.inputStream().use(::load) }
    }

    @Synchronized
    override fun leer(clave: String): String? = props.getProperty(clave)

    @Synchronized
    override fun escribir(clave: String, valor: String) {
        props.setProperty(clave, valor)
        runCatching {
            archivo.parentFile?.mkdirs()
            archivo.outputStream().use { props.store(it, "Moka") }
        }
    }
}

@Composable
actual fun recordarAjustesMoka(): AjustesMoka = remember { AjustesDesktop() }
