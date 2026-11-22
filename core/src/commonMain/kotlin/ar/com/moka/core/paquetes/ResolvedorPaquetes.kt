// ============================================================================
//  ResolvedorPaquetes.kt — Carga y union de paquetes (Moka 2, fase P1)
// ============================================================================
//  Un archivo Moka puede abrir con `paquete util;` (a que paquete
//  pertenece) y pedir otros con `usar util;`. Esta fase corre ENTRE la
//  sintactica y la semantica: lee las fuentes de los paquetes pedidos,
//  las analiza (lexico + sintaxis) y une todo en UN solo ProgramaAst.
//
//  Modelo de nombres (decision con Raul, estilo Java): cada declaracion
//  de un paquete se registra con su nombre cualificado ("util.sumar"),
//  asi dos paquetes pueden tener funciones con el mismo nombre sin
//  chocar. Desde otro archivo se llama cualificado: util.sumar(2, 3).
//
//  Reglas:
//   - El archivo principal es el unico con "codigo suelto": un paquete
//     solo declara funciones y clases (SEM-066 si tiene sentencias).
//   - El encabezado `paquete X;` del archivo cargado tiene que coincidir
//     con el nombre pedido en `usar X;` (SEM-064).
//   - Ciclos de usos (a usa b, b usa a) y paquetes no encontrados son
//     errores (SEM-065, SEM-062). Pedir dos veces el mismo paquete es
//     valido: se carga una sola vez.
//
//  Los ids de los nodos unidos se renumeran por archivo (cada fuente se
//  parsea con una base distinta) para que las tablas laterales del
//  semantico, indexadas por Nodo.id, no mezclen nodos de archivos
//  distintos.
// ============================================================================
package ar.com.moka.core.paquetes

import ar.com.moka.core.ast.DeclClase
import ar.com.moka.core.ast.DeclFuncion
import ar.com.moka.core.ast.Nodo
import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.ast.ProgramaAst
import ar.com.moka.core.ast.Sent
import ar.com.moka.core.lexico.AnalizadorLexico
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import ar.com.moka.core.sintactico.AnalizadorSintactico

// Quien sabe encontrar la fuente de un paquete. La CLI la busca en el
// disco; los tests usan un mapa en memoria; la GUI todavia no tiene
// carpeta de proyecto (P3) y pasa null: todo `usar` da SEM-062.
fun interface ProveedorPaquetes {
    fun fuenteDe(paquete: String): String?
}

class ResolvedorPaquetes(private val proveedor: ProveedorPaquetes?) {

    // Lo que la union deja para las fases siguientes:
    //  - programa: el AST unido, dependencias primero y principal al final.
    //  - paqueteDe: de que paquete vino cada declaracion toplevel (las del
    //    archivo principal no aparecen: su paquete es el vacio).
    //  - usosDe: los `usar` de cada archivo, por paquete ("" = principal).
    //  - diagnosticos: errores de carga (faltantes, ciclos, sintaxis...).
    data class Resultado(
        val programa: ProgramaAst,
        val paqueteDe: Map<Int, String>,
        val usosDe: Map<String, List<String>>,
        val diagnosticos: List<Diagnostico>
    )

    // Base de ids por archivo: el principal conserva los suyos (1 en
    // adelante, como siempre) y cada paquete corre un bloque entero.
    private val BLOQUE_IDS = 1_000_000

    fun resolver(principal: ProgramaAst): Resultado {
        val diagnosticos = mutableListOf<Diagnostico>()
        val cargados = LinkedHashMap<String, ProgramaAst>()   // orden de carga
        val usosDe = mutableMapOf<String, List<String>>()
        val enPila = ArrayDeque<String>()

        usosDe[""] = principal.usos.map { it.nombre }
        var proximoBloque = 1

        fun error(codigo: String, mensaje: String, pos: Posicion) {
            diagnosticos.add(Diagnostico(Severidad.ERROR, codigo, mensaje, pos))
        }

        // DFS con deteccion de ciclos. Devuelve false si hubo error grave.
        fun cargar(nombre: String, posPedido: Posicion, bloque: Int) {
            // El ciclo se chequea ANTES que el "ya cargado": en la pila
            // estan los paquetes cuya carga todavia no termino, y pedir
            // uno de esos es un ciclo aunque ya figure en cargados.
            if (nombre in enPila) {
                val ciclo = (enPila.dropWhile { it != nombre } + nombre).joinToString(" -> ")
                error("SEM-065", "Ciclo de paquetes: $ciclo. Un paquete no puede terminarse usando a si mismo.", posPedido)
                return
            }
            if (cargados.containsKey(nombre)) return           // ya cargado
            val fuente = proveedor?.fuenteDe(nombre)
            if (fuente == null) {
                error("SEM-062", "No se encontro el paquete '$nombre'.", posPedido)
                return
            }
            // P4: el nombre del paquete viaja como `archivo` en cada
            // posicion, asi un error dentro de util.moka dice "util".
            val lex = AnalizadorLexico().analizar(fuente, nombre)
            val sin = AnalizadorSintactico().analizar(fuente, primerId = bloque * BLOQUE_IDS, archivo = nombre)
            val ast = sin.ast
            if (lex.diagnosticos.isNotEmpty() || sin.diagnosticos.isNotEmpty() || ast == null) {
                val primero = (lex.diagnosticos + sin.diagnosticos).firstOrNull()
                val detalle = primero?.let { " (linea ${it.pos.linea}: ${it.mensaje})" } ?: ""
                error("SEM-063", "El paquete '$nombre' tiene errores de sintaxis$detalle", posPedido)
                return
            }
            if (ast.paquete != nombre) {
                val esperado = if (ast.paquete == null) "no declara ninguno" else "declara '${ast.paquete}'"
                error("SEM-064", "El archivo del paquete '$nombre' $esperado. Su primera linea tiene que ser 'paquete $nombre;'.", posPedido)
                return
            }
            val suelto = ast.declaraciones.firstOrNull { it is Sent }
            if (suelto != null) {
                error("SEM-066", "El paquete '$nombre' tiene codigo suelto (linea ${suelto.pos.linea} de su archivo). Un paquete solo declara funciones y clases; el programa principal es el unico que ejecuta.", posPedido)
                return
            }
            enPila.addLast(nombre)
            cargados[nombre] = ast
            usosDe[nombre] = ast.usos.map { it.nombre }
            for (uso in ast.usos) {
                proximoBloque++
                cargar(uso.nombre, uso.pos, proximoBloque)
            }
            enPila.removeLast()
        }

        for (uso in principal.usos) {
            proximoBloque++
            cargar(uso.nombre, uso.pos, proximoBloque)
        }

        // Si hubo errores de carga no unimos nada: el semantico no corre.
        if (diagnosticos.isNotEmpty()) {
            return Resultado(principal, emptyMap(), usosDe, diagnosticos)
        }

        // Union: dependencias primero (orden de carga), principal al final.
        // Asi las funciones de los paquetes ya estan registradas cuando el
        // semantico llega al codigo que las usa... aunque en realidad la
        // pasada A registra TODO antes de la B, asi que el orden solo
        // importa para los mensajes de redeclaracion.
        val paqueteDe = mutableMapOf<Int, String>()
        val declaraciones = mutableListOf<Nodo>()
        for ((nombre, ast) in cargados) {
            for (d in ast.declaraciones) {
                if (d is DeclFuncion || d is DeclClase) {
                    paqueteDe[d.id] = nombre
                    declaraciones.add(d)
                }
            }
        }
        declaraciones.addAll(principal.declaraciones)
        val unido = ProgramaAst(principal.id, principal.pos, declaraciones, principal.paquete, principal.usos)
        return Resultado(unido, paqueteDe, usosDe, diagnosticos)
    }
}
