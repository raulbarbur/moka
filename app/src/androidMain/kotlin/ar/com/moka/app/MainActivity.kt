package ar.com.moka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import ar.com.moka.app.archivos.EstadoProyecto
import ar.com.moka.app.archivos.SesionArchivoMoka
import ar.com.moka.app.archivos.registrarContextoProyecto
import ar.com.moka.app.estado.MokaControlador

/** Entrada de la app Android: ./gradlew :app:installDebug */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // P3: la lectura de paquetes desde la carpeta elegida necesita el
        // contentResolver (SAF), disponible recien con la Activity.
        registrarContextoProyecto(this)
        setContent {
            val proyecto = remember { EstadoProyecto() }
            val controlador = remember { MokaControlador(proyecto.proveedor) }
            val sesionArchivo = remember { SesionArchivoMoka() }

            // Atras usa la misma regla que cerrar la ventana desktop.
            BackHandler {
                if (sesionArchivo.pedirCierre(controlador.estado.value.fuente)) finish()
            }
            App(
                controlador = controlador,
                sesionArchivo = sesionArchivo,
                proyecto = proyecto,
                alSalir = { finish() }
            )
        }
    }
}
