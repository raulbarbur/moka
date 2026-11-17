// =======================================================================
//  OutlineArbol.kt — Outline expandible generico para arboles
// =======================================================================
//  Dibuja una lista de ElementoVista como outline con sangria, flechas
//  para expandir/colapsar y botones de expandir/colapsar todo. Lo usa el
//  panel sintactico con sus dos vistas (parseo y AST); nada aca sabe de
//  ANTLR ni del AST: solo de ElementoVista.
//
//  Expansion: cada nodo se identifica por su camino ("0/2/1"). Un arbol
//  chico (hasta 150 nodos) arranca todo expandido; uno grande, solo con
//  la raiz abierta. Tocar una fila alterna la expansion y, si el nodo
//  tiene posicion, la publica como seleccion compartida (el editor la
//  resalta, sec. 5.1).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.core.ast.Posicion

private val MONO_CHICO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

// Un arbol de hasta este tamaño arranca todo abierto; mas grande, solo
// la raiz (un programa mediano tiene miles de nodos de parseo).
private const val TOPE_TODO_ABIERTO = 150

private data class FilaVista(
    val clave: String,
    val texto: String,
    val nivel: Int,
    val esError: Boolean,
    val pos: Posicion?,
    val tieneHijos: Boolean,
    val expandida: Boolean
)

@Composable
fun OutlineArbol(
    raices: List<ElementoVista>,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    val todasLasClaves = remember(raices) { mutableListOf<String>().also { recolectarClaves(raices, "", it) } }
    var expandidos by remember(raices) {
        mutableStateOf(
            if (contarNodos(raices) <= TOPE_TODO_ABIERTO) todasLasClaves.toSet()
            else setOf("/0") // solo la raiz (las claves arrancan con "/")
        )
    }

    val filas = remember(raices, expandidos) {
        mutableListOf<FilaVista>().also { aplanar(raices, expandidos, 0, "", it) }
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { expandidos = todasLasClaves.toSet() }) { Text("Expandir todo") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { expandidos = emptySet() }) { Text("Colapsar todo") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filas, key = { it.clave }) { fila ->
                FilaOutline(
                    fila = fila,
                    seleccionada = fila.pos != null && fila.pos == seleccion,
                    onClick = {
                        if (fila.tieneHijos) {
                            expandidos = if (fila.expandida) expandidos - fila.clave else expandidos + fila.clave
                        }
                        if (fila.pos != null) onSeleccionar(fila.pos)
                    }
                )
            }
        }
    }
}

@Composable
private fun FilaOutline(fila: FilaVista, seleccionada: Boolean, onClick: () -> Unit) {
    val fondo = if (seleccionada) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth()
            .background(fondo)
            .clickable { onClick() }
            .horizontalScroll(rememberScrollState())
            .padding(start = (fila.nivel * 14).dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flecha (escapes Unicode: pegado seguro) o sangria si es hoja.
        Text(
            if (!fila.tieneHijos) " " else if (fila.expandida) "\u25BE" else "\u25B8",
            style = MONO_CHICO,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(16.dp)
        )
        Text(
            fila.texto,
            style = MONO_CHICO,
            color = if (fila.esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

// Aplana el arbol a filas visibles segun lo expandido.
private fun aplanar(
    nodos: List<ElementoVista>,
    expandidos: Set<String>,
    nivel: Int,
    prefijo: String,
    salida: MutableList<FilaVista>
) {
    nodos.forEachIndexed { indice, nodo ->
        val clave = "$prefijo/$indice"
        val tieneHijos = nodo.hijos.isNotEmpty()
        val expandida = clave in expandidos
        salida += FilaVista(clave, nodo.texto, nivel, nodo.esError, nodo.pos, tieneHijos, expandida)
        if (tieneHijos && expandida) aplanar(nodo.hijos, expandidos, nivel + 1, clave, salida)
    }
}

private fun recolectarClaves(nodos: List<ElementoVista>, prefijo: String, salida: MutableList<String>) {
    nodos.forEachIndexed { indice, nodo ->
        val clave = "$prefijo/$indice"
        if (nodo.hijos.isNotEmpty()) {
            salida += clave
            recolectarClaves(nodo.hijos, clave, salida)
        }
    }
}
