// ============================================================================
//  PistasTest.kt - Las pistas didacticas de los diagnosticos
// ============================================================================
//  Pistas.para(codigo, mensaje) decide la sugerencia que acompana a cada
//  error. Se prueba por codigo fijo y por patron de mensaje (SIN-001 y
//  LEX-001, los dos codigos que agrupan muchos casos), mas la propiedad
//  calculada Diagnostico.pista que la expone a la GUI y la CLI.
// ============================================================================
package ar.com.moka.core.errores

import ar.com.moka.core.ast.Posicion
import ar.com.moka.core.semantico.Diagnostico
import ar.com.moka.core.semantico.Severidad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PistasTest {

    private val pos = Posicion(1, 0, 1, 1)

    // ------------------------- pistas fijas -------------------------

    @Test
    fun codigosFijosTienenPista() {
        for (codigo in listOf("LEX-002", "SEM-001", "SEM-004", "SEM-007", "SEM-008", "SEM-013", "EJE-001", "EJE-003", "EJE-004", "EJE-005")) {
            assertNotNull(Pistas.para(codigo, "cualquier mensaje"), "Falta pista fija para $codigo")
        }
    }

    @Test
    fun divisionPorCeroSugiereValidar() {
        val pista = Pistas.para("EJE-001", "Division por cero.")
        assertNotNull(pista)
        assertTrue("divisor" in pista, pista)
    }

    @Test
    fun indiceFueraDeRangoExplicaLosLimites() {
        val pista = Pistas.para("EJE-003", "Indice 5 fuera de rango para arreglo de longitud 5.")
        assertNotNull(pista)
        assertTrue("longitud - 1" in pista, pista)
    }

    @Test
    fun codigoSinPistaDevuelveNull() {
        assertNull(Pistas.para("SEM-020", "La clase 'A' ya fue declarada (linea 1)."))
    }

    // ------------------------- SIN-001 por patron -------------------------

    @Test
    fun sin001FaltaPuntoYComa() {
        val pista = Pistas.para("SIN-001", "se esperaba ';' antes de 'imprimir'.")
        assertNotNull(pista)
        assertTrue("punto y coma" in pista, pista)
    }

    @Test
    fun sin001FaltaCerrarParentesis() {
        val pista = Pistas.para("SIN-001", "se esperaba ')' antes de '{'.")
        assertNotNull(pista)
        assertTrue("parentesis" in pista, pista)
    }

    @Test
    fun sin001IgualEnLugarDeIgualIgual() {
        val pista = Pistas.para("SIN-001", "se esperaba '==' o '!=' pero se encontro '='.")
        assertNotNull(pista)
        assertTrue("=='" in pista, pista)
    }

    @Test
    fun sin001PuntoYComaDeMas() {
        val pista = Pistas.para("SIN-001", "sobra ';'; se esperaba una instruccion.")
        assertNotNull(pista)
        assertTrue("no llevan ';'" in pista, pista)
    }

    @Test
    fun sin001SinPatronConocidoDevuelveNull() {
        assertNull(Pistas.para("SIN-001", "no se pudo interpretar '###'."))
    }

    // ------------------------- LEX-001 por patron -------------------------

    @Test
    fun lex001ComillasTipograficas() {
        val pista = Pistas.para("LEX-001", "caracter no reconocido: '“'.")
        assertNotNull(pista)
        assertTrue("comilla recta" in pista, pista)
    }

    @Test
    fun lex001OtroCaracter() {
        val pista = Pistas.para("LEX-001", "caracter no reconocido: '#'.")
        assertNotNull(pista)
        assertTrue("no pertenece al lenguaje" in pista, pista)
    }

    // ------------------- propiedad calculada -------------------

    @Test
    fun diagnosticoExponeLaPistaCalculada() {
        val conPista = Diagnostico(Severidad.ERROR, "EJE-001", "Division por cero.", pos)
        assertNotNull(conPista.pista)
        val sinPista = Diagnostico(Severidad.ERROR, "SEM-020", "duplicada.", pos)
        assertNull(sinPista.pista)
    }
}
