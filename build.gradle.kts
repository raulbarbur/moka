// Proyecto raíz: solo declara los plugins disponibles para los submódulos.
// La configuración concreta vive en el build.gradle.kts de cada módulo.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.antlrKotlin) apply false

}

