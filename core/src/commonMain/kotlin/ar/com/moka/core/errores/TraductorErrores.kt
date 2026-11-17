// ============================================================================
//  TraductorErrores.kt — Mensajes de ANTLR en ingles -> espanol (sec. 4.8.2)
// ============================================================================
//  ANTLR reporta los errores de sintaxis en ingles y con los NOMBRES de los
//  tokens ("mismatched input 'imprimir' expecting PUNTO_COMA"). Aca se
//  convierten al formato del documento:
//      "Error de sintaxis en linea 4, columna 12: se esperaba ';' pero se
//       encontro 'imprimir'."
//  La tabla TOKEN_A_TEXTO traduce cada nombre simbolico a lo que el alumno
//  ve en el lenguaje (PUNTO_COMA -> ';', ID -> "un identificador").
//  Vive en el paquete errores, no en sintactico, porque el catalogo de
//  mensajes es de todo el nucleo (sec. 4.2).
// ============================================================================
package ar.com.moka.core.errores

object TraductorErrores {

    // Convierte el mensaje crudo de ANTLR. Linea 1-based y columna 0-based,
    // la misma convencion de Posicion en todo el nucleo.
    fun traducir(msg: String, linea: Int, columna: Int): String {
        val detalle = traducirDetalle(msg)
        return "Error de sintaxis en linea $linea, columna $columna: $detalle"
    }

    private fun traducirDetalle(msg: String): String {
        // "mismatched input 'x' expecting Y"
        MISMATCH.find(msg)?.let { m ->
            val encontrado = m.groupValues[1]
            val esperado = traducirEsperado(m.groupValues[2])
            return "se esperaba $esperado pero se encontro $encontrado."
        }
        // "missing Y at 'x'"
        MISSING.find(msg)?.let { m ->
            val esperado = traducirEsperado(m.groupValues[1])
            val donde = m.groupValues[2]
            return "se esperaba $esperado antes de $donde."
        }
        // "extraneous input 'x' expecting Y"
        EXTRANEO.find(msg)?.let { m ->
            val sobrante = m.groupValues[1]
            val esperado = traducirEsperado(m.groupValues[2])
            return "sobra $sobrante; se esperaba $esperado."
        }
        // "no viable alternative at input 'x'"
        NO_VIABLE.find(msg)?.let { m ->
            return "no se pudo interpretar ${m.groupValues[1]}."
        }
        // Cualquier otra forma: se deja el texto original (mejor en ingles
        // que inventar un significado).
        return "$msg."
    }

    // El "expecting" de ANTLR puede ser un solo token ("PUNTO_COMA") o un
    // conjunto ("{PUNTO_COMA, LLAVE_DER}"). Se traduce nombre por nombre.
    private fun traducirEsperado(crudo: String): String {
        val nombres = crudo.trim().removePrefix("{").removeSuffix("}")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val traducidos = nombres.map { textoDeToken(it) }.distinct()
        return when (traducidos.size) {
            0 -> crudo
            1 -> traducidos[0]
            2 -> "${traducidos[0]} o ${traducidos[1]}"
            else -> traducidos.dropLast(1).joinToString(", ") + " o " + traducidos.last()
        }
    }

    private fun textoDeToken(nombre: String): String =
        TOKEN_A_TEXTO[nombre] ?: nombre

    private val MISMATCH = Regex("""mismatched input (.*?) expecting (.+)""")
    private val MISSING = Regex("""missing (.+?) at (.+)""")
    private val EXTRANEO = Regex("""extraneous input (.*?) expecting (.+)""")
    private val NO_VIABLE = Regex("""no viable alternative at input (.+)""")

    // Nombre simbolico de la gramatica (sec. 3.2) -> texto para el alumno.
    private val TOKEN_A_TEXTO = mapOf(
        // palabras clave: se muestran tal cual, entre comillas simples
        "SI" to "'si'", "SINO" to "'sino'", "MIENTRAS" to "'mientras'",
        "HACER" to "'hacer'", "PARA" to "'para'", "PARA_CADA" to "'para_cada'",
        "SEGUN" to "'segun'", "CASO" to "'caso'", "DEFECTO" to "'defecto'",
        "ROMPER" to "'romper'", "CONTINUAR" to "'continuar'",
        "RETORNAR" to "'retornar'", "FUNCION" to "'funcion'",
        "CLASE" to "'clase'", "CONSTRUCTOR" to "'constructor'",
        "NUEVO" to "'nuevo'", "ESTE" to "'este'", "ESTATICO" to "'estatico'",
        "CONSTANTE" to "'constante'",
        "Y" to "'y'", "O" to "'o'", "NO" to "'no'",
        "T_ENTERO" to "'entero'", "T_FLOTANTE" to "'flotante'",
        "T_BOOLEANO" to "'booleano'", "T_CARACTER" to "'caracter'",
        "T_CADENA" to "'cadena'", "T_VACIO" to "'vacio'",
        "VERDADERO" to "'verdadero'", "FALSO" to "'falso'", "NULO" to "'nulo'",
        // operadores
        "MAS" to "'+'", "MENOS" to "'-'", "POR" to "'*'", "DIV" to "'/'",
        "MODULO" to "'%'", "INCREMENTO" to "'++'", "DECREMENTO" to "'--'",
        "MENOR" to "'<'", "MENOR_IGUAL" to "'<='", "MAYOR" to "'>'",
        "MAYOR_IGUAL" to "'>='", "IGUAL_IGUAL" to "'=='", "DISTINTO" to "'!='",
        "ASIGNA" to "'='", "MAS_ASIGNA" to "'+='", "MENOS_ASIGNA" to "'-='",
        "POR_ASIGNA" to "'*='", "DIV_ASIGNA" to "'/='", "MOD_ASIGNA" to "'%='",
        // delimitadores
        "PAREN_IZQ" to "'('", "PAREN_DER" to "')'",
        "LLAVE_IZQ" to "'{'", "LLAVE_DER" to "'}'",
        "CORCHETE_IZQ" to "'['", "CORCHETE_DER" to "']'",
        "PUNTO_COMA" to "';'", "COMA" to "','", "PUNTO" to "'.'",
        "DOS_PUNTOS" to "':'",
        // literales e identificadores: descripcion, no comillas
        "ID" to "un identificador",
        "ENTERO_LIT" to "un entero",
        "FLOTANTE_LIT" to "un flotante",
        "CADENA_LIT" to "una cadena",
        "CARACTER_LIT" to "un caracter",
        "EOF" to "el fin del archivo"
    )
}
