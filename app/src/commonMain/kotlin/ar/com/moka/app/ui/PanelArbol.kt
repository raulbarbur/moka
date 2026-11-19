// =======================================================================
//  PanelArbol.kt - El panel del analisis sintactico (sec. 5.3, panel 3)
// =======================================================================
//  Dos contenidos sobre el mismo fuente: el arbol de PARSEO (reglas del
//  parser con sus tokens como hojas, errores en rojo) y el AST (mucho
//  mas compacto: "Binaria(+)", "Variable(x)...").
//
//  Extra final: el AST tambien puede verse como GRAFICO con Canvas, con
//  zoom, pan y seleccion sincronizada con el editor. El arbol de parseo
//  queda solo como outline a proposito: incluye cada token, parentesis y
//  punto y coma, asi que un programa chico ya produce un diagrama enorme.
//
//  El AST puede no existir: si hubo errores de sintaxis, Artefacto.
//  Sintactico.ast es null (sec. 4.7) y la vista AST lo dice, en vez de
//  mostrar un arbol a medias.
//
//  Panel sin estado propio (sec. 5.1): recibe el Artefacto.Sintactico;
//  la vista elegida es estado de vista local.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.pipeline.Artefacto

@Composable
fun PanelArbol(
    sintactico: Artefacto.Sintactico?,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    // 5C: tamano de letra propio del panel (outline y nodos del grafico).
    tamanio: Int,
    modifier: Modifier = Modifier
) {
    if (sintactico == null) {
        PanelVacio("Todavia no corrio la fase sintactica.", modifier)
        return
    }

    var vistaAst by remember(sintactico) { mutableStateOf(false) }
    var vistaGrafica by remember(sintactico) { mutableStateOf(false) }

    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Primer nivel: QUE arbol se muestra.
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !vistaAst,
                onClick = { vistaAst = false },
                label = { Text("Arbol de parseo") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = vistaAst,
                onClick = { vistaAst = true },
                label = { Text("AST") },
                enabled = sintactico.ast != null
            )
        }

        val ast = sintactico.ast
        when {
            vistaAst && ast == null -> PanelVacio(
                "No hay AST: la fase sintactica termino con errores.",
                Modifier.fillMaxSize()
            )
            vistaAst -> {
                // Segundo nivel, solo para AST: COMO se muestra.
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !vistaGrafica,
                        onClick = { vistaGrafica = false },
                        label = { Text("Outline") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = vistaGrafica,
                        onClick = { vistaGrafica = true },
                        label = { Text("Grafico") }
                    )
                }
                Text("AST (raiz: Programa)", style = MaterialTheme.typography.labelLarge)
                if (vistaGrafica) {
                    ArbolGrafico(
                        raiz = vistaDeAst(ast!!),
                        seleccion = seleccion,
                        onSeleccionar = onSeleccionar,
                        tamanio = tamanio,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    OutlineArbol(
                        raices = listOf(vistaDeAst(ast!!)),
                        seleccion = seleccion,
                        onSeleccionar = onSeleccionar,
                        tamanio = tamanio,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            else -> {
                Text("Arbol de parseo (outline; el grafico queda para el AST)",
                    style = MaterialTheme.typography.labelLarge)
                OutlineArbol(
                    raices = listOf(vistaDeParseo(sintactico.arbolParseo)),
                    seleccion = seleccion,
                    onSeleccionar = onSeleccionar,
                    tamanio = tamanio,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
