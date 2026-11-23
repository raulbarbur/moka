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
        ),
        ArchivoMoka(
            nombre = "buscaminas.moka",
            contenido = """
                // buscaminas.moka - Buscaminas de consola en Moka (8x8, 10 minas).
                // Version en Moka de un Buscaminas hecho originalmente en Python.
                // Corre en la app (Archivo > Abrir) y en la CLI:
                //   .\gradlew :cli:run --args="ejemplos\buscaminas.moka"
                //
                // El tablero se guarda en arreglos "planos" de 64 casillas: la casilla
                // de fila f y columna c (de 0 a 7) esta en la posicion f * 8 + c.
                //   minas[p]  : 1 si la casilla p tiene mina, 0 si no.
                //   estado[p] : 0 tapada, 1 descubierta, 2 con bandera.

                constante entero LADO = 8;     // el tablero es de 8 x 8
                constante entero MINAS = 10;   // cantidad de minas

                // ---------------------------------------------------------------------
                // Numeros al azar. Moka no tiene una funcion de azar, asi que usamos un
                // generador congruencial lineal chico (x = (75 * (x + 1)) - 1 modulo
                // 65537): da una secuencia que parece al azar. La "semilla" se guarda en
                // un arreglo de un elemento para que se conserve entre partidas.
                // Devuelve un entero entre 0 y tope - 1.
                // ---------------------------------------------------------------------
                funcion entero azar(entero[] semilla, entero tope) {
                    semilla[0] = (semilla[0] * 75 + 74) % 65537;
                    retornar (semilla[0] / 7) % tope;
                }

                // Convierte lo que escribio el jugador en un numero del 0 al 9.
                // Si no es un numero de una cifra devuelve -1 (no se rompe el programa).
                funcion entero cifra(cadena texto) {
                    segun (texto) {
                        caso "0": retornar 0;
                        caso "1": retornar 1;
                        caso "2": retornar 2;
                        caso "3": retornar 3;
                        caso "4": retornar 4;
                        caso "5": retornar 5;
                        caso "6": retornar 6;
                        caso "7": retornar 7;
                        caso "8": retornar 8;
                        caso "9": retornar 9;
                        defecto: retornar -1;
                    }
                    retornar -1;
                }

                // Espera que el jugador apriete ENTER.
                funcion vacio esperar() {
                    imprimir("");
                    imprimir("Presione ENTER para continuar...");
                    leer();
                }

                // ---------------------------------------------------------------------
                // Preparacion del tablero
                // ---------------------------------------------------------------------

                // Cuenta cuantos casilleros de un arreglo valen "valor".
                funcion entero contar(entero[] arreglo, entero valor) {
                    entero total = 0;
                    para (entero p = 0; p < LADO * LADO; p++) {
                        si (arreglo[p] == valor) {
                            total++;
                        }
                    }
                    retornar total;
                }

                // Reparte las minas en casillas distintas.
                funcion vacio ponerMinas(entero[] minas, entero[] semilla) {
                    entero puestas = 0;
                    mientras (puestas < MINAS) {
                        entero p = azar(semilla, LADO * LADO);
                        si (minas[p] == 0) {
                            minas[p] = 1;
                            puestas++;
                        }
                    }
                }

                // Cuenta las minas que rodean a la casilla (f, c): hasta 8 vecinas.
                // Un mismo bucle sirve para el centro, los bordes y las esquinas.
                funcion entero vecinas(entero[] minas, entero f, entero c) {
                    entero total = 0;
                    para (entero df = -1; df <= 1; df++) {
                        para (entero dc = -1; dc <= 1; dc++) {
                            entero nf = f + df;
                            entero nc = c + dc;
                            si (nf >= 0 y nf < LADO y nc >= 0 y nc < LADO y (df != 0 o dc != 0)) {
                                total = total + minas[nf * LADO + nc];
                            }
                        }
                    }
                    retornar total;
                }

                // ---------------------------------------------------------------------
                // Jugada: descubrir una casilla. Si no tiene minas alrededor, descubre
                // tambien las vecinas, y asi sigue (recursion). Las casillas con
                // bandera no se destapan.
                // ---------------------------------------------------------------------
                funcion vacio descubrir(entero[] minas, entero[] estado, entero f, entero c) {
                    si (f < 0 o f >= LADO o c < 0 o c >= LADO) {
                        retornar;
                    }
                    si (estado[f * LADO + c] != 0) {
                        retornar;
                    }
                    estado[f * LADO + c] = 1;
                    si (vecinas(minas, f, c) == 0) {
                        para (entero df = -1; df <= 1; df++) {
                            para (entero dc = -1; dc <= 1; dc++) {
                                descubrir(minas, estado, f + df, c + dc);
                            }
                        }
                    }
                }

                // Se gana cuando todas las casillas SIN mina estan descubiertas.
                // (Las banderas no cuentan: una bandera sobre una casilla segura no gana.)
                funcion booleano gano(entero[] minas, entero[] estado) {
                    para (entero p = 0; p < LADO * LADO; p++) {
                        si (minas[p] == 0 y estado[p] != 1) {
                            retornar falso;
                        }
                    }
                    retornar verdadero;
                }

                // ---------------------------------------------------------------------
                // Pantalla
                // ---------------------------------------------------------------------

                // Escribe un numero de minas vecinas con su color.
                funcion vacio colorNumero(entero n) {
                    segun (n) {
                        caso 1: color("azul"); romper;
                        caso 2: color("verde"); romper;
                        caso 3: color("magenta"); romper;
                        caso 4: color("celeste"); romper;
                        caso 5: color("rojo"); romper;
                        caso 6: color("amarillo"); romper;
                        caso 7: color("blanco"); romper;
                        defecto: color("gris");
                    }
                }

                // Dibuja el tablero. Si "verMinas" es verdadero (al terminar la partida)
                // muestra tambien donde estaban las minas.
                funcion vacio mostrar(entero[] minas, entero[] estado, booleano verMinas) {
                    limpiar();
                    imprimir("     1 2 3 4 5 6 7 8");
                    para (entero f = 0; f < LADO; f++) {
                        imprimir_sin_salto(a_cadena(f + 1) + "-> |");
                        para (entero c = 0; c < LADO; c++) {
                            entero p = f * LADO + c;
                            si (estado[p] == 2) {
                                color("rojo");
                                imprimir_sin_salto("F");
                            } sino si (estado[p] == 1) {
                                entero n = vecinas(minas, f, c);
                                si (minas[p] == 1) {
                                    color("rojo");
                                    imprimir_sin_salto("*");
                                } sino si (n == 0) {
                                    color("gris");
                                    imprimir_sin_salto("-");
                                } sino {
                                    colorNumero(n);
                                    imprimir_sin_salto(a_cadena(n));
                                }
                            } sino si (verMinas y minas[p] == 1) {
                                color("rojo");
                                imprimir_sin_salto("*");
                            } sino {
                                imprimir_sin_salto(" ");
                            }
                            restaurar_color();
                            imprimir_sin_salto("|");
                        }
                        imprimir("");
                    }
                    imprimir("");
                    imprimir("Banderas: " + a_cadena(contar(estado, 2)) + " de " + a_cadena(MINAS));
                }

                funcion vacio opciones() {
                    imprimir("Seleccione una opcion:");
                    imprimir("1- Excavar.");
                    imprimir("2- Colocar bandera (F).");
                    imprimir("3- Quitar bandera.");
                    imprimir("0- Finalizar el juego.");
                }

                // Pide fila y columna hasta tener una casilla valida cuyo estado sea
                // "estadoPedido" (0 para excavar o poner bandera, 2 para quitarla).
                // Devuelve la posicion de la casilla, o -1 si el jugador escribe 0 en la
                // fila para cancelar. Lo que no es un numero de 1 a 8 solo da un aviso:
                // el programa no se corta.
                funcion entero pedirCasilla(entero[] estado, entero estadoPedido) {
                    mientras (verdadero) {
                        imprimir_sin_salto("Fila (1 - 8, 0 para cancelar): ");
                        entero f = cifra(leer());
                        si (f == 0) {
                            retornar -1;
                        }
                        imprimir_sin_salto("Columna (1 - 8): ");
                        entero c = cifra(leer());
                        si (f >= 1 y f <= LADO y c >= 1 y c <= LADO) {
                            si (estado[(f - 1) * LADO + (c - 1)] == estadoPedido) {
                                retornar (f - 1) * LADO + (c - 1);
                            }
                            si (estadoPedido == 0) {
                                imprimir("Esa casilla ya esta descubierta o tiene bandera.");
                            } sino {
                                imprimir("Esa casilla no tiene bandera.");
                            }
                        } sino {
                            imprimir("Posicion invalida.");
                        }
                    }
                    retornar -1;
                }

                // ---------------------------------------------------------------------
                // Una partida
                // ---------------------------------------------------------------------
                funcion vacio jugar(entero[] semilla) {
                    entero[] minas = nuevo entero[64];    // todo en 0
                    entero[] estado = nuevo entero[64];   // todo tapado
                    ponerMinas(minas, semilla);

                    booleano perdio = falso;
                    booleano abandono = falso;
                    booleano gano_ = falso;

                    mientras (no perdio y no abandono y no gano_) {
                        mostrar(minas, estado, falso);
                        opciones();
                        entero opcion = cifra(leer());

                        si (opcion == 0) {
                            abandono = verdadero;
                        } sino si (opcion == 1) {
                            entero p = pedirCasilla(estado, 0);
                            si (p >= 0) {
                                si (minas[p] == 1) {
                                    estado[p] = 1;
                                    perdio = verdadero;
                                } sino {
                                    descubrir(minas, estado, p / LADO, p % LADO);
                                }
                            }
                        } sino si (opcion == 2) {
                            si (contar(estado, 2) >= MINAS) {
                                imprimir("No se pueden colocar mas banderas.");
                                esperar();
                            } sino {
                                entero p = pedirCasilla(estado, 0);
                                si (p >= 0) {
                                    estado[p] = 2;
                                }
                            }
                        } sino si (opcion == 3) {
                            si (contar(estado, 2) == 0) {
                                imprimir("No hay banderas en el tablero.");
                                esperar();
                            } sino {
                                entero p = pedirCasilla(estado, 2);
                                si (p >= 0) {
                                    estado[p] = 0;
                                }
                            }
                        }
                        // Cualquier otra opcion se ignora y se vuelve a mostrar el tablero.

                        gano_ = gano(minas, estado);
                    }

                    mostrar(minas, estado, perdio o abandono);
                    si (gano_) {
                        color("verde");
                        imprimir("Ha ganado.");
                    } sino si (perdio) {
                        color("rojo");
                        imprimir("Ha perdido.");
                    } sino {
                        imprimir("Partida finalizada.");
                    }
                    restaurar_color();
                }

                // ---------------------------------------------------------------------
                // Menu y ayuda
                // ---------------------------------------------------------------------
                funcion vacio menu() {
                    limpiar();
                    color("rojo");
                    imprimir("________________________________________");
                    imprimir("");
                    imprimir("[BUSCAMINAS]");
                    imprimir("");
                    imprimir("1- Iniciar partida.");
                    imprimir("2- Ayuda.");
                    imprimir("");
                    imprimir("0- Salir.");
                    imprimir("________________________________________");
                    restaurar_color();
                }

                funcion vacio ayuda() {
                    limpiar();
                    color("celeste");
                    imprimir("REGLAS:");
                    imprimir("");
                    imprimir("El juego consiste en despejar todas las casillas de una pantalla");
                    imprimir("que no oculten una mina. Algunas casillas tienen un numero, el cual");
                    imprimir("indica la cantidad de minas que hay en las casillas circundantes.");
                    imprimir("Asi, si una casilla tiene el numero 3, significa que de las ocho");
                    imprimir("casillas que hay alrededor (si no es en una esquina o borde) hay 3");
                    imprimir("con minas y 5 sin minas. Si se descubre una casilla sin numero");
                    imprimir("indica que ninguna de las casillas vecinas tiene mina y estas se");
                    imprimir("descubren automaticamente.");
                    imprimir("Si se descubre una casilla con una mina se pierde la partida.");
                    imprimir("Se puede poner una marca en las casillas que el jugador piensa que");
                    imprimir("hay minas para ayudar a descubrir las que estan cerca.");
                    imprimir("");
                    imprimir("Para realizar un movimiento, debera elegir un numero de fila y uno");
                    imprimir("de columna.");
                    restaurar_color();
                    esperar();
                }

                // ---------------------------------------------------------------------
                // Programa principal
                // ---------------------------------------------------------------------

                // La semilla del azar: el jugador escribe un numero (o ENTER). Cada
                // partida usa la semilla que dejo la anterior, asi que no se repiten.
                entero[] semilla = [1];
                imprimir("Buscaminas. Para mezclar las minas, escriba un numero de 1 a 9");
                imprimir("(o solo ENTER para usar uno fijo):");
                entero inicial = cifra(leer());
                si (inicial > 0) {
                    semilla[0] = inicial * 7919;
                }

                booleano seguir = verdadero;
                mientras (seguir) {
                    menu();
                    entero opcion = cifra(leer());
                    si (opcion == 0) {
                        seguir = falso;
                        imprimir("Programa finalizado.");
                    } sino si (opcion == 1) {
                        jugar(semilla);
                        esperar();
                    } sino si (opcion == 2) {
                        ayuda();
                    }
                }
            """.trimIndent() + "\n"
        )
    )
}
