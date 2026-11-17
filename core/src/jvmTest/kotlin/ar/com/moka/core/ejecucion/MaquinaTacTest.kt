// ============================================================================
//  MaquinaTacTest.kt — Tests de la maquina de ejecucion TAC (sec. 4.6 y 4.9)
// ============================================================================
//  Cada test mete la FUENTE Moka al pipeline completo (lexer -> parser
//  -> ConstructorAst -> AnalizadorSemantico -> GeneradorTac -> MaquinaTac)
//  y verifica la secuencia de eventos que sale por el Flow: la salida
//  impresa, los errores EJE- con su codigo y mensaje, y los eventos
//  especiales (PidiendoEntrada, Paso, Fin).
//
//  Los primeros tests son goldens de punta a punta de los ejemplos del
//  documento de disenio (sec. 2.3, 2.5, 2.6), ejecutados de verdad.
//
//  Vive en jvmTest (no commonTest) porque recolectar el Flow usa
//  runBlocking, que es de JVM.
// ============================================================================
package ar.com.moka.core.ejecucion

import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import ar.com.moka.core.semantico.AnalizadorSemantico
import ar.com.moka.core.semantico.Severidad
import ar.com.moka.core.sintactico.ConstructorAst
import ar.com.moka.core.tac.GeneradorTac
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

// Consola de mentira: ignora la escritura (la salida se mira por los
// eventos) y sirve las lineas de entrada prefijadas en orden.
class ConsolaDePrueba(entradas: List<String> = emptyList()) : Consola {
    private val pendientes = entradas.toMutableList()
    override fun escribir(texto: String) {}
    override suspend fun leerLinea(): String? =
        if (pendientes.isEmpty()) null else pendientes.removeAt(0)
}

class MaquinaTacTest {

    // -------------------------------------------------------------------
    //  Pipeline completo: fuente -> tokens -> parse -> AST -> semantico
    //  -> TAC -> ejecucion
    // -------------------------------------------------------------------

    private fun ejecutar(
        fuente: String,
        consola: Consola = ConsolaDePrueba(),
        config: ConfigEjecucion = ConfigEjecucion()
    ): List<EventoEjecucion> {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val ast = ConstructorAst.construir(parser.programa())
        val sem = AnalizadorSemantico.analizar(ast)
        val errores = sem.diagnosticos.filter { it.severidad == Severidad.ERROR }
        assertTrue(
            errores.isEmpty(),
            "El programa del test tiene errores semanticos: ${errores.map { it.codigo + ": " + it.mensaje }}"
        )
        val tac = GeneradorTac.generar(ast, sem)
        return runBlocking { MaquinaTac(tac, consola, config).ejecutar().toList() }
    }

    private fun salidaDe(eventos: List<EventoEjecucion>): String =
        eventos.filterIsInstance<EventoEjecucion.Salida>().joinToString("") { it.texto }

    private fun errorDe(eventos: List<EventoEjecucion>): EventoEjecucion.ErrorEjecucion? =
        eventos.filterIsInstance<EventoEjecucion.ErrorEjecucion>().firstOrNull()

    // Todo programa sin errores termina con Fin y sin ErrorEjecucion.
    private fun ejecutarOk(
        fuente: String,
        consola: Consola = ConsolaDePrueba(),
        config: ConfigEjecucion = ConfigEjecucion()
    ): List<EventoEjecucion> {
        val eventos = ejecutar(fuente, consola, config)
        assertNull(errorDe(eventos), "Error de ejecucion inesperado: ${errorDe(eventos)?.diagnostico}")
        assertTrue(eventos.last() is EventoEjecucion.Fin, "El programa no termino con Fin: $eventos")
        return eventos
    }

    // ===================================================================
    //  Goldens: los ejemplos del documento, de punta a punta
    // ===================================================================

    // Sec. 2.3, ejemplo 1: precedencia y promocion entero -> flotante.
    @Test
    fun ejemplo1ExpresionesYPromocion() {
        val eventos = ejecutarOk(
            """
            entero r = 4 + 3 * 2;
            flotante p = 10 / 2.0;
            imprimir(r);
            imprimir(p);
            """.trimIndent()
        )
        assertEquals("10\n5\n", salidaDe(eventos))
    }

    // Sec. 2.5, ejemplo 2: condicional con funcion y variable global.
    @Test
    fun ejemplo2Condicional() {
        val eventos = ejecutarOk(
            """
            entero nota = 7;
            funcion vacio asignarNota() {
                si (nota >= 6) {
                    imprimir("Aprobado");
                } sino {
                    imprimir("Recursa");
                }
            }
            asignarNota();
            """.trimIndent()
        )
        assertEquals("Aprobado\n", salidaDe(eventos))
    }

    @Test
    fun ejemplo2CondicionalRamaSino() {
        val eventos = ejecutarOk(
            """
            entero nota = 4;
            funcion vacio asignarNota() {
                si (nota >= 6) {
                    imprimir("Aprobado");
                } sino {
                    imprimir("Recursa");
                }
            }
            asignarNota();
            """.trimIndent()
        )
        assertEquals("Recursa\n", salidaDe(eventos))
    }

    // Sec. 2.5, ejemplo 3: ciclo mientras.
    @Test
    fun ejemplo3Mientras() {
        val eventos = ejecutarOk(
            """
            entero i = 0;
            mientras (i < 3) {
                imprimir(i);
                i = i + 1;
            }
            """.trimIndent()
        )
        assertEquals("0\n1\n2\n", salidaDe(eventos))
    }

    // Sec. 2.3, ejemplo 4: cortocircuito. Con a = 0 el `y` no evalua el
    // segundo operando: si lo evaluara, b / a seria division por cero.
    @Test
    fun ejemplo4CortocircuitoYNoDividePorCero() {
        val eventos = ejecutarOk(
            """
            entero a = 0;
            entero b = 3;
            booleano ok = a > 0 y b / a > 0;
            imprimir(ok);
            """.trimIndent()
        )
        assertEquals("falso\n", salidaDe(eventos))
    }

    // El `o` tampoco evalua el segundo operando cuando el primero ya
    // alcanza (con a = 0, 10 / a explotaria si se evaluara).
    @Test
    fun ejemplo4CortocircuitoONoDividePorCero() {
        val eventos = ejecutarOk(
            """
            entero a = 0;
            booleano ok = a == 0 o 10 / a > 0;
            imprimir(ok);
            """.trimIndent()
        )
        assertEquals("verdadero\n", salidaDe(eventos))
    }

    // Sec. 2.5, ejemplo 5: recursividad (factorial).
    @Test
    fun ejemplo5Factorial() {
        val eventos = ejecutarOk(
            """
            funcion entero factorial(entero n) {
                si (n <= 1) {
                    retornar 1;
                }
                retornar n * factorial(n - 1);
            }
            entero x = factorial(5);
            imprimir(x);
            """.trimIndent()
        )
        assertEquals("120\n", salidaDe(eventos))
    }

    // Sec. 2.6, ejemplo 6: arreglos.
    @Test
    fun ejemplo6Arreglos() {
        val eventos = ejecutarOk(
            """
            entero[] v = nuevo entero[3];
            v[0] = 7;
            v[1] = v[0] * 2;
            entero z = v[0] + v[2];
            imprimir(z);
            """.trimIndent()
        )
        assertEquals("7\n", salidaDe(eventos))
    }

    // Sec. 2.6, ejemplo 7: clase con constructor y metodo.
    @Test
    fun ejemplo7ClasesCompleto() {
        val eventos = ejecutarOk(
            """
            clase Alumno {
                cadena nombre;
                entero legajo;
                constructor(cadena n, entero l) {
                    este.nombre = n;
                    este.legajo = l;
                }
                funcion cadena descripcion() {
                    retornar nombre + " (legajo " + a_cadena(legajo) + ")";
                }
            }
            Alumno a = nuevo Alumno("Ana", 1234);
            imprimir(a.descripcion());
            """.trimIndent()
        )
        assertEquals("Ana (legajo 1234)\n", salidaDe(eventos))
    }

    // ===================================================================
    //  Formato de impresion (sec. 2.8)
    // ===================================================================

    @Test
    fun imprimirFormatosBasicos() {
        val eventos = ejecutarOk(
            """
            imprimir(5.0);
            imprimir(3.14);
            imprimir(nulo);
            imprimir(verdadero);
            imprimir('a');
            """.trimIndent()
        )
        assertEquals("5\n3.14\nnulo\nverdadero\na\n", salidaDe(eventos))
    }

    @Test
    fun imprimirSinSaltoNoAgregaSalto() {
        val eventos = ejecutarOk(
            """
            imprimir_sin_salto("a");
            imprimir_sin_salto(1);
            imprimir("b");
            """.trimIndent()
        )
        assertEquals("a1b\n", salidaDe(eventos))
    }

    // ===================================================================
    //  Sentencias y operadores
    // ===================================================================

    @Test
    fun paraClasicoAcumula() {
        val eventos = ejecutarOk(
            """
            entero suma = 0;
            para (entero i = 0; i < 10; i = i + 1) {
                suma = suma + i;
            }
            imprimir(suma);
            """.trimIndent()
        )
        assertEquals("45\n", salidaDe(eventos))
    }

    @Test
    fun paraCadaRecorreArreglo() {
        val eventos = ejecutarOk(
            """
            entero[] nums = nuevo entero[3];
            nums[0] = 10;
            nums[1] = 20;
            nums[2] = 30;
            entero suma = 0;
            para_cada (entero n : nums) {
                suma = suma + n;
            }
            imprimir(suma);
            imprimir(longitud(nums));
            """.trimIndent()
        )
        assertEquals("60\n3\n", salidaDe(eventos))
    }

    @Test
    fun hacerMientrasEjecutaAlMenosUnaVez() {
        val eventos = ejecutarOk(
            """
            entero i = 10;
            hacer {
                i = i + 1;
            } mientras (i < 3);
            imprimir(i);
            """.trimIndent()
        )
        assertEquals("11\n", salidaDe(eventos))
    }

    @Test
    fun segunEligeCasoConRomper() {
        val eventos = ejecutarOk(
            """
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
            """.trimIndent()
        )
        assertEquals("dos\n", salidaDe(eventos))
    }

    // Sin romper, el caso cae en el siguiente (sec. 2.5).
    @Test
    fun segunCaeAlSiguienteSinRomper() {
        val eventos = ejecutarOk(
            """
            entero dia = 1;
            segun (dia) {
                caso 1:
                    imprimir("uno");
                caso 2:
                    imprimir("dos");
                defecto:
                    imprimir("otro");
            }
            """.trimIndent()
        )
        assertEquals("uno\ndos\notro\n", salidaDe(eventos))
    }

    @Test
    fun romperYContinuarEnPara() {
        val eventos = ejecutarOk(
            """
            para (entero i = 0; i < 5; i = i + 1) {
                si (i == 1) { continuar; }
                si (i == 3) { romper; }
                imprimir(i);
            }
            """.trimIndent()
        )
        assertEquals("0\n2\n", salidaDe(eventos))
    }

    @Test
    fun incrementosPrefijoYPostfijo() {
        val eventos = ejecutarOk(
            """
            entero i = 5;
            entero a = i++;
            entero b = ++i;
            i--;
            --i;
            imprimir(a);
            imprimir(b);
            imprimir(i);
            """.trimIndent()
        )
        assertEquals("5\n7\n5\n", salidaDe(eventos))
    }

    @Test
    fun asignacionesCompuestas() {
        val eventos = ejecutarOk(
            """
            entero x = 10;
            x += 5;
            x -= 3;
            x *= 2;
            x /= 5;
            x %= 3;
            imprimir(x);
            """.trimIndent()
        )
        assertEquals("1\n", salidaDe(eventos))
    }

    // El literal flotante en contexto entero promueve por itof (sec.
    // 4.5.2) y 5.0 se imprime como "5".
    @Test
    fun itofPromueveEnterosAFlotante() {
        val eventos = ejecutarOk(
            """
            flotante p = 3;
            funcion entero duplicar(entero n) {
                retornar 2 * n;
            }
            imprimir(p);
            imprimir(duplicar(4));
            """.trimIndent()
        )
        assertEquals("3\n8\n", salidaDe(eventos))
    }

    @Test
    fun concatenacionConNumeros() {
        val eventos = ejecutarOk(
            """
            cadena msg = "n=" + 42;
            imprimir(msg + "!");
            """.trimIndent()
        )
        assertEquals("n=42!\n", salidaDe(eventos))
    }

    // ===================================================================
    //  Funciones, marcos y memoria
    // ===================================================================

    // Los arreglos pasan por referencia: la funcion ve la misma memoria
    // (modelo de heap de la sec. 4.6).
    @Test
    fun mutacionDeArregloEsCompartida() {
        val eventos = ejecutarOk(
            """
            funcion vacio poner(entero[] v, entero x) {
                v[0] = x;
            }
            entero[] v = nuevo entero[1];
            v[0] = 1;
            poner(v, 99);
            imprimir(v[0]);
            """.trimIndent()
        )
        assertEquals("99\n", salidaDe(eventos))
    }

    // Variables distintas (x y x#2) en marcos/distintos ambitos no se
    // pisan (sec. 2.7).
    @Test
    fun sombreadoMantieneVariables() {
        val eventos = ejecutarOk(
            """
            entero x = 1;
            si (verdadero) {
                entero x = 2;
                imprimir(x);
            }
            imprimir(x);
            """.trimIndent()
        )
        assertEquals("2\n1\n", salidaDe(eventos))
    }

    // Campos estaticos: uno solo por clase, con valor por defecto 0, y
    // los de instancia son independientes (sec. 2.10).
    @Test
    fun campoYMetodoEstatico() {
        val eventos = ejecutarOk(
            """
            clase Contador {
                estatico entero total;
                entero x;
                estatico funcion entero doble(entero n) {
                    retornar 2 * n;
                }
            }
            imprimir(Contador.total);
            Contador.total = 5;
            imprimir(Contador.total);
            Contador c1 = nuevo Contador();
            Contador c2 = nuevo Contador();
            c1.x = 10;
            c2.x = 20;
            imprimir(c1.x);
            imprimir(c2.x);
            imprimir(Contador.doble(21));
            """.trimIndent()
        )
        assertEquals("0\n5\n10\n20\n42\n", salidaDe(eventos))
    }

    // a_cadena() propio se usa al imprimir el objeto (sec. 2.6); si la
    // clase no lo define, se imprime "Clase@id".
    @Test
    fun aCadenaPropioSeUsaAlImprimir() {
        val eventos = ejecutarOk(
            """
            clase Alumno {
                cadena nombre;
                funcion cadena a_cadena() {
                    retornar "alumno " + este.nombre;
                }
            }
            clase Punto {
                entero x;
            }
            Alumno a = nuevo Alumno();
            a.nombre = "Ana";
            Punto p = nuevo Punto();
            imprimir(a);
            imprimir(p);
            """.trimIndent()
        )
        assertEquals("alumno Ana\nPunto@2\n", salidaDe(eventos))
    }

    // ===================================================================
    //  Conversiones builtin (sec. 2.8)
    // ===================================================================

    @Test
    fun conversiones() {
        val eventos = ejecutarOk(
            """
            imprimir(a_entero(3.9));
            imprimir(a_flotante("2.5"));
            imprimir(a_entero("41") + 1);
            imprimir(a_booleano("verdadero"));
            """.trimIndent()
        )
        assertEquals("3\n2.5\n42\nverdadero\n", salidaDe(eventos))
    }

    // ===================================================================
    //  Entrada (sec. 2.8 y 4.6)
    // ===================================================================

    @Test
    fun leerYLeerEnteroFuncionan() {
        val eventos = ejecutarOk(
            """
            cadena s = leer();
            imprimir(s);
            entero n = leer_entero();
            imprimir(n + 1);
            """.trimIndent(),
            consola = ConsolaDePrueba(listOf("hola", "42"))
        )
        assertEquals("hola\n43\n", salidaDe(eventos))
    }

    // Antes de suspenderse esperando la linea, la maquina avisa con un
    // evento PidiendoEntrada (sec. 4.6).
    @Test
    fun pidiendoEntradaSeEmiteAntesDeLeer() {
        val eventos = ejecutarOk(
            """
            cadena s = leer();
            imprimir(s);
            """.trimIndent(),
            consola = ConsolaDePrueba(listOf("hola"))
        )
        val orden = eventos.map {
            when (it) {
                is EventoEjecucion.PidiendoEntrada -> "pide"
                is EventoEjecucion.Salida -> "sale"
                else -> "otro"
            }
        }
        assertEquals(listOf("pide", "sale", "otro"), orden)
    }

    @Test
    fun errorEntradaInvalidaLeerEntero() {
        val eventos = ejecutar(
            "entero n = leer_entero();",
            consola = ConsolaDePrueba(listOf("abc"))
        )
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-005", err.diagnostico.codigo)
    }

    // ===================================================================
    //  Errores de ejecucion (sec. 2.9 y 4.6)
    // ===================================================================

    @Test
    fun errorDivisionPorCero() {
        val eventos = ejecutar("imprimir(1 / 0);")
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-001", err.diagnostico.codigo)
    }

    // Division flotante por cero NO es error: sigue la semantica de los
    // flotantes (Infinity), igual que en los lenguajes de referencia.
    @Test
    fun divisionFlotantePorCeroNoEsError() {
        val eventos = ejecutarOk("imprimir(1.0 / 0.0 > 0.0);")
        assertEquals("verdadero\n", salidaDe(eventos))
    }

    @Test
    fun errorRestoPorCero() {
        val eventos = ejecutar("imprimir(7 % 0);")
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-002", err.diagnostico.codigo)
    }

    // El mensaje es el del ejemplo de la sec. 2.9 del documento.
    @Test
    fun errorIndiceFueraDeRango() {
        val eventos = ejecutar(
            """
            entero[] v = nuevo entero[5];
            v[7] = 1;
            """.trimIndent()
        )
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-003", err.diagnostico.codigo)
        assertEquals("Indice 7 fuera de rango para arreglo de longitud 5.", err.diagnostico.mensaje)
    }

    @Test
    fun errorAccesoANulo() {
        val eventos = ejecutar(
            """
            clase Alumno {
                entero legajo;
            }
            Alumno a = nulo;
            imprimir(a.legajo);
            """.trimIndent()
        )
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-004", err.diagnostico.codigo)
        assertTrue(err.diagnostico.mensaje.contains("nulo"))
    }

    @Test
    fun errorTamanoNegativoDeArreglo() {
        val eventos = ejecutar(
            """
            entero n = 0 - 5;
            entero[] v = nuevo entero[n];
            """.trimIndent()
        )
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-008", err.diagnostico.codigo)
    }

    // El mensaje es el de la sec. 4.6 del documento.
    @Test
    fun errorProfundidadMaxima() {
        val eventos = ejecutar(
            """
            funcion entero infinito(entero n) {
                retornar infinito(n + 1);
            }
            imprimir(infinito(0));
            """.trimIndent(),
            config = ConfigEjecucion(profundidadMaxima = 60)
        )
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-006", err.diagnostico.codigo)
        assertTrue(err.diagnostico.mensaje.contains("profundidad maxima de llamadas"))
    }

    // Guardarrial de la GUI: un ciclo infinito corta en el limite
    // configurado (sec. 4.6).
    @Test
    fun errorLimiteDeCuadruplas() {
        val eventos = ejecutar(
            """
            mientras (verdadero) {
                entero x = 1;
            }
            """.trimIndent(),
            config = ConfigEjecucion(limiteCuadruplas = 500)
        )
        val err = errorDe(eventos)
        assertNotNull(err)
        assertEquals("EJE-007", err.diagnostico.codigo)
    }

    // Un error de ejecucion corta el programa: no hay Fin despues.
    @Test
    fun errorNoEmiteFin() {
        val eventos = ejecutar("imprimir(1 / 0);")
        assertNull(eventos.filterIsInstance<EventoEjecucion.Fin>().firstOrNull())
    }

    // ===================================================================
    //  Eventos de control
    // ===================================================================

    @Test
    fun finReportaCuadruplasEjecutadas() {
        val eventos = ejecutarOk("imprimir(1);")
        val fin = eventos.last() as EventoEjecucion.Fin
        assertTrue(fin.cuadruplasEjecutadas > 0)
    }

    // Modo paso a paso: cada cuadrupla sale como Paso con la vista de la
    // pila, y la variable x ya ligada aparece en el marco (sec. 4.6).
    @Test
    fun pasoAPasoEmitePasosConMarcos() {
        val eventos = ejecutarOk(
            """
            funcion vacio f() {
                entero x = 1;
                imprimir(x);
            }
            f();
            """.trimIndent(),
            config = ConfigEjecucion(pasoAPaso = true)
        )
        val pasos = eventos.filterIsInstance<EventoEjecucion.Paso>()
        assertTrue(pasos.isNotEmpty())
        // La x del test es LOCAL a f: las variables toplevel son globales
        // y no viven en ningun marco (mapa global, sec. 4.6), asi que la
        // vista se verifica dentro de la funcion.
        assertTrue(pasos.any { it.marcos.last().nombre == "f" && it.marcos.last().variables["x"] == "1" })
    }
}
