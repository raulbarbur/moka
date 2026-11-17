// =======================================================================
//  App.kt — Raiz de la UI (reemplaza la pantalla demo de la Fase 0)
// =======================================================================
//  Crea el controlador UNA vez por pantalla (remember), lo libera cuando
//  la pantalla se destruye (DisposableEffect) y monta el Taller. Nada
//  mas: la UI de verdad vive en ui/ y la logica de estado en estado/.
//
//  A partir de aca la app ya no usa el object Moka de la Fase 0
//  (ar.com.moka.core.Moka): habla con el nucleo solo por la fachada
//  pipeline.Moka, a traves del controlador.
// =======================================================================
package ar.com.moka.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ar.com.moka.app.estado.MokaControlador
import ar.com.moka.app.ui.Taller

@Composable
fun App() {
    val controlador = remember { MokaControlador() }
    DisposableEffect(Unit) {
        onDispose { controlador.liberar() }
    }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Taller(controlador)
        }
    }
}
