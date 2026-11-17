package ar.com.moka.core.sintactico

import ar.com.moka.core.ast.*
import ar.com.moka.core.generado.MokaLexer
import ar.com.moka.core.generado.MokaParser
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// Tests del ConstructorAst: verifican que el parse tree de ANTLR se traduce
// al AST con la forma esperada. El patron siempre es el mismo:
//   1. se arma el AST desde un fuente en un String (construirAst)
//   2. se navega el arbol con `assertIs`, que falla si el nodo no es
//      del tipo esperado y lo devuelve ya casteado si lo es
//   3. se hacen asserts sobre los campos
class ConstructorAstTest {

    private fun construirAst(fuente: String): ProgramaAst {
        val lexer = MokaLexer(CharStreams.fromString(fuente))
        val parser = MokaParser(CommonTokenStream(lexer))
        val arbol = parser.programa()
        assertEquals(0, parser.numberOfSyntaxErrors, "el programa debe parsear sin errores")
        return ConstructorAst.construir(arbol)
    }

    @Test
    fun declaracionConPrecedencia() {
        // "entero x = 1 + 2 * 3;" -> DeclVariable cuyo inicial es
        // Binaria(MAS, LitEntero(1), Binaria(POR, LitEntero(2), LitEntero(3)))
        // El assert clave: el POR quedo ADENTRO del MAS. La precedencia la
        // resolvio ANTLR al parsear y el AST la hereda gratis.
        val ast = construirAst("entero x = 1 + 2 * 3;")
        assertEquals(1, ast.declaraciones.size)
        val decl = assertIs<DeclVariable>(ast.declaraciones[0])
        assertEquals("x", decl.nombre)
        assertEquals("entero", decl.tipo.nombre)
        val suma = assertIs<Binaria>(decl.inicial)
        assertEquals(OpBinario.MAS, suma.op)
        assertEquals(1L, assertIs<LitEntero>(suma.izq).valor)
        val producto = assertIs<Binaria>(suma.der)
        assertEquals(OpBinario.POR, producto.op)
        assertEquals(2L, assertIs<LitEntero>(producto.izq).valor)
        assertEquals(3L, assertIs<LitEntero>(producto.der).valor)
    }

    @Test
    fun siSinoAnidado() {
        // Las sentencias (si, mientras, asignaciones...) solo viven dentro de
        // un bloque: por eso el codigo de prueba va dentro de una funcion.
        val ast = construirAst("""
            funcion vacio prueba() {
                si (x > 0) {
                    si (x > 10) { b = 1; } sino { b = 2; }
                }
            }
        """.trimIndent())

        val fun1 = assertIs<DeclFuncion>(ast.declaraciones[0])
        val siExterno = assertIs<SentSi>(fun1.cuerpo.sentencias[0])

        // la condicion es Binaria(MAYOR, Nombre(x), LitEntero(0))
        val cond = assertIs<Binaria>(siExterno.cond)
        assertEquals(OpBinario.MAYOR, cond.op)
        assertEquals("x", assertIs<Nombre>(cond.izq).ident)

        // el si externo NO tiene sino: el campo es null, no una rama vacia
        assertNull(siExterno.sino)

        // el "entonces" es un Bloque con el si interno adentro
        val entonces = assertIs<Bloque>(siExterno.entonces)
        val siInterno = assertIs<SentSi>(entonces.sentencias[0])

        // y aca si hay sino: un Bloque con "y = 2;"
        val sinoInterno = assertIs<Bloque>(siInterno.sino)
        // una asignacion seguida de ";" es una SentExpr que envuelve la
        // expresion Asignacion (en Moka la asignacion es una expresion,
        // como en Java: se puede usar adentro de otra expresion)
        val asig = assertIs<Asignacion>(assertIs<SentExpr>(sinoInterno.sentencias[0]).expr)
        assertEquals(OpAsignacion.ASIGNA, asig.op)
        assertEquals("b", assertIs<Nombre>(asig.destino).ident)
        assertEquals(2L, assertIs<LitEntero>(asig.valor).valor)
    }

    @Test
    fun llamadaImprimir() {
        val ast = construirAst("""
            funcion vacio prueba() {
                imprimir("hola");
            }
        """.trimIndent())

        val fun1 = assertIs<DeclFuncion>(ast.declaraciones[0])
        val sent = assertIs<SentExpr>(fun1.cuerpo.sentencias[0])
        val llamada = assertIs<Llamada>(sent.expr)

        // `imprimir` es un Nombre cualquiera, NO una palabra clave: para el
        // lenguaje es una funcion como cualquier otra. Que exista y que haga
        // lo que hace es cosa de la biblioteca estandar (fase semantica).
        assertEquals("imprimir", assertIs<Nombre>(llamada.callee).ident)
        assertEquals(1, llamada.args.size)
        assertEquals("hola", assertIs<LitCadena>(llamada.args[0]).valor)
    }
}
