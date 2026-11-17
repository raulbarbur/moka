// =======================================================================
//  PanelArbol.kt — El panel del analisis sintactico (sec. 5.3, panel 3)
// =======================================================================
//  Dos vistas conmutables sobre el mismo fuente: el arbol de PARSEO
//  (reglas del parser con sus tokens como hojas, errores en rojo) y el
//  AST (mucho mas compacto: "Binaria(+)", "Variable(x)..."). Comparar
//  una con la otra es el recurso didactico del panel. Ambas usan el
//  mismo outline expandible.
//
//  El AST puede no existir: si hubo errores de sintaxis, Artefacto.
//  Sintactico.ast es null (sec. 4.7) y la vista AST lo dice, en vez de
//  mostrar un arbol a medias.
//
//  Panel sin estado propio (sec. 5.1): recibe el Artefacto.Sintactico;
//  la vista elegida y la expansion son estado de vista local.
//  La vista grafica con Canvas (zoom/pan) quedo como extra del final.
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
    modifier: Modifier = Modifier
) {
    if (sintactico == null) {
        PanelVacio("Todavia no corrio la fase sintactica.", modifier)
        return
    }

    var vistaAst by remember(sintactico) { mutableStateOf(false) }

    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
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
                Text("AST (raiz: Programa)", style = MaterialTheme.typography.labelLarge)
                OutlineArbol(
                    raices = listOf(vistaDeAst(ast!!)),
                    seleccion = seleccion,
                    onSeleccionar = onSeleccionar,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Text("Arbol de parseo", style = MaterialTheme.typography.labelLarge)
                OutlineArbol(
                    raices = listOf(vistaDeParseo(sintactico.arbolParseo)),
                    seleccion = seleccion,
                    onSeleccionar = onSeleccionar,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
