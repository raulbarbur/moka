// ============================================================================
// AnalizadorSemantico.kt - Fase 3 completa: ambitos, nombres y tipos
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/AnalizadorSemantico.kt
// (documento de diseno, seccion 4.4)
//
// El analisis semantico responde las preguntas que la sintaxis no puede:
// ¿existe esta variable? ¿los tipos cierran? ¿toda funcion devuelve lo que
// promete? Trabaja sobre el AST (nunca sobre el parse tree) y NO lo modifica:
// todo lo que calcula va a TABLAS LATERALES indexadas por Nodo.id:
//
//   tipos:     Map<Int, Tipo>     el tipo de cada expresion y TipoAst
//   simbolos:  Map<Int, Simbolo>  a que simbolo se liga cada nombre/declaracion
//
// LAS DOS PASADAS (seccion 4.4):
//
//   Pasada A (declaraciones): registra en el ambito global los built-ins,
//     los "cascarones" de las clases (solo el nombre: asi una clase puede
//     nombrar a otra declarada mas abajo), los miembros de cada clase y
//     las firmas de las funciones toplevel. Es lo que habilita el hoisting
//     de funciones y clases (seccion 2.8). Las variables NO se hoistean.
//
//   Pasada B (cuerpos): recorre el programa EN ORDEN TEXTUAL abriendo y
//     cerrando ambitos, declarando variables a medida que aparecen (asi un
//     uso anterior a la declaracion no encuentra nada y se reporta),
//     ligando cada Nombre contra la cadena de ambitos y tipando cada
//     expresion. El tipado de expresiones vive en Tipificador.kt.
//
// CODIGOS DE ERROR: los del catalogo (SEM-001 a SEM-015) respetan el texto
// de la seccion 4.4. Las reglas que el documento lista sin codigo usan
// SEM-016 en adelante, con el mismo estilo de mensaje.
//
// RECORRIDO: when exhaustivo sobre las clases selladas del AST, propio de
// esta fase. NO se usa el visitor de ANTLR: ese es del parse tree.
// ============================================================================

package ar.com.moka.core.semantico

import ar.com.moka.core.ast.*

// Lo que la fase entrega al pipeline (Artefacto.Semantico de la seccion
// 4.7): la raiz del arbol de ambitos, las dos tablas laterales, el
// registro de chequeos de tipo y los diagnosticos.
data class ResultadoSemantico(
    val raiz: Ambito,
    val tipos: Map<Int, Tipo>,
    val simbolos: Map<Int, Simbolo>,
    val chequeos: List<Chequeo>,
    val diagnosticos: List<Diagnostico>,
    // Moka 2 (P1): de que paquete vino cada declaracion toplevel (las del
    // archivo principal no figuran) y las llamadas a funcion de paquete
    // ya ligadas a su nombre cualificado ("util.sumar"), por id de nodo.
    val paqueteDe: Map<Int, String> = emptyMap(),
    val llamadasPaquete: Map<Int, SimboloFuncion> = emptyMap()
)

// Moka 2 (P1): lo que el resolvedor de paquetes le pasa al semantico.
//  - paqueteDe: declaracion toplevel -> paquete del que vino.
//  - usosDe: paquete -> sus `usar` ("" es el archivo principal). Sus
//    claves (menos "") son los paquetes cargados.
data class ContextoPaquetes(
    val paqueteDe: Map<Int, String> = emptyMap(),
    val usosDe: Map<String, List<String>> = emptyMap()
)

class AnalizadorSemantico {

    // ------------------------- estado compartido -------------------------
    // Lo usa tambien el Tipificador (mismo paquete, misma pasada).

    internal val tipos = mutableMapOf<Int, Tipo>()
    internal val simbolos = mutableMapOf<Int, Simbolo>()
    internal val chequeos = mutableListOf<Chequeo>()
    internal val diag = AcumuladorDiagnosticos()

    // Contexto de la pasada B: donde estamos parados AHORA.
    internal var claseActual: SimboloClase? = null       // para `este` y los campos
    internal var retornoActual: Tipo? = null             // null: codigo toplevel
    internal var nombreContextoRetorno: String = ""      // "la funcion 'f'", para mensajes
    internal var estaticoActual: Boolean = false         // un miembro estatico no usa `este`
    internal var dentroCiclo: Int = 0
    internal var dentroSegun: Int = 0

    // Moka 2 (P1): el paquete de la declaracion que se esta chequeando
    // (null en el archivo principal) y las llamadas a paquetes ya ligadas.
    internal var paqueteActual: String? = null
    internal var contextoPaquetes: ContextoPaquetes = ContextoPaquetes()
    internal val llamadasPaquete = mutableMapOf<Int, SimboloFuncion>()

    // Variables de para_cada: de solo lectura en el cuerpo (seccion 2.7).
    // Se identifican por idUnico, no por nombre: un sombreado interno es
    // otra variable y SI se puede modificar.
    internal val idsParaCada = mutableSetOf<String>()

    // Contador para los idUnico: la primera `x` es "x", la segunda "x#2".
    private val contadorPorNombre = mutableMapOf<String, Int>()

    private val tipificador = Tipificador(this)

    companion object {
        // Punto de entrada comodo: AnalizadorSemantico.analizar(ast)
        fun analizar(programa: ProgramaAst, paquetes: ContextoPaquetes = ContextoPaquetes()): ResultadoSemantico =
            AnalizadorSemantico().ejecutar(programa, paquetes)
    }

    // ------------------------------ orquestacion --------------------------

    private fun ejecutar(programa: ProgramaAst, paquetes: ContextoPaquetes): ResultadoSemantico {
        val raiz = Ambito(TipoAmbito.GLOBAL, "global", padre = null, pos = null)
        contextoPaquetes = paquetes

        // ---- Pasada A ----
        registrarBuiltins(raiz)
        for (d in programa.declaraciones) {
            if (d is DeclClase) {
                paqueteActual = paquetes.paqueteDe[d.id]
                registrarCascaronClase(d, raiz)
            }
        }
        for (d in programa.declaraciones) {
            paqueteActual = paquetes.paqueteDe[d.id]
            when (d) {
                is DeclClase -> completarMiembrosClase(d, raiz)
                is DeclFuncion -> registrarFirmaFuncion(d, raiz)
                else -> { }   // las sentencias toplevel son de la pasada B
            }
        }

        // ---- Pasada B ----
        for (d in programa.declaraciones) {
            if (diag.completo) break
            paqueteActual = paquetes.paqueteDe[d.id]
            when (d) {
                is DeclClase -> chequearCuerposClase(d, raiz)
                is DeclFuncion -> chequearCuerpoFuncion(d, raiz)
                is Sent -> chequearSentencia(d, raiz)
                else -> { }   // no hay otra alternativa toplevel en la gramatica
            }
        }

        advertirVariablesSinUso(raiz)

        return ResultadoSemantico(
            raiz = raiz,
            tipos = tipos,
            simbolos = simbolos,
            paqueteDe = paquetes.paqueteDe,
            llamadasPaquete = llamadasPaquete,
            chequeos = chequeos,
            diagnosticos = diag.diagnosticos
        )
    }

    // ------------------------------ pasada A -------------------------------

    // Las funciones incorporadas (seccion 2.4) son simbolos comunes con el
    // flag esBuiltin: se declaran en el global antes que todo, asi un
    // programa puede llamarlas desde la primera linea y hasta redeclarar
    // el nombre da error como con cualquier funcion.
    private fun registrarBuiltins(raiz: Ambito) {
        val posInterna = Posicion(0, 0, 0, 0)
        fun b(nombre: String, retorno: Tipo) {
            raiz.declarar(SimboloFuncion(nombre, retorno, posInterna, emptyList(), esBuiltin = true))
        }
        b("imprimir", TVacio)
        b("imprimir_sin_salto", TVacio)
        b("leer", TCadena)
        b("leer_entero", TEntero)
        b("leer_flotante", TFlotante)
        b("longitud", TEntero)
        b("a_entero", TEntero)
        b("a_flotante", TFlotante)
        b("a_cadena", TCadena)
        b("a_booleano", TBooleano)
        // Consola con estilo: limpiar, color, restaurar_color, pausa y
        // titulo. Devuelven vacio: son instrucciones, no valores.
        b("limpiar", TVacio)
        b("color", TVacio)
        b("restaurar_color", TVacio)
        b("pausa", TVacio)
        b("titulo", TVacio)
    }

    // Primer tiempo de las clases: solo el nombre. Una clase puede
    // referenciar a otra declarada despues (`clase A { B b; }`), asi que
    // todos los nombres tienen que existir antes de resolver UN solo tipo.
    private fun registrarCascaronClase(decl: DeclClase, raiz: Ambito) {
        val clase = SimboloClase(cualificar(decl.nombre), decl.pos, mutableMapOf(), mutableMapOf(), null)
        if (!raiz.declarar(clase)) {
            val previa = raiz.clases.getValue(decl.nombre)
            diag.error(
                "SEM-020",
                "La clase '${decl.nombre}' ya fue declarada (linea ${previa.pos.linea}).",
                decl.pos
            )
            return
        }
        simbolos[decl.id] = clase
    }

    // Segundo tiempo: campos, metodos y constructor. Ya estan TODOS los
    // cascarones, asi que cualquier tipo de clase se resuelve aca.
    private fun completarMiembrosClase(decl: DeclClase, raiz: Ambito) {
        val clase = raiz.clases[cualificar(decl.nombre)] ?: return   // hubo duplicado: ya se reporto

        for (campo in decl.campos) {
            val tipo = resolverTipo(campo.tipo, raiz, permitirVacio = false)
            val sim = SimboloVariable(
                nombre = campo.nombre,
                tipo = tipo,
                pos = campo.pos,
                categoria = Categoria.CAMPO,
                esConstante = campo.esConstante,
                esEstatico = campo.esEstatico,
                idUnico = idUnico(campo.nombre)
            )
            if (clase.campos.containsKey(campo.nombre)) {
                diag.error(
                    "SEM-002",
                    "El campo '${campo.nombre}' ya fue declarado en la clase '${decl.nombre}' (linea ${clase.campos.getValue(campo.nombre).pos.linea}).",
                    campo.pos
                )
            } else {
                clase.campos[campo.nombre] = sim
                simbolos[campo.id] = sim
            }
            if (campo.esConstante && campo.inicial == null) {
                diag.error("SEM-021", "La constante '${campo.nombre}' necesita un valor inicial.", campo.pos)
            }
        }

        for (metodo in decl.metodos) {
            val sim = firmaDeMetodo(metodo, raiz)
            if (clase.metodos.containsKey(metodo.nombre)) {
                diag.error(
                    "SEM-022",
                    "El metodo '${metodo.nombre}' ya fue declarado en la clase '${decl.nombre}' (linea ${clase.metodos.getValue(metodo.nombre).pos.linea}).",
                    metodo.pos
                )
            } else {
                clase.metodos[metodo.nombre] = sim
            }
            simbolos[metodo.id] = sim
        }

        decl.constructor?.let { ctor ->
            val params = ctor.params.map { resolverTipo(it.tipo, raiz, permitirVacio = false) }
            clase.constructor = SimboloFuncion("constructor", TVacio, ctor.pos, params, esBuiltin = false)
            simbolos[ctor.id] = clase.constructor!!
        }
    }

    private fun firmaDeMetodo(metodo: DeclFuncion, raiz: Ambito): SimboloFuncion {
        val retorno = resolverTipo(metodo.retorno, raiz, permitirVacio = true)
        val params = metodo.params.map { resolverTipo(it.tipo, raiz, permitirVacio = false) }
        return SimboloFuncion(metodo.nombre, retorno, metodo.pos, params, esBuiltin = false, esEstatico = metodo.esEstatico)
    }

    // --------------------- paquetes (Moka 2, P1) ---------------------

    // Nombre cualificado de una declaracion toplevel: dentro de un
    // paquete, "sumar" se registra como "util.sumar".
    internal fun cualificar(nombre: String): String =
        paqueteActual?.let { "$it.$nombre" } ?: nombre

    // Resolucion con paquete: primero en el paquete actual (lo propio
    // gana, como en Java) y despues en el global (principal y builtins).
    internal fun resolverFuncionVisible(amb: Ambito, nombre: String): SimboloFuncion? =
        paqueteActual?.let { amb.resolverFuncion("$it.$nombre") } ?: amb.resolverFuncion(nombre)

    internal fun resolverClaseVisible(amb: Ambito, nombre: String): SimboloClase? =
        paqueteActual?.let { amb.resolverClase("$it.$nombre") } ?: amb.resolverClase(nombre)

    // Prefijo de paquete de un nombre cualificado ("geometria.Punto" ->
    // "geometria"); null si el nombre es simple (Moka 2 P2).
    internal fun paqueteDeNombreCualificado(nombre: String): String? =
        if ('.' in nombre) nombre.substringBeforeLast('.') else null

    // Regla P2 para tipos escritos con paquete: el prefijo tiene que ser
    // el paquete actual o uno pedido con `usar`. Devuelve true cuando el
    // nombre cualificado es utilizable; emite SEM-069 (el paquete esta
    // cargado pero falta el `usar`) y devuelve false cuando no. Un nombre
    // simple, o un paquete que ni siquiera se cargo, no se decide aca:
    // lo resuelve el llamador con su error de siempre.
    internal fun tipoCualificadoUsable(nombre: String, pos: Posicion, accion: String): Boolean {
        val paquete = paqueteDeNombreCualificado(nombre) ?: return true
        if (paquete == paqueteActual || paqueteUsable(paquete)) return true
        if (!paqueteCargadoNoUsado(paquete)) return true   // no cargado: SEM-035 lo reporta el llamador
        diag.error("SEM-069", "Para $accion '$nombre' falta pedir el paquete: escribi 'usar $paquete;' al principio del archivo.", pos)
        return false
    }

    // ¿El archivo actual pidio este paquete con `usar`?
    internal fun paqueteUsable(nombre: String): Boolean =
        nombre in (contextoPaquetes.usosDe[paqueteActual ?: ""] ?: emptyList())

    // Paquetes cargados pero NO pedidos por el archivo actual (para el
    // error didactico "te falta usar X;").
    internal fun paqueteCargadoNoUsado(nombre: String): Boolean =
        nombre in contextoPaquetes.usosDe.keys - "" && !paqueteUsable(nombre)

    private fun registrarFirmaFuncion(decl: DeclFuncion, raiz: Ambito) {
        val retorno = resolverTipo(decl.retorno, raiz, permitirVacio = true)
        val params = decl.params.map { resolverTipo(it.tipo, raiz, permitirVacio = false) }
        // Las funciones de un paquete se registran cualificadas
        // ("util.sumar"): dos paquetes pueden tener la misma funcion.
        val sim = SimboloFuncion(cualificar(decl.nombre), retorno, decl.pos, params, esBuiltin = false)
        if (!raiz.declarar(sim)) {
            diag.error(
                "SEM-023",
                "La funcion '${decl.nombre}' ya fue declarada (linea ${raiz.funciones.getValue(decl.nombre).pos.linea}).",
                decl.pos
            )
            return
        }
        simbolos[decl.id] = sim
    }

    // --------------------- pasada B: clases y funciones ---------------------

    private fun chequearCuerposClase(decl: DeclClase, raiz: Ambito) {
        val clase = raiz.clases[cualificar(decl.nombre)] ?: return
        val ambitoClase = raiz.abrir(TipoAmbito.CLASE, "clase ${decl.nombre}", decl.pos)

        // Los campos viven en el ambito de la clase: un metodo los nombra
        // sin `este.` y la resolucion sube por la cadena hasta aca.
        for (campo in clase.campos.values) ambitoClase.declarar(campo)
        for (metodo in clase.metodos.values) ambitoClase.declarar(metodo)

        val clasePrevia = claseActual
        claseActual = clase
        try {
            // Inicializadores de campos: corren como si fueran parte del
            // constructor; un campo estatico no puede tocar `este`.
            for (campoDecl in decl.campos) {
                val sim = clase.campos[campoDecl.nombre] ?: continue
                estaticoActual = campoDecl.esEstatico
                val inicial = campoDecl.inicial
                if (inicial != null) {
                    val tIni = tipificador.tiparValor(inicial, ambitoClase)
                    verificarAsignable(sim.tipo, tIni, inicial, "inicializacion del campo '${campoDecl.nombre}'")
                }
            }

            decl.constructor?.let { ctor ->
                estaticoActual = false
                chequearCuerpoInvocable(
                    params = ctor.params,
                    cuerpo = ctor.cuerpo,
                    padre = ambitoClase,
                    nombreAmbito = "constructor de ${decl.nombre}",
                    retorno = TVacio,
                    nombreRetorno = "el constructor de '${decl.nombre}'",
                    exigirRetorno = false
                )
            }

            for (metodo in decl.metodos) {
                estaticoActual = metodo.esEstatico
                val sim = clase.metodos[metodo.nombre] ?: continue
                chequearCuerpoInvocable(
                    params = metodo.params,
                    cuerpo = metodo.cuerpo,
                    padre = ambitoClase,
                    nombreAmbito = "metodo ${metodo.nombre}",
                    retorno = sim.tipo,
                    nombreRetorno = "el metodo '${metodo.nombre}'",
                    exigirRetorno = sim.tipo !is TVacio && sim.tipo !is TError
                )
            }
        } finally {
            claseActual = clasePrevia
            estaticoActual = false
        }
    }

    private fun chequearCuerpoFuncion(decl: DeclFuncion, raiz: Ambito) {
        val sim = raiz.funciones[cualificar(decl.nombre)] ?: return
        estaticoActual = false
        chequearCuerpoInvocable(
            params = decl.params,
            cuerpo = decl.cuerpo,
            padre = raiz,
            nombreAmbito = "funcion ${decl.nombre}",
            retorno = sim.tipo,
            nombreRetorno = "la funcion '${decl.nombre}'",
            exigirRetorno = sim.tipo !is TVacio && sim.tipo !is TError
        )
    }

    // Lo comun a funciones, metodos y constructores: abrir el ambito de la
    // funcion, declarar los parametros (viven en el MISMO ambito que el
    // cuerpo, seccion 2.5) y analizar el bloque. Si la funcion no es vacio,
    // todos los caminos tienen que retornar (seccion 4.4, SEM-009).
    private fun chequearCuerpoInvocable(
        params: List<Param>,
        cuerpo: Bloque,
        padre: Ambito,
        nombreAmbito: String,
        retorno: Tipo,
        nombreRetorno: String,
        exigirRetorno: Boolean
    ) {
        val ambito = padre.abrir(TipoAmbito.FUNCION, nombreAmbito, cuerpo.pos)
        for (param in params) {
            val tipo = resolverTipo(param.tipo, padre, permitirVacio = false)
            val sim = SimboloVariable(
                nombre = param.nombre,
                tipo = tipo,
                pos = param.pos,
                categoria = Categoria.PARAMETRO,
                esConstante = false,
                esEstatico = false,
                idUnico = idUnico(param.nombre)
            )
            if (!ambito.declarar(sim)) {
                diag.error("SEM-024", "El parametro '${param.nombre}' esta repetido.", param.pos)
            } else {
                simbolos[param.id] = sim
            }
        }

        val retornoPrevio = retornoActual
        val nombrePrevio = nombreContextoRetorno
        retornoActual = retorno
        nombreContextoRetorno = nombreRetorno
        try {
            // El cuerpo NO abre otro ambito: sus declaraciones conviven
            // con los parametros (redeclarar un parametro aca es error).
            for (s in cuerpo.sentencias) {
                if (diag.completo) break
                chequearSentencia(s, ambito)
            }
        } finally {
            retornoActual = retornoPrevio
            nombreContextoRetorno = nombrePrevio
        }

        if (exigirRetorno && !siempreRetorna(cuerpo)) {
            diag.error(
                "SEM-009",
                "La funcion '$nombreAmbito' puede terminar sin retornar un valor de tipo ${retorno.descripcion}.",
                cuerpo.pos
            )
        }
    }

    // Analisis de caminos de retorno (seccion 4.4): un bloque retorna si
    // alguna de sus sentencias retorna (lo que sigue es inalcanzable); un
    // si CON sino retorna si ambas ramas retornan; el resto no cuenta (un
    // ciclo puede no ejecutar ni una vuelta).
    private fun siempreRetorna(s: Sent): Boolean = when (s) {
        is SentRetornar -> true
        is Bloque -> s.sentencias.any { siempreRetorna(it) }
        is SentSi -> s.sino != null && siempreRetorna(s.entonces) && siempreRetorna(s.sino)
        else -> false
    }

    // ----------------------- pasada B: sentencias --------------------------

    internal fun chequearSentencia(s: Sent, amb: Ambito) {
        when (s) {
            is Bloque -> {
                val hijo = amb.abrir(TipoAmbito.BLOQUE, "bloque", s.pos)
                for (interna in s.sentencias) {
                    if (diag.completo) break
                    chequearSentencia(interna, hijo)
                }
            }

            is DeclVariable -> chequearDeclVariable(s, amb)

            is SentSi -> {
                chequearCondicion(s.cond, amb, "si")
                advertirDeclaracionSinLlaves(s.entonces, "si")
                if (s.sino != null) advertirDeclaracionSinLlaves(s.sino, "sino")
                chequearSentencia(s.entonces, amb)
                s.sino?.let { chequearSentencia(it, amb) }
            }

            is SentMientras -> {
                chequearCondicion(s.cond, amb, "mientras")
                dentroCiclo++
                try { chequearSentencia(s.cuerpo, amb) } finally { dentroCiclo-- }
            }

            is SentHacer -> {
                dentroCiclo++
                try { chequearSentencia(s.cuerpo, amb) } finally { dentroCiclo-- }
                chequearCondicion(s.cond, amb, "hacer...mientras")
            }

            is SentPara -> {
                // El para abre su propio ambito: la variable del inicio
                // existe solo dentro del ciclo (seccion 2.7).
                val ambitoPara = amb.abrir(TipoAmbito.BLOQUE, "para", s.pos)
                when (val inicio = s.inicio) {
                    is DeclVariable -> chequearDeclVariable(inicio, ambitoPara)
                    is Expr -> tipificador.tipar(inicio, ambitoPara)
                    null -> { }   // para (;; ...)
                    else -> { }   // no hay otra forma de inicio
                }
                s.cond?.let { chequearCondicion(it, ambitoPara, "para") }
                s.paso?.let { tipificador.tipar(it, ambitoPara) }
                dentroCiclo++
                try { chequearSentencia(s.cuerpo, ambitoPara) } finally { dentroCiclo-- }
            }

            is SentParaCada -> {
                val tColeccion = tipificador.tiparValor(s.coleccion, amb)
                val tElemento = when (tColeccion) {
                    is TArreglo -> tColeccion.elemento
                    is TError -> TError
                    else -> {
                        diag.error(
                            "SEM-025",
                            "El 'para_cada' recorre arreglos, pero '${textoDe(s.coleccion)}' es de tipo ${tColeccion.descripcion}.",
                            s.coleccion.pos
                        )
                        TError
                    }
                }
                val tDeclarado = resolverTipo(s.tipo, amb, permitirVacio = false)
                if (tElemento !is TError && tDeclarado !is TError && !esAsignable(tDeclarado, tElemento)) {
                    diag.error(
                        "SEM-026",
                        "La variable del 'para_cada' es ${tDeclarado.descripcion} pero el arreglo tiene elementos ${tElemento.descripcion}.",
                        s.tipo.pos
                    )
                }
                registrarChequeo(
                    s.pos, "para_cada (${textoTipo(s.tipo)} ${s.variable} : ${textoDe(s.coleccion)})",
                    "para_cada: elemento ${tElemento.descripcion} -> variable ${tDeclarado.descripcion}",
                    tDeclarado, tElemento, esAsignable(tDeclarado, tElemento)
                )
                val ambitoPara = amb.abrir(TipoAmbito.BLOQUE, "para_cada", s.pos)
                val sim = SimboloVariable(
                    nombre = s.variable,
                    tipo = tDeclarado,
                    pos = s.pos,
                    categoria = Categoria.LOCAL,
                    esConstante = false,
                    esEstatico = false,
                    idUnico = idUnico(s.variable)
                )
                if (!ambitoPara.declarar(sim)) {
                    diag.error("SEM-002", "La variable '${s.variable}' ya fue declarada en este bloque.", s.pos)
                }
                sim.usada = true   // no advertir "nunca usada": es el mecanismo del ciclo
                idsParaCada.add(sim.idUnico)
                dentroCiclo++
                try { chequearSentencia(s.cuerpo, ambitoPara) } finally {
                    dentroCiclo--
                    idsParaCada.remove(sim.idUnico)
                }
            }

            is SentSegun -> {
                val tOperando = tipificador.tiparValor(s.operando, amb)
                val operandoValido = tOperando is TEntero || tOperando is TCaracter || tOperando is TCadena
                if (tOperando !is TError && !operandoValido) {
                    diag.error(
                        "SEM-027",
                        "El 'segun' funciona con entero, caracter o cadena; se obtuvo ${tOperando.descripcion}.",
                        s.operando.pos
                    )
                }
                val valoresVistos = mutableSetOf<Any>()
                var defectos = 0
                dentroSegun++
                try {
                    for (rama in s.ramas) {
                        when (rama) {
                            is RamaCaso -> {
                                val tEtiqueta = tipificador.tipar(rama.etiqueta, amb)
                                if (operandoValido && tEtiqueta !is TError && tEtiqueta != tOperando) {
                                    diag.error(
                                        "SEM-028",
                                        "Este 'caso' es ${tEtiqueta.descripcion} pero el 'segun' es ${tOperando.descripcion}.",
                                        rama.etiqueta.pos
                                    )
                                }
                                val clave = claveDeLiteral(rama.etiqueta)
                                if (clave != null && !valoresVistos.add(clave)) {
                                    diag.error("SEM-029", "El 'caso' ${textoDe(rama.etiqueta)} esta repetido.", rama.etiqueta.pos)
                                }
                                val ambitoRama = amb.abrir(TipoAmbito.BLOQUE, "caso", rama.pos)
                                for (interna in rama.sentencias) chequearSentencia(interna, ambitoRama)
                            }
                            is RamaDefecto -> {
                                defectos++
                                if (defectos > 1) {
                                    diag.error("SEM-030", "El 'segun' tiene mas de un 'defecto'.", rama.pos)
                                }
                                val ambitoRama = amb.abrir(TipoAmbito.BLOQUE, "defecto", rama.pos)
                                for (interna in rama.sentencias) chequearSentencia(interna, ambitoRama)
                            }
                            else -> { }   // RamaSegun solo tiene estas dos variantes
                        }
                    }
                } finally {
                    dentroSegun--
                }
            }

            is SentRetornar -> {
                val retorno = retornoActual
                if (retorno == null) {
                    diag.error("SEM-031", "'retornar' solo puede usarse dentro de una funcion o metodo.", s.pos)
                } else if (s.valor == null) {
                    if (retorno !is TVacio && retorno !is TError) {
                        diag.error(
                            "SEM-032",
                            "'retornar' sin valor solo es valido en funciones 'vacio'; $nombreContextoRetorno devuelve ${retorno.descripcion}.",
                            s.pos
                        )
                    }
                } else {
                    if (retorno is TVacio) {
                        diag.error("SEM-033", "$nombreContextoRetorno es 'vacio': no puede retornar un valor.", s.pos)
                        tipificador.tipar(s.valor, amb)   // se tipa igual: los errores internos importan
                    } else {
                        val tValor = tipificador.tiparValor(s.valor, amb)
                        verificarAsignable(retorno, tValor, s.valor, "retorno de $nombreContextoRetorno")
                    }
                }
            }

            is SentRomper -> {
                if (dentroCiclo == 0 && dentroSegun == 0) {
                    diag.error("SEM-010", "'romper' solo puede usarse dentro de un ciclo o de un 'segun'.", s.pos)
                }
            }

            is SentContinuar -> {
                if (dentroCiclo == 0) {
                    diag.error("SEM-016", "'continuar' solo puede usarse dentro de un ciclo.", s.pos)
                }
            }

            is SentExpr -> tipificador.tipar(s.expr, amb)

            is SentVacia -> { }   // el ';' suelto: nada que verificar

            else -> { }   // when como statement: la red si se agrega una Sent nueva
        }
    }

    // Declaracion de variable: toplevel (GLOBAL), local (LOCAL) o inicio
    // de un para (LOCAL tambien). Declara en el ambito actual DESPUES de
    // tipar el inicializador: `entero x = x;` es uso antes de declaracion.
    private fun chequearDeclVariable(d: DeclVariable, amb: Ambito) {
        val tipo = resolverTipo(d.tipo, amb, permitirVacio = false)
        val categoria = if (amb.tipo == TipoAmbito.GLOBAL) Categoria.GLOBAL else Categoria.LOCAL

        d.inicial?.let { ini ->
            val tIni = tipificador.tiparValor(ini, amb)
            verificarAsignable(tipo, tIni, ini, "asignacion a '${d.nombre}'")
        }
        if (d.esConstante && d.inicial == null) {
            diag.error("SEM-021", "La constante '${d.nombre}' necesita un valor inicial.", d.pos)
        }

        val sim = SimboloVariable(
            nombre = d.nombre,
            tipo = tipo,
            pos = d.pos,
            categoria = categoria,
            esConstante = d.esConstante,
            esEstatico = false,
            idUnico = idUnico(d.nombre)
        )
        if (!amb.declarar(sim)) {
            val previa = amb.simbolos.getValue(d.nombre)
            diag.error(
                "SEM-002",
                "La variable '${d.nombre}' ya fue declarada en este bloque (linea ${previa.pos.linea}).",
                d.pos
            )
            return
        }
        simbolos[d.id] = sim
    }

    // Condicion de si/mientras/hacer/para: booleano, sin truthiness
    // (seccion 2.7, decision pedagogica). SEM-005 del catalogo.
    private fun chequearCondicion(cond: Expr, amb: Ambito, construccion: String) {
        val t = tipificador.tiparValor(cond, amb)
        val ok = t is TBooleano || t is TError
        registrarChequeo(cond.pos, textoDe(cond), "condicion de '$construccion': debe ser booleano", TBooleano, t, ok)
        if (!ok) {
            diag.error(
                "SEM-005",
                "La condicion del '$construccion' debe ser booleano, pero es ${t.descripcion}.",
                cond.pos
            )
        }
    }

    // Advertencia didactica (seccion 4.8): `si (x) entero y = 2;` compila
    // pero confunde: la variable solo vive en esa rama y se pierde.
    private fun advertirDeclaracionSinLlaves(cuerpo: Sent, rama: String) {
        if (cuerpo is DeclVariable) {
            diag.advertencia(
                "SEM-017",
                "La declaracion de '${cuerpo.nombre}' es todo el cuerpo del '$rama': conviene usar llaves { }.",
                cuerpo.pos
            )
        }
    }

    // --------------------- utilidades compartidas --------------------------

    // De TipoAst (lo escrito) a Tipo (lo verificado). Con permitirVacio en
    // false, "vacio" como tipo de variable/parametro/arreglo es error.
    internal fun resolverTipo(t: TipoAst, amb: Ambito, permitirVacio: Boolean): Tipo {
        val base: Tipo = when (t.nombre) {
            "entero" -> TEntero
            "flotante" -> TFlotante
            "booleano" -> TBooleano
            "caracter" -> TCaracter
            "cadena" -> TCadena
            "vacio" -> if (permitirVacio) {
                TVacio
            } else {
                diag.error("SEM-034", "El tipo 'vacio' solo sirve como retorno de funciones.", t.pos)
                TError
            }
            else -> {
                if (!tipoCualificadoUsable(t.nombre, t.pos, "usar el tipo")) {
                    tipos[t.id] = TError
                    return TError
                }
                val clase = resolverClaseVisible(amb, t.nombre)
                if (clase != null) {
                    TClase(clase.nombre)
                } else {
                    diag.error("SEM-035", "No existe el tipo '${t.nombre}'. ¿Es una clase declarada?", t.pos)
                    TError
                }
            }
        }
        val resultado = if (t.esArreglo) {
            if (base is TError || base is TVacio) TError else TArreglo(base)
        } else {
            base
        }
        tipos[t.id] = resultado
        return resultado
    }

    // La regla de asignacion del lenguaje, con los dos mensajes del
    // catalogo: SEM-004 para flotante->entero (tiene solucion: a_entero)
    // y SEM-003 para el resto de las incompatibilidades.
    internal fun verificarAsignable(destino: Tipo, valor: Tipo, exprValor: Expr, contexto: String) {
        if (esAsignable(destino, valor)) {
            registrarChequeo(
                exprValor.pos, "${textoDe(exprValor)}",
                "$contexto: ${valor.descripcion} -> ${destino.descripcion}" +
                        if (destino is TFlotante && valor is TEntero) " (conversion itof)" else "",
                destino, valor, true
            )
            return
        }
        registrarChequeo(exprValor.pos, textoDe(exprValor), "$contexto: se obtuvo ${valor.descripcion}", destino, valor, false)
        if (destino is TEntero && valor is TFlotante) {
            diag.error("SEM-004", "No se puede asignar flotante a entero sin conversion. Usa a_entero(...).", exprValor.pos)
        } else {
            diag.error(
                "SEM-003",
                "No se puede asignar un valor de tipo ${valor.descripcion} a una variable de tipo ${destino.descripcion}.",
                exprValor.pos
            )
        }
    }

    internal fun registrarChequeo(pos: Posicion, texto: String, regla: String, esperado: Tipo?, obtenido: Tipo?, ok: Boolean) {
        chequeos.add(Chequeo(pos, texto, regla, esperado, obtenido, ok))
    }

    // "x", despues "x#2", "x#3"... Distingue sombreados en TAC y GUI.
    private fun idUnico(nombre: String): String {
        val visto = contadorPorNombre.getOrDefault(nombre, 0) + 1
        contadorPorNombre[nombre] = visto
        return if (visto == 1) nombre else "$nombre#$visto"
    }

    // Valor hashable del literal de un caso, para detectar repeticiones.
    private fun claveDeLiteral(e: Expr): Any? = when (e) {
        is LitEntero -> e.valor
        is LitCaracter -> e.valor
        is LitCadena -> e.valor
        else -> null
    }

    // Advertencia (seccion 4.8): variable declarada y nunca usada. Aplica
    // a locales, parametros y globales toplevel; los campos de una clase
    // no: su vida excede al texto (los puede leer cualquier metodo).
    private fun advertirVariablesSinUso(amb: Ambito) {
        for (sim in amb.simbolos.values) {
            if (!sim.usada && sim.categoria != Categoria.CAMPO) {
                diag.advertencia(
                    "SEM-018",
                    "La variable '${sim.nombre}' fue declarada pero nunca se usa.",
                    sim.pos
                )
            }
        }
        for (hijo in amb.hijos) advertirVariablesSinUso(hijo)
    }
}
