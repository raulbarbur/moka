// =======================================================================
//  PanelTokens.kt — El panel del analisis lexico (sec. 5.3, panel 2)
// =======================================================================
//  Tabla con una fila por token: #, lexema, tipo (SI, ID, ENTERO_LIT...),
//  categoria, linea:columna y, con el conmutador "Mostrar regla", la
//  expresion de la sec. 3.2 que lo reconocio. Filtro por categoria y
//  busqueda por lexema o tipo. Los tokens de error van en rojo. Tocar
//  una fila selecciona su posicion: el editor resalta el lexema.
//
//  Panel sin estado propio (sec. 5.1): recibe el Artefacto.Lexico y ya;
//  los filtros son estado de vista local (remember), no del compilador.
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import ar.com.moka.core.lexico.CategoriaToken
import ar.com.moka.core.lexico.Token
import ar.com.moka.core.pipeline.Artefacto

private val MONO_CHICO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable
fun PanelTokens(
    lexico: Artefacto.Lexico?,
    seleccion: Posicion?,
    onSeleccionar: (Posicion) -> Unit,
    // 5C: tamano de letra propio del panel.
    tamanio: Int,
    modifier: Modifier = Modifier
) {
    if (lexico == null) {
        PanelVacio("Todavia no corrio la fase lexica.", modifier)
        return
    }

    var busqueda by remember { mutableStateOf("") }
    var categoriaFiltro by remember { mutableStateOf<CategoriaToken?>(null) }
    var mostrarRegla by remember { mutableStateOf(false) }

    val visibles = remember(lexico, busqueda, categoriaFiltro) {
        lexico.tokens.filter { t ->
            (categoriaFiltro == null || t.categoria == categoriaFiltro) &&
                    (
                            busqueda.isBlank() ||
                                    t.lexema.contains(busqueda, ignoreCase = true) ||
                                    t.tipo.contains(busqueda.trim(), ignoreCase = true)
                            )
        }
    }

    // La tabla usa esta fuente mono; el tamano es el del panel.
    val mono = MONO_CHICO.copy(fontSize = tamanio.sp)

    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            "Tokens (${visibles.size} de ${lexico.tokens.size})",
            style = MaterialTheme.typography.labelLarge
        )

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            singleLine = true,
            label = { Text("Buscar por lexema o tipo") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        // Filtro por categoria.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = categoriaFiltro == null,
                onClick = { categoriaFiltro = null },
                label = { Text("Todas") }
            )
            for (categoria in CategoriaToken.entries) {
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = categoriaFiltro == categoria,
                    onClick = { categoriaFiltro = categoria },
                    label = { Text(etiquetaDe(categoria)) }
                )
            }
        }

        // Conmutador de la columna "regla" (sec. 3.2).
        FilterChip(
            selected = mostrarRegla,
            onClick = { mostrarRegla = !mostrarRegla },
            label = { Text("Mostrar regla") },
            modifier = Modifier.padding(vertical = 2.dp)
        )

        // Cabecera de la tabla.
        FilaToken(
            nro = "#", lexema = "Lexema", tipo = "Token",
            categoria = "Categoria", pos = "Lin:Col",
            regla = if (mostrarRegla) "Regla" else null,
            esCabecera = true, esError = false, seleccionada = false,
            onClick = null,
            estilo = mono
        )

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(visibles) { indice, token ->
                val seleccionada = seleccion != null && token.pos == seleccion
                FilaToken(
                    nro = "${indice + 1}",
                    lexema = "\"${token.lexema}\"",
                    tipo = token.tipo,
                    categoria = etiquetaDe(token.categoria),
                    pos = "${token.pos.linea}:${token.pos.columna}",
                    regla = if (mostrarRegla) reglaDe(token.tipo) else null,
                    esCabecera = false,
                    esError = token.categoria == CategoriaToken.ERROR,
                    seleccionada = seleccionada,
                    onClick = { onSeleccionar(token.pos) },
                    estilo = mono
                )
            }
        }
    }
}

// Una fila de la tabla (la cabecera es la misma fila con otro estilo).
@Composable
private fun FilaToken(
    nro: String,
    lexema: String,
    tipo: String,
    categoria: String,
    pos: String,
    regla: String?,
    esCabecera: Boolean,
    esError: Boolean,
    seleccionada: Boolean,
    onClick: (() -> Unit)?,
    estilo: TextStyle
) {
    val color = when {
        esCabecera -> MaterialTheme.colorScheme.outline
        esError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val fondo = if (seleccionada) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    var fila = Modifier.fillMaxWidth().background(fondo)
    if (onClick != null) fila = fila.clickable { onClick() }
    Row(fila.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(nro, modifier = Modifier.width(36.dp), style = estilo, color = color)
        Text(lexema, modifier = Modifier.weight(1.2f), style = estilo, color = color, maxLines = 1)
        Text(tipo, modifier = Modifier.weight(1f), style = estilo, color = color, maxLines = 1)
        Text(categoria, modifier = Modifier.width(96.dp), style = estilo, color = color, maxLines = 1)
        Text(pos, modifier = Modifier.width(52.dp), style = estilo, color = color)
        if (regla != null) {
            Text(regla, modifier = Modifier.weight(1.2f), style = estilo, color = color, maxLines = 1)
        }
    }
}

// Nombre visible de cada categoria (sin tildes, convencion del proyecto).
fun etiquetaDe(categoria: CategoriaToken): String = when (categoria) {
    CategoriaToken.PALABRA_CLAVE -> "Palabra clave"
    CategoriaToken.OPERADOR -> "Operador"
    CategoriaToken.LITERAL -> "Literal"
    CategoriaToken.IDENTIFICADOR -> "Identificador"
    CategoriaToken.DELIMITADOR -> "Delimitador"
    CategoriaToken.ERROR -> "Error"
}
