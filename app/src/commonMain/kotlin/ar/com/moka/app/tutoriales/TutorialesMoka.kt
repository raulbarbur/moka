// =======================================================================
//  TutorialesMoka.kt - Lecciones para aprender Moka dentro de la app
// =======================================================================
//  Todo el contenido vive en codigo (sin archivos ni internet): funciona
//  igual en escritorio y en Android. Cada leccion tiene:
//    - texto corto, en parrafos;
//    - un programa de ejemplo que se puede cargar al editor;
//    - la salida que produce (la comprueba un test con el interprete
//      real, asi no se publica un ejemplo roto);
//    - un mini ejercicio con su solucion.
//  Para agregar una leccion: sumar un Leccion a LECCIONES; el test
//  TutorialesMokaTest la corre sola.
// =======================================================================
package ar.com.moka.app.tutoriales

class Leccion(
    val id: String,
    val titulo: String,
    // Cada elemento es un parrafo.
    val parrafos: List<String>,
    // Programa de ejemplo (se carga al editor tal cual).
    val codigo: String,
    // Lo que imprime el ejemplo. null = no se muestra (p. ej. el ejemplo
    // es para ver errores en Problemas).
    val salida: String? = null,
    // Lineas que se teclean al pedir datos (solo para el test; en la
    // app las escribe quien prueba).
    val entradas: List<String> = emptyList(),
    // Si el ejemplo DEBE dar errores (leccion de errores), un trozo del
    // mensaje esperado; el test comprueba que aparezca.
    val errorEsperado: String? = null,
    val ejercicio: String,
    val solucion: String,
    val entradasSolucion: List<String> = emptyList()
)

// Quita la sangria comun y deja un salto de linea final.
private fun codigo(texto: String) = texto.trimIndent().trim('\n') + "\n"

val LECCIONES: List<Leccion> = listOf(
    Leccion(
        id = "hola",
        titulo = "1. Hola, Moka",
        parrafos = listOf(
            "Un programa Moka es una lista de instrucciones que se ejecutan de arriba hacia abajo. Cada instrucción termina con punto y coma (;).",
            "imprimir(...) muestra algo en la consola y pasa a la línea siguiente. imprimir_sin_salto(...) escribe sin pasar de línea.",
            "Los textos van entre comillas dobles. Con + se pegan textos (y también un texto con un número).",
            "Lo que sigue a // es un comentario: Moka lo ignora y sirve para explicar tu código. Los comentarios largos van entre /* y */."
        ),
        codigo = codigo(
            """
            // Mi primer programa
            imprimir("Hola, Moka!");
            imprimir_sin_salto("Sin salto, ");
            imprimir("con salto");
            imprimir(2 + 3);
            imprimir("Suma: " + 5);
            """
        ),
        salida = "Hola, Moka!\nSin salto, con salto\n5\nSuma: 5",
        ejercicio = "Imprime tu nombre en una línea y, en otra, el resultado de 6 * 7.",
        solucion = codigo(
            """
            imprimir("Ana");
            imprimir(6 * 7);
            """
        )
    ),
    Leccion(
        id = "variables",
        titulo = "2. Variables y tipos",
        parrafos = listOf(
            "Una variable es una caja con nombre donde guardas un valor. Al crearla dices su tipo: entero (1, 42), flotante (1.75), booleano (verdadero o falso), caracter ('M', una letra entre comillas simples) y cadena (\"texto\").",
            "Se declara así: tipo nombre = valor;. Se puede cambiar después con nombre = otroValor;. Una variable sin valor inicial empieza en 0 si es entero.",
            "Con constante la variable no puede cambiar nunca: si lo intentas, Moka te avisa en Problemas.",
            "Moka es estricto con los tipos: un flotante no entra en un entero sin convertirlo con a_entero(...)."
        ),
        codigo = codigo(
            """
            entero edad = 17;
            flotante altura = 1.75;
            booleano activo = verdadero;
            caracter letra = 'M';
            cadena nombre = "Ana";
            constante flotante PI = 3.14159;
            imprimir(nombre);
            imprimir(edad);
            imprimir(altura);
            imprimir(activo);
            imprimir(letra);
            imprimir(PI);
            edad = edad + 1;
            imprimir("Ahora tiene " + edad);
            imprimir(nombre + " mide " + altura);
            """
        ),
        salida = "Ana\n17\n1.75\nverdadero\nM\n3.14159\nAhora tiene 18\nAna mide 1.75",
        ejercicio = "Crea una cadena con tu ciudad y un entero con tu año de nacimiento, e imprime una frase que use las dos.",
        solucion = codigo(
            """
            cadena ciudad = "Salta";
            entero anio = 1998;
            imprimir("Nací en " + ciudad + " en " + anio);
            """
        )
    ),
    Leccion(
        id = "operadores",
        titulo = "3. Operadores y expresiones",
        parrafos = listOf(
            "Aritméticos: + - * / y % (resto). Con enteros, / da un entero: 7 / 2 es 3. Si uno de los números es flotante (7.0 / 2), el resultado tiene decimales. Se respeta el orden matemático y puedes usar paréntesis.",
            "Comparación: < <= > >= == (igual) y != (distinto). Dan verdadero o falso.",
            "Lógicos: en Moka son palabras: y, o, no. Por eso y, o y no no se pueden usar como nombres de variable.",
            "Atajos: n += 2 es n = n + 2, y n++ suma uno."
        ),
        codigo = codigo(
            """
            imprimir(7 / 2);
            imprimir(7.0 / 2);
            imprimir(7 % 3);
            imprimir(2 + 3 * 4);
            imprimir((2 + 3) * 4);
            imprimir(5 > 3);
            imprimir(5 != 5);
            imprimir(verdadero y falso);
            imprimir(verdadero o falso);
            imprimir(no verdadero);
            entero n = 5;
            n += 2;
            n++;
            imprimir(n);
            """
        ),
        salida = "3\n3.5\n1\n14\n20\nverdadero\nfalso\nfalso\nverdadero\nfalso\n8",
        ejercicio = "Imprime si el número 17 es par (pista: el resto de dividir por 2 es 0).",
        solucion = codigo(
            """
            imprimir(17 % 2 == 0);
            """
        )
    ),
    Leccion(
        id = "entrada",
        titulo = "4. Pedir datos al usuario",
        parrafos = listOf(
            "leer() espera que el usuario escriba una línea en la consola y la devuelve como cadena. leer_entero() y leer_flotante() hacen lo mismo pero devuelven un número.",
            "Para convertir: a_entero(\"42\"), a_cadena(42), a_flotante(...) y a_booleano(...). longitud(texto) cuenta los caracteres.",
            "Al probar este ejemplo la consola te pide que escribas: hazlo en la parte de abajo y pulsa Enviar."
        ),
        codigo = codigo(
            """
            imprimir("Como te llamas?");
            cadena nombre = leer();
            imprimir("Hola, " + nombre);
            imprimir("Edad?");
            entero e = leer_entero();
            imprimir(e + 1);
            imprimir(a_cadena(e) + "!");
            imprimir(longitud(nombre));
            """
        ),
        salida = "Como te llamas?\nHola, Luis\nEdad?\n31\n30!\n4",
        entradas = listOf("Luis", "30"),
        ejercicio = "Pide dos números enteros e imprime su suma.",
        solucion = codigo(
            """
            imprimir("Primer numero:");
            entero a = leer_entero();
            imprimir("Segundo numero:");
            entero b = leer_entero();
            imprimir(a + b);
            """
        ),
        entradasSolucion = listOf("4", "5")
    ),
    Leccion(
        id = "condicionales",
        titulo = "5. Condicionales: si y segun",
        parrafos = listOf(
            "si (condición) { ... } sino { ... } ejecuta un bloque u otro. La condición tiene que ser booleana: si (5) da error, si (nota >= 6) está bien.",
            "segun (valor) compara un valor con varios caso. Cada caso termina con romper;: sin romper, Moka sigue con el caso siguiente (mira el segundo ejemplo). defecto: corre si ningún caso coincide."
        ),
        codigo = codigo(
            """
            entero nota = 7;
            si (nota >= 6) {
                imprimir("Aprobado");
            } sino {
                imprimir("Desaprobado");
            }
            entero dia = 3;
            segun (dia) {
                caso 1:
                    imprimir("Lunes");
                    romper;
                caso 3:
                    imprimir("Miercoles");
                    romper;
                defecto:
                    imprimir("Otro dia");
            }
            segun (dia) {
                caso 3:
                    imprimir("tres");
                caso 4:
                    imprimir("cuatro (sin romper)");
                    romper;
            }
            """
        ),
        salida = "Aprobado\nMiercoles\ntres\ncuatro (sin romper)",
        ejercicio = "Dada una variable entero temperatura = 31;, imprime \"Hace calor\" si es mayor a 25 y \"Esta fresco\" si no.",
        solucion = codigo(
            """
            entero temperatura = 31;
            si (temperatura > 25) {
                imprimir("Hace calor");
            } sino {
                imprimir("Esta fresco");
            }
            """
        )
    ),
    Leccion(
        id = "bucles",
        titulo = "6. Bucles",
        parrafos = listOf(
            "Un bucle repite instrucciones. mientras (condición) { ... } repite mientras la condición sea verdadera. hacer { ... } mientras (condición); es igual pero ejecuta el bloque al menos una vez.",
            "para (inicio; condición; paso) { ... } junta las tres partes de un contador. Dentro de cualquier bucle, romper; lo corta y continuar; salta a la vuelta siguiente.",
            "para_cada (tipo x : arreglo) { ... } recorre todos los elementos de un arreglo (lo vemos en la lección 8).",
            "Cuidado con los bucles infinitos: si la condición nunca deja de ser verdadera, el programa no termina. Con el botón Detener lo cortas."
        ),
        codigo = codigo(
            """
            entero i = 1;
            mientras (i <= 3) {
                imprimir("mientras " + i);
                i++;
            }
            hacer {
                imprimir("hacer " + i);
                i--;
            } mientras (i > 2);
            para (entero k = 0; k < 5; k++) {
                si (k == 1) { continuar; }
                si (k == 4) { romper; }
                imprimir("para " + k);
            }
            """
        ),
        salida = "mientras 1\nmientras 2\nmientras 3\nhacer 4\nhacer 3\npara 0\npara 2\npara 3",
        ejercicio = "Imprime los números del 1 al 5 con un bucle para, uno por línea.",
        solucion = codigo(
            """
            para (entero n = 1; n <= 5; n++) {
                imprimir(n);
            }
            """
        )
    ),
    Leccion(
        id = "funciones",
        titulo = "7. Funciones",
        parrafos = listOf(
            "Una función es un trozo de código con nombre que puedes llamar las veces que quieras: funcion tipoDeRetorno nombre(tipo parametro, ...) { ... }.",
            "Si devuelve un valor, usa retornar valor;. Si no devuelve nada, su tipo es vacio.",
            "Una función puede llamarse a sí misma (recursión): factorial(5) = 5 * factorial(4), y así hasta llegar a 1. No olvides el caso base que corta la recursión."
        ),
        codigo = codigo(
            """
            funcion entero cuadrado(entero n) {
                retornar n * n;
            }
            funcion vacio saludar(cadena nombre) {
                imprimir("Hola, " + nombre + "!");
            }
            funcion entero factorial(entero n) {
                si (n <= 1) { retornar 1; }
                retornar n * factorial(n - 1);
            }
            saludar("Ana");
            imprimir(cuadrado(7));
            imprimir(factorial(5));
            """
        ),
        salida = "Hola, Ana!\n49\n120",
        ejercicio = "Escribe una función entero doble(entero n) que devuelva el doble de n e imprime doble(21).",
        solucion = codigo(
            """
            funcion entero doble(entero n) {
                retornar n * 2;
            }
            imprimir(doble(21));
            """
        )
    ),
    Leccion(
        id = "arreglos",
        titulo = "8. Arreglos",
        parrafos = listOf(
            "Un arreglo guarda varios valores del mismo tipo. Se crea vacío con nuevo entero[3] o con valores: [10, 20, 30]. Su tipo se escribe con corchetes: entero[].",
            "Los lugares se cuentan desde 0: a[0] es el primero. longitud(a) dice cuántos hay.",
            "Si pides un lugar que no existe (a[5] en un arreglo de 3), el programa se detiene con un error de ejecución. Pruébalo con el ejemplo."
        ),
        codigo = codigo(
            """
            entero[] a = nuevo entero[3];
            a[0] = 5;
            a[1] = 6;
            a[2] = a[0] + a[1];
            imprimir(a[2]);
            imprimir(longitud(a));
            cadena[] nombres = ["Ana", "Luis"];
            imprimir(nombres[1]);
            entero suma = 0;
            para_cada (entero n : a) { suma += n; }
            imprimir(suma);
            imprimir(a[5]);
            """
        ),
        salida = "11\n3\nLuis\n22",
        errorEsperado = "fuera de rango",
        ejercicio = "Crea el arreglo [4, 8, 15] e imprime la suma de sus elementos con para_cada.",
        solucion = codigo(
            """
            entero[] v = [4, 8, 15];
            entero total = 0;
            para_cada (entero x : v) { total += x; }
            imprimir(total);
            """
        )
    ),
    Leccion(
        id = "clases",
        titulo = "9. Clases y objetos",
        parrafos = listOf(
            "Una clase describe un tipo de cosa: sus campos (datos) y sus métodos (acciones). Un objeto es una copia concreta, que se crea con nuevo Clase(...).",
            "constructor(...) se ejecuta al crear el objeto. Dentro de la clase, este se refiere al propio objeto: este.nombre es el campo nombre de ese objeto.",
            "Un campo o método estatico pertenece a la clase y no a cada objeto: se usa como Clase.nombre.",
            "Un objeto nulo no tiene campos: intentar usarlo da un error de ejecución."
        ),
        codigo = codigo(
            """
            clase Persona {
                cadena nombre;
                entero edad;
                constructor(cadena n, entero e) {
                    este.nombre = n;
                    este.edad = e;
                }
                funcion vacio presentarse() {
                    imprimir("Soy " + este.nombre + " y tengo " + este.edad);
                }
                funcion booleano esMayor() {
                    retornar este.edad >= 18;
                }
                estatico funcion entero doble(entero x) { retornar x * 2; }
            }
            Persona p = nuevo Persona("Ana", 20);
            p.presentarse();
            imprimir(p.esMayor());
            p.edad = 15;
            imprimir(p.esMayor());
            imprimir(Persona.doble(4));
            """
        ),
        salida = "Soy Ana y tengo 20\nverdadero\nfalso\n8",
        ejercicio = "Crea una clase Contador con un campo entero valor (empieza en 0) y un método sumar() que le suma 1. Llámalo dos veces e imprime el valor.",
        solucion = codigo(
            """
            clase Contador {
                entero valor;
                constructor() { este.valor = 0; }
                funcion vacio sumar() { este.valor++; }
            }
            Contador c = nuevo Contador();
            c.sumar();
            c.sumar();
            imprimir(c.valor);
            """
        )
    ),
    Leccion(
        id = "errores",
        titulo = "10. Leer los errores",
        parrafos = listOf(
            "Moka revisa tu programa en fases y te dice dónde está cada problema, con línea y columna. Aparecen en la pestaña Problemas; tócalos y el editor te lleva a esa línea.",
            "Errores de sintaxis: falta algo, como el ; al final (\"se esperaba ';'\"). Errores de significado: usar una variable que no existe, o mezclar tipos. Moka a veces sugiere el nombre correcto (\"¿Quisiste decir...?\").",
            "Errores de ejecución: el programa es válido pero algo falla mientras corre, como dividir por cero (\"Division por cero.\") o salirte de un arreglo.",
            "Este ejemplo tiene dos errores a propósito. Cárgalo y mira Problemas: arréglalos y vuelve a probar."
        ),
        codigo = codigo(
            """
            entero x = "hola";
            imprimir(z);
            """
        ),
        errorEsperado = "no esta declarada",
        ejercicio = "Corrige el ejemplo de esta lección para que imprima hola.",
        solucion = codigo(
            """
            cadena x = "hola";
            imprimir(x);
            """
        )
    ),
    Leccion(
        id = "proyecto",
        titulo = "11. Proyecto: FizzBuzz",
        parrafos = listOf(
            "FizzBuzz es un clásico: cuenta del 1 al 15, pero en vez de los múltiplos de 3 escribe Fizz, en vez de los de 5 escribe Buzz, y en vez de los de 15 escribe FizzBuzz.",
            "Junta lo aprendido: un bucle para, el resto (%) para saber si un número es múltiplo, y si/sino en cadena. Fíjate que se pregunta primero por 15, porque también es múltiplo de 3 y de 5.",
            "En el menú Archivo > Ejemplos tienes la versión que llega hasta 100."
        ),
        codigo = codigo(
            """
            para (entero i = 1; i <= 15; i++) {
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
            """
        ),
        salida = "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz",
        ejercicio = "Cambia el programa para que llegue hasta 30 y escriba Moka en vez de Fizz.",
        solucion = codigo(
            """
            para (entero i = 1; i <= 30; i++) {
                si (i % 15 == 0) {
                    imprimir("MokaBuzz");
                } sino {
                    si (i % 3 == 0) {
                        imprimir("Moka");
                    } sino {
                        si (i % 5 == 0) {
                            imprimir("Buzz");
                        } sino {
                            imprimir(i);
                        }
                    }
                }
            }
            """
        )
    )
)
