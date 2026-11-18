// =======================================================================
//  SesionArchivoMoka.kt - Estado compartido del documento abierto
// =======================================================================
//  ArchivoTaller maneja las ACCIONES (abrir/guardar); esta clase guarda
//  el ESTADO minimo del documento para que las entradas de plataforma
//  tambien puedan decidir si cerrar es seguro:
//
//    - que archivo esta abierto (o ninguno, si es nuevo/ejemplo);
//    - cual fue la ultima copia efectivamente guardada;
//    - si hay una operacion de archivo en curso;
//    - si el usuario ya pidio cerrar y debe ver la confirmacion.
//
//  Es un objeto de UI chico con mutableStateOf: vive en compose y hace
//  reaccionar la barra de archivo/dialogo, sin meter archivos en el
//  controlador del pipeline.
// =======================================================================
package ar.com.moka.app.archivos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SesionArchivoMoka {
    var archivo by mutableStateOf<ArchivoMoka?>(null)
        private set
    var ultimaGuardada by mutableStateOf("")
        private set
    var ocupado by mutableStateOf(false)
        private set
    var cierrePendiente by mutableStateOf(false)
        private set

    fun hayCambios(fuente: String): Boolean = fuente != ultimaGuardada

    fun cargar(nuevo: ArchivoMoka) {
        archivo = nuevo
        // Un archivo del disco arranca limpio. Un ejemplo es una COPIA
        // nueva sin destino: su contenido cuenta como cambio sin guardar.
        ultimaGuardada = if (nuevo.referencia != null) nuevo.contenido else ""
        cierrePendiente = false
    }

    fun marcarGuardado(guardado: ArchivoMoka, contenido: String) {
        archivo = guardado
        ultimaGuardada = contenido
    }

    fun comenzarOperacion() {
        ocupado = true
    }

    fun terminarOperacion() {
        ocupado = false
    }

    // Devuelve true cuando la plataforma puede cerrar ya. Si no, deja
    // cierrePendiente para que ArchivoTaller muestre la confirmacion.
    fun pedirCierre(fuente: String): Boolean {
        if (!ocupado && !hayCambios(fuente)) {
            cierrePendiente = false
            return true
        }
        cierrePendiente = true
        return false
    }

    fun cancelarCierre() {
        cierrePendiente = false
    }

    fun confirmarCierre() {
        cierrePendiente = false
    }
}
