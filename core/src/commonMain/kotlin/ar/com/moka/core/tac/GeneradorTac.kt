// ============================================================================
// GeneradorTac.kt - Fase 4: del AST verificado al codigo de 3 direcciones
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/tac/GeneradorTac.kt
// (documento de diseno, seccion 4.5)
//
// Recorre el AST (el mismo que verifico el semantico) y emite cuadruplas.
// NO descubre nada por su cuenta: cada Nombre ya esta ligado a su Simbolo
// y cada Expr ya tiene su Tipo en las tablas laterales del
// ResultadoSemantico. El generador solo TRADUCE.
//
// IDEAS QUE ATRAVIESAN TODO EL ARCHIVO:
//
// 1) TEMPORALES POR FUNCION. t1, t2, ... se reinician en cada unidad
//    (sec. 4.5.1): viven en el marco de la llamada, no en el programa.
//    Las ETIQUETAS, en cambio, son unicas en todo el programa (la
//    MaquinaTac arma una sola tabla etiqueta -> indice).
//
// 2) CONVERSIONES EXPLICITAS. Donde el semantico acepto una promocion
//    entero -> flotante, el generador materializa el itof: se detecta
//    comparando el tipo del operando (tabla tipos) con el tipo que el
//    contexto espera. En el TAC toda operacion aritmetica tiene sus dos
//    operandos del mismo tipo (sec. 4.5.2).
//
// 3) CORTOCIRCUITO CON SALTOS. `y` y `o` jamas son una operacion: se
//    traducen a si_falso / si_verdadero (sec. 2.14). Cuando la condicion
//    de un si/mientras ES un y/o, se salta sin materializar el booleano;
//    cuando el y/o produce un valor (booleano ok = a y b), se acumula en
//    un temporal siguiendo el patron del ejemplo 4 de la sec. 4.5.4.
//
// 4) EL PROGRAMA TIENE ORDEN: primero metodos, constructores y funciones
//    (cada uno una unidad inicio_func ... fin_func), y al final la unidad
//    __principal con las sentencias toplevel. La ejecucion arranca en
//    __principal (sec. 4.5.3). Los inicializadores de campos estaticos
//    van al inicio de __principal; los de instancia, al inicio del
//    constructor (corren "como si fueran parte del constructor", 4.4).
//
// NUMERACION: las cuadruplas nacen con indice provisorio y se RENUMERAN
// al final. El cortocircuito genera el operando derecho en un buffer
// aparte (para que su temporal quede numerado antes que el acumulador,
// como en el ejemplo 4) y despues lo injerta en el medio: sin la
// renumeracion final los indices quedarian desordenados.
// ============================================================================

package ar.com.moka.core.tac

import ar.com.moka.core.ast.*
import ar.com.moka.core.semantico.*

class GeneradorTac private constructor(private val sem: ResultadoSemantico) {

    companion object {
        // Punto de entrada comodo: GeneradorTac.generar(ast, resultadoSemantico)
        fun generar(programa: ProgramaAst, semantico: ResultadoSemantico): ProgramaTac =
            GeneradorTac(semantico).ejecutar(programa)
    }

    // ------------------------------ estado ---------------------------------

    private val cuadruplas = mutableListOf<Cuadrupla>()

    // A donde va cada cuadrupla emitida. Normalmente es `cuadruplas`;
    // durante el cortocircuito se desvia a un buffer (ver genLogicoValor).
    private var salida: MutableList<Cuadrupla> = cuadruplas

    private var contadorTemporal = 0        // se reinicia en cada funcion
    private var contadorEtiqueta = 0        // unico en todo el programa

    // Contexto: la clase cuyo cuerpo se esta generando y su `este`
    // sintetico (primer parametro implicito de metodos y constructor).
    private var claseActual: SimboloClase? = null
    private var simboloEste: SimboloVariable? = null
    private var retornoActual: Tipo? = null   // null: codigo toplevel

    // Pilas de destinos para romper / continuar: la cima es el ciclo (o
    // segun) mas interno. romper sale al Lfin; continuar va al Lcont.
    private val pilaRomper = mutableListOf<Operando.Etiqueta>()
    private val pilaContinuar = mutableListOf<Operando.Etiqueta>()

    // idUnico de campo estatico -> "Clase.campo" (sec. 4.5.3).
    private val estaticos = mutableMapOf<String, String>()

    // Nombre de unidad -> idUnicos de sus parametros EN ORDEN (este
    // primero en metodos y constructor). La MaquinaTac los liga con los
    // argumentos apilados por `param` al ejecutar `llamar` (sec. 4.6).
    private val parametrosPorFuncion = mutableMapOf<String, List<String>>()

    // --------------------------- orquestacion -------------------------------

    private fun ejecutar(programa: ProgramaAst): ProgramaTac {
        precomputarEstaticos()

        // Primero todas las funciones, metodos y constructores (orden
        // textual); al final, __principal (sec. 4.5.3).
        for (d in programa.declaraciones) {
            when (d) {
                is DeclFuncion -> {
                    // Las funciones de paquete se nombran cualificadas
                    // ("util.sumar"), igual que en el semantico (Moka 2, P1).
                    val nombreReal = sem.paqueteDe[d.id]?.let { "$it.${d.nombre}" } ?: d.nombre
                    val retorno = sem.raiz.funciones[nombreReal]?.tipo
                    unidad(nombreReal, d.params.size, d.pos, retorno, idsDeParams(d.params)) {
                        for (s in d.cuerpo.sentencias) genSentencia(s)
                    }
                }
                is DeclClase -> genClase(d)
                else -> { }   // las sentencias toplevel van en __principal
            }
        }

        unidad("__principal", 0, programa.pos, null, emptyList()) {
            genInicializadoresEstaticos(programa)
            for (d in programa.declaraciones) {
                if (d is Sent) genSentencia(d)
            }
        }

        // Renumeracion final: el cortocircuito injerta buffers en el
        // medio de la lista, asi que los indices se fijan aca, al cerrar.
        val numeradas = cuadruplas.mapIndexed { i, c -> c.copy(indice = i) }
        return ProgramaTac(numeradas, estaticos, parametrosPorFuncion, sem.raiz.clases)
    }

    // Una unidad inicio_func ... fin_func: funcion, metodo, constructor o
    // __principal. Los temporales se reinician: son del marco (sec. 4.5.1).
    private fun unidad(nombre: String, nroParametros: Int, pos: Posicion, retorno: Tipo?, paramIds: List<String>, cuerpo: () -> Unit) {
        contadorTemporal = 0
        parametrosPorFuncion[nombre] = paramIds
        val retornoPrevio = retornoActual
        retornoActual = retorno
        emit(Operacion.INICIO_FUNC, Operando.Nombre(nombre), constante(nroParametros), null, pos)
        try {
            cuerpo()
        } finally {
            retornoActual = retornoPrevio
        }
        emit(Operacion.FIN_FUNC, Operando.Nombre(nombre), null, null, pos)
    }

    private fun genClase(d: DeclClase) {
        val nombreReal = sem.paqueteDe[d.id]?.let { "$it.${d.nombre}" } ?: d.nombre
        val clase = sem.raiz.clases[nombreReal] ?: return
        val clasePrevia = claseActual
        val estePrevio = simboloEste
        claseActual = clase
        // `este` es el primer parametro implicito de metodos y constructor
        // (sec. 4.5.3): un SimboloVariable sintetico de categoria PARAMETRO.
        simboloEste = SimboloVariable("este", TClase(clase.nombre), d.pos, Categoria.PARAMETRO, false, false, "este")
        try {
            // El constructor SIEMPRE existe como unidad: si no se declaro,
            // es el vacio implicito (sec. 2.10) y al menos corre los
            // inicializadores de campos de instancia.
            val ctor = d.constructor
            unidad(
                "${clase.nombre}.constructor", (ctor?.params?.size ?: 0) + 1, ctor?.pos ?: d.pos, TVacio,
                listOf("este") + idsDeParams(ctor?.params ?: emptyList())
            ) {
                genInicializadoresDeInstancia(d, clase)
                if (ctor != null) for (s in ctor.cuerpo.sentencias) genSentencia(s)
            }
            for (metodo in d.metodos) {
                val sim = clase.metodos[metodo.nombre]
                val nro = metodo.params.size + if (metodo.esEstatico) 0 else 1
                unidad(
                    "${clase.nombre}.${metodo.nombre}", nro, metodo.pos, sim?.tipo,
                    (if (metodo.esEstatico) emptyList() else listOf("este")) + idsDeParams(metodo.params)
                ) {
                    for (s in metodo.cuerpo.sentencias) genSentencia(s)
                }
            }
        } finally {
            claseActual = clasePrevia
            simboloEste = estePrevio
        }
    }

    // Campos de instancia con "= ...": corren al inicio del constructor,
    // en orden de declaracion (sec. 4.4: "como si fueran parte del
    // constructor").
    private fun genInicializadoresDeInstancia(d: DeclClase, clase: SimboloClase) {
        for (campo in d.campos) {
            if (campo.esEstatico) continue
            val inicial = campo.inicial ?: continue
            val sim = clase.campos[campo.nombre] ?: continue
            var v = genExpr(inicial)
            v = convertir(v, tipoDe(inicial), sim.tipo, inicial.pos)
            emit(Operacion.ASIGNAR_CAMPO, Operando.Variable(simboloEste!!), Operando.Nombre(campo.nombre), v, campo.pos)
        }
    }

    // Campos estaticos con "= ...": viven en el almacen global con el
    // nombre Clase.campo (sec. 4.5.3) y se inicializan al arrancar el
    // programa, antes que cualquier sentencia toplevel.
    private fun genInicializadoresEstaticos(programa: ProgramaAst) {
        for (d in programa.declaraciones) {
            if (d !is DeclClase) continue
            val clase = sem.raiz.clases[d.nombre] ?: continue
            for (campo in d.campos) {
                if (!campo.esEstatico) continue
                val inicial = campo.inicial ?: continue
                val sim = clase.campos[campo.nombre] ?: continue
                var v = genExpr(inicial)
                v = convertir(v, tipoDe(inicial), sim.tipo, inicial.pos)
                emit(Operacion.ASIGNAR, v, null, Operando.Variable(sim), campo.pos)
            }
        }
    }

    // Los idUnicos de los parametros, en orden. El semantico los registro
    // en la tabla simbolos al abrir el ambito de la funcion (sec. 4.4).
    private fun idsDeParams(params: List<Param>): List<String> =
        params.map { (sem.simbolos[it.id] as? SimboloVariable)?.idUnico ?: it.nombre }

    private fun precomputarEstaticos() {
        for ((_, clase) in sem.raiz.clases) {
            for (campo in clase.campos.values) {
                if (campo.esEstatico) estaticos[campo.idUnico] = "${clase.nombre}.${campo.nombre}"
            }
        }
    }

    // ----------------------------- sentencias -------------------------------

    private fun genSentencia(s: Sent) {
        when (s) {
            is Bloque -> for (interna in s.sentencias) genSentencia(interna)

            is DeclVariable -> {
                val sim = sem.simbolos[s.id] as? SimboloVariable ?: return
                val inicial = s.inicial ?: return   // sin inicializador: vale el default (sec. 2.5)
                var v = genExpr(inicial)
                v = convertir(v, tipoDe(inicial), sim.tipo, inicial.pos)
                emit(Operacion.ASIGNAR, v, null, Operando.Variable(sim), s.pos)
            }

            is SentSi -> {
                val lFinEntonces = nuevaEtiqueta()
                if (s.sino != null) {
                    val lFin = nuevaEtiqueta()
                    genSiFalso(s.cond, lFinEntonces)
                    genSentencia(s.entonces)
                    emit(Operacion.IR_A, null, null, lFin, s.pos)
                    emit(Operacion.ETIQUETA, null, null, lFinEntonces, s.pos)
                    genSentencia(s.sino)
                    emit(Operacion.ETIQUETA, null, null, lFin, s.pos)
                } else {
                    genSiFalso(s.cond, lFinEntonces)
                    genSentencia(s.entonces)
                    emit(Operacion.ETIQUETA, null, null, lFinEntonces, s.pos)
                }
            }

            is SentMientras -> {
                // Lcond: cond; si_falso -> Lfin; cuerpo; ir_a Lcond; Lfin (sec. 4.5.3)
                val lCond = nuevaEtiqueta()
                val lFin = nuevaEtiqueta()
                emit(Operacion.ETIQUETA, null, null, lCond, s.pos)
                genSiFalso(s.cond, lFin)
                pilaRomper.add(lFin)
                pilaContinuar.add(lCond)
                try { genSentencia(s.cuerpo) } finally {
                    pilaRomper.removeAt(pilaRomper.lastIndex)
                    pilaContinuar.removeAt(pilaContinuar.lastIndex)
                }
                emit(Operacion.IR_A, null, null, lCond, s.pos)
                emit(Operacion.ETIQUETA, null, null, lFin, s.pos)
            }

            is SentHacer -> {
                // Lcuerpo: cuerpo; Lcont: cond; si_verdadero -> Lcuerpo; Lfin (sec. 4.5.3)
                val lCuerpo = nuevaEtiqueta()
                val lCont = nuevaEtiqueta()
                val lFin = nuevaEtiqueta()
                emit(Operacion.ETIQUETA, null, null, lCuerpo, s.pos)
                pilaRomper.add(lFin)
                pilaContinuar.add(lCont)
                try { genSentencia(s.cuerpo) } finally {
                    pilaRomper.removeAt(pilaRomper.lastIndex)
                    pilaContinuar.removeAt(pilaContinuar.lastIndex)
                }
                emit(Operacion.ETIQUETA, null, null, lCont, s.pos)
                genSiVerdadero(s.cond, lCuerpo)
                emit(Operacion.ETIQUETA, null, null, lFin, s.pos)
            }

            is SentPara -> {
                // inicio; Lcond: cond; si_falso -> Lfin; cuerpo; Lcont: paso;
                // ir_a Lcond; Lfin (sec. 4.5.3). continuar -> Lcont.
                when (val inicio = s.inicio) {
                    is DeclVariable -> genSentencia(inicio)
                    is Expr -> genExprComoSentencia(inicio)
                    else -> { }   // para (;; ...)
                }
                val lCond = nuevaEtiqueta()
                val lFin = nuevaEtiqueta()
                val lCont = nuevaEtiqueta()
                emit(Operacion.ETIQUETA, null, null, lCond, s.pos)
                s.cond?.let { genSiFalso(it, lFin) }
                pilaRomper.add(lFin)
                pilaContinuar.add(lCont)
                try { genSentencia(s.cuerpo) } finally {
                    pilaRomper.removeAt(pilaRomper.lastIndex)
                    pilaContinuar.removeAt(pilaContinuar.lastIndex)
                }
                emit(Operacion.ETIQUETA, null, null, lCont, s.pos)
                s.paso?.let { genExprComoSentencia(it) }
                emit(Operacion.IR_A, null, null, lCond, s.pos)
                emit(Operacion.ETIQUETA, null, null, lFin, s.pos)
            }

            is SentParaCada -> genParaCada(s)

            is SentSegun -> genSegun(s)

            is SentRetornar -> {
                val valor = s.valor
                if (valor == null) {
                    emit(Operacion.RETORNAR, null, null, null, s.pos)
                } else {
                    var v = genExpr(valor)
                    v = convertir(v, tipoDe(valor), retornoActual ?: TError, valor.pos)
                    emit(Operacion.RETORNAR, v, null, null, s.pos)
                }
            }

            is SentRomper -> emit(Operacion.IR_A, null, null, pilaRomper.last(), s.pos)

            is SentContinuar -> emit(Operacion.IR_A, null, null, pilaContinuar.last(), s.pos)

            is SentExpr -> genExprComoSentencia(s.expr)

            is SentVacia -> { }   // el ';' suelto no genera nada

            else -> { }   // red de seguridad si Sent crece
        }
    }

    // Expresion usada como sentencia: se genera y se descarta el valor.
    // Excepcion (sec. 4.5.3): ++ y -- como sentencia NO copian el valor
    // viejo, porque a nadie le interesa.
    private fun genExprComoSentencia(e: Expr) {
        if (e is Unaria && (e.op == OpUnario.INCREMENTO || e.op == OpUnario.DECREMENTO)) {
            genIncremento(e, comoSentencia = true)
        } else {
            genExpr(e)
        }
    }

    // para_cada desazucarado con indice oculto (sec. 4.5.3):
    //   ta = arreglo; ti = 0; tn = longitud(ta)
    //   Lcond: ti < tn; si_falso -> Lfin; x = ta[ti]; cuerpo
    //   Lcont: ti = ti + 1; ir_a Lcond; Lfin
    // ta/ti/tn son temporales: no pisan ninguna variable del programador.
    private fun genParaCada(s: SentParaCada) {
        val sim = buscarSimboloParaCada(sem.raiz, s)
        val coleccion = genExpr(s.coleccion)
        val ta = nuevoTemporal()
        emit(Operacion.ASIGNAR, coleccion, null, ta, s.coleccion.pos)
        val ti = nuevoTemporal()
        emit(Operacion.ASIGNAR, constante(0), null, ti, s.pos)
        val tn = nuevoTemporal()
        emit(Operacion.PARAM, ta, null, null, s.pos)
        emit(Operacion.LLAMAR, Operando.Nombre("longitud"), constante(1), tn, s.pos)

        val lCond = nuevaEtiqueta()
        val lFin = nuevaEtiqueta()
        val lCont = nuevaEtiqueta()
        emit(Operacion.ETIQUETA, null, null, lCond, s.pos)
        val tCond = nuevoTemporal()
        emit(Operacion.MENOR, ti, tn, tCond, s.pos)
        emit(Operacion.SI_FALSO, tCond, null, lFin, s.pos)
        val tElem = nuevoTemporal()
        emit(Operacion.LEER_INDICE, ta, ti, tElem, s.pos)
        if (sim != null) emit(Operacion.ASIGNAR, tElem, null, Operando.Variable(sim), s.pos)
        pilaRomper.add(lFin)
        pilaContinuar.add(lCont)
        try { genSentencia(s.cuerpo) } finally {
            pilaRomper.removeAt(pilaRomper.lastIndex)
            pilaContinuar.removeAt(pilaContinuar.lastIndex)
        }
        emit(Operacion.ETIQUETA, null, null, lCont, s.pos)
        val tSig = nuevoTemporal()
        emit(Operacion.MAS, ti, constante(1), tSig, s.pos)
        emit(Operacion.ASIGNAR, tSig, null, ti, s.pos)
        emit(Operacion.IR_A, null, null, lCond, s.pos)
        emit(Operacion.ETIQUETA, null, null, lFin, s.pos)
    }

    // La variable del para_cada no queda en la tabla simbolos del
    // semantico (se declara directo en el ambito del ciclo), asi que se
    // recupera del arbol de ambitos: es el ambito BLOQUE llamado
    // "para_cada" abierto en la posicion de esta sentencia.
    private fun buscarSimboloParaCada(amb: Ambito, s: SentParaCada): SimboloVariable? {
        if (amb.tipo == TipoAmbito.BLOQUE && amb.nombre == "para_cada" && amb.pos == s.pos) {
            amb.simbolos[s.variable]?.let { return it }
        }
        for (hijo in amb.hijos) buscarSimboloParaCada(hijo, s)?.let { return it }
        return null
    }

    // segun (sec. 4.5.3): el operando se evalua UNA vez en un temporal;
    // una cadena de == + si_verdadero salta a la etiqueta de cada caso
    // (o al defecto, o al fin); los cuerpos se emiten EN ORDEN TEXTUAL y
    // sin salto entre ellos: eso ES el fall-through de Java. romper -> Lfin.
    private fun genSegun(s: SentSegun) {
        val valor = genExpr(s.operando)
        val tOp = nuevoTemporal()
        emit(Operacion.ASIGNAR, valor, null, tOp, s.operando.pos)

        val lFin = nuevaEtiqueta()
        val etiquetaDe = LinkedHashMap<RamaSegun, Operando.Etiqueta>()
        for (rama in s.ramas) etiquetaDe[rama] = nuevaEtiqueta()
        val lDefecto = s.ramas.filterIsInstance<RamaDefecto>().firstOrNull()?.let { etiquetaDe[it] }

        // La cadena de comparaciones, en orden textual de los casos.
        for (rama in s.ramas) {
            if (rama !is RamaCaso) continue
            val lit = genExpr(rama.etiqueta)
            val tCmp = nuevoTemporal()
            emit(Operacion.IGUAL, tOp, lit, tCmp, rama.etiqueta.pos)
            emit(Operacion.SI_VERDADERO, tCmp, null, etiquetaDe.getValue(rama), rama.etiqueta.pos)
        }
        emit(Operacion.IR_A, null, null, lDefecto ?: lFin, s.pos)

        // Los cuerpos, sin salto entre ellos: quien no rompe, cae (fall-through).
        pilaRomper.add(lFin)
        try {
            for (rama in s.ramas) {
                emit(Operacion.ETIQUETA, null, null, etiquetaDe.getValue(rama), rama.pos)
                val sentencias = when (rama) {
                    is RamaCaso -> rama.sentencias
                    is RamaDefecto -> rama.sentencias
                    else -> emptyList()
                }
                for (interna in sentencias) genSentencia(interna)
            }
        } finally {
            pilaRomper.removeAt(pilaRomper.lastIndex)
        }
        emit(Operacion.ETIQUETA, null, null, lFin, s.pos)
    }

    // ------------------------ condiciones con saltos ------------------------

    // Cuando una condicion de si/mientras/hacer es DIRECTAMENTE un y/o, no
    // se materializa el booleano: se salta (sec. 4.5.3, cortocircuito).
    //   genSiFalso(a y b, L)  =  genSiFalso(a, L); genSiFalso(b, L)
    //   genSiFalso(a o b, L)  =  si_verdadero a -> Lok; genSiFalso(b, L); Lok:
    private fun genSiFalso(cond: Expr, destino: Operando.Etiqueta) {
        when {
            cond is Binaria && cond.op == OpBinario.Y -> {
                genSiFalso(cond.izq, destino)
                genSiFalso(cond.der, destino)
            }
            cond is Binaria && cond.op == OpBinario.O -> {
                val lOk = nuevaEtiqueta()
                genSiVerdadero(cond.izq, lOk)
                genSiFalso(cond.der, destino)
                emit(Operacion.ETIQUETA, null, null, lOk, cond.pos)
            }
            else -> {
                val v = genExpr(cond)
                emit(Operacion.SI_FALSO, v, null, destino, cond.pos)
            }
        }
    }

    private fun genSiVerdadero(cond: Expr, destino: Operando.Etiqueta) {
        when {
            cond is Binaria && cond.op == OpBinario.O -> {
                genSiVerdadero(cond.izq, destino)
                genSiVerdadero(cond.der, destino)
            }
            cond is Binaria && cond.op == OpBinario.Y -> {
                val lNo = nuevaEtiqueta()
                genSiFalso(cond.izq, lNo)
                genSiVerdadero(cond.der, destino)
                emit(Operacion.ETIQUETA, null, null, lNo, cond.pos)
            }
            else -> {
                val v = genExpr(cond)
                emit(Operacion.SI_VERDADERO, v, null, destino, cond.pos)
            }
        }
    }

    // ----------------------------- expresiones -------------------------------

    // Devuelve el operando que TIENE el valor de la expresion (una
    // constante, una variable o un temporal recien calculado).
    private fun genExpr(e: Expr): Operando = when (e) {
        is LitEntero -> Operando.Constante(Valor.VEntero(e.valor.toInt()))
        is LitFlotante -> Operando.Constante(Valor.VFlotante(e.valor))
        is LitCaracter -> Operando.Constante(Valor.VCaracter(e.valor))
        is LitCadena -> Operando.Constante(Valor.VCadena(e.valor))
        is LitBool -> Operando.Constante(Valor.VBooleano(e.valor))
        is LitNulo -> Operando.Constante(Valor.VNulo)

        is Nombre -> genNombre(e)

        is Este -> Operando.Variable(simboloEste!!)

        is Unaria -> genUnaria(e)

        is Binaria -> when (e.op) {
            OpBinario.Y -> genLogicoValor(e, esY = true)
            OpBinario.O -> genLogicoValor(e, esY = false)
            OpBinario.MAS -> {
                val ti = tipoDe(e.izq)
                val td = tipoDe(e.der)
                if (ti is TCadena || td is TCadena) genConcat(e) else genAritmetica(e, Operacion.MAS)
            }
            OpBinario.MENOS -> genAritmetica(e, Operacion.MENOS)
            OpBinario.POR -> genAritmetica(e, Operacion.POR)
            OpBinario.DIV -> genAritmetica(e, Operacion.DIV)
            OpBinario.MODULO -> genAritmetica(e, Operacion.MODULO)
            OpBinario.MENOR -> genAritmetica(e, Operacion.MENOR)
            OpBinario.MENOR_IGUAL -> genAritmetica(e, Operacion.MENOR_IGUAL)
            OpBinario.MAYOR -> genAritmetica(e, Operacion.MAYOR)
            OpBinario.MAYOR_IGUAL -> genAritmetica(e, Operacion.MAYOR_IGUAL)
            OpBinario.IGUAL_IGUAL -> genAritmetica(e, Operacion.IGUAL)
            OpBinario.DISTINTO -> genAritmetica(e, Operacion.DISTINTO)
        }

        is Asignacion -> genAsignacion(e)

        is Llamada -> genLlamada(e) ?: Operando.Constante(Valor.VNulo)

        is AccesoMiembro -> genAccesoMiembro(e)

        is AccesoIndice -> {
            val tArr = genExpr(e.arr)
            val tIndice = genExpr(e.indice)
            val t = nuevoTemporal()
            emit(Operacion.LEER_INDICE, tArr, tIndice, t, e.pos)
            t
        }

        is NuevoObjeto -> genNuevoObjeto(e)

        is NuevoArreglo -> {
            val tTamano = genExpr(e.tamano)
            val tipo = sem.tipos[e.id] as? TArreglo
            val elemento = tipo?.elemento?.descripcion ?: e.tipoElemento.nombre
            val t = nuevoTemporal()
            emit(Operacion.NUEVO_ARREGLO, Operando.Nombre(elemento), tTamano, t, e.pos)
            t
        }

        is LitArreglo -> genLitArreglo(e)

        else -> Operando.Constante(Valor.VNulo)   // Expr sealed: red de seguridad
    }

    // Nombre usado como valor. El semantico ya lo ligo: si es un campo de
    // la clase actual (y no estatico), la lectura es leer_campo sobre
    // `este`; si no, es una variable comun (local, parametro, global o
    // campo estatico, que vive en el almacen global).
    private fun genNombre(e: Nombre): Operando {
        val sim = sem.simbolos[e.id] as? SimboloVariable
            ?: return Operando.Constante(Valor.VNulo)   // programa con errores: no se genera
        return if (sim.categoria == Categoria.CAMPO && !sim.esEstatico) {
            val t = nuevoTemporal()
            emit(Operacion.LEER_CAMPO, Operando.Variable(simboloEste!!), Operando.Nombre(sim.nombre), t, e.pos)
            t
        } else {
            Operando.Variable(sim)
        }
    }

    private fun genAccesoMiembro(e: AccesoMiembro): Operando {
        val miembro = sem.simbolos[e.id]
        if (miembro is SimboloVariable) {
            if (miembro.esEstatico) return Operando.Variable(miembro)
            val tObj = genExpr(e.obj)
            val t = nuevoTemporal()
            emit(Operacion.LEER_CAMPO, tObj, Operando.Nombre(e.miembro), t, e.pos)
            return t
        }
        // Un metodo suelto no es un valor (solo se llama); con el
        // semantico en verde no se llega aca.
        return Operando.Constante(Valor.VNulo)
    }

    private fun genUnaria(e: Unaria): Operando = when (e.op) {
        OpUnario.NEGATIVO -> {
            // Pliegue del signo (sec. 4.4): -5 es la constante -5, no una
            // operacion. Asi tambien entra -2147483648, el minimo entero.
            when (val operando = e.operando) {
                is LitEntero -> Operando.Constante(Valor.VEntero((-operando.valor).toInt()))
                is LitFlotante -> Operando.Constante(Valor.VFlotante(-operando.valor))
                else -> {
                    val v = genExpr(operando)
                    val t = nuevoTemporal()
                    emit(Operacion.NEG, v, null, t, e.pos)
                    t
                }
            }
        }
        OpUnario.NO -> {
            val v = genExpr(e.operando)
            val t = nuevoTemporal()
            emit(Operacion.NO, v, null, t, e.pos)
            t
        }
        OpUnario.INCREMENTO, OpUnario.DECREMENTO -> genIncremento(e, comoSentencia = false)
    }

    // ++ / -- (sec. 4.5.3): el destino se evalua UNA vez (a[i++] no existe,
    // pero a[f()] si: f se llama una sola vez). Como sentencia o en forma
    // prefija el valor es el nuevo; como expresion postfija se copia el
    // viejo a un temporal ANTES de modificar y ese es el valor.
    private fun genIncremento(e: Unaria, comoSentencia: Boolean): Operando {
        val lugar = genLugar(e.operando)
        val uno: Operando = if (tipoDe(e.operando) is TFlotante) {
            Operando.Constante(Valor.VFlotante(1.0))
        } else {
            Operando.Constante(Valor.VEntero(1))
        }
        val op = if (e.op == OpUnario.INCREMENTO) Operacion.MAS else Operacion.MENOS
        val base = lugar.leer(e.pos)
        if (!e.esPrefijo && !comoSentencia) {
            val tViejo = nuevoTemporal()
            emit(Operacion.ASIGNAR, base, null, tViejo, e.pos)
            val tNuevo = nuevoTemporal()
            emit(op, base, uno, tNuevo, e.pos)
            lugar.escribir(tNuevo, e.pos)
            return tViejo
        }
        val tNuevo = nuevoTemporal()
        emit(op, base, uno, tNuevo, e.pos)
        lugar.escribir(tNuevo, e.pos)
        return tNuevo
    }

    // Aritmetica y comparaciones: postorden, cada operacion en un temporal
    // nuevo (sec. 4.5.3). Si un operando es entero y el otro flotante, el
    // entero se convierte con itof: en el TAC ambos son del mismo tipo.
    // (MODULO nunca: el semantico ya exigio entero % entero.)
    private fun genAritmetica(e: Binaria, op: Operacion): Operando {
        var tI = genExpr(e.izq)
        var tD = genExpr(e.der)
        if (op != Operacion.MODULO) {
            val ti = tipoDe(e.izq)
            val td = tipoDe(e.der)
            if (ti is TEntero && td is TFlotante) tI = itof(tI, e.izq.pos)
            if (td is TEntero && ti is TFlotante) tD = itof(tD, e.der.pos)
        }
        val t = nuevoTemporal()
        emit(op, tI, tD, t, e.pos)
        return t
    }

    // Concatenacion (sec. 4.5.2): concat une dos cadenas; el operando que
    // no es cadena pasa antes por a_cadena (el mismo builtin del lenguaje).
    private fun genConcat(e: Binaria): Operando {
        var tI = genExpr(e.izq)
        var tD = genExpr(e.der)
        if (tipoDe(e.izq) !is TCadena) tI = aCadena(tI, e.izq.pos)
        if (tipoDe(e.der) !is TCadena) tD = aCadena(tD, e.der.pos)
        val t = nuevoTemporal()
        emit(Operacion.CONCAT, tI, tD, t, e.pos)
        return t
    }

    // y / o como VALOR (sec. 4.5.4, ejemplo 4): el izquierdo se copia al
    // acumulador; si ya decide el resultado, se salta la evaluacion del
    // derecho. El derecho se genera en un BUFFER para que su temporal se
    // numere antes que el acumulador (t2 antes que t3, como en el doc),
    // aunque en el listado aparezca despues.
    private fun genLogicoValor(e: Binaria, esY: Boolean): Operando {
        val tIzq = genExpr(e.izq)
        val buffer = mutableListOf<Cuadrupla>()
        val salidaPrevia = salida
        salida = buffer
        val tDer = try {
            genExpr(e.der)
        } finally {
            salida = salidaPrevia
        }
        val tAcum = nuevoTemporal()
        val lFin = nuevaEtiqueta()
        emit(Operacion.ASIGNAR, tIzq, null, tAcum, e.izq.pos)
        emit(if (esY) Operacion.SI_FALSO else Operacion.SI_VERDADERO, tAcum, null, lFin, e.pos)
        salida.addAll(buffer)
        emit(Operacion.ASIGNAR, tDer, null, tAcum, e.der.pos)
        emit(Operacion.ETIQUETA, null, null, lFin, e.pos)
        return tAcum
    }

    // ----------------------------- asignacion --------------------------------

    // Un "lugar" donde se puede guardar un valor: variable, campo o
    // elemento de arreglo. Las subexpresiones del destino (el objeto, el
    // indice) se evaluan UNA vez al crear el lugar; leer y escribir las
    // reusan. Es lo que hace seguro a[f()]++.
    private class Lugar(
        val leer: (Posicion) -> Operando,
        val escribir: (Operando, Posicion) -> Unit
    )

    private fun genLugar(destino: Expr): Lugar = when (destino) {
        is Nombre -> {
            val sim = sem.simbolos[destino.id] as? SimboloVariable
            if (sim != null && sim.categoria == Categoria.CAMPO && !sim.esEstatico) {
                val este = Operando.Variable(simboloEste!!)
                Lugar(
                    leer = { pos ->
                        val t = nuevoTemporal()
                        emit(Operacion.LEER_CAMPO, este, Operando.Nombre(sim.nombre), t, pos)
                        t
                    },
                    escribir = { v, pos -> emit(Operacion.ASIGNAR_CAMPO, este, Operando.Nombre(sim.nombre), v, pos) }
                )
            } else if (sim != null) {
                val variable = Operando.Variable(sim)
                Lugar(
                    leer = { variable },
                    escribir = { v, pos -> emit(Operacion.ASIGNAR, v, null, variable, pos) }
                )
            } else {
                // Con el semantico en verde no se llega: un destino siempre
                // esta ligado. Se deja un lugar mudo para no romper.
                Lugar(leer = { Operando.Constante(Valor.VNulo) }, escribir = { _, _ -> })
            }
        }
        is AccesoMiembro -> {
            val miembro = sem.simbolos[destino.id]
            if (miembro is SimboloVariable && miembro.esEstatico) {
                val variable = Operando.Variable(miembro)
                Lugar(
                    leer = { variable },
                    escribir = { v, pos -> emit(Operacion.ASIGNAR, v, null, variable, pos) }
                )
            } else {
                val tObj = genExpr(destino.obj)
                val campo = Operando.Nombre(destino.miembro)
                Lugar(
                    leer = { pos ->
                        val t = nuevoTemporal()
                        emit(Operacion.LEER_CAMPO, tObj, campo, t, pos)
                        t
                    },
                    escribir = { v, pos -> emit(Operacion.ASIGNAR_CAMPO, tObj, campo, v, pos) }
                )
            }
        }
        is AccesoIndice -> {
            val tArr = genExpr(destino.arr)
            val tIndice = genExpr(destino.indice)
            Lugar(
                leer = { pos ->
                    val t = nuevoTemporal()
                    emit(Operacion.LEER_INDICE, tArr, tIndice, t, pos)
                    t
                },
                escribir = { v, pos -> emit(Operacion.ASIGNAR_INDICE, tArr, tIndice, v, pos) }
            )
        }
        else -> Lugar(leer = { Operando.Constante(Valor.VNulo) }, escribir = { _, _ -> })
    }

    // Asignacion (sec. 4.5.3): = copia (con itof si el destino es flotante
    // y el valor entero); las compuestas son x = x op e. La expresion
    // devuelve el valor asignado (la asignacion es asociativa a derecha).
    private fun genAsignacion(e: Asignacion): Operando {
        val lugar = genLugar(e.destino)
        val tDestino = tipoDe(e.destino)
        if (e.op == OpAsignacion.ASIGNA) {
            var v = genExpr(e.valor)
            v = convertir(v, tipoDe(e.valor), tDestino, e.valor.pos)
            lugar.escribir(v, e.pos)
            return v
        }
        val viejo = lugar.leer(e.pos)
        var vDer = genExpr(e.valor)
        val tValor = tipoDe(e.valor)
        val t: Operando
        if (e.op == OpAsignacion.MAS_ASIGNA && tDestino is TCadena) {
            if (tValor !is TCadena) vDer = aCadena(vDer, e.valor.pos)
            t = nuevoTemporal()
            emit(Operacion.CONCAT, viejo, vDer, t, e.pos)
        } else {
            val op = when (e.op) {
                OpAsignacion.MAS_ASIGNA -> Operacion.MAS
                OpAsignacion.MENOS_ASIGNA -> Operacion.MENOS
                OpAsignacion.POR_ASIGNA -> Operacion.POR
                OpAsignacion.DIV_ASIGNA -> Operacion.DIV
                OpAsignacion.MOD_ASIGNA -> Operacion.MODULO
                else -> Operacion.MAS   // ASIGNA ya salio arriba
            }
            if (op != Operacion.MODULO && tDestino is TFlotante && tValor is TEntero) {
                vDer = itof(vDer, e.valor.pos)
            }
            t = nuevoTemporal()
            emit(op, viejo, vDer, t, e.pos)
        }
        lugar.escribir(t, e.pos)
        return t
    }

    // ------------------------------ llamadas ---------------------------------

    // Llamada (sec. 4.5.2): los argumentos se apilan con param EN ORDEN
    // (izquierda a derecha, sec. 2.14) y llamar los consume. Devuelve el
    // temporal que recibe el retorno, o null si la funcion es vacio.
    private fun genLlamada(e: Llamada): Operando? {
        // Llamada a funcion de paquete (Moka 2, P1): el semantico ya la
        // ligo a su nombre cualificado ("util.sumar").
        sem.llamadasPaquete[e.id]?.let { f ->
            genArgumentos(e.args, f.parametros, false)
            return emitirLlamada(f.nombre, e.args.size, f.tipo, e.pos)
        }
        return when (val callee = e.callee) {
            is Nombre -> {
                val f = sem.simbolos[callee.id] as? SimboloFuncion ?: return null
                genArgumentos(e.args, f.parametros, f.esBuiltin)
                emitirLlamada(f.nombre, e.args.size, f.tipo, e.pos)
            }
            is AccesoMiembro -> {
                val m = sem.simbolos[callee.id]
                if (m !is SimboloFuncion) {
                    null
                } else if (m.esEstatico) {
                    // Util.doble(21): sin este; la clase sale del ligado del Nombre.
                    val objNombre = callee.obj as? Nombre
                    val claseNombre = (objNombre?.let { sem.simbolos[it.id] } as? SimboloClase)?.nombre
                        ?: nombreClaseDe(callee.obj)
                    genArgumentos(e.args, m.parametros, false)
                    emitirLlamada("$claseNombre.${m.nombre}", e.args.size, m.tipo, e.pos)
                } else {
                    // obj.metodo(...): este es el primer parametro (sec. 4.5.3)
                    // y el metodo se nombra Clase.metodo.
                    val tObj = genExpr(callee.obj)
                    emit(Operacion.PARAM, tObj, null, null, callee.obj.pos)
                    genArgumentos(e.args, m.parametros, false)
                    emitirLlamada("${nombreClaseDe(callee.obj)}.${m.nombre}", e.args.size + 1, m.tipo, e.pos)
                }
            }
            else -> null
        }
    }

    private fun nombreClaseDe(obj: Expr): String =
        (sem.tipos[obj.id] as? TClase)?.nombre ?: "?"

    private fun genArgumentos(args: List<Expr>, parametros: List<Tipo>, esBuiltin: Boolean) {
        for (i in args.indices) {
            val arg = args[i]
            var v = genExpr(arg)
            // Promocion entero -> flotante en el pasaje (sec. 4.4). Los
            // builtins no tienen firma declarada: aceptan lo que venga.
            if (!esBuiltin && i < parametros.size) {
                v = convertir(v, tipoDe(arg), parametros[i], arg.pos)
            }
            emit(Operacion.PARAM, v, null, null, arg.pos)
        }
    }

    private fun emitirLlamada(nombre: String, n: Int, retorno: Tipo?, pos: Posicion): Operando? {
        return if (retorno == null || retorno is TVacio || retorno is TError) {
            emit(Operacion.LLAMAR, Operando.Nombre(nombre), constante(n), null, pos)
            null
        } else {
            val t = nuevoTemporal()
            emit(Operacion.LLAMAR, Operando.Nombre(nombre), constante(n), t, pos)
            t
        }
    }

    // nuevo Alumno(...): reserva el objeto, lo apila como `este`, apila
    // los argumentos y llama Alumno.constructor (sec. 4.5.4, ejemplo 7).
    // El valor de la expresion es el OBJETO (t1), no el retorno del ctor.
    private fun genNuevoObjeto(e: NuevoObjeto): Operando {
        val clase = sem.simbolos[e.id] as? SimboloClase
        // El nombre resuelto (cualificado si la clase vive en un paquete,
        // Moka 2 P1), no el texto que escribio el programador.
        val nombreClase = clase?.nombre ?: e.clase
        val t = nuevoTemporal()
        emit(Operacion.NUEVO_OBJETO, Operando.Nombre(nombreClase), null, t, e.pos)
        emit(Operacion.PARAM, t, null, null, e.pos)
        val parametros = clase?.constructor?.parametros ?: emptyList()
        genArgumentos(e.args, parametros, false)
        emit(Operacion.LLAMAR, Operando.Nombre("$nombreClase.constructor"), constante(e.args.size + 1), null, e.pos)
        return t
    }

    // [2, 3, 5]: nuevo_arreglo del tamaño justo y una escritura por
    // elemento, con itof donde la mezcla entero/flotante lo pida (sec. 2.9).
    private fun genLitArreglo(e: LitArreglo): Operando {
        val tipo = sem.tipos[e.id] as? TArreglo
        val elemento = tipo?.elemento ?: TError
        val t = nuevoTemporal()
        emit(Operacion.NUEVO_ARREGLO, Operando.Nombre(elemento.descripcion), constante(e.elementos.size), t, e.pos)
        for (i in e.elementos.indices) {
            val elem = e.elementos[i]
            var v = genExpr(elem)
            v = convertir(v, tipoDe(elem), elemento, elem.pos)
            emit(Operacion.ASIGNAR_INDICE, t, constante(i), v, elem.pos)
        }
        return t
    }

    // ----------------------------- utilidades --------------------------------

    private fun tipoDe(e: Expr): Tipo = sem.tipos[e.id] ?: TError

    // itof donde el semantico acepto una promocion entero -> flotante
    // (sec. 4.5.3: "la conversion ya es explicita" en el TAC).
    private fun convertir(v: Operando, origen: Tipo, destino: Tipo, pos: Posicion): Operando =
        if (destino is TFlotante && origen is TEntero) itof(v, pos) else v

    private fun itof(v: Operando, pos: Posicion): Operando {
        val t = nuevoTemporal()
        emit(Operacion.ITOF, v, null, t, pos)
        return t
    }

    private fun aCadena(v: Operando, pos: Posicion): Operando {
        emit(Operacion.PARAM, v, null, null, pos)
        val t = nuevoTemporal()
        emit(Operacion.LLAMAR, Operando.Nombre("a_cadena"), constante(1), t, pos)
        return t
    }

    private fun constante(n: Int): Operando = Operando.Constante(Valor.VEntero(n))

    private fun nuevoTemporal(): Operando.Temporal = Operando.Temporal(++contadorTemporal)

    private fun nuevaEtiqueta(): Operando.Etiqueta = Operando.Etiqueta("L${++contadorEtiqueta}")

    // El indice queda en -1 (provisorio): se fija en la renumeracion final
    // de ejecutar(), despues de injertar los buffers del cortocircuito.
    private fun emit(op: Operacion, arg1: Operando?, arg2: Operando?, resultado: Operando?, origen: Posicion?) {
        salida.add(Cuadrupla(-1, op, arg1, arg2, resultado, origen))
    }
}
