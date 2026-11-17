// ============================================================================
// AnalizadorSemanticoTest.kt - Tests de la Fase 3 (analisis semantico)
// Ubicacion: core/src/commonTest/kotlin/ar/com/moka/core/semantico/AnalizadorSemanticoTest.kt
// (documento de diseno, seccion 4.9)
//
// Mismo patron que ConstructorAstTest: el programa se escribe como String
// en el propio test, se pasa por el pipeline real (lexer -> parser ->
// ConstructorAst) y recien ahi por el AnalizadorSemantico. Nada de
// construir ASTs a mano: si la gramatica cambia, estos tests avisan.
//
// Se prueban dos cosas:
//   1) DIAGNOSTICOS: un programa minimo por codigo de error del catalogo
//      (SEM-001 a SEM-015) y de las reglas sin codigo (SEM-016 en adelante).
//   2) TABLAS LATERALES: ambitos como arbol, idUnico de sombreados, tipos
//      por nodo, ligado de nombres.
//
// Los filtros por severidad ERROR son a proposito: varios programas chicos
// tambien disparan la advertencia "variable nunca usada", que no es lo que
// el test mira.
// ============================================================================

package ar.com.moka.core.semantico

import ar.com.moka.core.ast.*
import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import ar.com.moka.core.sintactico.ConstructorAst
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AnalizadorSemanticoTest {

    // Pipeline completo hasta el semantico: String -> tokens -> parse tree
    // -> AST -> ResultadoSemantico. Cada corrida crea sus instancias: el
    // runtime de ANTLR no es thread-safe (seccion 4.1).
    private fun analizar(fuente: String): ResultadoSemantico {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val ast = ConstructorAst.construir(parser.programa())
        return AnalizadorSemantico.analizar(ast)
    }

    private fun errores(r: ResultadoSemantico): List<Diagnostico> =
        r.diagnosticos.filter { it.severidad == Severidad.ERROR }

    private fun assertTieneCodigo(r: ResultadoSemantico, codigo: String) {
        assertTrue(
            r.diagnosticos.any { it.codigo == codigo },
            "Se esperaba el diagnostico $codigo. Diagnosticos: ${r.diagnosticos.map { it.codigo + ": " + it.mensaje }}"
        )
    }

    private fun assertSinErrores(r: ResultadoSemantico) {
        assertTrue(
            errores(r).isEmpty(),
            "Se esperaba cero errores. Errores: ${errores(r).map { it.codigo + ": " + it.mensaje }}"
        )
    }

    // --------------------------- programa sano ------------------------------

    @Test
    fun programaSanoNoProduceErroresYLlenaLasTablas() {
        val r = analizar("""
            entero contador = 0;
            mientras (contador < 5) {
                contador = contador + 1;
            }
            imprimir(contador);
        """.trimIndent())
        assertSinErrores(r)
        assertTrue(r.tipos.isNotEmpty())
        assertTrue(r.simbolos.isNotEmpty())
        assertTrue(r.chequeos.isNotEmpty())
        // El arbol de ambitos: global con un hijo (el bloque del mientras).
        assertEquals(TipoAmbito.GLOBAL, r.raiz.tipo)
        assertEquals(1, r.raiz.hijos.size)
        assertEquals(TipoAmbito.BLOQUE, r.raiz.hijos[0].tipo)
    }

    // ------------------------- catalogo SEM-001 a SEM-015 --------------------

    @Test
    fun variableNoDeclaradaSugiereLaMasParecida() {
        val r = analizar("""
            entero contador = 1;
            imprimir(contdor);
        """.trimIndent())
        assertTieneCodigo(r, "SEM-001")
        val d = r.diagnosticos.first { it.codigo == "SEM-001" }
        assertEquals("La variable 'contdor' no esta declarada. ¿Quisiste decir 'contador'?", d.mensaje)
    }

    @Test
    fun redeclararEnElMismoBloqueEsError() {
        val r = analizar("""
            entero x = 1;
            entero x = 2;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-002")
        assertTrue(r.diagnosticos.first { it.codigo == "SEM-002" }.mensaje.contains("linea 1"))
    }

    @Test
    fun sombrearEnBloqueInternoNoEsError() {
        val r = analizar("""
            entero x = 1;
            si (verdadero) {
                entero x = 2;
                imprimir(x);
            }
        """.trimIndent())
        assertSinErrores(r)
        // Los dos x quedan con idUnico distinto: "x" y "x#2".
        val bloque = r.raiz.hijos.single()
        assertEquals("x", r.raiz.simbolos.getValue("x").idUnico)
        assertEquals("x#2", bloque.simbolos.getValue("x").idUnico)
    }

    @Test
    fun asignarCadenaAEnteroEsSem003() {
        val r = analizar("""
            entero x = "hola";
        """.trimIndent())
        assertTieneCodigo(r, "SEM-003")
        val d = r.diagnosticos.first { it.codigo == "SEM-003" }
        assertEquals("No se puede asignar un valor de tipo cadena a una variable de tipo entero.", d.mensaje)
    }

    @Test
    fun flotanteAEnteroPideConversion() {
        val r = analizar("""
            entero x = 1.5;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-004")
        assertTrue(r.diagnosticos.first { it.codigo == "SEM-004" }.mensaje.contains("a_entero"))
    }

    @Test
    fun condicionDelSiDebeSerBooleana() {
        val r = analizar("""
            si (1) {
                imprimir("x");
            }
        """.trimIndent())
        assertTieneCodigo(r, "SEM-005")
        assertEquals("La condicion del 'si' debe ser booleano, pero es entero.", r.diagnosticos.first { it.codigo == "SEM-005" }.mensaje)
    }

    @Test
    fun noSePuedeModificarUnaConstante() {
        val r = analizar("""
            constante entero MAXIMO = 100;
            MAXIMO = 200;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-006")
    }

    @Test
    fun constanteSinInicializadorEsError() {
        val r = analizar("""
            constante entero MAXIMO;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-021")
    }

    @Test
    fun cantidadDeArgumentosIncorrecta() {
        val r = analizar("""
            funcion entero sumar(entero a, entero b) {
                retornar a + b;
            }
            entero r = sumar(1, 2, 3);
        """.trimIndent())
        assertTieneCodigo(r, "SEM-007")
        assertEquals("La funcion 'sumar' espera 2 argumentos pero recibio 3.", r.diagnosticos.first { it.codigo == "SEM-007" }.mensaje)
    }

    @Test
    fun tipoDeArgumentoIncorrecto() {
        val r = analizar("""
            funcion entero sumar(entero a, entero b) {
                retornar a + b;
            }
            entero r = sumar(1, "dos");
        """.trimIndent())
        assertTieneCodigo(r, "SEM-008")
        assertEquals("El argumento 2 de 'sumar' debe ser entero, pero es cadena.", r.diagnosticos.first { it.codigo == "SEM-008" }.mensaje)
    }

    @Test
    fun enteroDondeEsperaFlotantePromociona() {
        val r = analizar("""
            funcion flotante duplicar(flotante x) {
                retornar x * 2;
            }
            flotante r = duplicar(4);
        """.trimIndent())
        assertSinErrores(r)
        assertTrue(r.chequeos.any { it.regla.contains("itof") })
    }

    @Test
    fun funcionSinRetornoEnTodosLosCaminos() {
        val r = analizar("""
            funcion entero valor(entero n) {
                si (n > 0) {
                    retornar 1;
                }
            }
        """.trimIndent())
        assertTieneCodigo(r, "SEM-009")
    }

    @Test
    fun siYSinoConRetornoEnAmbasRamasCierra() {
        val r = analizar("""
            funcion entero signo(entero n) {
                si (n >= 0) {
                    retornar 1;
                } sino {
                    retornar -1;
                }
            }
        """.trimIndent())
        assertSinErrores(r)
    }

    @Test
    fun romperFueraDeCicloEsError() {
        val r = analizar("""
            romper;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-010")
    }

    @Test
    fun continuarFueraDeCicloEsError() {
        val r = analizar("""
            continuar;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-016")
    }

    @Test
    fun romperDentroDelSegunEsValido() {
        val r = analizar("""
            entero dia = 2;
            segun (dia) {
                caso 1:
                    imprimir("uno");
                    romper;
                defecto:
                    imprimir("otro");
            }
        """.trimIndent())
        assertSinErrores(r)
    }

    @Test
    fun moduloConFlotanteEsSem011() {
        val r = analizar("""
            imprimir(5.5 % 2.0);
        """.trimIndent())
        assertTieneCodigo(r, "SEM-011")
    }

    @Test
    fun miembroInexistenteEsSem012() {
        val r = analizar("""
            clase Alumno {
                entero legajo;
            }
            Alumno a = nuevo Alumno();
            imprimir(a.edad);
        """.trimIndent())
        assertTieneCodigo(r, "SEM-012")
    }

    @Test
    fun resultadoVacioComoValorEsSem013() {
        val r = analizar("""
            funcion vacio saludar() {
                imprimir("hola");
            }
            entero x = saludar();
        """.trimIndent())
        assertTieneCodigo(r, "SEM-013")
    }

    @Test
    fun literalEnteroFueraDeRango() {
        val r = analizar("""
            entero grande = 3000000000;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-014")
        val d = r.diagnosticos.first { it.codigo == "SEM-014" }
        assertEquals("Literal entero fuera de rango: 3000000000 (maximo 2147483647).", d.mensaje)
    }

    @Test
    fun menosUnarioPliegaElMinimoEntero() {
        // -2147483648 entra: el menos es unario y se pliega (seccion 4.4).
        val ok = analizar("""
            entero minimo = -2147483648;
            imprimir(minimo);
        """.trimIndent())
        assertSinErrores(ok)
        // pero 2147483648 pelado, sin el signo, no entra
        val mal = analizar("""
            entero noEntra = 2147483648;
        """.trimIndent())
        assertTieneCodigo(mal, "SEM-014")
    }

    @Test
    fun ladoIzquierdoInvalidoEsSem015() {
        val r = analizar("""
            1 + 2 = 3;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-015")
    }

    // --------------------------- funciones -----------------------------------

    @Test
    fun funcionesSeUsanAntesDeSuDeclaracion() {
        // Hoisting de firmas (seccion 2.8): la pasada A registra cuadrado
        // antes de que la primera linea lo llame.
        val r = analizar("""
            imprimir(cuadrado(5));
            funcion entero cuadrado(entero n) {
                retornar n * n;
            }
        """.trimIndent())
        assertSinErrores(r)
    }

    @Test
    fun variableGlobalNoSeUsaAntesDeDeclararla() {
        // Las variables NO se hoistean: f se analiza en orden textual y g
        // todavia no existe.
        val r = analizar("""
            funcion entero f() {
                retornar g;
            }
            entero g = 3;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-001")
    }

    @Test
    fun redeclararParametroEnElCuerpoEsError() {
        val r = analizar("""
            funcion entero f(entero n) {
                entero n = 2;
                retornar n;
            }
        """.trimIndent())
        assertTieneCodigo(r, "SEM-002")
    }

    @Test
    fun recursionTipaBien() {
        val r = analizar("""
            funcion entero factorial(entero n) {
                si (n <= 1) {
                    retornar 1;
                }
                retornar n * factorial(n - 1);
            }
            imprimir(factorial(5));
        """.trimIndent())
        assertSinErrores(r)
    }

    // ---------------------------- arreglos -----------------------------------

    @Test
    fun literalArregloConMezclaPromocionaAFlotante() {
        val r = analizar("""
            flotante[] notas = [1, 2.5, 3];
            imprimir(longitud(notas));
        """.trimIndent())
        assertSinErrores(r)
    }

    @Test
    fun literalArregloConTiposIncompatibles() {
        val r = analizar("""
            entero[] raro = [1, "dos"];
        """.trimIndent())
        assertTieneCodigo(r, "SEM-041")
    }

    @Test
    fun paraCadaRecorreArreglosYLaVariableEsDeSoloLectura() {
        val r = analizar("""
            entero[] notas = [1, 2, 3];
            para_cada (entero nota : notas) {
                nota = 5;
            }
        """.trimIndent())
        assertTieneCodigo(r, "SEM-019")
    }

    @Test
    fun paraCadaSobreAlgoQueNoEsArreglo() {
        val r = analizar("""
            entero n = 3;
            para_cada (entero x : n) {
                imprimir(x);
            }
        """.trimIndent())
        assertTieneCodigo(r, "SEM-025")
    }

    // ------------------------------ segun ------------------------------------

    @Test
    fun segunConOperandoInvalido() {
        val r = analizar("""
            flotante x = 1.5;
            segun (x) {
                defecto:
                    imprimir("d");
            }
        """.trimIndent())
        assertTieneCodigo(r, "SEM-027")
    }

    @Test
    fun segunConCasoRepetidoYDosDefecto() {
        val r = analizar("""
            entero dia = 2;
            segun (dia) {
                caso 1:
                    imprimir("uno");
                caso 1:
                    imprimir("repetido");
                defecto:
                    imprimir("a");
                defecto:
                    imprimir("b");
            }
        """.trimIndent())
        assertTieneCodigo(r, "SEM-029")
        assertTieneCodigo(r, "SEM-030")
    }

    // ------------------------------ clases -----------------------------------

    @Test
    fun claseCompletaDelDocumentoSinErrores() {
        // El ejemplo de la seccion 2.10, tal cual.
        val r = analizar("""
            clase Alumno {
                cadena nombre;
                entero legajo;
                flotante promedio;

                constructor(cadena n, entero l) {
                    este.nombre = n;
                    este.legajo = l;
                    este.promedio = 0.0;
                }

                funcion cadena descripcion() {
                    retornar este.nombre + " (legajo " + a_cadena(este.legajo) + ")";
                }
            }

            Alumno a = nuevo Alumno("Ana", 1234);
            a.promedio = 8.5;
            imprimir(a.descripcion());
        """.trimIndent())
        assertSinErrores(r)
        // La clase quedo registrada con sus miembros en el ambito global.
        val alumno = r.raiz.clases.getValue("Alumno")
        assertEquals(setOf("nombre", "legajo", "promedio"), alumno.campos.keys)
        assertEquals(setOf("descripcion"), alumno.metodos.keys)
        assertTrue(alumno.constructor != null)
    }

    @Test
    fun esteFueraDeClaseEsError() {
        val r = analizar("""
            imprimir(este);
        """.trimIndent())
        assertTieneCodigo(r, "SEM-036")
    }

    @Test
    fun estaticoSeAccedePorNombreDeClase() {
        val r = analizar("""
            clase Contador {
                estatico entero total = 0;
            }
            Contador.total = 5;
            imprimir(Contador.total);
        """.trimIndent())
        assertSinErrores(r)
    }

    @Test
    fun miembroDeInstanciaNoSeAccedePorClase() {
        val r = analizar("""
            clase Punto {
                entero x;
            }
            imprimir(Punto.x);
        """.trimIndent())
        assertTieneCodigo(r, "SEM-054")
    }

    @Test
    fun constructorConArgumentosIncorrectos() {
        val r = analizar("""
            clase Alumno {
                entero legajo;
                constructor(entero l) {
                    este.legajo = l;
                }
            }
            Alumno a = nuevo Alumno("mal");
        """.trimIndent())
        assertTieneCodigo(r, "SEM-008")
    }

    // --------------------------- advertencias --------------------------------

    @Test
    fun variableLocalNuncaUsadaAdvierteSinFrenar() {
        val r = analizar("""
            entero sinUso = 3;
            imprimir("hola");
        """.trimIndent())
        assertTieneCodigo(r, "SEM-018")
        val d = r.diagnosticos.first { it.codigo == "SEM-018" }
        assertEquals(Severidad.ADVERTENCIA, d.severidad)
        assertTrue(errores(r).isEmpty())
    }

    @Test
    fun declaracionComoCuerpoDelSiAdvierte() {
        // La variable no puede llamarse "y" (ni "o" ni "no"): son palabras
        // reservadas, los operadores logicos del lenguaje (seccion 3.2).
        val r = analizar("""
            si (verdadero) entero temp = 2;
        """.trimIndent())
        assertTieneCodigo(r, "SEM-017")
        assertTrue(errores(r).isEmpty())
    }

    // --------------------------- tablas laterales ----------------------------

    @Test
    fun elLigadoLigaCadaNombreConSuSimbolo() {
        val r = analizar("""
            entero x = 1;
            si (verdadero) {
                entero x = 2;
                imprimir(x);
            }
            imprimir(x);
        """.trimIndent())
        assertSinErrores(r)
        // Hay dos SimboloVariable llamados x y cada Nombre ligo con el suyo:
        // el del bloque con "x#2", el de afuera con "x".
        val simbolosX = r.simbolos.values.filterIsInstance<SimboloVariable>().filter { it.nombre == "x" }
        assertEquals(setOf("x", "x#2"), simbolosX.map { it.idUnico }.toSet())
    }

    @Test
    fun losTiposQuedanAnotadosPorNodo() {
        val r = analizar("""
            imprimir(1 + 2.5);
        """.trimIndent())
        assertSinErrores(r)
        // La suma mixta da flotante en ALGUN nodo de la tabla de tipos.
        assertTrue(r.tipos.values.any { it is TFlotante })
        assertTrue(r.chequeos.any { it.regla.contains("aditiva") && it.ok })
    }

    @Test
    fun acumulaVariosErroresSinFrenarEnElPrimero() {
        // Politica de la v2 (seccion 4.1): no se frena en el primero.
        val r = analizar("""
            entero a = "uno";
            entero b = "dos";
            entero c = "tres";
        """.trimIndent())
        assertEquals(3, errores(r).count { it.codigo == "SEM-003" })
    }

    @Test
    fun nombreSinDeclararNiSugerencia() {
        // Ningun nombre visible se parece a "zzzz": mensaje sin sugerencia.
        val r = analizar("""
            imprimir(zzzz);
        """.trimIndent())
        assertTieneCodigo(r, "SEM-001")
        val d = r.diagnosticos.first { it.codigo == "SEM-001" }
        assertFalse(d.mensaje.contains("Quisiste"))
    }
}
