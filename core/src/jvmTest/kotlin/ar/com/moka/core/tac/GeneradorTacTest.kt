// ============================================================================
// GeneradorTacTest.kt - Tests de la Fase 4 (generacion de TAC)
// Ubicacion: core/src/commonTest/kotlin/ar/com/moka/core/tac/GeneradorTacTest.kt
// (documento de diseno, seccion 4.9)
//
// Mismo patron que AnalizadorSemanticoTest: el programa se escribe como
// String en el propio test y pasa por el pipeline REAL (lexer -> parser ->
// ConstructorAst -> semantico -> generador). Si cualquier fase cambia,
// estos tests avisan.
//
// Los SIETE ejemplos de la seccion 4.5.4 del documento son tests golden:
// se compara el listado canonico cuadrupla por cuadrupla. Diferencia
// deliberada con el doc: los listados 1-4, 6 y 7 del documento omiten el
// envoltorio inicio_func/fin_func de __principal por brevedad; el TAC
// real lo lleva (como SI muestra el ejemplo 5), y aca se verifica.
//
// El resto de los tests cubre las traducciones de la seccion 4.5.3:
// cortocircuito, ciclos, segun con fall-through, ++/--, asignaciones
// compuestas, conversiones itof, concatenacion, estaticos y clases.
// ============================================================================

package ar.com.moka.core.tac

import ar.com.moka.core.semantico.AnalizadorSemantico
import ar.com.moka.core.semantico.Severidad
import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import ar.com.moka.core.sintactico.ConstructorAst
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneradorTacTest {

    // Pipeline completo hasta el TAC. Corta si el semantico encontro
    // errores: el generador solo corre sobre programas verificados.
    private fun generar(fuente: String): ProgramaTac {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val ast = ConstructorAst.construir(parser.programa())
        val sem = AnalizadorSemantico.analizar(ast)
        val errores = sem.diagnosticos.filter { it.severidad == Severidad.ERROR }
        assertTrue(errores.isEmpty(), "El programa del test tiene errores semanticos: ${errores.map { it.codigo + ": " + it.mensaje }}")
        return GeneradorTac.generar(ast, sem)
    }

    private fun assertTac(p: ProgramaTac, vararg esperado: String) {
        assertEquals(
            esperado.toList(),
            p.comoLineas(),
            "El TAC no coincide.\nEsperado:\n${esperado.joinToString("\n")}\nObtenido:\n${p.comoLineas().joinToString("\n")}"
        )
    }

    // --------------------- ejemplos de la seccion 4.5.4 ----------------------

    @Test
    fun ejemplo1AsignacionYAritmetica() {
        val p = generar("""
            entero a = 4;
            entero b = 3;
            entero r = a + b * 2;
            flotante p = r / 2.0;
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 4, _, a)",
            "2: (=, 3, _, b)",
            "3: (*, b, 2, t1)",
            "4: (+, a, t1, t2)",
            "5: (=, t2, _, r)",
            "6: (itof, r, _, t3)",
            "7: (/, t3, 2.0, t4)",
            "8: (=, t4, _, p)",
            "9: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun ejemplo2SiSino() {
        val p = generar("""
            entero nota = 7;
            si (nota >= 6) {
                imprimir("Aprobado");
            } sino {
                imprimir("Recursa");
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 7, _, nota)",
            "2: (>=, nota, 6, t1)",
            "3: (si_falso, t1, _, L1)",
            "4: (param, \"Aprobado\", _, _)",
            "5: (llamar, imprimir, 1, _)",
            "6: (ir_a, _, _, L2)",
            "7: (etiqueta, _, _, L1)",
            "8: (param, \"Recursa\", _, _)",
            "9: (llamar, imprimir, 1, _)",
            "10: (etiqueta, _, _, L2)",
            "11: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun ejemplo3Mientras() {
        val p = generar("""
            entero i = 0;
            mientras (i < 3) {
                imprimir(i);
                i = i + 1;
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 0, _, i)",
            "2: (etiqueta, _, _, L1)",
            "3: (<, i, 3, t1)",
            "4: (si_falso, t1, _, L2)",
            "5: (param, i, _, _)",
            "6: (llamar, imprimir, 1, _)",
            "7: (+, i, 1, t2)",
            "8: (=, t2, _, i)",
            "9: (ir_a, _, _, L1)",
            "10: (etiqueta, _, _, L2)",
            "11: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun ejemplo4CortocircuitoYComoValor() {
        val p = generar("""
            entero a = 1;
            entero b = 2;
            booleano ok = a > 0 y b > 0;
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 1, _, a)",
            "2: (=, 2, _, b)",
            "3: (>, a, 0, t1)",
            "4: (=, t1, _, t3)",
            "5: (si_falso, t3, _, L1)",
            "6: (>, b, 0, t2)",
            "7: (=, t2, _, t3)",
            "8: (etiqueta, _, _, L1)",
            "9: (=, t3, _, ok)",
            "10: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun ejemplo5LlamadaRecursiva() {
        val p = generar("""
            funcion entero factorial(entero n) {
                si (n <= 1) {
                    retornar 1;
                }
                retornar n * factorial(n - 1);
            }
            entero x = factorial(5);
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, factorial, 1, _)",
            "1: (<=, n, 1, t1)",
            "2: (si_falso, t1, _, L1)",
            "3: (retornar, 1, _, _)",
            "4: (etiqueta, _, _, L1)",
            "5: (-, n, 1, t2)",
            "6: (param, t2, _, _)",
            "7: (llamar, factorial, 1, t3)",
            "8: (*, n, t3, t4)",
            "9: (retornar, t4, _, _)",
            "10: (fin_func, factorial, _, _)",
            "11: (inicio_func, __principal, 0, _)",
            "12: (param, 5, _, _)",
            "13: (llamar, factorial, 1, t1)",
            "14: (=, t1, _, x)",
            "15: (fin_func, __principal, _, _)"
        )
        // Los temporales se reinician en cada funcion (sec. 4.5.1).
        assertEquals(mapOf("factorial" to 0, "__principal" to 11), p.funciones)
        assertEquals(mapOf("L1" to 4), p.etiquetas)
    }

    @Test
    fun ejemplo6Arreglos() {
        val p = generar("""
            entero[] v = nuevo entero[3];
            v[1] = 7;
            entero z = v[1];
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (nuevo_arreglo, entero, 3, t1)",
            "2: (=, t1, _, v)",
            "3: (asignar_indice, v, 1, 7)",
            "4: (leer_indice, v, 1, t2)",
            "5: (=, t2, _, z)",
            "6: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun ejemplo7ObjetosYMetodos() {
        val p = generar("""
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
        assertTac(p,
            "0: (inicio_func, Alumno.constructor, 3, _)",
            "1: (asignar_campo, este, nombre, n)",
            "2: (asignar_campo, este, legajo, l)",
            "3: (asignar_campo, este, promedio, 0.0)",
            "4: (fin_func, Alumno.constructor, _, _)",
            "5: (inicio_func, Alumno.descripcion, 1, _)",
            "6: (leer_campo, este, nombre, t1)",
            "7: (concat, t1, \" (legajo \", t2)",
            "8: (leer_campo, este, legajo, t3)",
            "9: (param, t3, _, _)",
            "10: (llamar, a_cadena, 1, t4)",
            "11: (concat, t2, t4, t5)",
            "12: (concat, t5, \")\", t6)",
            "13: (retornar, t6, _, _)",
            "14: (fin_func, Alumno.descripcion, _, _)",
            "15: (inicio_func, __principal, 0, _)",
            "16: (nuevo_objeto, Alumno, _, t1)",
            "17: (param, t1, _, _)",
            "18: (param, \"Ana\", _, _)",
            "19: (param, 1234, _, _)",
            "20: (llamar, Alumno.constructor, 3, _)",
            "21: (=, t1, _, a)",
            "22: (asignar_campo, a, promedio, 8.5)",
            "23: (param, a, _, _)",
            "24: (llamar, Alumno.descripcion, 1, t2)",
            "25: (param, t2, _, _)",
            "26: (llamar, imprimir, 1, _)",
            "27: (fin_func, __principal, _, _)"
        )
    }

    // --------------------------- cortocircuito ------------------------------

    @Test
    fun oComoValorTambienCorta() {
        val p = generar("""
            entero a = 1;
            entero b = 2;
            booleano ok = a > 0 o b > 0;
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 1, _, a)",
            "2: (=, 2, _, b)",
            "3: (>, a, 0, t1)",
            "4: (=, t1, _, t3)",
            "5: (si_verdadero, t3, _, L1)",
            "6: (>, b, 0, t2)",
            "7: (=, t2, _, t3)",
            "8: (etiqueta, _, _, L1)",
            "9: (=, t3, _, ok)",
            "10: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun condicionDelSiConYNoMaterializaBooleano() {
        // Si la condicion ES un y/o, se salta directo (sec. 4.5.3): no hay
        // temporal acumulador, cada lado alimenta su propio si_falso.
        val p = generar("""
            entero a = 1;
            entero b = 2;
            si (a > 0 y b > 0) {
                imprimir("si");
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 1, _, a)",
            "2: (=, 2, _, b)",
            "3: (>, a, 0, t1)",
            "4: (si_falso, t1, _, L1)",
            "5: (>, b, 0, t2)",
            "6: (si_falso, t2, _, L1)",
            "7: (param, \"si\", _, _)",
            "8: (llamar, imprimir, 1, _)",
            "9: (etiqueta, _, _, L1)",
            "10: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun mientrasConOEnLaCondicion() {
        val p = generar("""
            entero a = 3;
            entero b = 3;
            mientras (a > 0 o b > 0) {
                a = a - 1;
                b = b - 1;
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 3, _, a)",
            "2: (=, 3, _, b)",
            "3: (etiqueta, _, _, L1)",
            "4: (>, a, 0, t1)",
            "5: (si_verdadero, t1, _, L3)",
            "6: (>, b, 0, t2)",
            "7: (si_falso, t2, _, L2)",
            "8: (etiqueta, _, _, L3)",
            "9: (-, a, 1, t3)",
            "10: (=, t3, _, a)",
            "11: (-, b, 1, t4)",
            "12: (=, t4, _, b)",
            "13: (ir_a, _, _, L1)",
            "14: (etiqueta, _, _, L2)",
            "15: (fin_func, __principal, _, _)"
        )
    }

    // ------------------------------- ciclos ----------------------------------

    @Test
    fun hacerMientrasEvaluaLaCondicionAlFinal() {
        val p = generar("""
            entero i = 0;
            hacer {
                i = i + 1;
            } mientras (i < 3);
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 0, _, i)",
            "2: (etiqueta, _, _, L1)",
            "3: (+, i, 1, t1)",
            "4: (=, t1, _, i)",
            "5: (etiqueta, _, _, L2)",
            "6: (<, i, 3, t2)",
            "7: (si_verdadero, t2, _, L1)",
            "8: (etiqueta, _, _, L3)",
            "9: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun paraClasicoConRomperYContinuar() {
        // romper -> Lfin (L2), continuar -> Lcont (L3), como dice 4.5.3.
        val p = generar("""
            para (entero i = 0; i < 3; i++) {
                si (i == 1) continuar;
                si (i == 2) romper;
                imprimir(i);
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 0, _, i)",
            "2: (etiqueta, _, _, L1)",
            "3: (<, i, 3, t1)",
            "4: (si_falso, t1, _, L2)",
            "5: (==, i, 1, t2)",
            "6: (si_falso, t2, _, L4)",
            "7: (ir_a, _, _, L3)",
            "8: (etiqueta, _, _, L4)",
            "9: (==, i, 2, t3)",
            "10: (si_falso, t3, _, L5)",
            "11: (ir_a, _, _, L2)",
            "12: (etiqueta, _, _, L5)",
            "13: (param, i, _, _)",
            "14: (llamar, imprimir, 1, _)",
            "15: (etiqueta, _, _, L3)",
            "16: (+, i, 1, t4)",
            "17: (=, t4, _, i)",
            "18: (ir_a, _, _, L1)",
            "19: (etiqueta, _, _, L2)",
            "20: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun paraCadaSeDesazucaraConIndiceOculto() {
        val p = generar("""
            entero[] notas = [10, 20, 30];
            para_cada (entero nota : notas) {
                imprimir(nota);
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (nuevo_arreglo, entero, 3, t1)",
            "2: (asignar_indice, t1, 0, 10)",
            "3: (asignar_indice, t1, 1, 20)",
            "4: (asignar_indice, t1, 2, 30)",
            "5: (=, t1, _, notas)",
            "6: (=, notas, _, t2)",
            "7: (=, 0, _, t3)",
            "8: (param, t2, _, _)",
            "9: (llamar, longitud, 1, t4)",
            "10: (etiqueta, _, _, L1)",
            "11: (<, t3, t4, t5)",
            "12: (si_falso, t5, _, L2)",
            "13: (leer_indice, t2, t3, t6)",
            "14: (=, t6, _, nota)",
            "15: (param, nota, _, _)",
            "16: (llamar, imprimir, 1, _)",
            "17: (etiqueta, _, _, L3)",
            "18: (+, t3, 1, t7)",
            "19: (=, t7, _, t3)",
            "20: (ir_a, _, _, L1)",
            "21: (etiqueta, _, _, L2)",
            "22: (fin_func, __principal, _, _)"
        )
    }

    // ------------------------------- segun -----------------------------------

    @Test
    fun segunComparaUnaVezYSaltaACadaCaso() {
        val p = generar("""
            entero dia = 2;
            segun (dia) {
                caso 1:
                    imprimir("uno");
                    romper;
                caso 2:
                    imprimir("dos");
                    romper;
                defecto:
                    imprimir("otro");
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 2, _, dia)",
            "2: (=, dia, _, t1)",
            "3: (==, t1, 1, t2)",
            "4: (si_verdadero, t2, _, L2)",
            "5: (==, t1, 2, t3)",
            "6: (si_verdadero, t3, _, L3)",
            "7: (ir_a, _, _, L4)",
            "8: (etiqueta, _, _, L2)",
            "9: (param, \"uno\", _, _)",
            "10: (llamar, imprimir, 1, _)",
            "11: (ir_a, _, _, L1)",
            "12: (etiqueta, _, _, L3)",
            "13: (param, \"dos\", _, _)",
            "14: (llamar, imprimir, 1, _)",
            "15: (ir_a, _, _, L1)",
            "16: (etiqueta, _, _, L4)",
            "17: (param, \"otro\", _, _)",
            "18: (llamar, imprimir, 1, _)",
            "19: (etiqueta, _, _, L1)",
            "20: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun segunSinRomperCaeAlSiguienteCaso() {
        // Fall-through de Java (sec. 2.7): entre cuerpos NO hay salto.
        val p = generar("""
            cadena letra = "a";
            segun (letra) {
                caso "a":
                    imprimir("A");
                caso "b":
                    imprimir("B");
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, \"a\", _, letra)",
            "2: (=, letra, _, t1)",
            "3: (==, t1, \"a\", t2)",
            "4: (si_verdadero, t2, _, L2)",
            "5: (==, t1, \"b\", t3)",
            "6: (si_verdadero, t3, _, L3)",
            "7: (ir_a, _, _, L1)",
            "8: (etiqueta, _, _, L2)",
            "9: (param, \"A\", _, _)",
            "10: (llamar, imprimir, 1, _)",
            "11: (etiqueta, _, _, L3)",
            "12: (param, \"B\", _, _)",
            "13: (llamar, imprimir, 1, _)",
            "14: (etiqueta, _, _, L1)",
            "15: (fin_func, __principal, _, _)"
        )
    }

    // --------------------- incrementos y asignaciones ------------------------

    @Test
    fun incrementosPrefijosYPostfijos() {
        // Postfijo como expresion: copia el viejo ANTES (sec. 4.5.3).
        // Como sentencia: sin copia. El flotante suma 1.0, no 1 + itof.
        val p = generar("""
            entero i = 5;
            entero a = i++;
            entero b = ++i;
            i--;
            --i;
            flotante f = 1.5;
            f++;
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 5, _, i)",
            "2: (=, i, _, t1)",
            "3: (+, i, 1, t2)",
            "4: (=, t2, _, i)",
            "5: (=, t1, _, a)",
            "6: (+, i, 1, t3)",
            "7: (=, t3, _, i)",
            "8: (=, t3, _, b)",
            "9: (-, i, 1, t4)",
            "10: (=, t4, _, i)",
            "11: (-, i, 1, t5)",
            "12: (=, t5, _, i)",
            "13: (=, 1.5, _, f)",
            "14: (+, f, 1.0, t6)",
            "15: (=, t6, _, f)",
            "16: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun asignacionesCompuestas() {
        // x op= e es x = x op e (sec. 4.5.3), con itof y concat segun el tipo.
        val p = generar("""
            entero x = 10;
            x += 5;
            x %= 3;
            flotante f = 1.0;
            f += 2;
            cadena s = "a";
            s += 3;
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 10, _, x)",
            "2: (+, x, 5, t1)",
            "3: (=, t1, _, x)",
            "4: (%, x, 3, t2)",
            "5: (=, t2, _, x)",
            "6: (=, 1.0, _, f)",
            "7: (itof, 2, _, t3)",
            "8: (+, f, t3, t4)",
            "9: (=, t4, _, f)",
            "10: (=, \"a\", _, s)",
            "11: (param, 3, _, _)",
            "12: (llamar, a_cadena, 1, t5)",
            "13: (concat, s, t5, t6)",
            "14: (=, t6, _, s)",
            "15: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun indiceComoDestinoEvaluaArregloEIndiceUnaSolaVez() {
        val p = generar("""
            entero[] v = nuevo entero[3];
            entero i = 1;
            v[i] = v[i] + 1;
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (nuevo_arreglo, entero, 3, t1)",
            "2: (=, t1, _, v)",
            "3: (=, 1, _, i)",
            "4: (leer_indice, v, i, t2)",
            "5: (+, t2, 1, t3)",
            "6: (asignar_indice, v, i, t3)",
            "7: (fin_func, __principal, _, _)"
        )
    }

    // ----------------------- conversiones y cadenas --------------------------

    @Test
    fun itofEnOperacionesArgumentosYRetorno() {
        val p = generar("""
            funcion flotante duplicar(flotante x) {
                retornar x * 2;
            }
            flotante r = duplicar(4);
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, duplicar, 1, _)",
            "1: (itof, 2, _, t1)",
            "2: (*, x, t1, t2)",
            "3: (retornar, t2, _, _)",
            "4: (fin_func, duplicar, _, _)",
            "5: (inicio_func, __principal, 0, _)",
            "6: (itof, 4, _, t1)",
            "7: (param, t1, _, _)",
            "8: (llamar, duplicar, 1, t2)",
            "9: (=, t2, _, r)",
            "10: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun concatenacionAnteponeACadenaAlQueNoEsCadena() {
        val p = generar("""
            imprimir("n=" + 42);
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (param, 42, _, _)",
            "2: (llamar, a_cadena, 1, t1)",
            "3: (concat, \"n=\", t1, t2)",
            "4: (param, t2, _, _)",
            "5: (llamar, imprimir, 1, _)",
            "6: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun literalArregloConMezclaPromocionaAFlotante() {
        val p = generar("""
            flotante[] notas = [1, 2.5, 3];
            imprimir(longitud(notas));
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (nuevo_arreglo, flotante, 3, t1)",
            "2: (itof, 1, _, t2)",
            "3: (asignar_indice, t1, 0, t2)",
            "4: (asignar_indice, t1, 1, 2.5)",
            "5: (itof, 3, _, t3)",
            "6: (asignar_indice, t1, 2, t3)",
            "7: (=, t1, _, notas)",
            "8: (param, notas, _, _)",
            "9: (llamar, longitud, 1, t4)",
            "10: (param, t4, _, _)",
            "11: (llamar, imprimir, 1, _)",
            "12: (fin_func, __principal, _, _)"
        )
    }

    // --------------------------- funciones -----------------------------------

    @Test
    fun funcionesVanAntesDelPrincipalAunqueSeDeclarenDespues() {
        val p = generar("""
            imprimir(cuadrado(5));
            funcion entero cuadrado(entero n) {
                retornar n * n;
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, cuadrado, 1, _)",
            "1: (*, n, n, t1)",
            "2: (retornar, t1, _, _)",
            "3: (fin_func, cuadrado, _, _)",
            "4: (inicio_func, __principal, 0, _)",
            "5: (param, 5, _, _)",
            "6: (llamar, cuadrado, 1, t1)",
            "7: (param, t1, _, _)",
            "8: (llamar, imprimir, 1, _)",
            "9: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun retornarSinValorYLlamadaVacio() {
        val p = generar("""
            funcion vacio saludar() {
                imprimir("hola");
                retornar;
            }
            saludar();
            cadena s = leer();
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, saludar, 0, _)",
            "1: (param, \"hola\", _, _)",
            "2: (llamar, imprimir, 1, _)",
            "3: (retornar, _, _, _)",
            "4: (fin_func, saludar, _, _)",
            "5: (inicio_func, __principal, 0, _)",
            "6: (llamar, saludar, 0, _)",
            "7: (llamar, leer, 0, t1)",
            "8: (=, t1, _, s)",
            "9: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun menosUnarioPliegaConstantes() {
        // -5 es UNA constante, no una operacion (sec. 4.4, pliegue del signo).
        val p = generar("""
            entero n = -5;
            entero m = -n;
            entero minimo = -2147483648;
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, -5, _, n)",
            "2: (neg, n, _, t1)",
            "3: (=, t1, _, m)",
            "4: (=, -2147483648, _, minimo)",
            "5: (fin_func, __principal, _, _)"
        )
    }

    // ------------------------------ clases -----------------------------------

    @Test
    fun campoEstaticoSeAlmacenaComoClasePuntoCampo() {
        // Sec. 4.5.3: el campo estatico vive en el almacen global con el
        // nombre Contador.total, y su inicializador corre al arrancar.
        val p = generar("""
            clase Contador {
                estatico entero total = 0;
            }
            Contador.total = 5;
            imprimir(Contador.total);
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, Contador.constructor, 1, _)",
            "1: (fin_func, Contador.constructor, _, _)",
            "2: (inicio_func, __principal, 0, _)",
            "3: (=, 0, _, Contador.total)",
            "4: (=, 5, _, Contador.total)",
            "5: (param, Contador.total, _, _)",
            "6: (llamar, imprimir, 1, _)",
            "7: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun metodoEstaticoSeLlamaSinEste() {
        val p = generar("""
            clase Util {
                estatico funcion entero doble(entero n) {
                    retornar n * 2;
                }
            }
            imprimir(Util.doble(21));
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, Util.constructor, 1, _)",
            "1: (fin_func, Util.constructor, _, _)",
            "2: (inicio_func, Util.doble, 1, _)",
            "3: (*, n, 2, t1)",
            "4: (retornar, t1, _, _)",
            "5: (fin_func, Util.doble, _, _)",
            "6: (inicio_func, __principal, 0, _)",
            "7: (param, 21, _, _)",
            "8: (llamar, Util.doble, 1, t1)",
            "9: (param, t1, _, _)",
            "10: (llamar, imprimir, 1, _)",
            "11: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun constructorImplicitoCorreLosInicializadoresDeCampos() {
        val p = generar("""
            clase Punto {
                entero px = 3;
                entero py;
            }
            Punto p = nuevo Punto();
            imprimir(p.px);
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, Punto.constructor, 1, _)",
            "1: (asignar_campo, este, px, 3)",
            "2: (fin_func, Punto.constructor, _, _)",
            "3: (inicio_func, __principal, 0, _)",
            "4: (nuevo_objeto, Punto, _, t1)",
            "5: (param, t1, _, _)",
            "6: (llamar, Punto.constructor, 1, _)",
            "7: (=, t1, _, p)",
            "8: (leer_campo, p, px, t2)",
            "9: (param, t2, _, _)",
            "10: (llamar, imprimir, 1, _)",
            "11: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun campoSinCalificarSeLeeYEscribeSobreEste() {
        // Dentro de un metodo, `total` pelado equivale a este.total (2.10).
        val p = generar("""
            clase Acum {
                entero total;
                funcion vacio sumar(entero n) {
                    total = total + n;
                }
            }
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, Acum.constructor, 1, _)",
            "1: (fin_func, Acum.constructor, _, _)",
            "2: (inicio_func, Acum.sumar, 2, _)",
            "3: (leer_campo, este, total, t1)",
            "4: (+, t1, n, t2)",
            "5: (asignar_campo, este, total, t2)",
            "6: (fin_func, Acum.sumar, _, _)",
            "7: (inicio_func, __principal, 0, _)",
            "8: (fin_func, __principal, _, _)"
        )
    }

    // --------------------------- tablas y origen -----------------------------

    @Test
    fun sombreadoUsaElIdUnicoDeCadaVariable() {
        val p = generar("""
            entero x = 1;
            si (verdadero) {
                entero x = 2;
                imprimir(x);
            }
            imprimir(x);
        """.trimIndent())
        assertTac(p,
            "0: (inicio_func, __principal, 0, _)",
            "1: (=, 1, _, x)",
            "2: (si_falso, verdadero, _, L1)",
            "3: (=, 2, _, x#2)",
            "4: (param, x#2, _, _)",
            "5: (llamar, imprimir, 1, _)",
            "6: (etiqueta, _, _, L1)",
            "7: (param, x, _, _)",
            "8: (llamar, imprimir, 1, _)",
            "9: (fin_func, __principal, _, _)"
        )
    }

    @Test
    fun lasCuadruplasLlevanLaPosicionDelFuenteQueLasGenero() {
        val p = generar("""
            funcion entero factorial(entero n) {
                si (n <= 1) {
                    retornar 1;
                }
                retornar n * factorial(n - 1);
            }
            entero x = factorial(5);
        """.trimIndent())
        // La comparacion n <= 1 esta en la linea 2 del fuente.
        assertEquals(2, p.cuadruplas[1].origen!!.linea)
        // Y el listado alineado para la GUI se puede producir.
        assertTrue(p.comoTexto().lines().any { it.contains("(llamar,") && it.contains("factorial") })
    }
}
