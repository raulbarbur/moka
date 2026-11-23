# Estado y roadmap de Moka

Moka 1.0 esta cerrada: todo lo planeado para esta version esta
implementado, probado y empaquetado. Este documento resume el estado
al 23/11/2026: lo entregado en la seccion 1, dos notas de uso y
mantenimiento en la seccion 2, una verificacion opcional en la
seccion 3 y las ideas para versiones futuras en la seccion 4. Nada
fuera de la seccion 1 es un bloqueo de la 1.0.

## 1. Implementado (Moka 1.0)

Todo esto esta implementado y compila. Que parte esta ademas probada
en equipos reales se indica en cada punto.

- El interprete completo: lexico, sintactico, semantico, generador de
  TAC, maquina de ejecucion, fachada de pipeline por eventos y CLI.
- La app completa: taller con editor y consola, visualizacion de las
  cinco fases con sus artefactos, arbol de sintaxis grafico con
  seleccion, ejecucion paso a paso (Siguiente / Continuar, con
  variables globales visibles), aviso de cambios sin guardar, buscar y
  reemplazar, deshacer y rehacer, numeros de linea, tamano de letra,
  modo simple, tema claro y oscuro, layouts adaptativos.
- Menus (3A), autocompletado con salto a la linea del error (3B), 11
  tutoriales dentro de la app (3C, con un solo boton "Cargar en el
  editor") y paneles con zoom por panel con Ctrl + rueda (5A a 5C).
- Los lotes de la 1.0, instalados de a uno y en orden (despues de P4
  vinieron los cambios BM e icono, abajo):
    1. **4A**: funciones de consola `limpiar()`, `color("...")`,
       `restaurar_color()`, `pausa(ms)`, `titulo("...")`.
    2. **4B**: errores mas didacticos, con pistas concretas.
    3. **4C**: colores personalizables centralizados en Tema.kt.
    4. **4D**: seis ejemplos nuevos en Archivo > Ejemplos.
    5. **P1**: paquetes (`paquete` / `usar`) desde la CLI.
    6. **P2**: tipos cualificados entre paquetes (`geometria.Punto`).
    7. **P3**: carpeta de proyecto desde la app (menu Proyecto).
    8. **P4**: los errores nombran el archivo del que salen.
    9. **BM**: ENTER envia la entrada de la consola (incluso vacia) y
       `buscaminas.moka` entra en Archivo > Ejemplos.
    10. **Icono**: icono propio para Android, la ventana de escritorio
        y el exe/MSI; `versionCode` pasa a 2.
- Pruebas automaticas: 196 tests del nucleo y 79 de la app en
  escritorio (`:core:jvmTest` y `:app:desktopTest`), mas la
  compilacion de Android. Hasta el lote P4 se corrieron tambien en el
  equipo de desarrollo en Windows; la tanda final (Buscaminas, ENTER
  e icono) se corrio en el entorno de desarrollo.
- Empaquetado, probado en Windows y en un telefono Android:
    - **Escritorio**: `.\gradlew :app:createDistributable` genera la
      carpeta con Moka.exe; el ejecutable funciona y muestra el icono.
    - **MSI**: se genera, se instala y se abre el instalador de
      Windows.
    - **APK**: firmado con la clave propia guardada fuera del proyecto
      (la configuracion de la app declara `versionCode` 2) e instalado
      en el telefono; el icono aparece en el telefono.
- ENTER en la consola (incluida la tecla Enviar del teclado de
  Android) y el icono: probados en escritorio y en el telefono.
- Buscaminas: probado en escritorio.

## 2. Notas de uso y mantenimiento

- **Firma del APK**: si el telefono tuvo una copia anterior de Moka
  con otra firma (por ejemplo una instalada para pruebas desde
  Android Studio), el APK firmado no se instala encima. Antes de
  desinstalar hay que guardar los archivos .moka del telefono; el ID
  de la app no se cambia para esquivarlo.
- **Limpieza tecnica diferida**: no afecta el uso. Mover `:app` a un
  subproyecto separado antes de migrar a AGP 9 con el nuevo DSL, y
  quitar warnings redundantes del analizador semantico. Se hace cuando
  toque trabajar esa migracion, no antes.

## 3. Verificacion opcional

- El selector de carpetas de proyecto compila y funciona en
  escritorio, y quedo sin una prueba especifica en el telefono. No
  bloquea la 1.0: conviene probarlo la primera vez que se use desde
  el telefono.

## 4. Futuro (fuera de la 1.0, por aprobar cuando llegue el momento)

- **Biblioteca grafica dentro del lenguaje**: crear interfaces desde
  un programa Moka (pantalla, botones, con una API de polling; existe
  una opcion mas chica de solo un lienzo de dibujo). Es el item mas
  grande y quedo explicitamente para una version futura.
- Mas lecciones, mas ejemplos, mas funciones incorporadas: se agregan
  cuando haga falta; la estructura ya los admite.
