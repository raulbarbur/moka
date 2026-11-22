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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

// El ambar de las advertencias viene del tema (ColoresEditorMoka
// .advertencia): el ColorScheme no trae un "amarillo de warning" propio.

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
        PintadoEditor(fuente, tokens.orEmpty(), diagnosticos, seleccion, coloresSintaxis, colorError, coloresEditor.advertencia, colorSeleccion)
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
    // Disposicion real del texto (con saltos de linea visuales). Sirve para
    // dos cosas: llevar el scroll a la linea correcta aunque haya lineas
    // largas partidas en dos, y alinear los numeros de linea con ellas.
    var disposicion by remember { mutableStateOf<TextLayoutResult?>(null) }
    val salto = LocalSaltoEditor.current
    // La clave incluye 'salto.version': tocar OTRA VEZ el mismo error
    // (misma posicion) tambien vuelve a desplazar, aunque el usuario haya
    // movido el scroll a mano entre toque y toque.
    LaunchedEffect(seleccion, salto.version) {
        if (seleccion != null) {
            val dis = disposicion
            val destino = if (dis != null && dis.layoutInput.text.length == fuente.length && dis.lineCount > 0) {
                val inicio = inicioDeLinea(fuente, seleccion.linea)
                dis.getLineTop(dis.getLineForOffset(inicio.coerceIn(0, fuente.length)))
            } else {
                (seleccion.linea - 1) * altoLineaPx
            }
            // Dos lineas de aire arriba para ver el contexto del error.
            desplazamiento.scrollTo((destino - 2 * altoLineaPx).toInt().coerceAtLeast(0))
        }
    }

    // ---- Texto con cursor + autocompletado ----
    // El campo trabaja con TextFieldValue (necesitamos el cursor); la
    // fuente "oficial" sigue siendo la del controlador. Si cambia desde
    // afuera (deshacer, abrir archivo, reemplazar) se adopta; los textos
    // que enviamos nosotros y todavia no volvieron no cuentan como cambio
    // externo, para no pisar lo que se esta tipeando.
    var valor by remember { mutableStateOf(TextFieldValue(fuente)) }
    val enviados = remember { ArrayDeque<String>() }
    if (valor.text == fuente) {
        enviados.clear()
    } else if (fuente !in enviados) {
        valor = TextFieldValue(fuente, TextRange(fuente.length))
    }
    fun cambiar(nuevo: TextFieldValue) {
        valor = nuevo
        if (nuevo.text != fuente) {
            enviados.addLast(nuevo.text)
            while (enviados.size > 8) enviados.removeFirst()
            onCambio(nuevo.text)
        }
    }
    val opciones = LocalOpcionesEditor.current
    var descartado by remember { mutableStateOf<String?>(null) }
    val claveActual = valor.text + "|" + valor.selection.start
    val prefijo = if (opciones.autocompletado && valor.selection.collapsed && descartado != claveActual) {
        Autocompletado.prefijoEn(valor.text, valor.selection.start)
    } else null
    val sugerencias = if (prefijo != null) Autocompletado.sugerir(valor.text, valor.selection.start) else emptyList()
    fun aceptar(palabra: String) {
        val p = prefijo ?: return
        val (texto, cursor) = Autocompletado.aceptar(valor.text, p, palabra)
        cambiar(TextFieldValue(texto, TextRange(cursor)))
        foco.requestFocus()
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
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
                            numerosDeLinea(fuente, disposicion, totalLineas),
                            style = estiloCodigo.copy(color = coloresEditor.delimitador),
                            textAlign = TextAlign.End
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = valor,
                            onValueChange = ::cambiar,
                            visualTransformation = transformacion,
                            textStyle = estiloCodigo,
                            onTextLayout = { disposicion = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(foco)
                                .onPreviewKeyEvent { evento ->
                                    // Escritorio: Tab acepta la primera sugerencia, Esc la oculta.
                                    if (evento.type != KeyEventType.KeyDown || sugerencias.isEmpty()) false
                                    else when (evento.key) {
                                        Key.Tab -> { aceptar(sugerencias.first()); true }
                                        Key.Escape -> { descartado = claveActual; true }
                                        else -> false
                                    }
                                }
                        )
                    }
                }
            }
            // Tira de sugerencias: va debajo del editor y no tapa el texto;
            // un toque acepta la palabra (sirve igual con teclado tactil).
            if (sugerencias.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    for (palabra in sugerencias) {
                        TextButton(onClick = { aceptar(palabra) }) { Text(palabra) }
                    }
                }
            }
        }
    }
}

// Desplazamiento del inicio de la linea 'linea' (1-based) dentro de la fuente.
private fun inicioDeLinea(fuente: String, linea: Int): Int {
    var restantes = linea - 1
    var i = 0
    while (restantes > 0) {
        val salto = fuente.indexOf('\n', i)
        if (salto < 0) return fuente.length
        i = salto + 1
        restantes--
    }
    return i
}

// Columna de numeros: uno por linea del programa. Si una linea larga se
// parte en varias lineas visuales, el numero va en la primera y las demas
// quedan en blanco, asi los numeros siguen alineados con el texto.
private fun numerosDeLinea(fuente: String, dis: TextLayoutResult?, totalLineas: Int): String {
    if (dis == null || dis.layoutInput.text.length != fuente.length || dis.lineCount <= totalLineas) {
        return (1..totalLineas).joinToString("\n")
    }
    var numero = 0
    return (0 until dis.lineCount).joinToString("\n") { v ->
        val inicio = dis.getLineStart(v)
        if (inicio == 0 || fuente[inicio - 1] == '\n') { numero++; numero.toString() } else ""
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
    private val colorAdvertencia: Color,
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
                    color = if (d.severidad == Severidad.ERROR) colorError else colorAdvertencia,
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
