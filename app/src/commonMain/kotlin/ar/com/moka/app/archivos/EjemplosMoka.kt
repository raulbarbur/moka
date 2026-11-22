// EjemplosMoka.kt - Copias de los ejemplos de ejemplos/.
// Se incluyen en el codigo para funcionar en desktop empaquetado y
// Android, sin depender del directorio de trabajo ni agregar recursos.
// Si se modifica un ejemplo en ejemplos/, actualizar tambien esta copia.
// La salida exacta de cada uno la verifica EjemplosMokaTest (desktopTest)
// corriendolos con el interprete real.
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
            nombre = "bucles.moka",
            contenido = """
                // Bucles: la tabla del 7 con para y una cuenta regresiva con mientras.
                para (entero i = 1; i <= 10; i = i + 1) {
                    imprimir("7 x " + a_cadena(i) + " = " + a_cadena(7 * i));
                }

                imprimir("");

                entero falta = 3;
                mientras (falta > 0) {
                    imprimir("Faltan " + a_cadena(falta) + "...");
                    falta = falta - 1;
                }
                imprimir("Despegue!");
            """.trimIndent() + "\n"
        ),
        ArchivoMoka(
            nombre = "funciones.moka",
            contenido = """
                // Funciones: con parametros, con retorno y recursivas.
                funcion vacio saludar(cadena nombre) {
                    imprimir("Hola, " + nombre + "!");
                }

                funcion entero factorial(entero n) {
                    si (n <= 1) {
                        retornar 1;
                    }
                    retornar n * factorial(n - 1);
                }

                saludar("Moka");
                imprimir("factorial(5) = " + a_cadena(factorial(5)));
            """.trimIndent() + "\n"
        ),
        ArchivoMoka(
            nombre = "arreglos.moka",
            contenido = """
                // Arreglos: notas de un curso, promedio y nota mas alta.
                entero[] notas = [7, 8, 10, 6, 9];

                flotante suma = 0;
                entero maxima = notas[0];
                para_cada (entero nota : notas) {
                    suma = suma + nota;
                    si (nota > maxima) {
                        maxima = nota;
                    }
                }

                imprimir("Cantidad de notas: " + a_cadena(longitud(notas)));
                imprimir("Promedio: " + a_cadena(suma / longitud(notas)));
                imprimir("Nota mas alta: " + a_cadena(maxima));
            """.trimIndent() + "\n"
        ),
        ArchivoMoka(
            nombre = "segun.moka",
            contenido = """
                // Segun: elegir por valor. El romper evita caer en el caso que sigue.
                entero opcion = 2;
                segun (opcion) {
                    caso 1:
                        imprimir("Sumas un punto");
                        romper;
                    caso 2:
                        imprimir("Restas un punto");
                        romper;
                    defecto:
                        imprimir("Opcion desconocida");
                }
            """.trimIndent() + "\n"
        ),
        ArchivoMoka(
            nombre = "clases.moka",
            contenido = """
                // Clases: campos, constructor y metodos.
                clase Persona {
                    cadena nombre;
                    entero edad;

                    constructor(cadena n, entero e) {
                        este.nombre = n;
                        este.edad = e;
                    }

                    funcion vacio saludar() {
                        imprimir("Hola, soy " + este.nombre + " y tengo " + a_cadena(este.edad) + " anos.");
                    }

                    funcion vacio cumplir() {
                        este.edad = este.edad + 1;
                        imprimir(este.nombre + " cumplio " + a_cadena(este.edad) + " anos.");
                    }
                }

                Persona ana = nuevo Persona("Ana", 20);
                ana.saludar();
                ana.cumplir();
            """.trimIndent() + "\n"
        ),
        ArchivoMoka(
            nombre = "entrada.moka",
            contenido = """
                // Entrada por consola: el programa pregunta y responde.
                imprimir("Como te llamas?");
                cadena nombre = leer();
                imprimir("Hola, " + nombre + "!");

                imprimir("Cuantos anos tenes?");
                entero edad = leer_entero();
                imprimir("El ano que viene vas a tener " + a_cadena(edad + 1) + ".");
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
        ),
        ArchivoMoka(
            nombre = "consola.moka",
            contenido = """
                // Consola con estilo: titulo, color, pausa y limpiar.
                titulo("Demo de consola");
                color("celeste");
                imprimir("=== Moka ===");
                restaurar_color();
                imprimir("Texto normal.");
                color("verde");
                imprimir("Texto en verde.");
                color("rojo");
                imprimir("Texto en rojo.");
                restaurar_color();
                imprimir("Se limpia la consola en un segundo...");
                pausa(1000);
                limpiar();
                imprimir("Consola limpia: la salida anterior ya no esta.");
            """.trimIndent() + "\n"
        )
    )
}
