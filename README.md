# Moka

Interprete de un lenguaje didactico con sintaxis en espanol, escrito en
Kotlin Multiplatform. Incluye:

- **core**: el interprete completo (lexico, sintactico, semantico,
  generacion de TAC y maquina de ejecucion), libreria multiplataforma.
- **cli**: una CLI JVM para correr programas .moka desde la terminal.
- **app**: la aplicacion con interfaz grafica (Compose Multiplatform)
  para escritorio y Android, que muestra cada fase de la compilacion
  con sus artefactos: tokens, arbol, semantica, TAC y ejecucion.

La gramatica vive en `core/antlr/Moka.g4` y el codigo que ANTLR genera
va a `core/build/` (paquete `ar.com.moka.core.generado`): no se edita
ni se versiona.

## Documentacion

- `docs/LENGUAJE.md`: referencia completa del lenguaje Moka.
- `docs/APLICACION.md`: guia de la app (taller, fases, tutoriales) y de la CLI.
- `docs/DISTRIBUCION.md`: como generar el instalador de Windows y el APK firmado.
- `docs/ROADMAP.md`: estado de cada parte (implementado, pendiente de comprobar, futuro).

## Estado

Version 1.0. El interprete y la app funcionan y estan en uso; el
instalador de Windows (MSI), la carpeta ejecutable de escritorio y el
APK firmado se generaron y probaron. Lo que queda pendiente y lo que
queda para versiones futuras
estan en `docs/ROADMAP.md`.

## Requisitos

- JDK 17 o superior para compilar y correr los tests (probado con
  JDK 21).
- Para empaquetar (carpeta ejecutable o MSI): un JDK COMPLETO que
  incluya `jpackage` (Eclipse Temurin 21 sirve). El Java que trae
  Android Studio no lo incluye y el empaquetado falla.
- Para Android: Android SDK con la plataforma 36 (Android Studio la instala).
- Para generar el instalador MSI de Windows: no hace falta instalar WiX
  a mano; el plugin de Compose lo descarga solo (tarea `downloadWix`).
  Ver `docs/DISTRIBUCION.md`.
- No hace falta instalar Gradle: se usa el wrapper incluido (`gradlew`).

## Como correr cada cosa

```powershell
# Tests del nucleo (196)
.\gradlew :core:jvmTest

# Tests de la app en escritorio (79)
.\gradlew :app:desktopTest

# Compilar la variante Android
.\gradlew :app:compileDebugKotlinAndroid

# App de escritorio
.\gradlew :app:run

# App Android en un telefono conectado o emulador
.\gradlew :app:installDebug

# CLI
.\gradlew :cli:run --args="ejemplos/hola.moka"
```

Ojo: `:core:test` NO corre los tests del nucleo (es un agregador que no
ejecuta nada en este proyecto). La tarea real es `:core:jvmTest`.

## Estructura

```
moka/
├── core/    Nucleo del interprete (Kotlin Multiplatform: JVM + Android)
│   ├── antlr/Moka.g4        Gramatica completa del lenguaje
│   └── src/commonMain/...   lexico, sintactico, semantico, tac, ejecucion, pipeline
├── cli/     CLI JVM
├── app/     Compose Multiplatform: escritorio + Android
├── docs/    Documentacion (esta carpeta)
└── ejemplos/                Programas .moka de ejemplo (incluye buscaminas.moka)
```

## Versiones

| Componente | Version |
|---|---|
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 9.5.1 |
| antlr-kotlin (plugin + runtime) | 1.0.14 |
| kotlinx-coroutines | 1.11.0 |
| Gradle (wrapper) | 9.9.1 |

Regla: Kotlin tiene que estar soportado por antlr-kotlin y por Compose a
la vez; no actualizar ninguna version sin probar las otras.

Nota AGP 9: `gradle.properties` lleva `android.builtInKotlin=false` y
`android.newDsl=false` para conservar el combo clasico
com.android.application + Kotlin Multiplatform. Si Android Studio ofrece
migrar al nuevo DSL o a Kotlin integrado, hay que RECHAZAR la oferta:
rompe el build. Si ya se aplico por accidente, el sintoma es un error de
Gradle del tipo "Cannot add extension with name 'kotlin'" (plugin
`kotlin.android` agregado junto a Kotlin Multiplatform). Se arregla
quitando solo lo que agrego la migracion en `app/build.gradle.kts` y
`build.gradle.kts` (la linea del plugin `kotlinAndroid`, la dependencia
`core-ktx` agregada y el `apply false` de la raiz) y comprobando con
`git --no-pager diff` que no quedan cambios; el codigo no se toca.
