// =======================================================================
//  PantallaTutoriales.kt - Lecciones dentro de la app
// =======================================================================
//  Pantalla completa que reemplaza al taller mientras esta abierta.
//  Ancha: lista de lecciones a la izquierda y la leccion a la derecha.
//  Angosta (celular vertical): primero la lista; al tocar una se ve la
//  leccion con un boton para volver a la lista.
//  Cada leccion permite cargar su ejemplo en el editor (con el aviso de
//  cambios sin guardar de siempre) y ver un ejercicio con su solucion.
//  El contenido sale de tutoriales/TutorialesMoka.kt.
//  (Simplificacion pedida por Raul: antes habia un boton "Probar ahora"
//  que ademas lo ejecutaba; con un solo boton se entiende mejor.)
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.com.moka.app.tutoriales.LECCIONES
import ar.com.moka.app.tutoriales.Leccion

// Ancho a partir del cual lista y leccion se ven lado a lado.
private val ANCHO_DOS_COLUMNAS = 700.dp

@Composable
fun PantallaTutoriales(
    // Ids de las lecciones ya abiertas (se marcan con una tilde).
    vistas: Set<String>,
    // -1 = lista (solo en pantalla angosta); 0.. = leccion abierta.
    indice: Int,
    alCambiarIndice: (Int) -> Unit,
    alMarcarVista: (String) -> Unit,
    alCargar: (String, String) -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tutoriales", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = alCerrar) { Text("Volver al editor") }
            }
            HorizontalDivider()
            // 7.13: pinza con dos dedos tambien aca. La pantalla no tiene un
            // tamano de letra propio, asi que se escala el "fontScale" de
            // todo su contenido (en porcentaje: 100 = normal).
            var escala by remember { mutableFloatStateOf(1f) }
            val densidad = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(densidad.density, densidad.fontScale * escala)
            ) {
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .zoomConPinza((escala * 100).roundToInt()) { nuevo ->
                        escala = (nuevo / 100f).coerceIn(0.7f, 2.5f)
                    }
            ) {
                if (maxWidth >= ANCHO_DOS_COLUMNAS) {
                    val actual = indice.coerceAtLeast(0)
                    Row(Modifier.fillMaxSize()) {
                        ListaLecciones(vistas, actual, alCambiarIndice, Modifier.width(260.dp).fillMaxHeight())
                        DetalleLeccion(
                            LECCIONES[actual], actual, mostrarLista = false,
                            alCambiarIndice, alMarcarVista, alCargar,
                            Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                } else if (indice < 0) {
                    ListaLecciones(vistas, -1, alCambiarIndice, Modifier.fillMaxSize())
                } else {
                    DetalleLeccion(
                        LECCIONES[indice], indice, mostrarLista = true,
                        alCambiarIndice, alMarcarVista, alCargar,
                        Modifier.fillMaxSize()
                    )
                }
            }
            } // CompositionLocalProvider
        }
    }
}

@Composable
private fun ListaLecciones(
    vistas: Set<String>,
    seleccionada: Int,
    alElegir: (Int) -> Unit,
    modifier: Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        LECCIONES.forEachIndexed { i, leccion ->
            val fondo = if (i == seleccionada) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.background
            Row(
                Modifier.fillMaxWidth().background(fondo)
                    .clickable { alElegir(i) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(leccion.titulo, style = MaterialTheme.typography.bodyLarge)
                if (leccion.id in vistas) Text("\u2713", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun DetalleLeccion(
    leccion: Leccion,
    indice: Int,
    mostrarLista: Boolean,
    alCambiarIndice: (Int) -> Unit,
    alMarcarVista: (String) -> Unit,
    alCargar: (String, String) -> Unit,
    modifier: Modifier
) {
    // La solucion se oculta de nuevo al cambiar de leccion.
    var verSolucion by rememberSaveable(leccion.id) { mutableStateOf(false) }
    LaunchedEffect(leccion.id) { alMarcarVista(leccion.id) }
    val nombreArchivo = "tutorial-${leccion.id}.moka"

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (mostrarLista) {
            TextButton(onClick = { alCambiarIndice(-1) }) { Text("\u2190 Todas las lecciones") }
        }
        Text(leccion.titulo, style = MaterialTheme.typography.headlineSmall)
        for (parrafo in leccion.parrafos) {
            Text(parrafo, style = MaterialTheme.typography.bodyLarge)
        }

        Text("Ejemplo", style = MaterialTheme.typography.titleMedium)
        BloqueCodigo(leccion.codigo)
        if (leccion.salida != null) {
            Text("Salida", style = MaterialTheme.typography.titleSmall)
            BloqueCodigo(leccion.salida)
        }
        // Un solo boton: carga el ejemplo en el editor y Raul lo ejecuta
        // con Ejecutar cuando quiera (simplificacion pedida por el).
        Button(onClick = { alCargar(nombreArchivo, leccion.codigo) }) { Text("Cargar en el editor") }

        HorizontalDivider()
        Text("Ejercicio", style = MaterialTheme.typography.titleMedium)
        Text(leccion.ejercicio, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { verSolucion = !verSolucion }) {
            Text(if (verSolucion) "Ocultar solucion" else "Ver solucion")
        }
        if (verSolucion) {
            BloqueCodigo(leccion.solucion)
            TextButton(onClick = { alCargar("solucion-${leccion.id}.moka", leccion.solucion) }) {
                Text("Cargar la solucion")
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { alCambiarIndice(indice - 1) }, enabled = indice > 0) { Text("Anterior") }
            TextButton(onClick = { alCambiarIndice(indice + 1) }, enabled = indice < LECCIONES.size - 1) { Text("Siguiente") }
        }
    }
}

// Codigo o salida en letra de ancho fijo, seleccionable y con scroll
// horizontal si una linea es larga.
@Composable
private fun BloqueCodigo(texto: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Text(
                texto.trimEnd(),
                fontFamily = FuenteMono,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
            )
        }
    }
}

