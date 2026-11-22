// ============================================================================
// Tipificador.kt - El tipo de cada EXPRESION
// Ubicacion: core/src/commonMain/kotlin/ar/com/moka/core/semantico/Tipificador.kt
// (documento de diseno, seccion 4.4)
//
// Mitad del analizador semantico: las sentencias viven en
// AnalizadorSemantico.kt; aca se calcula el tipo de cada Expr, de las hojas
// hacia la raiz (el tipo de `1 + 2 * 3` sale de los tipos de 1, 2 y 3).
//
// CADA REGLA DEJA DOS RASTROS:
//   - tipos[e.id] = tipo calculado       (tabla lateral, para TAC y GUI)
//   - un Chequeo con la notacion del curso ("aditiva: entero + flotante ->
//     flotante"), ok o no, para el panel "Chequeos de tipo".
//
// TRES IDEAS QUE SE REPITEN:
//
// 1) TError SE PROPAGA EN SILENCIO. Si un operando ya fallo, la operacion
//    da TError sin nuevo mensaje: un error, un diagnostico.
//
// 2) L-VALORES. La gramatica acepta `hola + 2 = 3` (seccion 3.4); aca se
//    verifica que el destino de =, ++ y -- sea variable, campo o elemento
//    de arreglo (SEM-015), que no sea constante (SEM-006) ni la variable
//    de un para_cada.
//
// 3) PROMOCION NUMERICA. entero -> flotante es automatica en operaciones
//    mixtas, asignaciones y argumentos (el TAC insertara `itof`). La
//    inversa nunca: SEM-004 avisa que hay que usar a_entero.
//
// tipar() devuelve el tipo crudo; tiparValor() ademas rechaza `vacio`
// (SEM-013): una funcion vacio puede llamarse como SENTENCIA, nunca como
// valor dentro de otra expresion.
// ============================================================================

package ar.com.moka.core.semantico

import ar.com.moka.core.ast.*
import ar.com.moka.core.ejecucion.ColoresConsola

// Texto corto de una expresion, para chequeos y mensajes: "i < 10",
// "nuevo Alumno(\"Ana\")". No es el fuente exacto: es una descripcion
// compacta, suficiente para ubicar la construccion en un panel.
internal fun textoDe(e: Expr): String = when (e) {
    is LitEntero -> e.valor.toString()
    is LitFlotante -> e.valor.toString()
    is LitCaracter -> "'" + e.valor + "'"
    is LitCadena -> "\"" + e.valor + "\""
    is LitBool -> if (e.valor) "verdadero" else "falso"
    is LitNulo -> "nulo"
    is Nombre -> e.ident
    is Este -> "este"
    is Unaria -> if (e.esPrefijo) e.op.simbolo + textoDe(e.operando) else textoDe(e.operando) + e.op.simbolo
    is Binaria -> textoDe(e.izq) + " " + e.op.simbolo + " " + textoDe(e.der)
    is Asignacion -> textoDe(e.destino) + " " + e.op.simbolo + " " + textoDe(e.valor)
    is Llamada -> textoDe(e.callee) + "(" + e.args.joinToString(", ") { textoDe(it) } + ")"
    is AccesoMiembro -> textoDe(e.obj) + "." + e.miembro
    is AccesoIndice -> textoDe(e.arr) + "[" + textoDe(e.indice) + "]"
    is NuevoObjeto -> "nuevo " + e.clase + "(" + e.args.joinToString(", ") { textoDe(it) } + ")"
    is NuevoArreglo -> "nuevo " + e.tipoElemento.nombre + "[" + textoDe(e.tamano) + "]"
    is LitArreglo -> "[" + e.elementos.joinToString(", ") { textoDe(it) } + "]"
}

internal fun textoTipo(t: TipoAst): String = t.nombre + if (t.esArreglo) "[]" else ""

internal class Tipificador(private val a: AnalizadorSemantico) {

    // Tipa y registra. Toda expresion visitada termina con su tipo en la
    // tabla lateral, incluso las erroneas (quedan TError).
    fun tipar(e: Expr, amb: Ambito): Tipo {
        val t = calcular(e, amb)
        a.tipos[e.id] = t
        return t
    }

    // Tipa y exige un VALOR: `vacio` solo es valido como sentencia suelta
    // (SentExpr), nunca dentro de otra expresion (SEM-013).
    fun tiparValor(e: Expr, amb: Ambito): Tipo {
        val t = tipar(e, amb)
        if (t is TVacio) {
            a.diag.error("SEM-013", "No se puede usar el resultado de una funcion 'vacio' como valor.", e.pos)
            return TError
        }
        return t
    }

    private fun calcular(e: Expr, amb: Ambito): Tipo = when (e) {

        // ----------------------------- literales -----------------------------

        is LitEntero -> {
            // Rango de 32 bits (SEM-014). El unico "negativo" especial es
            // -2147483648: el - es unario y se pliega en Unaria, asi que
            // aca llega como 2147483648 y se acepta SOLO desde ahi.
            if (e.valor > 2147483647L) {
                a.diag.error("SEM-014", "Literal entero fuera de rango: ${e.valor} (maximo 2147483647).", e.pos)
                TError
            } else {
                TEntero
            }
        }
        is LitFlotante -> TFlotante
        is LitCaracter -> TCaracter
        is LitCadena -> TCadena
        is LitBool -> TBooleano
        is LitNulo -> TNulo

        // ----------------------------- referencias ---------------------------

        is Nombre -> tiparNombre(e, amb)

        is Este -> {
            val clase = a.claseActual
            when {
                clase == null -> {
                    a.diag.error("SEM-036", "'este' solo puede usarse dentro de metodos y constructores.", e.pos)
                    TError
                }
                a.estaticoActual -> {
                    a.diag.error("SEM-037", "Un miembro 'estatico' no puede usar 'este'.", e.pos)
                    TError
                }
                else -> TClase(clase.nombre)
            }
        }

        // ----------------------------- operaciones ---------------------------

        is Unaria -> tiparUnaria(e, amb)
        is Binaria -> tiparBinaria(e, amb)
        is Asignacion -> tiparAsignacion(e, amb)

        is Llamada -> tiparLlamada(e, amb)

        is AccesoMiembro -> {
            val (tipo, _) = resolverAcceso(e, amb)
            tipo
        }

        is AccesoIndice -> {
            val tArr = tiparValor(e.arr, amb)
            val tIndice = tiparValor(e.indice, amb)
            if (tIndice !is TEntero && tIndice !is TError) {
                a.diag.error("SEM-038", "El indice de un arreglo debe ser entero, pero es ${tIndice.descripcion}.", e.indice.pos)
            }
            when (tArr) {
                is TArreglo -> {
                    a.registrarChequeo(e.pos, textoDe(e), "indexacion: ${tArr.descripcion}[entero] -> ${tArr.elemento.descripcion}", tArr.elemento, tArr.elemento, true)
                    tArr.elemento
                }
                is TError -> TError
                else -> {
                    a.diag.error("SEM-039", "Solo se puede indexar un arreglo; se obtuvo ${tArr.descripcion}.", e.arr.pos)
                    TError
                }
            }
        }

        is NuevoObjeto -> tiparNuevoObjeto(e, amb)

        is NuevoArreglo -> {
            val tElemento = a.resolverTipo(e.tipoElemento, amb, permitirVacio = false)
            val tTamano = tiparValor(e.tamano, amb)
            if (tTamano !is TEntero && tTamano !is TError) {
                a.diag.error("SEM-040", "El tamano de un arreglo debe ser entero, pero es ${tTamano.descripcion}.", e.tamano.pos)
            }
            if (tElemento is TError) TError else TArreglo(tElemento)
        }

        is LitArreglo -> {
            // Todos los elementos del mismo tipo, con promocion entero ->
            // flotante si hay mezcla (seccion 2.9). El [] vacio no existe:
            // la gramatica pide al menos un elemento.
            var comun: Tipo = TError
            var primero = true
            for (elem in e.elementos) {
                val t = tiparValor(elem, amb)
                if (primero) {
                    comun = t
                    primero = false
                } else if (comun !is TError && t !is TError) {
                    if (comun == t) {
                        // listo
                    } else if (comun.esNumerico && t.esNumerico) {
                        comun = tipoNumericoComun(comun, t)
                    } else {
                        a.diag.error(
                            "SEM-041",
                            "Todos los elementos del arreglo deben tener el mismo tipo; se obtuvo ${comun.descripcion} y ${t.descripcion}.",
                            elem.pos
                        )
                        comun = TError
                    }
                } else {
                    comun = TError
                }
            }
            if (comun is TError) TError else TArreglo(comun)
        }

        else -> TError   // Expr es sealed: si crece, este when avisa
    }

    // ------------------------------ nombres --------------------------------

    // Ligado de un nombre usado como valor: variable local, campo de la
    // clase actual o global, en ese orden (eso ES la cadena de ambitos).
    // Si no esta, puede que el programador haya escrito mal: se sugiere el
    // nombre visible mas parecido (Levenshtein <= 2, SEM-001).
    private fun tiparNombre(e: Nombre, amb: Ambito): Tipo {
        val sim = amb.resolver(e.ident)
        if (sim != null) {
            sim.usada = true
            a.simbolos[e.id] = sim
            return sim.tipo
        }
        if (amb.resolverFuncion(e.ident) != null) {
            a.diag.error("SEM-042", "'${e.ident}' es una funcion: para usarla hay que llamarla, por ejemplo ${e.ident}(...).", e.pos)
            return TError
        }
        if (amb.resolverClase(e.ident) != null) {
            a.diag.error("SEM-043", "'${e.ident}' es una clase, no un valor. Se usa con 'nuevo ${e.ident}(...)'.", e.pos)
            return TError
        }
        val sugerencia = sugerirNombre(e.ident, amb.nombresVisibles())
        val mensaje = if (sugerencia != null) {
            "La variable '${e.ident}' no esta declarada. ¿Quisiste decir '$sugerencia'?"
        } else {
            "La variable '${e.ident}' no esta declarada."
        }
        a.diag.error("SEM-001", mensaje, e.pos)
        return TError
    }

    // ------------------------------ unarias --------------------------------

    private fun tiparUnaria(e: Unaria, amb: Ambito): Tipo = when (e.op) {
        OpUnario.NEGATIVO -> {
            // Pliegue del signo (seccion 4.4): -2147483648 entra, aunque
            // 2147483648 solo no entra en 32 bits.
            val operando = e.operando
            if (operando is LitEntero && operando.valor == 2147483648L) {
                a.tipos[operando.id] = TEntero
                a.registrarChequeo(e.pos, textoDe(e), "menos unario: -2147483648 (minimo entero)", TEntero, TEntero, true)
                TEntero
            } else {
                val t = tiparValor(operando, amb)
                when {
                    t is TError -> TError
                    t.esNumerico -> {
                        a.registrarChequeo(e.pos, textoDe(e), "menos unario: ${t.descripcion} -> ${t.descripcion}", t, t, true)
                        t
                    }
                    else -> {
                        a.diag.error("SEM-044", "El menos unario solo se aplica a numeros; se obtuvo ${t.descripcion}.", operando.pos)
                        TError
                    }
                }
            }
        }

        OpUnario.NO -> {
            val t = tiparValor(e.operando, amb)
            when {
                t is TError -> TError
                t is TBooleano -> {
                    a.registrarChequeo(e.pos, textoDe(e), "no: booleano -> booleano", TBooleano, t, true)
                    TBooleano
                }
                else -> {
                    a.diag.error("SEM-045", "El operador 'no' solo se aplica a booleanos; se obtuvo ${t.descripcion}.", e.operando.pos)
                    TError
                }
            }
        }

        // ++x, x++, --x, x-- : solo sobre enteros y flotantes modificables
        // (seccion 2.6). El tipo del resultado es el del operando.
        OpUnario.INCREMENTO, OpUnario.DECREMENTO -> {
            val t = verificarModificable(e.operando, amb, e.op.simbolo)
            when {
                t is TError -> TError
                t.esNumerico -> t
                else -> {
                    a.diag.error("SEM-046", "'${e.op.simbolo}' solo se aplica a entero o flotante; se obtuvo ${t.descripcion}.", e.operando.pos)
                    TError
                }
            }
        }
    }

    // ------------------------------ binarias -------------------------------

    private fun tiparBinaria(e: Binaria, amb: Ambito): Tipo {
        val ti = tiparValor(e.izq, amb)
        val td = tiparValor(e.der, amb)
        if (ti is TError || td is TError) return TError
        val texto = textoDe(e)
        return when (e.op) {

            OpBinario.MAS -> when {
                ti is TCadena || td is TCadena -> {
                    a.registrarChequeo(e.pos, texto, "concatenacion: ${ti.descripcion} + ${td.descripcion} -> cadena", TCadena, TCadena, true)
                    TCadena
                }
                ti.esNumerico && td.esNumerico -> {
                    val r = tipoNumericoComun(ti, td)
                    a.registrarChequeo(e.pos, texto, "aditiva: ${ti.descripcion} + ${td.descripcion} -> ${r.descripcion}", r, r, true)
                    r
                }
                else -> errorBinaria(e, "+", ti, td)
            }

            OpBinario.MENOS, OpBinario.POR, OpBinario.DIV -> {
                if (ti.esNumerico && td.esNumerico) {
                    val r = tipoNumericoComun(ti, td)
                    a.registrarChequeo(e.pos, texto, "aritmetica: ${ti.descripcion} ${e.op.simbolo} ${td.descripcion} -> ${r.descripcion}", r, r, true)
                    r
                } else {
                    errorBinaria(e, e.op.simbolo, ti, td)
                }
            }

            OpBinario.MODULO -> {
                if (ti is TEntero && td is TEntero) {
                    a.registrarChequeo(e.pos, texto, "resto: entero % entero -> entero", TEntero, TEntero, true)
                    TEntero
                } else {
                    val cual = if (ti !is TEntero) ti else td
                    a.diag.error("SEM-011", "El operador '%' solo se aplica a enteros; se obtuvo ${cual.descripcion}.", e.pos)
                    TError
                }
            }

            OpBinario.MENOR, OpBinario.MENOR_IGUAL, OpBinario.MAYOR, OpBinario.MAYOR_IGUAL -> {
                if (ti.esNumerico && td.esNumerico) {
                    a.registrarChequeo(e.pos, texto, "relacional: ${ti.descripcion} ${e.op.simbolo} ${td.descripcion} -> booleano", TBooleano, TBooleano, true)
                    TBooleano
                } else {
                    a.diag.error("SEM-047", "El operador '${e.op.simbolo}' solo compara numeros; se obtuvo ${ti.descripcion} y ${td.descripcion}.", e.pos)
                    TError
                }
            }

            OpBinario.IGUAL_IGUAL, OpBinario.DISTINTO -> {
                val comparables = (ti == td)
                        || (ti.esNumerico && td.esNumerico)
                        || (ti is TNulo && td.esReferencia)
                        || (td is TNulo && ti.esReferencia)
                if (comparables) {
                    a.registrarChequeo(e.pos, texto, "igualdad: ${ti.descripcion} ${e.op.simbolo} ${td.descripcion} -> booleano", TBooleano, TBooleano, true)
                    TBooleano
                } else {
                    a.diag.error("SEM-048", "No se puede comparar ${ti.descripcion} con ${td.descripcion} por igualdad.", e.pos)
                    TError
                }
            }

            OpBinario.Y, OpBinario.O -> {
                if (ti is TBooleano && td is TBooleano) {
                    a.registrarChequeo(e.pos, texto, "logica: booleano ${e.op.simbolo} booleano -> booleano", TBooleano, TBooleano, true)
                    TBooleano
                } else {
                    a.diag.error("SEM-049", "El operador '${e.op.simbolo}' solo se aplica a booleanos; se obtuvo ${ti.descripcion} y ${td.descripcion}.", e.pos)
                    TError
                }
            }
        }
    }

    private fun errorBinaria(e: Binaria, op: String, ti: Tipo, td: Tipo): Tipo {
        a.diag.error("SEM-050", "El operador '$op' no se aplica a ${ti.descripcion} y ${td.descripcion}.", e.pos)
        return TError
    }

    // ----------------------------- asignacion -------------------------------

    private fun tiparAsignacion(e: Asignacion, amb: Ambito): Tipo {
        val tDestino = verificarModificable(e.destino, amb, "asignar")
        val tValor = tiparValor(e.valor, amb)
        if (tDestino is TError || tValor is TError) return TError

        if (e.op == OpAsignacion.ASIGNA) {
            a.verificarAsignable(tDestino, tValor, e.valor, "asignacion a '${textoDe(e.destino)}'")
            return tDestino
        }

        // Asignacion compuesta: x op= v  equivale a  x = x op v, con las
        // mismas reglas de la operacion binaria correspondiente.
        val resultado = when (e.op) {
            OpAsignacion.MAS_ASIGNA -> when {
                tDestino is TCadena -> TCadena
                tDestino.esNumerico && tValor.esNumerico -> tipoNumericoComun(tDestino, tValor)
                else -> {
                    a.diag.error("SEM-050", "El operador '+=' no se aplica a ${tDestino.descripcion} y ${tValor.descripcion}.", e.pos)
                    TError
                }
            }
            OpAsignacion.MENOS_ASIGNA, OpAsignacion.POR_ASIGNA, OpAsignacion.DIV_ASIGNA -> {
                if (tDestino.esNumerico && tValor.esNumerico) {
                    tipoNumericoComun(tDestino, tValor)
                } else {
                    a.diag.error("SEM-050", "El operador '${e.op.simbolo}' no se aplica a ${tDestino.descripcion} y ${tValor.descripcion}.", e.pos)
                    TError
                }
            }
            OpAsignacion.MOD_ASIGNA -> {
                if (tDestino is TEntero && tValor is TEntero) {
                    TEntero
                } else {
                    val cual = if (tDestino !is TEntero) tDestino else tValor
                    a.diag.error("SEM-011", "El operador '%=' solo se aplica a enteros; se obtuvo ${cual.descripcion}.", e.pos)
                    TError
                }
            }
            else -> TError   // ASIGNA ya salio arriba
        }
        if (resultado is TError) return TError
        // El resultado de la operacion tiene que volver a entrar en el
        // destino: flotante += entero anda; entero += flotante no.
        a.verificarAsignable(tDestino, resultado, e.valor, "asignacion a '${textoDe(e.destino)}'")
        return tDestino
    }

    // ¿Se puede MODIFICAR este destino? Es la verificacion de l-valor
    // (SEM-015) mas las dos prohibiciones: constantes (SEM-006) y la
    // variable del para_cada. Devuelve el tipo del destino (o TError).
    private fun verificarModificable(destino: Expr, amb: Ambito, accion: String): Tipo {
        when (destino) {
            is Nombre, is AccesoMiembro, is AccesoIndice -> { }   // formas validas
            else -> {
                a.diag.error("SEM-015", "El lado izquierdo de la asignacion no es una variable, campo ni elemento de arreglo.", destino.pos)
                tipar(destino, amb)
                return TError
            }
        }
        val t = tipar(destino, amb)
        val sim = a.simbolos[destino.id]
        if (sim is SimboloVariable) {
            if (sim.esConstante) {
                a.diag.error("SEM-006", "No se puede modificar '${sim.nombre}': es una constante.", destino.pos)
                return TError
            }
            if (sim.idUnico in a.idsParaCada) {
                a.diag.error("SEM-019", "La variable de un 'para_cada' es de solo lectura.", destino.pos)
                return TError
            }
        }
        return t
    }

    // ------------------------------ llamadas --------------------------------

    private fun tiparLlamada(e: Llamada, amb: Ambito): Tipo = when (val callee = e.callee) {
        is Nombre -> {
            val f = amb.resolverFuncion(callee.ident)
            when {
                f != null -> {
                    a.simbolos[callee.id] = f
                    a.tipos[callee.id] = f.tipo
                    chequearArgumentos(e, callee.ident, f, amb)
                    f.tipo
                }
                amb.resolver(callee.ident) != null -> {
                    a.diag.error("SEM-051", "'${callee.ident}' es una variable, no una funcion: no se puede llamar.", callee.pos)
                    tiparTodo(e.args, amb)
                    TError
                }
                else -> {
                    val candidatas = funcionesVisibles(amb)
                    val sugerencia = sugerirNombre(callee.ident, candidatas)
                    val mensaje = if (sugerencia != null) {
                        "La funcion '${callee.ident}' no esta declarada. ¿Quisiste decir '$sugerencia'?"
                    } else {
                        "La funcion '${callee.ident}' no esta declarada."
                    }
                    a.diag.error("SEM-001", mensaje, callee.pos)
                    tiparTodo(e.args, amb)
                    TError
                }
            }
        }
        is AccesoMiembro -> {
            val (tipo, simbolo) = resolverAcceso(callee, amb, paraLlamada = true)
            if (simbolo is SimboloFuncion) {
                chequearArgumentos(e, "${textoDe(callee.obj)}.${callee.miembro}", simbolo, amb)
            } else {
                tiparTodo(e.args, amb)
            }
            tipo
        }
        else -> {
            a.diag.error("SEM-052", "Solo se pueden llamar funciones y metodos.", callee.pos)
            tipar(callee, amb)
            tiparTodo(e.args, amb)
            TError
        }
    }

    private fun tiparTodo(args: List<Expr>, amb: Ambito) {
        for (arg in args) tipar(arg, amb)
    }

    private fun funcionesVisibles(amb: Ambito): Set<String> {
        val resultado = mutableSetOf<String>()
        var actual: Ambito? = amb
        while (actual != null) {
            resultado.addAll(actual.funciones.keys)
            actual = actual.padre
        }
        return resultado
    }

    // Cantidad y tipos de argumentos (SEM-007 / SEM-008), con promocion
    // entero -> flotante. Los built-ins tienen reglas propias (seccion 2.4):
    // imprimir acepta cualquier cosa, longitud pide arreglo o cadena...
    private fun chequearArgumentos(e: Llamada, nombreVisible: String, f: SimboloFuncion, amb: Ambito) {
        if (f.esBuiltin) {
            chequearBuiltin(e, nombreVisible, f, amb)
            return
        }
        val esperados = f.parametros
        if (e.args.size != esperados.size) {
            a.diag.error(
                "SEM-007",
                "La funcion '$nombreVisible' espera ${esperados.size} argumentos pero recibio ${e.args.size}.",
                e.pos
            )
            tiparTodo(e.args, amb)
            return
        }
        for (i in e.args.indices) {
            val arg = e.args[i]
            val tArg = tiparValor(arg, amb)
            val tParam = esperados[i]
            val ok = esAsignable(tParam, tArg)
            val regla = "argumento ${i + 1}: ${tArg.descripcion} -> ${tParam.descripcion}" +
                    if (tParam is TFlotante && tArg is TEntero) " (conversion itof)" else ""
            a.registrarChequeo(arg.pos, textoDe(arg), regla, tParam, tArg, ok)
            if (!ok) {
                if (tParam is TEntero && tArg is TFlotante) {
                    a.diag.error("SEM-008", "El argumento ${i + 1} de '$nombreVisible' debe ser entero, pero es flotante. Usa a_entero(...).", arg.pos)
                } else {
                    a.diag.error("SEM-008", "El argumento ${i + 1} de '$nombreVisible' debe ser ${tParam.descripcion}, pero es ${tArg.descripcion}.", arg.pos)
                }
            }
        }
    }

    // Los built-ins se chequean por nombre: sus firmas no entran en el
    // molde comun (imprimir acepta cualquier tipo, conversiones idem).
    private fun chequearBuiltin(e: Llamada, nombreVisible: String, f: SimboloFuncion, amb: Ambito) {
        fun cantidad(n: Int): Boolean {
            if (e.args.size == n) return true
            a.diag.error("SEM-007", "La funcion '$nombreVisible' espera $n argumentos pero recibio ${e.args.size}.", e.pos)
            tiparTodo(e.args, amb)
            return false
        }
        when (f.nombre) {
            "imprimir", "imprimir_sin_salto" -> {
                if (!cantidad(1)) return
                tiparValor(e.args[0], amb)   // cualquier tipo vale; vacio no (SEM-013)
                a.registrarChequeo(e.pos, textoDe(e), "$nombreVisible: cualquier tipo -> vacio", null, null, true)
            }
            "leer", "leer_entero", "leer_flotante" -> {
                cantidad(0)
            }
            // Consola con estilo. limpiar y restaurar_color no llevan
            // argumentos; color y titulo esperan una cadena; pausa, un
            // entero de milisegundos. Con un literal de color se valida
            // el nombre ya en compilacion (SEM-061), lo mismo que la
            // maquina hace en ejecucion (EJE-009).
            "limpiar", "restaurar_color" -> {
                cantidad(0)
            }
            "color" -> {
                if (!cantidad(1)) return
                val t = tiparValor(e.args[0], amb)
                val ok = t is TCadena || t is TError
                a.registrarChequeo(e.args[0].pos, textoDe(e.args[0]), "color: cadena -> vacio", TCadena, t, ok)
                if (!ok) {
                    a.diag.error("SEM-058", "'color' espera una cadena con el nombre del color (por ejemplo \"rojo\"), pero recibio ${t.descripcion}.", e.args[0].pos)
                } else {
                    val lit = e.args[0] as? LitCadena
                    if (lit != null && ColoresConsola.normalizar(lit.valor) == null) {
                        a.diag.error("SEM-061", "Color desconocido: \"${lit.valor}\". Los colores son: ${ColoresConsola.lista}.", lit.pos)
                    }
                }
            }
            "titulo" -> {
                if (!cantidad(1)) return
                val t = tiparValor(e.args[0], amb)
                val ok = t is TCadena || t is TError
                a.registrarChequeo(e.args[0].pos, textoDe(e.args[0]), "titulo: cadena -> vacio", TCadena, t, ok)
                if (!ok) {
                    a.diag.error("SEM-059", "'titulo' espera una cadena, pero recibio ${t.descripcion}.", e.args[0].pos)
                }
            }
            "pausa" -> {
                if (!cantidad(1)) return
                val t = tiparValor(e.args[0], amb)
                val ok = t is TEntero || t is TError
                a.registrarChequeo(e.args[0].pos, textoDe(e.args[0]), "pausa: entero (milisegundos) -> vacio", TEntero, t, ok)
                if (!ok) {
                    a.diag.error("SEM-060", "'pausa' espera un entero de milisegundos (por ejemplo pausa(500)), pero recibio ${t.descripcion}.", e.args[0].pos)
                }
            }
            "longitud" -> {
                if (!cantidad(1)) return
                val t = tiparValor(e.args[0], amb)
                val ok = t is TArreglo || t is TCadena || t is TError
                a.registrarChequeo(e.args[0].pos, textoDe(e.args[0]), "longitud: arreglo o cadena -> entero", TEntero, t, ok)
                if (!ok) {
                    a.diag.error("SEM-053", "'longitud' solo sirve para arreglos y cadenas; se obtuvo ${t.descripcion}.", e.args[0].pos)
                }
            }
            // a_entero, a_flotante, a_cadena, a_booleano: un argumento de
            // cualquier tipo con valor (seccion 2.3, conversiones y parseo).
            else -> {
                if (!cantidad(1)) return
                tiparValor(e.args[0], amb)
                a.registrarChequeo(e.pos, textoDe(e), "${f.nombre}: conversion -> ${f.tipo.descripcion}", f.tipo, null, true)
            }
        }
    }

    // ------------------------ miembros y objetos ----------------------------

    // Resuelve obj.miembro, usado tanto como valor (campo) como para
    // llamar (metodo). Devuelve (tipo, simbolo del miembro).
    //
    // El caso delicado: `Alumno.contador` con Alumno CLASE (acceso
    // estatico, seccion 2.10). `Alumno` no es una variable: se detecta
    // ANTES de tipar el objeto, porque como expresion no existe.
    private fun resolverAcceso(e: AccesoMiembro, amb: Ambito, paraLlamada: Boolean = false): Pair<Tipo, Simbolo?> {
        // ¿El "objeto" es en realidad el nombre de una clase?
        val objNombre = e.obj as? Nombre
        if (objNombre != null && amb.resolver(objNombre.ident) == null) {
            val claseEstatica = amb.resolverClase(objNombre.ident)
            if (claseEstatica != null) {
                a.simbolos[objNombre.id] = claseEstatica
                a.tipos[objNombre.id] = TClase(claseEstatica.nombre)
                val miembro = claseEstatica.campos[e.miembro] ?: claseEstatica.metodos[e.miembro]
                if (miembro == null) {
                    a.diag.error("SEM-012", "La clase '${claseEstatica.nombre}' no tiene un campo ni metodo '${e.miembro}'.", e.pos)
                    return TError to null
                }
                val esEstatico = when (miembro) {
                    is SimboloVariable -> miembro.esEstatico
                    is SimboloFuncion -> miembro.esEstatico
                    else -> false
                }
                if (!esEstatico) {
                    a.diag.error("SEM-054", "'${e.miembro}' no es estatico: se accede con un objeto de la clase '${claseEstatica.nombre}', no con el nombre de la clase.", e.pos)
                    return TError to null
                }
                if (miembro is SimboloVariable && paraLlamada) {
                    a.diag.error("SEM-051", "'${e.miembro}' es un campo, no un metodo: no se puede llamar.", e.pos)
                    return TError to null
                }
                a.simbolos[e.id] = miembro
                return miembro.tipo to miembro
            }
        }

        // Acceso comun por instancia.
        val tObj = tiparValor(e.obj, amb)
        when (tObj) {
            is TError -> return TError to null
            is TClase -> {
                val clase = amb.resolverClase(tObj.nombre)
                if (clase == null) return TError to null   // ya se reporto al declarar el tipo
                val campo = clase.campos[e.miembro]
                val metodo = clase.metodos[e.miembro]
                val miembro: Simbolo? = campo ?: metodo
                if (miembro == null) {
                    a.diag.error("SEM-012", "La clase '${tObj.nombre}' no tiene un campo ni metodo '${e.miembro}'.", e.pos)
                    return TError to null
                }
                val esEstatico = when (miembro) {
                    is SimboloVariable -> miembro.esEstatico
                    is SimboloFuncion -> miembro.esEstatico
                    else -> false
                }
                if (esEstatico) {
                    // Java lo permite con advertencia; aca tambien: el
                    // acceso correcto es por el nombre de la clase.
                    a.diag.advertencia("SEM-055", "'${e.miembro}' es estatico: conviene accederlo como '${tObj.nombre}.${e.miembro}'.", e.pos)
                }
                if (campo != null && paraLlamada) {
                    a.diag.error("SEM-051", "'${e.miembro}' es un campo, no un metodo: no se puede llamar.", e.pos)
                    return TError to null
                }
                if (campo != null) campo.usada = true
                a.simbolos[e.id] = miembro
                return miembro.tipo to miembro
            }
            else -> {
                a.diag.error("SEM-056", "El tipo ${tObj.descripcion} no tiene campos ni metodos.", e.obj.pos)
                return TError to null
            }
        }
    }

    private fun tiparNuevoObjeto(e: NuevoObjeto, amb: Ambito): Tipo {
        val clase = amb.resolverClase(e.clase)
        if (clase == null) {
            a.diag.error("SEM-035", "No existe la clase '${e.clase}'.", e.pos)
            tiparTodo(e.args, amb)
            return TError
        }
        val ctor = clase.constructor
        val esperados = ctor?.parametros ?: emptyList()   // constructor vacio implicito
        if (e.args.size != esperados.size) {
            a.diag.error("SEM-057", "El constructor de '${e.clase}' espera ${esperados.size} argumentos pero recibio ${e.args.size}.", e.pos)
            tiparTodo(e.args, amb)
        } else {
            for (i in e.args.indices) {
                val arg = e.args[i]
                val tArg = tiparValor(arg, amb)
                val tParam = esperados[i]
                if (!esAsignable(tParam, tArg)) {
                    a.diag.error("SEM-008", "El argumento ${i + 1} del constructor de '${e.clase}' debe ser ${tParam.descripcion}, pero es ${tArg.descripcion}.", arg.pos)
                }
            }
        }
        a.simbolos[e.id] = clase
        return TClase(clase.nombre)
    }
}
