// ============================================================================
//  MaquinaTac.kt - Maquina de ejecucion de cuadruplas (sec. 4.6)
// ============================================================================
//  Ejecuta un ProgramaTac cuadrupla por cuadrupla y va emitiendo un Flow
//  de EventoEjecucion (salida, pedidos de entrada, pasos, errores, fin).
//
//  Modelo de memoria (diagrama de la sec. 4.6):
//
//    pila de marcos   cada llamada apila un Marco con sus variables (por
//                     idUnico: el sombreado sale gratis) y sus temporales
//    globales         un solo mapa para las variables globales y los
//                     campos estaticos (con clave "Clase.campo", la misma
//                     que el generador puso en ProgramaTac.estaticos)
//    heap             objetos (VObjeto) y arreglos (VArreglo) con un id
//                     creciente; las variables guardan referencias, por
//                     eso pasar un arreglo a una funcion comparte la
//                     mutacion
//    cola de args     los `param` previos a un `llamar` dejan los
//                     argumentos aca; `llamar` los liga por orden con la
//                     tabla ProgramaTac.parametros
//
//  Reglas del documento que se cumplen aca:
//    - 4.6: sin excepciones de control de flujo; todo es pc y marcos.
//    - 4.6: profundidad maxima de 1000 llamadas (EJE-006).
//    - 4.6: limite de cuadruplas para la GUI (EJE-007); la CLI no pone.
//    - 2.9: division/resto por cero (EJE-001/002), indice fuera de rango
//           (EJE-003), acceso a nulo (EJE-004), entrada invalida
//           (EJE-005), tamano negativo de arreglo (EJE-008).
//    - 2.8: las funciones builtin (imprimir, leer, longitud, a_*) no se
//           apilan: se resuelven inline en `llamar`.
//    - 2.6: imprimir un objeto usa su a_cadena() propio si existe (se
//           ejecuta como una llamada anidada real); si no, "Clase@id".
//    - 4.6: las operaciones numericas entre enteros dan enteros; si hay
//           al menos un flotante, flotante.
// ============================================================================
package ar.com.moka.core.ejecucion

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.semantico.Categoria
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import ar.com.moka.core.semantico.SimboloVariable
import ar.com.moka.core.semantico.TBooleano
import ar.com.moka.core.semantico.TCadena
import ar.com.moka.core.semantico.TCaracter
import ar.com.moka.core.semantico.TClase
import ar.com.moka.core.semantico.TEntero
import ar.com.moka.core.semantico.TFlotante
import ar.com.moka.core.semantico.Tipo
import ar.com.moka.core.tac.Cuadrupla
import ar.com.moka.core.tac.Operacion
import ar.com.moka.core.tac.Operando
import ar.com.moka.core.tac.ProgramaTac
import ar.com.moka.core.tac.Valor
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

// Ajustes de una corrida. profundidadMaxima y limiteCuadruplas son los
// guardarrailes de la sec. 4.6; pasoAPaso enciende los eventos Paso.
data class ConfigEjecucion(
    val profundidadMaxima: Int = 1000,
    val limiteCuadruplas: Long? = null,
    val pasoAPaso: Boolean = false
)

class MaquinaTac(
    private val programa: ProgramaTac,
    private val consola: Consola,
    private val configuracion: ConfigEjecucion = ConfigEjecucion()
) {

    private val marcos = ArrayDeque<Marco>()
    private val globales = mutableMapOf<String, Valor>()
    private val colaArgumentos = mutableListOf<Valor>()
    private var pc: Int = 0
    private var ejecutadas: Long = 0
    private var proximoIdHeap: Int = 1

    // Punto de entrada: un Flow frio; cada collect corre el programa de
    // cero (sec. 4.6: "ejecutar(programa) : Flow<EventoEjecucion>").
    fun ejecutar(): Flow<EventoEjecucion> = flow { correr() }

    // -------------------------------------------------------------------
    //  Lazo principal
    // -------------------------------------------------------------------

    private suspend fun FlowCollector<EventoEjecucion>.correr() {
        // Cada collect arranca con la memoria limpia.
        marcos.clear()
        globales.clear()
        colaArgumentos.clear()
        pc = 0
        ejecutadas = 0
        proximoIdHeap = 1
        try {
            val inicio = programa.funciones["__principal"]
                ?: throw ErrorMoka("EJE-000", "No se encontro la unidad __principal en el programa.", null)
            apilarMarco("__principal", emptyList(), pcRetorno = -1, destinoRetorno = null)
            pc = inicio + 1 // salta el inicio_func
            while (marcos.isNotEmpty()) {
                paso()
            }
            emit(EventoEjecucion.Fin(ejecutadas))
        } catch (e: ErrorMoka) {
            emit(
                EventoEjecucion.ErrorEjecucion(
                    Diagnostico(
                        Severidad.ERROR, e.codigo, e.mensaje,
                        e.pos ?: Posicion(0, 0, 0, 0)
                    )
                )
            )
        }
    }

    // Ejecuta UNA cuadrupla. Tambien la usa llamarMetodoInterno, que
    // corre el lazo hasta que se desapila el marco que el puso.
    private suspend fun FlowCollector<EventoEjecucion>.paso() {
        val limite = configuracion.limiteCuadruplas
        if (limite != null && ejecutadas >= limite) {
            throw ErrorMoka(
                "EJE-007",
                "Se alcanzo el limite de $limite cuadruplas ejecutadas (¿ciclo infinito?).",
                cuadruplaActual()?.origen
            )
        }
        val c = programa.cuadruplas.getOrNull(pc)
            ?: throw ErrorMoka("EJE-000", "El contador de programa salio del rango de cuadruplas.", null)
        pc++
        if (configuracion.pasoAPaso) {
            emit(EventoEjecucion.Paso(
                c.indice, c, vistaDeMarcos(),
                // mapValues construye otro mapa: una pausa anterior no
                // cambia cuando se asigna una global en el paso siguiente.
                globales.mapValues { (_, valor) -> textoCrudo(valor) }
            ))
        }
        ejecutadas++
        // Cede el hilo cada tanto para que la GUI siga respondiendo
        // durante ciclos largos (sec. 4.6).
        if (ejecutadas % 1024L == 0L) yield()
        ejecutar(c)
    }

    private fun cuadruplaActual(): Cuadrupla? = programa.cuadruplas.getOrNull(pc - 1)

    private fun vistaDeMarcos(): List<MarcoVista> = marcos.map { m ->
        MarcoVista(
            m.nombre,
            m.variables.mapValues { (_, v) -> textoCrudo(v) },
            m.temporales.entries.associate { (n, v) -> "t$n" to textoCrudo(v) }
        )
    }

    // -------------------------------------------------------------------
    //  Despacho por operacion
    // -------------------------------------------------------------------

    private suspend fun FlowCollector<EventoEjecucion>.ejecutar(c: Cuadrupla) {
        when (c.op) {
            // -- Datos --------------------------------------------------
            Operacion.ASIGNAR -> asignarDestino(c.resultado, valorDe(c.arg1))
            Operacion.CONCAT -> asignarDestino(c.resultado, Valor.VCadena(cadenaDe(valorDe(c.arg1)) + cadenaDe(valorDe(c.arg2))))
            Operacion.ITOF -> asignarDestino(c.resultado, Valor.VFlotante(enteroDe(valorDe(c.arg1)).toDouble()))

            // -- Aritmetica (entero+entero = entero; si hay flotante,
            //    flotante: sec. 2.3/4.6) --------------------------------
            Operacion.MAS -> operarNumerica(c, { a, b -> a + b }, { a, b -> a + b })
            Operacion.MENOS -> operarNumerica(c, { a, b -> a - b }, { a, b -> a - b })
            Operacion.POR -> operarNumerica(c, { a, b -> a * b }, { a, b -> a * b })
            Operacion.DIV -> {
                val a = valorDe(c.arg1); val b = valorDe(c.arg2)
                if (a is Valor.VEntero && b is Valor.VEntero) {
                    if (b.v == 0) throw ErrorMoka("EJE-001", "Division por cero.", c.origen)
                    asignarDestino(c.resultado, Valor.VEntero(a.v / b.v))
                } else {
                    asignarDestino(c.resultado, Valor.VFlotante(flotanteDe(a) / flotanteDe(b)))
                }
            }
            Operacion.MODULO -> {
                val a = enteroDe(valorDe(c.arg1)); val b = enteroDe(valorDe(c.arg2))
                if (b == 0) throw ErrorMoka("EJE-002", "Resto (%) por cero.", c.origen)
                asignarDestino(c.resultado, Valor.VEntero(a % b))
            }
            Operacion.NEG -> when (val v = valorDe(c.arg1)) {
                is Valor.VEntero -> asignarDestino(c.resultado, Valor.VEntero(-v.v))
                else -> asignarDestino(c.resultado, Valor.VFlotante(-flotanteDe(v)))
            }

            // -- Comparaciones y logica ---------------------------------
            Operacion.MENOR -> comparar(c) { a, b -> a < b }
            Operacion.MENOR_IGUAL -> comparar(c) { a, b -> a <= b }
            Operacion.MAYOR -> comparar(c) { a, b -> a > b }
            Operacion.MAYOR_IGUAL -> comparar(c) { a, b -> a >= b }
            Operacion.IGUAL -> asignarDestino(c.resultado, Valor.VBooleano(valorDe(c.arg1) == valorDe(c.arg2)))
            Operacion.DISTINTO -> asignarDestino(c.resultado, Valor.VBooleano(valorDe(c.arg1) != valorDe(c.arg2)))
            Operacion.NO -> asignarDestino(c.resultado, Valor.VBooleano(!booleanoDe(valorDe(c.arg1))))

            // -- Control de flujo ---------------------------------------
            Operacion.ETIQUETA, Operacion.INICIO_FUNC -> {}
            Operacion.FIN_FUNC -> retornar(Valor.VNulo)
            Operacion.IR_A -> pc = etiqueta(c.resultado)
            Operacion.SI_FALSO -> if (!booleanoDe(valorDe(c.arg1))) pc = etiqueta(c.resultado)
            Operacion.SI_VERDADERO -> if (booleanoDe(valorDe(c.arg1))) pc = etiqueta(c.resultado)
            Operacion.RETORNAR -> retornar(c.arg1?.let { valorDe(it) } ?: Valor.VNulo)

            // -- Llamadas -------------------------------------------------
            Operacion.PARAM -> colaArgumentos.add(valorDe(c.arg1))
            Operacion.LLAMAR -> llamar(c)

            // -- Objetos y arreglos ---------------------------------------
            Operacion.NUEVO_OBJETO -> nuevoObjeto(c)
            Operacion.NUEVO_ARREGLO -> nuevoArreglo(c)
            Operacion.LEER_CAMPO -> leerCampo(c)
            Operacion.ASIGNAR_CAMPO -> asignarCampo(c)
            Operacion.LEER_INDICE -> leerIndice(c)
            Operacion.ASIGNAR_INDICE -> asignarIndice(c)
        }
    }

    // a op b conservando la regla entero/flotante (sec. 4.6).
    private fun operarNumerica(c: Cuadrupla, opEntera: (Int, Int) -> Int, opFlotante: (Double, Double) -> Double) {
        val a = valorDe(c.arg1); val b = valorDe(c.arg2)
        if (a is Valor.VEntero && b is Valor.VEntero) {
            asignarDestino(c.resultado, Valor.VEntero(opEntera(a.v, b.v)))
        } else {
            asignarDestino(c.resultado, Valor.VFlotante(opFlotante(flotanteDe(a), flotanteDe(b))))
        }
    }

    private inline fun comparar(c: Cuadrupla, op: (Double, Double) -> Boolean) {
        val a = valorDe(c.arg1); val b = valorDe(c.arg2)
        val r = if (a is Valor.VEntero && b is Valor.VEntero) {
            op(a.v.toDouble(), b.v.toDouble())
        } else {
            op(flotanteDe(a), flotanteDe(b))
        }
        asignarDestino(c.resultado, Valor.VBooleano(r))
    }

    // -------------------------------------------------------------------
    //  Llamadas y retorno
    // -------------------------------------------------------------------

    // `llamar nombre, n, destino`: los ultimos n valores de la cola de
    // argumentos se ligan, en orden, con los idUnicos de
    // ProgramaTac.parametros[nombre]. Los builtin no apilan marco
    // (sec. 2.8); el resto apila y salta (sec. 4.6).
    private suspend fun FlowCollector<EventoEjecucion>.llamar(c: Cuadrupla) {
        val nombre = (c.arg1 as? Operando.Nombre)?.texto
            ?: throw ErrorMoka("EJE-000", "llamar sin nombre de unidad (error interno).", c.origen)
        val cantidad = enteroDe(valorDe(c.arg2))
        val args = colaArgumentos.takeLast(cantidad)
        repeat(cantidad) { colaArgumentos.removeAt(colaArgumentos.lastIndex) }
        val destino = (c.resultado as? Operando.Temporal)?.numero

        if (esBuiltin(nombre)) {
            val resultado = ejecutarBuiltin(nombre, args, c)
            if (destino != null && resultado != null) {
                marcos.last().temporales[destino] = resultado
            }
            return
        }

        if (marcos.size >= configuracion.profundidadMaxima) {
            throw ErrorMoka(
                "EJE-006",
                "Error: profundidad maxima de llamadas alcanzada (¿recursion sin caso base?)",
                c.origen
            )
        }
        val inicio = programa.funciones[nombre]
            ?: throw ErrorMoka("EJE-000", "No se encontro la unidad '$nombre' (error interno).", c.origen)
        apilarMarco(nombre, args, pcRetorno = pc, destinoRetorno = destino)
        pc = inicio + 1
    }

    private fun apilarMarco(nombre: String, args: List<Valor>, pcRetorno: Int, destinoRetorno: Int?): Marco {
        val marco = Marco(nombre, pcRetorno, destinoRetorno)
        val ids = programa.parametros[nombre] ?: emptyList()
        for (i in ids.indices) {
            marco.variables[ids[i]] = args.getOrElse(i) { Valor.VNulo }
        }
        marcos.addLast(marco)
        return marco
    }

    // Desapila y vuelve al llamador: el resultado viaja al temporal de
    // destino que el `llamar` habia registrado (sec. 4.6).
    private fun retornar(valor: Valor) {
        val marco = marcos.removeLast()
        if (marco.capturarRetorno) {
            marco.valorCapturado = valor
        } else if (marco.destinoRetorno != null && marcos.isNotEmpty()) {
            marcos.last().temporales[marco.destinoRetorno] = valor
        }
        if (marcos.isNotEmpty()) pc = marco.pcRetorno
    }

    // Llama a una funcion del usuario desde la maquina misma (hoy solo
    // el a_cadena() de un objeto al imprimirlo, sec. 2.6). Apila un
    // marco que captura el retorno y corre el lazo hasta que se va.
    private suspend fun FlowCollector<EventoEjecucion>.llamarMetodoInterno(
        nombre: String, args: List<Valor>, pos: Posicion?
    ): Valor {
        val profundidadAntes = marcos.size
        if (profundidadAntes >= configuracion.profundidadMaxima) {
            throw ErrorMoka(
                "EJE-006",
                "Error: profundidad maxima de llamadas alcanzada (¿recursion sin caso base?)",
                pos
            )
        }
        val inicio = programa.funciones[nombre] ?: return Valor.VNulo
        val marco = Marco(nombre, pcRetorno = pc, destinoRetorno = null, capturarRetorno = true)
        val ids = programa.parametros[nombre] ?: emptyList()
        for (i in ids.indices) {
            marco.variables[ids[i]] = args.getOrElse(i) { Valor.VNulo }
        }
        marcos.addLast(marco)
        pc = inicio + 1
        while (marcos.size > profundidadAntes) paso()
        return marco.valorCapturado ?: Valor.VNulo
    }

    // -------------------------------------------------------------------
    //  Objetos y arreglos
    // -------------------------------------------------------------------

    private fun nuevoObjeto(c: Cuadrupla) {
        val nombreClase = (c.arg1 as? Operando.Nombre)?.texto
            ?: throw ErrorMoka("EJE-000", "nuevo_objeto sin nombre de clase (error interno).", c.origen)
        val clase = programa.clases[nombreClase]
            ?: throw ErrorMoka("EJE-000", "La clase '$nombreClase' no esta en el programa (error interno).", c.origen)
        // Cada campo de instancia arranca con el valor por defecto de su
        // tipo (sec. 2.4); los estaticos viven en el mapa global.
        val campos = mutableMapOf<String, Valor>()
        for ((nombreCampo, sim) in clase.campos) {
            if (!sim.esEstatico) campos[nombreCampo] = valorPorDefecto(sim.tipo)
        }
        asignarDestino(c.resultado, Valor.VObjeto(clase, campos, proximoIdHeap++))
    }

    private fun nuevoArreglo(c: Cuadrupla) {
        val tipoTexto = (c.arg1 as? Operando.Nombre)?.texto
            ?: throw ErrorMoka("EJE-000", "nuevo_arreglo sin tipo (error interno).", c.origen)
        val tamano = enteroDe(valorDe(c.arg2))
        if (tamano < 0) {
            throw ErrorMoka("EJE-008", "El tamano de un arreglo no puede ser negativo: $tamano.", c.origen)
        }
        val tipo = tipoDesdeTexto(tipoTexto)
        asignarDestino(c.resultado, Valor.VArreglo(tipo, Array(tamano) { valorPorDefecto(tipo) }, proximoIdHeap++))
    }

    private fun leerCampo(c: Cuadrupla) {
        val campo = (c.arg2 as? Operando.Nombre)?.texto
            ?: throw ErrorMoka("EJE-000", "leer_campo sin nombre de campo (error interno).", c.origen)
        val obj = objetoDe(valorDe(c.arg1), "acceder al campo '$campo' de", c.origen)
        asignarDestino(c.resultado, obj.campos[campo] ?: Valor.VNulo)
    }

    private fun asignarCampo(c: Cuadrupla) {
        val campo = (c.arg2 as? Operando.Nombre)?.texto
            ?: throw ErrorMoka("EJE-000", "asignar_campo sin nombre de campo (error interno).", c.origen)
        val obj = objetoDe(valorDe(c.arg1), "asignar el campo '$campo' de", c.origen)
        obj.campos[campo] = valorDe(c.resultado)
    }

    private fun leerIndice(c: Cuadrupla) {
        val arr = arregloDe(valorDe(c.arg1), "indexar", c.origen)
        val i = indiceValido(arr, valorDe(c.arg2), c.origen)
        asignarDestino(c.resultado, arr.datos[i])
    }

    private fun asignarIndice(c: Cuadrupla) {
        val arr = arregloDe(valorDe(c.arg1), "indexar", c.origen)
        val i = indiceValido(arr, valorDe(c.arg2), c.origen)
        arr.datos[i] = valorDe(c.resultado)
    }

    // Desreferencia con el error didactico de la sec. 2.9/4.6.
    private fun objetoDe(v: Valor, accion: String, pos: Posicion?): Valor.VObjeto = when (v) {
        is Valor.VNulo -> throw ErrorMoka("EJE-004", "Se intento $accion un valor nulo.", pos)
        is Valor.VObjeto -> v
        else -> throw ErrorMoka("EJE-000", "Se esperaba un objeto (error interno del generador).", pos)
    }

    private fun arregloDe(v: Valor, accion: String, pos: Posicion?): Valor.VArreglo = when (v) {
        is Valor.VNulo -> throw ErrorMoka("EJE-004", "Se intento $accion un valor nulo.", pos)
        is Valor.VArreglo -> v
        else -> throw ErrorMoka("EJE-000", "Se esperaba un arreglo (error interno del generador).", pos)
    }

    // El mensaje es el del ejemplo de la sec. 2.9.
    private fun indiceValido(arr: Valor.VArreglo, indice: Valor, pos: Posicion?): Int {
        val i = enteroDe(indice)
        if (i < 0 || i >= arr.datos.size) {
            throw ErrorMoka("EJE-003", "Indice $i fuera de rango para arreglo de longitud ${arr.datos.size}.", pos)
        }
        return i
    }

    // -------------------------------------------------------------------
    //  Variables, temporales y valores por defecto
    // -------------------------------------------------------------------

    // Las globales y los campos estaticos no viven en ningun marco: se
    // guardan en el mapa global con la clave que uso el generador
    // ("Clase.campo" para estaticos, idUnico para globales, sec. 4.5.3).
    private fun esGlobal(sim: SimboloVariable): Boolean =
        sim.categoria == Categoria.GLOBAL || (sim.categoria == Categoria.CAMPO && sim.esEstatico)

    private fun claveGlobal(sim: SimboloVariable): String =
        programa.estaticos[sim.idUnico] ?: sim.idUnico

    private fun leerVariable(sim: SimboloVariable): Valor =
        if (esGlobal(sim)) globales[claveGlobal(sim)] ?: valorPorDefecto(sim.tipo)
        else marcos.last().variables[sim.idUnico] ?: valorPorDefecto(sim.tipo)

    private fun escribirVariable(sim: SimboloVariable, valor: Valor) {
        if (esGlobal(sim)) globales[claveGlobal(sim)] = valor
        else marcos.last().variables[sim.idUnico] = valor
    }

    private fun valorDe(o: Operando?): Valor = when (o) {
        null -> Valor.VNulo
        is Operando.Constante -> o.valor
        is Operando.Variable -> leerVariable(o.simbolo)
        is Operando.Temporal -> marcos.last().temporales[o.numero]
            ?: throw ErrorMoka("EJE-000", "El temporal t${o.numero} no tiene valor (error interno).", null)
        // Nombre y Etiqueta no llevan valor: aparecen en llamadas y
        // saltos, nunca donde se lee un dato.
        is Operando.Nombre, is Operando.Etiqueta -> Valor.VNulo
    }

    private fun asignarDestino(o: Operando?, valor: Valor) {
        when (o) {
            null -> {}
            is Operando.Variable -> escribirVariable(o.simbolo, valor)
            is Operando.Temporal -> marcos.last().temporales[o.numero] = valor
            else -> throw ErrorMoka("EJE-000", "Destino de asignacion invalido (error interno).", null)
        }
    }

    // Valor inicial de una variable sin inicializador (sec. 2.4).
    private fun valorPorDefecto(tipo: Tipo): Valor = when (tipo) {
        TEntero -> Valor.VEntero(0)
        TFlotante -> Valor.VFlotante(0.0)
        TBooleano -> Valor.VBooleano(false)
        TCaracter -> Valor.VCaracter('\u0000')
        else -> Valor.VNulo // cadena, arreglos y clases arrancan en nulo
    }

    private fun tipoDesdeTexto(texto: String): Tipo = when (texto) {
        "entero" -> TEntero
        "flotante" -> TFlotante
        "booleano" -> TBooleano
        "caracter" -> TCaracter
        "cadena" -> TCadena
        else -> TClase(texto)
    }

    // El destino de un salto (ir_a, si_falso, si_verdadero) viaja en
    // resultado como Operando.Etiqueta (L3); la tabla de ProgramaTac lo
    // lleva al indice de su cuadrupla etiqueta, que no hace nada.
    private fun etiqueta(o: Operando?): Int {
        val nombre = when (o) {
            is Operando.Etiqueta -> o.nombre
            is Operando.Nombre -> o.texto
            else -> throw ErrorMoka("EJE-000", "Salto sin etiqueta (error interno).", null)
        }
        return programa.etiquetas[nombre]
            ?: throw ErrorMoka("EJE-000", "La etiqueta '$nombre' no existe (error interno).", null)
    }

    // -------------------------------------------------------------------
    //  Funciones builtin (sec. 2.8)
    // -------------------------------------------------------------------

    private fun esBuiltin(nombre: String): Boolean = nombre in BUILTINS

    private suspend fun FlowCollector<EventoEjecucion>.ejecutarBuiltin(
        nombre: String, args: List<Valor>, c: Cuadrupla
    ): Valor? {
        when (nombre) {
            "imprimir" -> emit(EventoEjecucion.Salida(aCadenaDe(args.getOrElse(0) { Valor.VNulo }, c.origen) + "\n"))
            "imprimir_sin_salto" -> emit(EventoEjecucion.Salida(aCadenaDe(args.getOrElse(0) { Valor.VNulo }, c.origen)))
            "leer" -> {
                emit(EventoEjecucion.PidiendoEntrada(null))
                return Valor.VCadena(leerLinea())
            }
            "leer_entero" -> {
                emit(EventoEjecucion.PidiendoEntrada(null))
                val linea = leerLinea()
                val v = linea.trim().toIntOrNull()
                    ?: throw ErrorMoka("EJE-005", "Entrada invalida para leer_entero: '$linea'.", c.origen)
                return Valor.VEntero(v)
            }
            "leer_flotante" -> {
                emit(EventoEjecucion.PidiendoEntrada(null))
                val linea = leerLinea()
                val v = linea.trim().toDoubleOrNull()
                    ?: throw ErrorMoka("EJE-005", "Entrada invalida para leer_flotante: '$linea'.", c.origen)
                return Valor.VFlotante(v)
            }
            "longitud" -> return when (val a = args.getOrElse(0) { Valor.VNulo }) {
                is Valor.VArreglo -> Valor.VEntero(a.datos.size)
                is Valor.VCadena -> Valor.VEntero(a.v.length)
                else -> Valor.VEntero(0)
            }
            "a_entero" -> return Valor.VEntero(aEntero(args.getOrElse(0) { Valor.VNulo }, c.origen))
            "a_flotante" -> return Valor.VFlotante(aFlotante(args.getOrElse(0) { Valor.VNulo }, c.origen))
            "a_cadena" -> return Valor.VCadena(aCadenaDe(args.getOrElse(0) { Valor.VNulo }, c.origen))
            "a_booleano" -> return Valor.VBooleano(aBooleano(args.getOrElse(0) { Valor.VNulo }, c.origen))
        }
        return null
    }

    // EOF se lee como cadena vacia (leer) y da entrada invalida en los
    // numericos, como en las clases con scanner de la catedra.
    private suspend fun leerLinea(): String = consola.leerLinea() ?: ""

    // -- a_entero / a_flotante / a_booleano: el dominio es el del
    //    semantico (sec. 2.8 y chequeos 4.4); lo que no se puede
    //    interpretar es EJE-005, no nulo.
    private fun aEntero(v: Valor, pos: Posicion?): Int = when (v) {
        is Valor.VEntero -> v.v
        is Valor.VFlotante -> v.v.toInt() // trunca, no redondea
        is Valor.VCaracter -> v.v.code
        is Valor.VBooleano -> if (v.v) 1 else 0
        is Valor.VCadena -> v.v.trim().toIntOrNull()
            ?: throw ErrorMoka("EJE-005", "a_entero no pudo convertir '${v.v}' a entero.", pos)
        Valor.VNulo -> throw ErrorMoka("EJE-005", "a_entero no pudo convertir 'nulo' a entero.", pos)
        else -> throw ErrorMoka("EJE-005", "a_entero no se puede aplicar a este valor.", pos)
    }

    private fun aFlotante(v: Valor, pos: Posicion?): Double = when (v) {
        is Valor.VFlotante -> v.v
        is Valor.VEntero -> v.v.toDouble()
        is Valor.VCaracter -> v.v.code.toDouble()
        is Valor.VBooleano -> if (v.v) 1.0 else 0.0
        is Valor.VCadena -> v.v.trim().toDoubleOrNull()
            ?: throw ErrorMoka("EJE-005", "a_flotante no pudo convertir '${v.v}' a flotante.", pos)
        Valor.VNulo -> throw ErrorMoka("EJE-005", "a_flotante no pudo convertir 'nulo' a flotante.", pos)
        else -> throw ErrorMoka("EJE-005", "a_flotante no se puede aplicar a este valor.", pos)
    }

    private fun aBooleano(v: Valor, pos: Posicion?): Boolean = when (v) {
        is Valor.VBooleano -> v.v
        is Valor.VEntero -> v.v != 0
        is Valor.VFlotante -> v.v != 0.0
        is Valor.VCadena -> when (v.v.trim().lowercase()) {
            "verdadero", "true" -> true
            "falso", "false" -> false
            else -> throw ErrorMoka("EJE-005", "a_booleano no pudo convertir '${v.v}' a booleano.", pos)
        }
        else -> throw ErrorMoka("EJE-005", "a_booleano no se puede aplicar a este valor.", pos)
    }

    // -------------------------------------------------------------------
    //  Conversion a texto: imprimir y a_cadena (sec. 2.8)
    // -------------------------------------------------------------------

    // Flotante: si es entero exacto se imprime sin el ".0" ("5" y no
    // "5.0", convencion de la catedra para que los alumnos no peleen con
    // el formato). Hecho a mano para no depender de formatos de plataforma
    // (regla de pureza de la sec. 4.2).
    private fun formatearFlotante(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return d.toString()
        val l = d.toLong()
        return if (l.toDouble() == d && abs(d) < 1e15) l.toString() else d.toString()
    }

    // Texto de un valor tal como lo muestra `imprimir`.
    private suspend fun FlowCollector<EventoEjecucion>.aCadenaDe(v: Valor, pos: Posicion?): String = when (v) {
        Valor.VNulo -> "nulo"
        is Valor.VEntero -> v.v.toString()
        is Valor.VFlotante -> formatearFlotante(v.v)
        is Valor.VBooleano -> if (v.v) "verdadero" else "falso"
        is Valor.VCaracter -> v.v.toString()
        is Valor.VCadena -> v.v
        is Valor.VArreglo -> "${v.tipoElemento.descripcion}[]@${v.id}"
        is Valor.VObjeto -> {
            // Si la clase define `funcion cadena a_cadena()` se usa ese
            // (sec. 2.6); se ejecuta como una llamada real anidada, con
            // su propio marco y "este" ligado.
            val metodo = v.clase.metodos["a_cadena"]
            if (metodo != null && !metodo.esEstatico && metodo.parametros.isEmpty() && metodo.tipo is TCadena) {
                val r = llamarMetodoInterno("${v.clase.nombre}.a_cadena", listOf(v), pos)
                aCadenaDe(r, pos)
            } else {
                "${v.clase.nombre}@${v.id}"
            }
        }
    }

    // Render simple sin suspender, para la vista de marcos del modo paso
    // a paso (no ejecuta el a_cadena() del usuario: seria raro que un
    // Paso tenga efectos).
    private fun textoCrudo(v: Valor): String = when (v) {
        Valor.VNulo -> "nulo"
        is Valor.VEntero -> v.v.toString()
        is Valor.VFlotante -> formatearFlotante(v.v)
        is Valor.VBooleano -> if (v.v) "verdadero" else "falso"
        is Valor.VCaracter -> "'${v.v}'"
        is Valor.VCadena -> "\"${v.v}\""
        is Valor.VArreglo -> "${v.tipoElemento.descripcion}[]@${v.id}"
        is Valor.VObjeto -> "${v.clase.nombre}@${v.id}"
    }

    // -------------------------------------------------------------------
    //  Extractores primitivos
    // -------------------------------------------------------------------

    private fun enteroDe(v: Valor): Int = (v as? Valor.VEntero)?.v
        ?: throw ErrorMoka("EJE-000", "Se esperaba un entero (error interno).", null)

    private fun flotanteDe(v: Valor): Double = when (v) {
        is Valor.VEntero -> v.v.toDouble()
        is Valor.VFlotante -> v.v
        is Valor.VCaracter -> v.v.code.toDouble()
        else -> throw ErrorMoka("EJE-000", "Se esperaba un numero (error interno).", null)
    }

    private fun booleanoDe(v: Valor): Boolean = (v as? Valor.VBooleano)?.v
        ?: throw ErrorMoka("EJE-000", "Se esperaba un booleano (error interno).", null)

    private fun cadenaDe(v: Valor): String = (v as? Valor.VCadena)?.v
        ?: throw ErrorMoka("EJE-000", "Se esperaba una cadena (error interno).", null)

    // Error interno de la maquina: se atrapa en correr() y sale como un
    // evento ErrorEjecucion, nunca como excepcion suelta (sec. 4.6).
    private class ErrorMoka(val codigo: String, val mensaje: String, val pos: Posicion?) : RuntimeException()

    companion object {
        private val BUILTINS = setOf(
            "imprimir", "imprimir_sin_salto",
            "leer", "leer_entero", "leer_flotante",
            "longitud",
            "a_entero", "a_flotante", "a_cadena", "a_booleano"
        )
    }
}
