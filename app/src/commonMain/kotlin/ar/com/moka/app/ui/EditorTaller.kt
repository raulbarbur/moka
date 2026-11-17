// =======================================================================
//  EditorTaller.kt — El editor de codigo fuente (sec. 5.3, panel 1)
// =======================================================================
//  Version 7.2: campo de texto multilinea con fuente monoespaciada y
//  scroll propio. Cada tecla avisa al controlador (cambiarFuente), que
//  guarda el texto en el estado; al apretar Ejecutar se compila lo que
//  esta en pantalla.
//
//  Lo que le falta y ya tiene paso asignado: numeros de linea, subrayado
//  de errores y resaltado de sintaxis (7.3 y 7.8), abrir/guardar (7.9).
// =======================================================================
package ar.com.moka.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorTaller(
    fuente: String,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
        Column {
            Text(
                "Codigo fuente",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            BasicTextField(
                value = fuente,
                onValueChange = onCambio,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
