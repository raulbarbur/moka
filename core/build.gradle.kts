import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.antlrKotlin)
}

// Generacion del lexer/parser/visitor de Moka.g4 en Kotlin.
// Sigue la configuracion documentada por antlr-kotlin (ver README del plugin).
val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // Los .g4 viven en core/antlr (solo se incluyen *.g4: el IDE puede
    // generar archivos temporales en el mismo directorio).
    source = fileTree(layout.projectDirectory.dir("antlr")) {
        include("**/*.g4")
    }

    // Paquete del codigo generado. NO se edita ni se versiona (va a build/).
    val pkg = "ar.com.moka.core.generado"
    packageName = pkg

    // Visitor si; el lenguaje destino (Kotlin) y la codificacion (UTF-8) son implicitos.
    arguments = listOf("-visitor")

    outputDirectory = layout.buildDirectory.dir("generatedAntlr/${pkg.replace(".", "/")}").get().asFile
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain {
            // El codigo generado por ANTLR entra a commonMain: al referenciar
            // la tarea, Gradle infiere su directorio de salida como srcDir.
            kotlin.srcDir(generateKotlinGrammarSource)
            dependencies {
                implementation(libs.antlr.kotlin.runtime)
                api(libs.kotlinx.coroutines.core) // Flow forma parte de la API publica (seccion 4.7)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "ar.com.moka.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}
