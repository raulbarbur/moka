// =======================================================================
//  Modo.kt — Modos de procesamiento de la fachada (sec. 4.7)
// =======================================================================
//  SOLO_ANALISIS: fases 1 a 4, sin ejecutar (el "analisis en vivo" de
//  la GUI mientras se escribe). EJECUTAR: pipeline completo.
//  PASO_A_PASO: como EJECUTAR pero la maquina emite un evento Paso por
//  cuadrupla (sec. 4.6).
// =======================================================================
package ar.com.moka.core.pipeline

enum class Modo { SOLO_ANALISIS, EJECUTAR, PASO_A_PASO }
