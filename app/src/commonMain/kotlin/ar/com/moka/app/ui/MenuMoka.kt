// =======================================================================
//  MenuMoka.kt - Barra de menus agrupados (Archivo / Editar / Ver / Ayuda)
// =======================================================================
//  Un unico modelo de datos (GrupoMenu -> ItemMenu) alimenta la barra, tanto
//  en escritorio como en el celular: cada grupo es un boton de texto que
//  abre un DropdownMenu. Asi no hay dos logicas distintas por tamaño de
//  pantalla. La barra se desplaza en horizontal si no entra.
//
//  Tambien vive aca el estado compartido de "Buscar y reemplazar": el menu
//  Editar lo abre y el editor lo muestra, sin pasar parametros por todo el
//  arbol de paneles (se reparte con un CompositionLocal).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Una entrada del menu. 'marcado' dibuja una tilde delante (para las
// opciones excluyentes, como el tema). 'atajo' es solo un texto de ayuda.
sealed interface EntradaMenu

class ItemMenu(
    val texto: String,
    val atajo: String? = null,
    val habilitado: Boolean = true,
    val marcado: Boolean = false,
    // false para acciones que se repiten varias veces seguidas (A+/A-):
    // el menu queda abierto y se puede volver a tocar el mismo item.
    val cierraAlAccionar: Boolean = true,
    val accion: () -> Unit
) : EntradaMenu

// Un titulo gris sin accion, para separar secciones dentro de un menu.
class RotuloMenu(val texto: String) : EntradaMenu

data object SeparadorMenu : EntradaMenu

class GrupoMenu(val titulo: String, val entradas: List<EntradaMenu>)

@Composable
fun BarraMenus(grupos: List<GrupoMenu>, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState())) {
        for (grupo in grupos) {
            var abierto by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { abierto = true }) { Text(grupo.titulo) }
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    for (entrada in grupo.entradas) {
                        when (entrada) {
                            is ItemMenu -> DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (entrada.marcado) "\u2713 " else "") + entrada.texto +
                                                (entrada.atajo?.let { "    $it" } ?: "")
                                    )
                                },
                                enabled = entrada.habilitado,
                                onClick = {
                                    if (entrada.cierraAlAccionar) abierto = false
                                    entrada.accion()
                                }
                            )
                            is RotuloMenu -> DropdownMenuItem(
                                text = { Text(entrada.texto) },
                                enabled = false,
                                onClick = {}
                            )
                            SeparadorMenu -> HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

// Estado de la barra de busqueda del editor, compartido con el menu Editar.
class EstadoBusqueda {
    var activa by mutableStateOf(false)
}

val LocalBusquedaEditor = compositionLocalOf { EstadoBusqueda() }

// Opciones del editor que se cambian desde los menus.
class OpcionesEditor {
    var autocompletado by mutableStateOf(true)
}

val LocalOpcionesEditor = compositionLocalOf { OpcionesEditor() }

// Contador de "pedidos de salto": cada vez que se toca un error, token o
// nodo para seleccionarlo, sube en uno y el editor vuelve a desplazarse
// aunque la posicion elegida sea la misma de antes.
class PedidoSalto {
    var version by mutableStateOf(0)
}

val LocalSaltoEditor = compositionLocalOf { PedidoSalto() }
