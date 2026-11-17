// =======================================================================
//  EstadoFase.kt — Estado de una fase en la barra de fases (sec. 5.2)
// =======================================================================
//  Lo que cada chip de la barra de fases muestra: pendiente (gris),
//  corriendo, completada (tick), con errores (cruz y cantidad) u omitida
//  (guion, con el motivo). Es la expresion visual de los eventos
//  FaseIniciada / FaseCompletada / FaseOmitida del pipeline.
// =======================================================================
package ar.com.moka.app.estado

enum class EstadoFase { PENDIENTE, CORRIENDO, COMPLETADA, CON_ERRORES, OMITIDA }
