# Distribucion de Moka 1.0

Como generar los instalables de Moka. Los pasos de esta pagina se
probaron en la PC de Windows de Raul (carpeta ejecutable, MSI y APK
firmado) y en un entorno de desarrollo Linux (carpeta ejecutable y APK
sin firmar). Todos se corren desde la carpeta del proyecto con
PowerShell (`C:\Users\light\AndroidStudioProjects\moka`).

## Version

Se usan dos valores distintos, en `app/build.gradle.kts`:

- `versionName = "1.0"` (en `defaultConfig`): la version del APK.
  `versionCode` sube a 2 con el lote del icono. Android usa ese numero
  para saber cual version es mas reciente y para impedir bajar de
  version, y la documentacion oficial de Android recomienda aumentarlo
  en cada version que se distribuya. Para que el APK nuevo se reconozca
  como actualizacion de la instalada (con la misma clave de firma), se
  sube el numero; en pruebas locales a veces se puede reinstalar con el
  mismo codigo, pero no es lo recomendado.
- En `compose.desktop > application > nativeDistributions`, debajo de
  `targetFormats(...)`:

```kotlin
packageName = "Moka"
packageVersion = "1.0.0"
```

El MSI exige el formato MAJOR.MINOR.BUILD, por eso la version de
escritorio es 1.0.0 y no 1.0. `packageName` hace que el ejecutable y el
instalador se llamen Moka y no "app" (el nombre del modulo).

## Iconos

El icono de Moka (una M crema sobre gris carbon con un cursor verde)
vive en tres lugares distintos, que no se confunden entre si:

- **Telefono (launcher Android)**: `app/src/androidMain/res/mipmap-*`
  (`ic_launcher.png` por densidad e icono adaptativo en
  `mipmap-anydpi-v26`), referenciado con `android:icon` en
  `AndroidManifest.xml`.
- **Ventana de escritorio** (barra de titulo y de tareas):
  `app/src/desktopMain/resources/moka-icono.png`, cargado en `Main.kt`.
- **Moka.exe, acceso directo e instalador MSI**: `app/icons/moka.ico`
  (y `moka.png` / `moka.icns` para Linux y macOS), declarados con
  `iconFile` en `nativeDistributions`.

Para cambiar el icono hay que reemplazar esos archivos con la misma
estructura y volver a generar el MSI y el APK. Windows guarda iconos en
cache: si sigue viendose el anterior, reiniciar el Explorador de
archivos o reinstalar.

## Requisito: un JDK completo

El empaquetado de escritorio usa `jpackage`, que necesita un JDK 17 o
superior (documentacion de Compose Multiplatform, seccion "JDK
version"). El Java que trae Android Studio no incluye `jpackage` y el
empaquetado falla. Solucion usada: instalar Eclipse Temurin 21 (JDK,
Windows x64, instalador .msi) desde
https://adoptium.net/temurin/releases/?os=windows&version=21 y apuntar
a el solo en la ventana de PowerShell que se usa, sin cambiar el
proyecto:

```powershell
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-21*" | Select-Object -First 1).FullName
& "$env:JAVA_HOME\bin\jpackage.exe" --version
.\gradlew --stop
```

El ultimo comando para los procesos de Gradle viejos, que guardan el
Java anterior. Los pasos de abajo se corren en esa misma ventana.

Atencion: esta seleccion dura solo mientras la ventana de PowerShell
siga abierta. En una ventana nueva, `packageMsi` vuelve a usar el Java
de Android Studio y falla de nuevo por falta de `jpackage`; hay que
repetir los comandos de arriba.

## Carpeta ejecutable de escritorio

```powershell
.\gradlew :app:createDistributable
```

Deja `app\build\compose\binaries\main\app\Moka\` con `Moka.exe` y su
propio Java: quien lo use no necesita tener JDK. Se puede comprimir en un
zip y distribuir asi.

## Instalador de Windows (MSI)

```powershell
.\gradlew :app:packageMsi
```

El instalador queda en `app\build\compose\binaries\main\msi\`. No hace
falta instalar WiX a mano: el plugin de Compose lo descarga solo (tarea
`downloadWix`), y lo hizo en la PC de Raul. Incluye su propio Java.

## APK firmado para Android

Android solo instala un APK firmado. La firma usa una clave propia que
queda en la PC de quien publica.

1. Generar el APK sin firmar:

```powershell
.\gradlew :app:assembleRelease
```

Queda en `app\build\outputs\apk\release\app-release-unsigned.apk`.

2. Firmarlo. Se puede hacer de dos formas equivalentes (no hay que hacer
   las dos). Raul lo hizo con el asistente de Android Studio:

- **Asistente de Android Studio**: Build > Generate Signed App Bundle /
  APK > APK > modulo `app`. Si ya hay una clave, elegirla ("Choose
  existing"); si no, "Create new" y guardar el archivo .jks en una
  carpeta fuera del proyecto. Elegir la variante `release` y la firma V2
  (y V3 si aparece).
- **Linea de comandos**: `keytool` (del JDK) para crear la clave,
  `zipalign` y `apksigner` (de las build-tools del Android SDK) para
  alinear y firmar. Hay que buscar la version de build-tools que
  tenga la PC, sin suponerla (`dir` en la carpeta `build-tools` del SDK;
  el SDK se indica en `sdk.dir` de `local.properties`). El orden es
  `zipalign` primero y `apksigner` despues, y se verifica con
  `apksigner verify --verbose --print-certs`.

3. Instalar en el telefono: copiar el APK y abrirlo (permite instalar
   apps de origen desconocido una vez), o con `adb install`.

### La clave de firma: guardarla bien

- La clave (archivo .jks) y sus contrasenas quedan en la PC, fuera del
  proyecto y del repositorio, y no se envian a nadie ni por chat.
- Hacer una copia de seguridad del .jks. Si se pierde, no se pueden
  firmar actualizaciones de la misma app: Android solo actualiza un APK
  firmado con la misma clave.
- No crear otra clave ni reemplazar el .jks existente para publicar la
  misma app.

### Si el telefono ya tiene Moka instalada

Una copia anterior firmada con otra clave (por ejemplo la que instala
Android Studio para pruebas, firmada con la clave de debug) impide
instalar el APK firmado encima, porque el ID de la app es el mismo.
Desinstalar la copia anterior borra sus datos. Antes de hacerlo, guardar
los archivos .moka del telefono en otro lugar, o probar en un emulador u
otro equipo. El ID de la app no se cambia para esquivar el problema.

## Que NO hace falta

- Instalar WiX a mano.
- Cambiar el Java que usa Android Studio: el JDK completo se usa solo
  para empaquetar, en la ventana de PowerShell donde se configura.
- Tocar `gradle.properties` ni `libs.versions.toml` para empaquetar.
