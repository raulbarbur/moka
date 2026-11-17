// ============================================================================
// ConstructorAst.kt - De parse tree (ANTLR) a AST propio
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/sintactico/ConstructorAst.kt
// (documento de diseno, seccion 4.3; gramatica en seccion 3.3)
//
// Fase 2: este visitor recorre el arbol que genera ANTLR y construye el AST
// de ar.com.moka.core.ast, asignando id unico y posicion a cada nodo.
//
// ¿COMO FUNCIONA UN VISITOR DE ANTLR?
// Cada regla de la gramatica produce una clase de contexto
// (MokaParser.SentenciaSiContext, MokaParser.AditivaContext, ...). El
// visitor tiene un metodo visitXxx por contexto; llamar a visit(arbol)
// despacha al metodo correcto segun la clase real del nodo (double
// dispatch). MokaParserBaseVisitor ya trae una implementacion que visita
// los hijos; aca sobreescribimos solo lo que nos interesa.
//
// PRECEDENCIA: YA ESTA RESUELTA, GRATIS.
// ANTLR reescribio la regla recursiva `expresion` respetando el orden de
// las alternativas, asi que el parse tree de "1 + 2 * 3" ya viene anidado
// como 1 + (2 * 3). Nosotros solo copiamos esa forma al AST: el `der` de la
// suma es la multiplicacion, sin hacer ninguna cuenta de precedencia.
//
// OPERADORES: se resuelven por TEXTO del token (ctx.op?.text), nunca por
// constantes de token: el lexer generado guarda sus constantes en un
// companion privado y no son accesibles. Cada enum del AST sabe buscarse
// por simbolo (OpBinario.desde("+"), etc.).
//
// REGLAS QUE NO PRODUCEN NODOS (tipo, parametros, argumentos, inicioPara,
// etiquetaCaso): los metodos visitXxx del visitor deben devolver Nodo? (el
// tipo generico que elegimos), asi que no sirven para armar un TipoAst o
// una List<Param> sueltos. Esas reglas se procesan con funciones privadas
// auxiliares (tipoDe, paramsDe, ...) que devuelven lo que hace falta.
// ============================================================================

package ar.com.moka.core.sintactico

import ar.com.moka.core.ast.*
import ar.com.moka.core.generado.MokaParser
import ar.com.moka.core.generado.MokaBaseVisitor
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.Token
import org.antlr.v4.kotlinruntime.tree.ParseTree
import org.antlr.v4.kotlinruntime.tree.TerminalNode

class ConstructorAst : MokaBaseVisitor<Nodo?>() {

    // Contador de ids: cada nodo creado se lleva el siguiente. Los hijos se
    // construyen antes que los padres, asi que los ids crecen de las hojas
    // hacia la raiz; lo unico que importa es que no se repitan.
    private var proximoId: Int = 1
    private fun nuevoId(): Int = proximoId++

    // El visitor base exige un resultado por defecto (lo usa para terminales
    // y nodos de error). Con Nodo? como tipo de retorno, null es el neutro
    // perfecto: visitar el EOF o un signo de puntuacion da null y se filtra.
    override fun defaultResult(): Nodo? = null

    companion object {
        // Punto de entrada comodo: ids frescos en cada llamada.
        // Uso:  ConstructorAst.construir(parser.programa())
        fun construir(programa: MokaParser.ProgramaContext): ProgramaAst =
            ConstructorAst().visitPrograma(programa)
    }

    // ------------------------- utilidades internas -------------------------

    // Posicion del nodo = desde el primer token del contexto hasta el ultimo
    // caracter del ultimo token. start/stop son anulables en la API del
    // runtime; en la practica siempre estan en un parse exitoso, pero si
    // faltaran caemos en 0 en vez de romper.
    private fun pos(ctx: ParserRuleContext): Posicion {
        val ini = ctx.start
        val fin = ctx.stop
        return Posicion(
            linea = ini?.line ?: 0,
            columna = ini?.charPositionInLine ?: 0,
            finLinea = fin?.line ?: (ini?.line ?: 0),
            finColumna = if (fin != null) {
                fin.charPositionInLine + (fin.text?.length ?: 1) - 1
            } else {
                ini?.charPositionInLine ?: 0
            }
        )
    }

    // visit() devuelve Nodo?; estos atajos visitan y castean de una vez.
    // El cast es seguro porque el dispatch cae en el visitXxx correcto
    // (una expresion siempre produce una Expr). Si falta el subarbol es un
    // bug del constructor, no del programa del usuario: error() y listo.
    private fun exprDe(arbol: ParseTree?): Expr =
        visit(arbol ?: error("ConstructorAst: falta una expresion")) as Expr

    private fun sentDe(arbol: ParseTree?): Sent =
        visit(arbol ?: error("ConstructorAst: falta una sentencia")) as Sent

    // Texto de un contexto de UN solo token (tipoBase): su unico hijo es el
    // terminal. No usamos ctx.text porque en este runtime el texto de un
    // contexto se calcula desde el stream y preferimos lo mas directo.
    private fun textoUnico(ctx: ParserRuleContext): String =
        (ctx.getChild(0) as TerminalNode).text

    private fun tipoDe(ctx: MokaParser.TipoContext): TipoAst {
        // tipoBase es un solo token: T_ENTERO ... T_CADENA o ID (una clase).
        // El lexema ya es el nombre que queremos guardar.
        val base = ctx.tipoBase()
        val nombre = textoUnico(base)
        return TipoAst(
            id = nuevoId(),
            pos = pos(ctx),
            nombre = nombre,
            esArreglo = ctx.CORCHETE_IZQ() != null   // tipo : tipoBase ('[' ']')?
        )
    }

    private fun tipoRetornoDe(ctx: MokaParser.TipoRetornoContext): TipoAst =
        if (ctx.T_VACIO() != null) {
            TipoAst(nuevoId(), pos(ctx), nombre = "vacio", esArreglo = false)
        } else {
            tipoDe(ctx.tipo() ?: error("ConstructorAst: tipoRetorno sin tipo"))
        }

    private fun paramsDe(ctx: MokaParser.ParametrosContext?): List<Param> =
        ctx?.parametro()?.map { param ->
            Param(
                id = nuevoId(),
                pos = pos(param),
                nombre = param.ID().text,
                tipo = tipoDe(param.tipo())
            )
        } ?: emptyList()

    private fun argsDe(ctx: MokaParser.ArgumentosContext?): List<Expr> =
        ctx?.expresion()?.map { exprDe(it) } ?: emptyList()

    // ------------------------- programa y declaraciones ---------------------

    override fun visitPrograma(ctx: MokaParser.ProgramaContext): ProgramaAst {
        // programa: (declaracionClase | declaracionFuncion | sentencia)* EOF
        // Recorremos los hijos en orden: visit() despacha solo al metodo de
        // cada alternativa. El ultimo hijo es el EOF (un TerminalNode): al
        // visitarlo cae en visitTerminal, que da defaultResult() = null.
        val declaraciones = mutableListOf<Nodo>()
        for (hijo in ctx.children.orEmpty()) {
            if (hijo is TerminalNode) continue
            val nodo = visit(hijo)
            if (nodo != null) declaraciones.add(nodo)
        }
        return ProgramaAst(nuevoId(), pos(ctx), declaraciones)
    }

    override fun visitDeclaracionFuncion(ctx: MokaParser.DeclaracionFuncionContext): DeclFuncion =
        DeclFuncion(
            id = nuevoId(),
            pos = pos(ctx),
            nombre = ctx.ID().text,
            retorno = tipoRetornoDe(ctx.tipoRetorno()),
            params = paramsDe(ctx.parametros()),
            cuerpo = visitBloque(ctx.bloque()),
            esEstatico = false          // el ESTATICO solo existe en miembros de clase
        )

    override fun visitDeclaracionClase(ctx: MokaParser.DeclaracionClaseContext): DeclClase {
        // Cada miembro cae en visitMiembroCampo / visitMiembroConstructor /
        // visitMiembroMetodo segun la alternativa etiquetada, y aca los
        // repartimos en las tres listas del nodo clase.
        val campos = mutableListOf<DeclVariable>()
        var constructor: DeclConstructor? = null
        val metodos = mutableListOf<DeclFuncion>()
        for (m in ctx.miembroClase()) {
            when (val nodo = visit(m)) {
                is DeclVariable -> campos.add(nodo)
                is DeclFuncion -> metodos.add(nodo)
                is DeclConstructor -> if (constructor == null) constructor = nodo else -> { }
                // Si hubiera DOS constructores nos quedamos con el primero;
                // el semantico reporta el error con su mensaje en espanol.
            }
        }
        return DeclClase(nuevoId(), pos(ctx), ctx.ID().text, campos, constructor, metodos)
    }

    override fun visitMiembroCampo(ctx: MokaParser.MiembroCampoContext): DeclVariable {
        // Reusamos la construccion de una variable comun y solo cambia el
        // flag estatico: no hay "copiar", simplemente lo leemos aca.
        return construirVariable(ctx.declaracionVariable(), esEstatico = ctx.ESTATICO() != null)
    }

    override fun visitMiembroConstructor(ctx: MokaParser.MiembroConstructorContext): DeclConstructor =
        DeclConstructor(
            id = nuevoId(),
            pos = pos(ctx),
            params = paramsDe(ctx.parametros()),
            cuerpo = visitBloque(ctx.bloque())
        )

    override fun visitMiembroMetodo(ctx: MokaParser.MiembroMetodoContext): DeclFuncion =
        DeclFuncion(
            id = nuevoId(),
            pos = pos(ctx),
            nombre = ctx.ID().text,
            retorno = tipoRetornoDe(ctx.tipoRetorno()),
            params = paramsDe(ctx.parametros()),
            cuerpo = visitBloque(ctx.bloque()),
            esEstatico = ctx.ESTATICO() != null
        )

    // declaracionVariable aparece en tres lugares (toplevel, campo, inicio
    // del para); por eso la construccion va en esta funcion compartida.
    private fun construirVariable(ctx: MokaParser.DeclaracionVariableContext, esEstatico: Boolean): DeclVariable =
        DeclVariable(
            id = nuevoId(),
            pos = pos(ctx),
            nombre = ctx.ID().text,
            tipo = tipoDe(ctx.tipo()),
            inicial = ctx.expresion()?.let { exprDe(it) },
            esConstante = ctx.CONSTANTE() != null,
            esEstatico = esEstatico
        )

    // Igual que arriba pero para el caso toplevel / local: nunca estatica.
    override fun visitDeclaracionVariable(ctx: MokaParser.DeclaracionVariableContext): DeclVariable =
        construirVariable(ctx, esEstatico = false)

    // ------------------------------ sentencias ------------------------------

    override fun visitBloque(ctx: MokaParser.BloqueContext): Bloque =
        Bloque(
            id = nuevoId(),
            pos = pos(ctx),
            sentencias = ctx.sentencia().map { visitSentencia(it) }
        )

    // La regla `sentencia` NO tiene alternativas etiquetadas (a diferencia
    // de `expresion`): hay un solo SentenciaContext. Se despacha mirando el
    // primer hijo: cada alternativa arranca con una regla distinta, salvo
    // sentenciaVacia, que es el ';' suelto (un terminal).
    override fun visitSentencia(ctx: MokaParser.SentenciaContext): Sent =
        when (val hijo = ctx.getChild(0)) {
            is MokaParser.BloqueContext -> visitBloque(hijo)
            is MokaParser.DeclaracionVariableContext -> visitDeclaracionVariable(hijo)
            is MokaParser.SentenciaSiContext -> visitSentenciaSi(hijo)
            is MokaParser.SentenciaMientrasContext -> visitSentenciaMientras(hijo)
            is MokaParser.SentenciaHacerContext -> visitSentenciaHacer(hijo)
            is MokaParser.SentenciaParaContext -> visitSentenciaPara(hijo)
            is MokaParser.SentenciaParaCadaContext -> visitSentenciaParaCada(hijo)
            is MokaParser.SentenciaSegunContext -> visitSentenciaSegun(hijo)
            is MokaParser.SentenciaRetornarContext -> visitSentenciaRetornar(hijo)
            is MokaParser.SentenciaRomperContext -> SentRomper(nuevoId(), pos(hijo))
            is MokaParser.SentenciaContinuarContext -> SentContinuar(nuevoId(), pos(hijo))
            is MokaParser.SentenciaExpresionContext -> visitSentenciaExpresion(hijo)
            is TerminalNode -> SentVacia(nuevoId(), pos(ctx))   // el ';' solo
            else -> error("ConstructorAst: alternativa de sentencia no reconocida")
        } as Sent

    override fun visitSentenciaSi(ctx: MokaParser.SentenciaSiContext): SentSi =
        SentSi(
            id = nuevoId(),
            pos = pos(ctx),
            cond = exprDe(ctx.expresion()),
            entonces = sentDe(ctx.sentencia(0)),
            sino = ctx.sentencia(1)?.let { sentDe(it) }   // null si no hubo SINO
        )

    override fun visitSentenciaMientras(ctx: MokaParser.SentenciaMientrasContext): SentMientras =
        SentMientras(
            id = nuevoId(),
            pos = pos(ctx),
            cond = exprDe(ctx.expresion()),
            cuerpo = sentDe(ctx.sentencia())
        )

    override fun visitSentenciaHacer(ctx: MokaParser.SentenciaHacerContext): SentHacer =
        SentHacer(
            id = nuevoId(),
            pos = pos(ctx),
            cuerpo = sentDe(ctx.sentencia()),
            cond = exprDe(ctx.expresion())
        )

    override fun visitSentenciaPara(ctx: MokaParser.SentenciaParaContext): SentPara {
        // sentenciaPara: PARA '(' inicioPara expresion? ';' expresion? ')' sentencia
        // La expresion del inicio (si hay) vive DENTRO de inicioPara, asi que
        // las expresiones directas del para son siempre [condicion?, paso?].
        val ini = ctx.inicioPara()
        val dv = ini.declaracionVariable()   // nullable: una de las dos formas
        val ex = ini.expresion()             // nullable: idem, y ademas opcional
        val inicio: Nodo? = when {
            dv != null -> visitDeclaracionVariable(dv)
            ex != null -> exprDe(ex)
            else -> null                                // para (;; ...) con inicio vacio
        }
        return SentPara(
            id = nuevoId(),
            pos = pos(ctx),
            inicio = inicio,
            cond = ctx.expresion(0)?.let { exprDe(it) },
            paso = ctx.expresion(1)?.let { exprDe(it) },
            cuerpo = sentDe(ctx.sentencia())
        )
    }

    override fun visitSentenciaParaCada(ctx: MokaParser.SentenciaParaCadaContext): SentParaCada =
        SentParaCada(
            id = nuevoId(),
            pos = pos(ctx),
            tipo = tipoDe(ctx.tipo()),
            variable = ctx.ID().text,
            coleccion = exprDe(ctx.expresion()),
            cuerpo = sentDe(ctx.sentencia())
        )

    override fun visitSentenciaSegun(ctx: MokaParser.SentenciaSegunContext): SentSegun {
        // Las ramas son DOS reglas distintas (ramaCaso / ramaDefecto), asi
        // que hay dos listas separadas y perderiamos el orden del fuente.
        // Solucion: recorrer los hijos del contexto, que estan en orden.
        val ramas = mutableListOf<RamaSegun>()
        for (hijo in ctx.children.orEmpty()) {
            when (hijo) {
                is MokaParser.RamaCasoContext -> ramas.add(visitRamaCaso(hijo))
                is MokaParser.RamaDefectoContext -> ramas.add(visitRamaDefecto(hijo))
            }
        }
        return SentSegun(nuevoId(), pos(ctx), exprDe(ctx.expresion()), ramas)
    }

    override fun visitRamaCaso(ctx: MokaParser.RamaCasoContext): RamaCaso =
        RamaCaso(
            id = nuevoId(),
            pos = pos(ctx),
            etiqueta = etiquetaDe(ctx.etiquetaCaso()),
            sentencias = ctx.sentencia().map { visitSentencia(it) }
        )

    override fun visitRamaDefecto(ctx: MokaParser.RamaDefectoContext): RamaDefecto =
        RamaDefecto(
            id = nuevoId(),
            pos = pos(ctx),
            sentencias = ctx.sentencia().map { visitSentencia(it) }
        )

    // etiquetaCaso: MENOS? ENTERO_LIT | CARACTER_LIT | CADENA_LIT
    // El signo se aplica aca mismo: "caso -3" queda LitEntero(-3).
    private fun etiquetaDe(ctx: MokaParser.EtiquetaCasoContext): Expr {
        ctx.ENTERO_LIT()?.let { tok ->
            val signo = if (ctx.MENOS() != null) "-" else ""
            return LitEntero(nuevoId(), pos(ctx), (signo + tok.text).toLong())
        }
        ctx.CARACTER_LIT()?.let { tok ->
            return LitCaracter(nuevoId(), pos(ctx), caracterDe(tok.text))
        }
        ctx.CADENA_LIT()?.let { tok ->
            return LitCadena(nuevoId(), pos(ctx), cadenaDe(tok.text))
        }
        error("ConstructorAst: etiqueta de caso no reconocida")
    }

    override fun visitSentenciaRetornar(ctx: MokaParser.SentenciaRetornarContext): SentRetornar =
        SentRetornar(
            id = nuevoId(),
            pos = pos(ctx),
            valor = ctx.expresion()?.let { exprDe(it) }
        )

    override fun visitSentenciaExpresion(ctx: MokaParser.SentenciaExpresionContext): SentExpr =
        SentExpr(nuevoId(), pos(ctx), exprDe(ctx.expresion()))

    // ------------------------------ expresiones -----------------------------
    // Un metodo por alternativa etiquetada. Recorda: la forma anidada ya
    // viene resuelta por ANTLR; aca solo traducimos contextos a nodos.

    override fun visitPrimarioExpr(ctx: MokaParser.PrimarioExprContext): Expr =
        exprDe(ctx.primario())

    override fun visitAccesoMiembro(ctx: MokaParser.AccesoMiembroContext): AccesoMiembro =
        AccesoMiembro(
            id = nuevoId(),
            pos = pos(ctx),
            obj = exprDe(ctx.expresion()),
            miembro = ctx.ID().text
        )

    override fun visitAccesoIndice(ctx: MokaParser.AccesoIndiceContext): AccesoIndice =
        AccesoIndice(
            id = nuevoId(),
            pos = pos(ctx),
            arr = exprDe(ctx.expresion(0)),
            indice = exprDe(ctx.expresion(1))
        )

    override fun visitLlamada(ctx: MokaParser.LlamadaContext): Llamada =
        Llamada(
            id = nuevoId(),
            pos = pos(ctx),
            callee = exprDe(ctx.expresion()),
            args = argsDe(ctx.argumentos())
        )

    // x++ / x--  (el operador viene DESPUES: esPrefijo = false)
    override fun visitPostfijo(ctx: MokaParser.PostfijoContext): Unaria =
        Unaria(
            id = nuevoId(),
            pos = pos(ctx),
            op = OpUnario.desde(ctx.op?.text ?: error("ConstructorAst: postfijo sin operador")),
            operando = exprDe(ctx.expresion()),
            esPrefijo = false
        )

    // ++x / --x / -x / no x
    override fun visitUnario(ctx: MokaParser.UnarioContext): Unaria =
        Unaria(
            id = nuevoId(),
            pos = pos(ctx),
            op = OpUnario.desde(ctx.op?.text ?: error("ConstructorAst: unario sin operador")),
            operando = exprDe(ctx.expresion()),
            esPrefijo = true
        )

    // Las cuatro binarias con operador etiquetado (op=(...)) son identicas
    // salvo por el tipo del contexto. No hay helper compartido a proposito:
    // los getters expresion(0)/(1) viven en cada clase de contexto concreta,
    // no en la base ExpresionContext. Lo unico compartido es resolver el op.
    private fun opBin(op: Token?): OpBinario =
        OpBinario.desde(op?.text ?: error("ConstructorAst: binaria sin operador"))

    override fun visitMultiplicativa(ctx: MokaParser.MultiplicativaContext): Binaria =
        Binaria(nuevoId(), pos(ctx), opBin(ctx.op), exprDe(ctx.expresion(0)), exprDe(ctx.expresion(1)))

    override fun visitAditiva(ctx: MokaParser.AditivaContext): Binaria =
        Binaria(nuevoId(), pos(ctx), opBin(ctx.op), exprDe(ctx.expresion(0)), exprDe(ctx.expresion(1)))

    override fun visitRelacional(ctx: MokaParser.RelacionalContext): Binaria =
        Binaria(nuevoId(), pos(ctx), opBin(ctx.op), exprDe(ctx.expresion(0)), exprDe(ctx.expresion(1)))

    override fun visitIgualdad(ctx: MokaParser.IgualdadContext): Binaria =
        Binaria(nuevoId(), pos(ctx), opBin(ctx.op), exprDe(ctx.expresion(0)), exprDe(ctx.expresion(1)))

    // conjuncion y disyuncion no tienen op etiquetado: el operador es fijo.
    override fun visitConjuncion(ctx: MokaParser.ConjuncionContext): Binaria =
        Binaria(nuevoId(), pos(ctx), OpBinario.Y, exprDe(ctx.expresion(0)), exprDe(ctx.expresion(1)))

    override fun visitDisyuncion(ctx: MokaParser.DisyuncionContext): Binaria =
        Binaria(nuevoId(), pos(ctx), OpBinario.O, exprDe(ctx.expresion(0)), exprDe(ctx.expresion(1)))

    // Asignacion: asociativa a DERECHA (<assoc=right>), o sea a = b = c se
    // parsea a = (b = c). Tambien aca: ANTLR ya lo resolvio.
    override fun visitAsignacion(ctx: MokaParser.AsignacionContext): Asignacion =
        Asignacion(
            id = nuevoId(),
            pos = pos(ctx),
            op = OpAsignacion.desde(ctx.op?.text ?: error("ConstructorAst: asignacion sin operador")),
            destino = exprDe(ctx.expresion(0)),
            valor = exprDe(ctx.expresion(1))
        )

    // ------------------------------- primarios ------------------------------

    override fun visitLitEntero(ctx: MokaParser.LitEnteroContext): LitEntero =
        LitEntero(nuevoId(), pos(ctx), ctx.ENTERO_LIT().text.toLong())

    override fun visitLitFlotante(ctx: MokaParser.LitFlotanteContext): LitFlotante =
        LitFlotante(nuevoId(), pos(ctx), ctx.FLOTANTE_LIT().text.toDouble())

    override fun visitLitCaracter(ctx: MokaParser.LitCaracterContext): LitCaracter =
        LitCaracter(nuevoId(), pos(ctx), caracterDe(ctx.CARACTER_LIT().text))

    override fun visitLitCadena(ctx: MokaParser.LitCadenaContext): LitCadena =
        LitCadena(nuevoId(), pos(ctx), cadenaDe(ctx.CADENA_LIT().text))

    override fun visitLitVerdadero(ctx: MokaParser.LitVerdaderoContext): LitBool =
        LitBool(nuevoId(), pos(ctx), true)

    override fun visitLitFalso(ctx: MokaParser.LitFalsoContext): LitBool =
        LitBool(nuevoId(), pos(ctx), false)

    override fun visitLitNulo(ctx: MokaParser.LitNuloContext): LitNulo =
        LitNulo(nuevoId(), pos(ctx))

    override fun visitRefEste(ctx: MokaParser.RefEsteContext): Este =
        Este(nuevoId(), pos(ctx))

    override fun visitRefNombre(ctx: MokaParser.RefNombreContext): Nombre =
        Nombre(nuevoId(), pos(ctx), ctx.ID().text)

    // Los parentesis no generan nodo: devolvemos la expresion de adentro.
    // La agrupacion ya quedo grabada en la FORMA del arbol.
    override fun visitParentesis(ctx: MokaParser.ParentesisContext): Expr =
        exprDe(ctx.expresion())

    override fun visitNuevoObjeto(ctx: MokaParser.NuevoObjetoContext): NuevoObjeto =
        NuevoObjeto(
            id = nuevoId(),
            pos = pos(ctx),
            clase = ctx.ID().text,
            args = argsDe(ctx.argumentos())
        )

    override fun visitNuevoArreglo(ctx: MokaParser.NuevoArregloContext): NuevoArreglo =
        NuevoArreglo(
            id = nuevoId(),
            pos = pos(ctx),
            tipoElemento = TipoAst(nuevoId(), pos(ctx.tipoBase()), textoUnico(ctx.tipoBase()), esArreglo = false),
            tamano = exprDe(ctx.expresion())
        )

    override fun visitLitArreglo(ctx: MokaParser.LitArregloContext): LitArreglo =
        LitArreglo(
            id = nuevoId(),
            pos = pos(ctx),
            elementos = ctx.expresion().map { exprDe(it) }
        )

    // --------------------------- escapes de literales -----------------------
    // El lexer acepta \n \t \r \" \' \\ \0 (fragment ESCAPE, sec. 3.2).
    // Aca se convierte el lexema crudo (con comillas y barritas) al valor
    // real. Si aparece otra barra es porque la gramatica cambio y este
    // metodo quedo viejo: lo dejamos pasar tal cual en vez de romper.
    private fun desescapar(contenido: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < contenido.length) {
            val c = contenido[i]
            if (c == '\\' && i + 1 < contenido.length) {
                when (contenido[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    '\\' -> sb.append('\\')
                    '0' -> sb.append('\u0000')   // el caracter nulo
                    else -> {
                        sb.append(c)
                        sb.append(contenido[i + 1])
                    }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // Saca las comillas dobles y resuelve escapes: "ho\nla" -> ho<newline>la
    private fun cadenaDe(lexema: String): String =
        desescapar(lexema.substring(1, lexema.length - 1))

    // Misma idea con comillas simples; el contenido ya validado es 1 char.
    private fun caracterDe(lexema: String): Char =
        cadenaDe(lexema)[0]
}
