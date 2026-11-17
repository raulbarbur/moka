// =======================================================================
//  PanelSemantico.kt — El panel del analisis semantico (sec. 5.3, panel 4)
// =======================================================================
//  Tres sub-pestañas:
//    Simbolos:  el arbol de ambitos (global -> clases/funciones ->
//               bloques) con los simbolos de cada uno como filas hijas
//               (nombre, tipo, categoria, modificadores, linea, idUnico:
//               ahi se ve el sombreado, ej. x y x#2). Reusa el outline
//               generico de 7.5.
//    Chequeos:  los Chequeo registrados por el tipificador, con tick o
//               cruz y filtro "solo errores".
//    Errores:   los diagnosticos SEM- de la fase (las sugerencias tipo
//               "quisiste decir ..." ya vienen dentro del mensaje).
//
//  Los diagnosticos NO viajan en Artefacto.Semantico (ese lleva raiz,
//  tipos y chequeos): llegan aparte, del slot de la fase.
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.pipeline.Artefacto
import ar.com.moka.core.semantico.Ambito
import ar.com.moka.core.semantico.Categoria
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.SimboloClase
import ar.com.moka.core.semantico.SimboloFuncion
import ar.com.moka.core.semantico.SimboloVariable

private val MONO_CHICO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable
fun PanelSemantico(
    semantico: Artefacto.Semantico?,
    diagnosticos: List<Diagnostico>,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    if (semantico == null) {
        PanelVacio("Todavia no corrio la fase semantica.", modifier)
        return
    }

    var pestana by remember(semantico) { mutableIntStateOf(0) }

    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = pestana == 0, onClick = { pestana = 0 }, label = { Text("Simbolos") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = pestana == 1, onClick = { pestana = 1 }, label = { Text("Chequeos (${semantico.chequeos.size})") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = pestana == 2, onClick = { pestana = 2 }, label = { Text("Errores (${diagnosticos.size})") })
        }

        when (pestana) {
            0 -> SubPanelSimbolos(semantico.raiz, seleccion, onSeleccionar, Modifier.fillMaxSize())
            1 -> SubPanelChequeos(semantico, seleccion, onSeleccionar, Modifier.fillMaxSize())
            else -> SubPanelErrores(diagnosticos, onSeleccionar, Modifier.fillMaxSize())
        }
    }
}

// ------------------------------ Simbolos ------------------------------

@Composable
private fun SubPanelSimbolos(
    raiz: Ambito,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    Text("Tabla de simbolos (arbol de ambitos)", style = MaterialTheme.typography.labelLarge)
    OutlineArbol(
        raices = listOf(vistaDeAmbito(raiz)),
        seleccion = seleccion,
        onSeleccionar = onSeleccionar,
        modifier = modifier
    )
}

// Un ambito y todo lo declarado en el: primero sus simbolos, despues
// sus ambitos hijos.
private fun vistaDeAmbito(ambito: Ambito): ElementoVista {
    val simbolos = buildList {
        ambito.clases.values.forEach { add(vistaDeClase(it)) }
        ambito.funciones.values.forEach { add(vistaDeFuncion(it)) }
        ambito.simbolos.values.forEach { add(vistaDeVariable(it)) }
    }
    return ElementoVista(
        texto = "ambito: ${ambito.nombre}",
        pos = ambito.pos,
        hijos = simbolos + ambito.hijos.map(::vistaDeAmbito)
    )
}

private fun vistaDeVariable(v: SimboloVariable): ElementoVista {
    val modificadores = buildList {
        add(etiquetaDe(v.categoria))
        if (v.esConstante) add("constante")
        if (v.esEstatico) add("estatico")
    }
    return ElementoVista(
        texto = "${v.nombre} : ${v.tipo.descripcion}  [${modificadores.joinToString(", ")}]  lin ${v.pos.linea}  (${v.idUnico})",
        pos = v.pos
    )
}

private fun vistaDeFuncion(f: SimboloFuncion): ElementoVista {
    val params = f.parametros.joinToString(", ") { it.descripcion }
    val marcas = buildList {
        if (f.esEstatico) add("estatico")
        if (f.esBuiltin) add("builtin")
    }
    val sufijo = if (marcas.isNotEmpty()) "  [${marcas.joinToString(", ")}]" else ""
    return ElementoVista(
        texto = "${f.nombre}($params) : ${f.tipo.descripcion}  [funcion]$sufijo  lin ${f.pos.linea}",
        pos = f.pos
    )
}

private fun vistaDeClase(c: SimboloClase): ElementoVista =
    ElementoVista(texto = "${c.nombre}  [clase]  lin ${c.pos.linea}", pos = c.pos)

private fun etiquetaDe(categoria: Categoria): String = when (categoria) {
    Categoria.GLOBAL -> "global"
    Categoria.LOCAL -> "local"
    Categoria.PARAMETRO -> "parametro"
    Categoria.CAMPO -> "campo"
}

// ------------------------------ Chequeos ------------------------------

@Composable
private fun SubPanelChequeos(
    semantico: Artefacto.Semantico,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    var soloErrores by remember(semantico) { mutableStateOf(false) }
    val visibles = remember(semantico, soloErrores) {
        if (soloErrores) semantico.chequeos.filter { !it.ok } else semantico.chequeos
    }

    Column(modifier) {
        FilterChip(
            selected = soloErrores,
            onClick = { soloErrores = !soloErrores },
            label = { Text("Solo errores") },
            modifier = Modifier.padding(vertical = 2.dp)
        )
        if (visibles.isEmpty()) {
            Text(
                if (soloErrores) "Ningun chequeo fallo." else "No se registraron chequeos.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(visibles) { chequeo ->
                val color = if (chequeo.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { onSeleccionar(chequeo.pos) }
                        .padding(vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (chequeo.ok) "\u2713" else "\u2717",
                            style = MONO_CHICO,
                            color = color,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(chequeo.texto, style = MONO_CHICO, color = color, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "(${chequeo.pos.linea}:${chequeo.pos.columna})",
                            style = MONO_CHICO,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        buildString {
                            append(chequeo.regla)
                            append("  |  esperado: ")
                            append(chequeo.tipoEsperado?.descripcion ?: "-")
                            append(", obtenido: ")
                            append(chequeo.tipoObtenido?.descripcion ?: "-")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------- Errores -------------------------------

@Composable
private fun SubPanelErrores(
    diagnosticos: List<Diagnostico>,
    onSeleccionar: (Posicion) -> Unit,
    modifier: Modifier = Modifier
) {
    if (diagnosticos.isEmpty()) {
        PanelVacio("Sin errores semanticos.", modifier)
        return
    }
    LazyColumn(modifier) {
        items(diagnosticos) { diagnostico ->
            FilaDiagnostico(diagnostico, onSeleccionar)
        }
    }
}
