// ============================================================================
// ProgramaTac.kt - El programa completo como lista de cuadruplas
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/tac/ProgramaTac.kt
// (documento de diseno, secciones 4.5 y 4.7)
//
// Es el artefacto que entrega la Fase 4 (Artefacto.Tac de la sec. 4.7):
// la lista plana de cuadruplas mas las dos tablas que la MaquinaTac
// necesita para arrancar (sec. 4.6):
//
//   funciones:  nombre -> indice de su inicio_func  ("factorial" -> 0)
//   etiquetas:  nombre -> indice de la cuadrupla etiqueta ("L1" -> 4)
//
// Ademas `estaticos` guarda el nombre de ALMACEN de cada campo estatico:
// el simbolo de `total` en `clase Contador { estatico entero total; }`
// tiene idUnico "total", pero vive en el almacen global como
// "Contador.total" (sec. 4.5.3). Aca la clave es el idUnico y el valor
// el nombre completo; el render y la MaquinaTac lo consultan igual.
//
// RENDER: textoDe() dibuja un operando, comoLineas() da el listado
// canonico ("3: (*, b, 2, t1)", una cuadrupla por renglon, sin relleno)
// y comoTexto() el listado alineado por columnas que muestra el panel
// TAC de la GUI, con un renglon en blanco entre funciones.
// ============================================================================

package ar.com.moka.core.tac

class ProgramaTac(
    val cuadruplas: List<Cuadrupla>,
    val estaticos: Map<String, String>
) {

    // Tabla nombre de funcion -> indice de su inicio_func (sec. 4.6).
    val funciones: Map<String, Int> = cuadruplas
        .filter { it.op == Operacion.INICIO_FUNC }
        .associate { (it.arg1 as Operando.Nombre).texto to it.indice }

    // Tabla etiqueta -> indice (sec. 4.6). Las etiquetas son unicas en
    // todo el programa, asi que una sola tabla alcanza.
    val etiquetas: Map<String, Int> = cuadruplas
        .filter { it.op == Operacion.ETIQUETA }
        .associate { (it.resultado as Operando.Etiqueta).nombre to it.indice }

    // Como se dibuja un operando en el listado. null es el hueco vacio.
    fun textoDe(o: Operando?): String = when (o) {
        null -> "_"
        is Operando.Constante -> textoDeValor(o.valor)
        is Operando.Temporal -> "t${o.numero}"
        is Operando.Variable -> estaticos[o.simbolo.idUnico] ?: o.simbolo.idUnico
        is Operando.Etiqueta -> o.nombre
        is Operando.Nombre -> o.texto
    }

    // Las constantes se dibujan como en el fuente: 4, 2.0, "hola", 'a',
    // verdadero, nulo. Los escapes se re-arman para que el listado se
    // pueda leer en una sola linea.
    private fun textoDeValor(v: Valor): String = when (v) {
        is Valor.VEntero -> v.v.toString()
        is Valor.VFlotante -> v.v.toString()
        is Valor.VBooleano -> if (v.v) "verdadero" else "falso"
        is Valor.VCaracter -> "'" + escaparCaracter(v.v) + "'"
        is Valor.VCadena -> "\"" + escaparCadena(v.v) + "\""
        Valor.VNulo -> "nulo"
        // Los valores referencia nunca son constantes de compilacion;
        // aparecen recien en ejecucion (Fase 5). Se dibujan por las dudas.
        is Valor.VArreglo -> "<arreglo ${v.tipoElemento.descripcion}[]>"
        is Valor.VObjeto -> "<objeto ${v.clase.nombre}>"
    }

    private fun escaparCaracter(c: Char): String = when (c) {
        '\n' -> "\\n"
        '\t' -> "\\t"
        '\r' -> "\\r"
        '\\' -> "\\\\"
        '\'' -> "\\'"
        else -> c.toString()
    }

    private fun escaparCadena(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(c)
            }
        }
    }

    // Listado canonico: una cuadrupla por renglon, campos separados por
    // ", " y sin relleno de columnas. Es lo que comparan los tests.
    fun comoLineas(): List<String> = cuadruplas.map { c ->
        "${c.indice}: (${c.op.simbolo}, ${textoDe(c.arg1)}, ${textoDe(c.arg2)}, ${textoDe(c.resultado)})"
    }

    // Listado alineado para la GUI (y los golden files de jvmTest): cada
    // columna ocupa el ancho de su contenido mas largo y queda un renglon
    // en blanco despues de cada fin_func, como en los ejemplos de la
    // seccion 4.5.4.
    fun comoTexto(): String {
        if (cuadruplas.isEmpty()) return ""
        val anchoIndice = cuadruplas.last().indice.toString().length
        val ops = cuadruplas.map { it.op.simbolo }
        val a1 = cuadruplas.map { textoDe(it.arg1) }
        val a2 = cuadruplas.map { textoDe(it.arg2) }
        val res = cuadruplas.map { textoDe(it.resultado) }
        val anchoOp = ops.maxOf { it.length }
        val anchoA1 = a1.maxOf { it.length }
        val anchoA2 = a2.maxOf { it.length }
        val sb = StringBuilder()
        for (i in cuadruplas.indices) {
            if (i > 0 && cuadruplas[i - 1].op == Operacion.FIN_FUNC) sb.append('\n')
            sb.append(cuadruplas[i].indice.toString().padStart(anchoIndice))
            sb.append(": (")
            sb.append((ops[i] + ",").padEnd(anchoOp + 2))
            sb.append((a1[i] + ",").padEnd(anchoA1 + 2))
            sb.append((a2[i] + ",").padEnd(anchoA2 + 2))
            sb.append(res[i]).append(")\n")
        }
        return sb.toString()
    }
}
