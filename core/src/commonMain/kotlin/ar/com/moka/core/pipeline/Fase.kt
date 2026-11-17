// =======================================================================
//  Fase.kt — Las cinco fases del pipeline (sec. 4.7)
// =======================================================================
//  Enum identico al del documento. Orden de corrida: el ordinal es el
//  orden del pipeline, asi que no ponerle valores explicitos ni
//  reordenar las constantes.
// =======================================================================
package ar.com.moka.core.pipeline

enum class Fase { LEXICA, SINTACTICA, SEMANTICA, GENERACION_TAC, EJECUCION }
