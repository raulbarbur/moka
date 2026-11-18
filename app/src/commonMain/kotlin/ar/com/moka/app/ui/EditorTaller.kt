// =======================================================================
//  EditorTaller.kt — El editor de codigo fuente (sec. 5.3, panel 1)
// =======================================================================
//  Version 7.8: suma el RESALTADO DE SINTAXIS por categoria de token
//  (palabras clave, literales, delimitadores, errores) usando los tokens
//  del analisis lexico en vivo. Capas de pintado, de abajo hacia arriba:
//    1. color de sintaxis por token
//    2. fondo de la seleccion compartida
//    3. subrayado de diagnosticos (gana sobre el color del token)
//
//  Todo con una VisualTransformation: el texto no cambia
//  (OffsetMapping.Identity), solo se le superponen estilos.
//
//  Detalle honesto: entre tecla y analisis hay 400 ms en que los tokens
//  son del texto ANTERIOR; los rangos fuera de rango se descartan y los
//  corridos pueden pintar un caracter corrido por un instante. Se
//  corrige solo cuando llega el analisis nuevo.
//
//  Pendiente con paso propio: abrir/guardar (7.9).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.lexico.CategoriaToken
import ar.com.moka.core.lexico.Token
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad

// Ambar para las advertencias: el tema no trae un "amarillo de warning"
// propio, y este se lee bien en tema claro y oscuro.
private val COLOR_ADVERTENCIA = Color(0xFFB8860B)

@Composable
fun EditorTaller(
    fuente: String,
    tokens: List<Token>?,
    diagnosticos: List<Diagnostico>,
    seleccion: Posicion?,
    onCambio: (String) -> Unit,
    tamanioFuente: Int,
    alCambiarTamanio: (Int) -> Unit,
    alDeshacer: () -> Unit,
    alRehacer: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Los colores de sintaxis vienen de ui/Tema.kt (paleta IntelliJ),
    // no del colorScheme general: en Darcula la palabra clave es naranja
    // aunque el color de accion sea azul.
    val coloresEditor = LocalColoresEditor.current
    val coloresSintaxis = remember(coloresEditor) {
        mapOf(
            CategoriaToken.PALABRA_CLAVE to coloresEditor.palabraClave,
            CategoriaToken.LITERAL to coloresEditor.literal,
            CategoriaToken.DELIMITADOR to coloresEditor.delimitador,
            CategoriaToken.ERROR to coloresEditor.error
        )
    }
    val colorError = coloresEditor.error
    val colorSeleccion = coloresEditor.seleccion
    // Se recalcula solo cuando cambia alguna de sus entradas.
    val transformacion = remember(fuente, tokens, diagnosticos, seleccion, coloresSintaxis) {
        PintadoEditor(fuente, tokens.orEmpty(), diagnosticos, seleccion, coloresSintaxis, colorError, colorSeleccion)
    }

    // ---- Buscar / reemplazar (v1: literal, distingue mayusculas) ----
    // El menu Editar abre y cierra la busqueda (estado compartido).
    val estadoBusqueda = LocalBusquedaEditor.current
    val buscando = estadoBusqueda.activa
    var consulta by remember { mutableStateOf("") }
    var reemplazo by remember { mutableStateOf("") }
    val coincidencias = if (consulta.isEmpty()) 0
    else Regex.fromLiteral(consulta).findAll(fuente).count()

    // ---- Numeros de linea y salto a la seleccion ----
    // El editor y su margen viven en UN solo contenedor con scroll: asi
    // los numeros acompañan el texto sin sincronizacion manual, y tocar
    // un error en Problemas desplaza todo a esa linea. Tocar cualquier
    // parte del panel enfoca el campo (FocusRequester).
    val desplazamiento = rememberScrollState()
    val foco = remember { FocusRequester() }
    val altoLinea = (tamanioFuente * 1.3).sp
    val altoLineaPx = with(LocalDensity.current) { altoLinea.toPx() }
    LaunchedEffect(seleccion) {
        if (seleccion != null) {
            desplazamiento.scrollTo(((seleccion.linea - 1) * altoLineaPx).toInt().coerceAtLeast(0))
        }
    }

    val estiloCodigo = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = tamanioFuente.sp,
        lineHeight = altoLinea,
        color = MaterialTheme.colorScheme.onSurface
    )
    val totalLineas = fuente.count { it == '\n' } + 1

    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
        Column {
            // Barra del editor: solo el titulo. Tamaño de letra, deshacer,
            // rehacer y buscar viven ahora en los menus de la barra superior.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "Codigo fuente",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            if (buscando) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    TextField(
                        value = consulta,
                        onValueChange = { consulta = it },
                        label = { Text("Buscar") },
                        singleLine = true,
                        modifier = Modifier.width(160.dp)
                    )
                    Text(
                        "$coincidencias",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    TextField(
                        value = reemplazo,
                        onValueChange = { reemplazo = it },
                        label = { Text("Reemplazar por") },
                        singleLine = true,
                        modifier = Modifier.width(160.dp)
                    )
                    TextButton(onClick = { estadoBusqueda.activa = false }) { Text("Cerrar") }
                    TextButton(
                        onClick = {
                            val i = fuente.indexOf(consulta)
                            if (consulta.isNotEmpty() && i >= 0) {
                                onCambio(fuente.replaceRange(i, i + consulta.length, reemplazo))
                            }
                        },
                        enabled = coincidencias > 0
                    ) { Text("Reemplazar") }
                    TextButton(
                        onClick = { if (consulta.isNotEmpty()) onCambio(fuente.replace(consulta, reemplazo)) },
                        enabled = coincidencias > 0
                    ) { Text("Todo") }
                }
            }
            // El alto del VIEWPORT se toma afuera del scroll: adentro el
            // maximo es infinito y no sirve para minHeight.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val altoPanel = this.maxHeight
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(desplazamiento)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { foco.requestFocus() }
                        .padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                ) {
                    // defaultMinSize: aunque el programa sea de una linea, la
                    // zona de toque/edicion ocupa todo el panel.
                    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = altoPanel)) {
                        Text(
                            (1..totalLineas).joinToString("\n"),
                            style = estiloCodigo.copy(color = coloresEditor.delimitador),
                            textAlign = TextAlign.End
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = fuente,
                            onValueChange = onCambio,
                            visualTransformation = transformacion,
                            textStyle = estiloCodigo,
                            modifier = Modifier.fillMaxWidth().focusRequester(foco)
                        )
                    }
                }
            }
        }
    }
}

// Pinta sintaxis + seleccion + subrayado de diagnosticos. Las posiciones
// son las del nucleo: linea 1-based, columna 0-based, fin inclusivo; aca
// se convierten a desplazamientos de caracter.
private class PintadoEditor(
    private val fuente: String,
    private val tokens: List<Token>,
    private val diagnosticos: List<Diagnostico>,
    private val seleccion: Posicion?,
    private val coloresSintaxis: Map<CategoriaToken, Color>,
    private val colorError: Color,
    private val colorSeleccion: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (tokens.isEmpty() && diagnosticos.isEmpty() && seleccion == null) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val inicios = iniciosDeLinea(fuente)
        val constructor = AnnotatedString.Builder(text)

        // 1. sintaxis: solo las categorias con color propio (operadores e
        //    identificadores quedan con el color normal del texto).
        for (token in tokens) {
            val color = coloresSintaxis[token.categoria] ?: continue
            val rango = rangoDe(inicios, fuente.length, token.pos) ?: continue
            constructor.addStyle(SpanStyle(color = color), rango.first, rango.second)
        }

        // 2. seleccion compartida (fondo).
        val sel = seleccion
        if (sel != null) {
            val rango = rangoDe(inicios, fuente.length, sel)
            if (rango != null) {
                constructor.addStyle(SpanStyle(background = colorSeleccion), rango.first, rango.second)
            }
        }

        // 3. diagnosticos (subrayado; su color gana sobre el del token).
        for (d in diagnosticos) {
            val rango = rangoDe(inicios, fuente.length, d.pos) ?: continue
            constructor.addStyle(
                SpanStyle(
                    color = if (d.severidad == Severidad.ERROR) colorError else COLOR_ADVERTENCIA,
                    textDecoration = TextDecoration.Underline
                ),
                rango.first, rango.second
            )
        }
        return TransformedText(constructor.toAnnotatedString(), OffsetMapping.Identity)
    }

    // Posicion del nucleo -> par (desde, hasta) de desplazamientos, o
    // null si quedo vacia o fuera de rango (el texto cambio despues).
    private fun rangoDe(inicios: IntArray, largo: Int, pos: Posicion): Pair<Int, Int>? {
        val desde = desplazamiento(inicios, pos.linea, pos.columna).coerceIn(0, largo)
        val hasta = (desplazamiento(inicios, pos.finLinea, pos.finColumna) + 1) // fin inclusivo
            .coerceIn(desde, largo)
        return if (hasta > desde) desde to hasta else null
    }

    // Desplazamiento de inicio de cada linea (el indice 0 es la linea 1).
    private fun iniciosDeLinea(fuente: String): IntArray {
        val inicios = mutableListOf(0)
        for (i in fuente.indices) {
            if (fuente[i] == '\n') inicios.add(i + 1)
        }
        return inicios.toIntArray()
    }

    // Linea 1-based + columna 0-based -> desplazamiento en el texto.
    // Una linea fuera de rango cae lejos, donde coerceIn lo descarta.
    private fun desplazamiento(inicios: IntArray, linea: Int, columna: Int): Int {
        if (linea < 1 || linea > inicios.size) return Int.MAX_VALUE / 2
        return inicios[linea - 1] + columna
    }
}
