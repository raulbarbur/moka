package ar.com.moka.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.core.Moka

/**
 * Pantalla unica de la Fase 0: un "hola" compartido entre escritorio y
 * Android que demuestra que la UI llama al nucleo (core) y parsea con el.
 */
@Composable
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Moka - Taller", style = MaterialTheme.typography.headlineMedium)
            Text(Moka.saludo())
            Text(Moka.analizar("hola;\nmundo;").toString())
        }
    }
}
