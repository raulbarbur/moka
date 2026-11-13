# Moka

Interprete de un lenguaje didactico con sintaxis en espanol, con GUI que
visualiza cada fase de compilacion (lexico, sintactico, semantico, TAC,
ejecucion). Kotlin Multiplatform + Compose Multiplatform + ANTLR (target
Kotlin de Strumenta). Ver el documento de diseno (v2) para el detalle.

**Estado actual: Fase 0** - scaffold del proyecto y spike de compatibilidad
de versiones. El nucleo parsea un subconjunto minimo de la gramatica
(`Moka.g4`) y la app muestra una ventana/pantalla que llama al nucleo.

## Requisitos

- **JDK 17 o superior** (probado con JDK 21).
- Para los targets Android: **Android SDK** con la plataforma 36 (desde
  Android Studio se instala solo; Gradle la pide si falta).
- No hace falta instalar Gradle: usa el *wrapper* incluido (`./gradlew`).

## Versiones (Fase 0)

| Componente | Version | Fuente |
|---|---|---|
| Kotlin | 2.4.20 | Maven Central; es la version con la que se construye antlr-kotlin 1.0.14 |
| Compose Multiplatform | 1.11.1 | Maven Central (estable; la 1.12.x exige compileSdk 37, hoy solo en plataforma beta) |
| Android Gradle Plugin | 9.5.1 | Maven de Google (ultima estable) |
| antlr-kotlin (plugin + runtime) | 1.0.14 | Gradle Plugin Portal / Maven Central |
| kotlinx-coroutines | 1.11.0 | Maven Central (ultima estable) |
| Gradle (wrapper) | 9.9.1 | gradle.org |

Regla del documento de diseno (seccion 6): Kotlin tiene que estar soportado
por antlr-kotlin **y** por Compose a la vez; no actualizar ninguna version
sin probar las otras.

## Como correr cada cosa

```bash
# Tests del nucleo (genera el lexer/parser con ANTLR antes de compilar)
./gradlew :core:jvmTest

# Todos los tests del nucleo en todos los targets
./gradlew :core:allTests

# CLI: parsea un archivo .moka y muestra el resumen
./gradlew :cli:run --args="ejemplos/hola.moka"

# App de escritorio: abre la ventana
./gradlew :app:run

# App Android: instala en el emulador o celular conectado
./gradlew :app:installDebug

# Instalador nativo de escritorio (dmg/msi/deb segun el SO)
./gradlew :app:packageDistributionForCurrentOS
```

## Estructura

```
moka/
├── core/    # Nucleo: libreria Kotlin Multiplatform (targets JVM + Android)
│   ├── antlr/Moka.g4        # Gramatica (esqueleto de la Fase 0)
│   └── src/commonMain/...   # Moka.kt: fachada minima (crece desde la Fase 1)
├── cli/     # CLI JVM minima (crece en la Parte A del roadmap)
├── app/     # Compose Multiplatform: escritorio (desktop) + Android
└── ejemplos/                # Programas .moka de ejemplo
```

El codigo que ANTLR genera a partir de `Moka.g4` va a `core/build/` (paquete
`ar.com.moka.core.generado`): **no se edita ni se versiona**.

## Proximos pasos

Fase 1 (seccion 7 del documento): lexico completo, gramatica de expresiones,
`imprimir`, AST minimo, semantico de variables, TAC de expresiones y la
`MaquinaTac` con `Consola`, ya con la fachada `Moka.procesar(...)` por eventos.
