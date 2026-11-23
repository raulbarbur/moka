# La aplicacion Moka y la CLI

Guia de uso de la app (escritorio y Android) y de la CLI. Las partes
marcadas con un lote entre parentesis indican en que entrega llego cada
funcion; todas estan incorporadas en la 1.0. Ver docs/ROADMAP.md.

## El taller

La pantalla principal tiene:

- **Editor**: donde se escribe el programa. Numeros de linea, resaltado
  de sintaxis, deshacer y rehacer, buscar y reemplazar, tamano de letra
  con A+ y A-, y autocompletado de palabras del lenguaje.
- **Consola**: la salida del programa. Baja sola al agregarse texto y
  muestra en rojo los errores de ejecucion. Cuando el programa pide un
  dato con `leer()`, aparece el campo para escribirlo (ya con el foco) y se
  envia apretando ENTER o con el boton Enviar; en Android, con la tecla
  Enviar del teclado. Una linea vacia tambien se envia: sirve para los
  programas que piden "presione ENTER para continuar".
- **Fases**: lexico, sintactico, semantico, TAC y ejecucion, cada una
  con su estado (completada en verde, con errores en rojo, omitida) y
  su tiempo. Cada fase tiene su panel: tokens (con buscador y filtros),
  arbol de sintaxis, semantica, cuadruplas TAC y salida de ejecucion.
- **Arbol grafico**: el arbol de sintaxis dibujado; tocar un nodo
  resalta el codigo que lo genero en el editor.
- **Problemas**: la lista de errores y advertencias con su codigo y
  posicion. Tocar uno salta a esa linea del editor (los errores de un
  paquete no saltan: ese archivo no esta abierto, lote P4).

En ventana ancha todo esta a la vista; en angosta (o en el telefono en
vertical) la pantalla se reparte en pestañas.

## Ejecutar y depurar

- **Ejecutar**: corre el programa completo.
- **Paso a paso**: pausa ANTES de ejecutar cada cuadrupla. Siguiente
  avanza una; Continuar corre hasta el final. Las variables globales se
  ven mientras tanto.
- **Detener**: corta la ejecucion (los programas con ciclos infinitos
  tienen un limite de cuadruplas en la app, asi no la cuelgan).

## Menus

- **Archivo**: nuevo, abrir (.moka y .txt), guardar, ejemplos (hola,
  bucles, funciones, arreglos, segun, clases, entrada, fizzbuzz, consola y
  buscaminas) y salir. Si hay cambios sin guardar, avisa antes de reemplazarlos.
- **Editar**: deshacer, rehacer, buscar y reemplazar.
- **Ver**: modo simple (solo editor y consola) o completo, tema claro u
  oscuro.
- **Proyecto** (lote P3): elegir la carpeta del proyecto. Desde ahi, un
  programa con `usar util;` carga util.moka de esa carpeta sin tocar la
  CLI. La carpeta queda guardada entre sesiones; "Olvidar carpeta" la
  quita.
- **Ayuda**: tutoriales (11 lecciones con ejemplos cargables en el
  editor, ejercicios y soluciones) y acerca de.

## Paneles y zoom

Cada panel (editor, consola, problemas, panel de fase) tiene su propio
tamano de letra: Ctrl + rueda del mouse sobre el panel lo cambia solo
en ese panel.

## Colores

Tema claro (IntelliJ Light) y oscuro (Android Studio New UI), ambos
sobrios. Todos los colores viven en un solo archivo,
`app/src/commonMain/kotlin/ar/com/moka/app/ui/Tema.kt`, en tres grupos
(UI general, resaltado del editor, colores del builtin color()): se
cambia ahi y se recompila (lote 4C).

## Tutoriales

Menu Ayuda > Tutoriales: 11 lecciones, de "Hola, Moka" a un proyecto
FizzBuzz. Cada una tiene texto corto, un ejemplo con su salida, y un
ejercicio con solucion. El boton "Cargar en el editor" trae el ejemplo
(o la solucion) al editor para correrlo con Ejecutar. Un test corre el
ejemplo de cada leccion con el interprete real: ningun ejemplo se
publica roto.

## La CLI

```powershell
.\gradlew :cli:run --args="ruta\programa.moka"
.\gradlew :cli:run --args="--solo-analisis ruta\programa.moka"
.\gradlew :cli:run --args="--paso-a-paso ruta\programa.moka"
```

Muestra cada fase con su resumen y sus tiempos, los diagnosticos en
espanol (con pistas desde el lote 4B y con el nombre del archivo desde
el lote P4), la salida del programa con los colores del builtin
color() en ANSI (lote 4A), y sale con codigo 0 si anduvo o 1 si hubo
errores. Con `--solo-analisis` no ejecuta; con `--paso-a-paso` recorre
las cuadruplas de a una.

Para distribuirla: `.\gradlew :cli:installDist` deja una carpeta lista
en `cli\build\install\cli\` con scripts para Windows y Unix (requiere
Java instalado donde se use).
