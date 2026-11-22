// ============================================================================
//  TiposCualificadosTest.kt — Tipos cualificados entre paquetes (Moka 2, P2)
// ============================================================================
//  P1 trajo `paquete`/`usar` y llamadas a funciones cualificadas
//  (util.sumar(...)). P2 hace lo propio con los TIPOS: declarar variables,
//  parametros, retornos y arreglos de una clase de otro paquete
//  (geometria.Punto p = nuevo geometria.Punto(1, 2)).
//
//  Reglas cubiertas:
//   - El prefijo tiene que ser el paquete actual o uno pedido con `usar`.
//   - Paquete cargado pero no pedido: SEM-069 ("te falta usar ...").
//   - Paquete inexistente o clase inexistente: SEM-035, como siempre.
//   - Dos paquetes pueden tener clases con el mismo nombre sin chocar.
//   - Los campos estaticos con inicializador de una clase empaquetada se
//     generan con su nombre cualificado (arreglo de un faltante de P1).
// ============================================================================
package ar.com.moka.core.paquetes

import ar.com.moka.core.ejecucion.Consola
import ar.com.moka.core.ejecucion.EventoEjecucion
import ar.com.moka.core.pipeline.Configuracion
import ar.com.moka.core.pipeline.EventoPipeline
import ar.com.moka.core.pipeline.Moka
import ar.com.moka.core.semantico.Diagnostico
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

private class ConsolaMudaTipos : Consola {
    override fun escribir(texto: String) {}
    override suspend fun leerLinea(): String? = null
}

class TiposCualificadosTest {

    private data class Corrida(val salida: String, val diagnosticos: List<Diagnostico>, val exito: Boolean)

    private fun correr(principal: String, paquetes: Map<String, String> = emptyMap()): Corrida {
        val proveedor = ProveedorPaquetes { paquetes[it] }
        val eventos = runBlocking {
            Moka(Configuracion(proveedorPaquetes = proveedor)).procesar(principal, ConsolaMudaTipos()).toList()
        }
        val salida = eventos.filterIsInstance<EventoPipeline.DeEjecucion>()
            .map { it.evento }
            .filterIsInstance<EventoEjecucion.Salida>()
            .joinToString("") { it.texto }
        val diagnosticos = eventos.filterIsInstance<EventoPipeline.FaseCompletada>()
            .flatMap { it.diagnosticos }
        val exito = eventos.filterIsInstance<EventoPipeline.Terminado>().last().exito
        return Corrida(salida, diagnosticos, exito)
    }

    // Paquete con una clase Punto: campos, constructor y un metodo.
    private val geometria = """
        paquete geometria;
        clase Punto {
            entero cx;
            entero cy;

            constructor(entero px, entero py) {
                este.cx = px;
                este.cy = py;
            }

            funcion entero suma() {
                retornar este.cx + este.cy;
            }
        }
    """.trimIndent()

    // ------------------------- caminos felices -------------------------

    @Test
    fun variableYConstruccionCualificadasEjecutan() {
        val r = correr(
            """
            usar geometria;
            geometria.Punto p = nuevo geometria.Punto(3, 4);
            imprimir(a_cadena(p.suma()));
            """.trimIndent(),
            mapOf("geometria" to geometria)
        )
        assertEquals("7\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun parametroYRetornoCualificados() {
        val r = correr(
            """
            usar geometria;
            funcion entero dobleSuma(geometria.Punto p) {
                retornar p.suma() * 2;
            }
            funcion geometria.Punto espejo(geometria.Punto p) {
                retornar nuevo geometria.Punto(p.cy, p.cx);
            }
            geometria.Punto q = espejo(nuevo geometria.Punto(1, 2));
            imprimir(a_cadena(dobleSuma(q)));
            """.trimIndent(),
            mapOf("geometria" to geometria)
        )
        assertEquals("6\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun arregloDeTipoCualificado() {
        val r = correr(
            """
            usar geometria;
            geometria.Punto[] ps = nuevo geometria.Punto[2];
            ps[0] = nuevo geometria.Punto(1, 1);
            ps[1] = nuevo geometria.Punto(2, 2);
            entero total = 0;
            para_cada (geometria.Punto p : ps) {
                total = total + p.suma();
            }
            imprimir(a_cadena(total));
            """.trimIndent(),
            mapOf("geometria" to geometria)
        )
        assertEquals("6\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun dosPaquetesConLaMismaClaseNoChocan() {
        val dibujo = """
            paquete dibujo;
            clase Punto {
                cadena color;
                constructor(cadena c) {
                    este.color = c;
                }
            }
        """.trimIndent()
        val r = correr(
            """
            usar geometria;
            usar dibujo;
            geometria.Punto a = nuevo geometria.Punto(5, 5);
            dibujo.Punto b = nuevo dibujo.Punto("rojo");
            imprimir(a_cadena(a.suma()) + " " + b.color);
            """.trimIndent(),
            mapOf("geometria" to geometria, "dibujo" to dibujo)
        )
        assertEquals("10 rojo\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun dentroDelPaqueteLaClaseSeUsaSinCualificar() {
        val geoConFabrica = geometria + """
            
            funcion Punto origen() {
                retornar nuevo Punto(0, 0);
            }
        """.trimIndent()
        val r = correr(
            """
            usar geometria;
            geometria.Punto p = geometria.origen();
            imprimir(a_cadena(p.suma()));
            """.trimIndent(),
            mapOf("geometria" to geoConFabrica)
        )
        assertEquals("0\n", r.salida)
        assertTrue(r.exito)
    }

    @Test
    fun estaticoDeClaseEmpaquetadaSeInicializa() {
        // Regresion de P1: los campos estaticos con "=" de una clase en
        // paquete no se generaban (se buscaba la clase por nombre simple).
        val util = """
            paquete util;
            clase Contador {
                estatico entero total = 5;
            }
            funcion entero leerTotal() {
                retornar Contador.total;
            }
        """.trimIndent()
        val r = correr(
            """
            usar util;
            imprimir(a_cadena(util.leerTotal()));
            """.trimIndent(),
            mapOf("util" to util)
        )
        assertEquals("5\n", r.salida)
        assertTrue(r.exito)
    }

    // ------------------------- errores didacticos -------------------------

    @Test
    fun tipoCualificadoSinUsarDaSem069() {
        // El paquete figura cargado (principal lo pidio para otra cosa a
        // traves de otro paquete) pero el archivo que escribe el tipo no
        // lo pidio: el error tiene que nombrar el `usar` que falta.
        val consumidor = """
            paquete consumidor;
            funcion entero medir(geometria.Punto p) {
                retornar p.suma();
            }
        """.trimIndent()
        val r = correr(
            """
            usar geometria;
            usar consumidor;
            geometria.Punto p = nuevo geometria.Punto(1, 2);
            imprimir(a_cadena(consumidor.medir(p)));
            """.trimIndent(),
            mapOf("geometria" to geometria, "consumidor" to consumidor)
        )
        assertTrue(r.diagnosticos.any { it.codigo == "SEM-069" && "usar geometria;" in it.mensaje },
            "Se esperaba SEM-069, diagnosticos: ${r.diagnosticos.map { it.codigo + ": " + it.mensaje }}")
        assertTrue(!r.exito)
    }

    @Test
    fun construccionCualificadaSinUsarDaSem069() {
        val consumidor = """
            paquete consumidor;
            funcion entero medir() {
                geometria.Punto p = nuevo geometria.Punto(1, 2);
                retornar p.suma();
            }
        """.trimIndent()
        val r = correr(
            """
            usar geometria;
            usar consumidor;
            imprimir(a_cadena(consumidor.medir()));
            """.trimIndent(),
            mapOf("geometria" to geometria, "consumidor" to consumidor)
        )
        assertTrue(r.diagnosticos.any { it.codigo == "SEM-069" },
            "Se esperaba SEM-069, diagnosticos: ${r.diagnosticos.map { it.codigo + ": " + it.mensaje }}")
        assertTrue(!r.exito)
    }

    @Test
    fun tipoDePaqueteNoCargadoDaSem035() {
        val r = correr(
            """
            fantasma.Cosa c = nuevo fantasma.Cosa();
            """.trimIndent()
        )
        assertTrue(r.diagnosticos.any { it.codigo == "SEM-035" },
            "Se esperaba SEM-035, diagnosticos: ${r.diagnosticos.map { it.codigo + ": " + it.mensaje }}")
        assertTrue(!r.exito)
    }

    @Test
    fun claseInexistenteEnPaqueteUsadoDaSem035() {
        val r = correr(
            """
            usar geometria;
            geometria.Circulo c = nuevo geometria.Circulo();
            """.trimIndent(),
            mapOf("geometria" to geometria)
        )
        assertTrue(r.diagnosticos.any { it.codigo == "SEM-035" },
            "Se esperaba SEM-035, diagnosticos: ${r.diagnosticos.map { it.codigo + ": " + it.mensaje }}")
        assertTrue(!r.exito)
    }
}
