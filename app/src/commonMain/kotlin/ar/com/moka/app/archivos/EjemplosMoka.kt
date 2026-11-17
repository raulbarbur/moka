// EjemplosMoka.kt - Copias de ejemplos/hola.moka y ejemplos/fizzbuzz.moka.
// Se incluyen en el codigo para funcionar en desktop empaquetado y
// Android, sin depender del directorio de trabajo ni agregar recursos.
// Si se modifica un ejemplo en ejemplos/, actualizar tambien esta copia.
package ar.com.moka.app.archivos

object EjemplosMoka {
    val todos: List<ArchivoMoka> = listOf(
        ArchivoMoka(
            nombre = "hola.moka",
            contenido = """
                imprimir("Hola, Moka!");
            """.trimIndent() + "\n"
        ),
        ArchivoMoka(
            nombre = "fizzbuzz.moka",
            contenido = """
                para (entero i = 1; i <= 100; i = i + 1) {
                    si (i % 15 == 0) {
                        imprimir("FizzBuzz");
                    } sino {
                        si (i % 3 == 0) {
                            imprimir("Fizz");
                        } sino {
                            si (i % 5 == 0) {
                                imprimir("Buzz");
                            } sino {
                                imprimir(i);
                            }
                        }
                    }
                }
            """.trimIndent() + "\n"
        )
    )
}
