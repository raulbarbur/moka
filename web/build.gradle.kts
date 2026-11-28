// Fase 0 del playground web: modulo minimo que prueba que :core corre en JS.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js {
        browser()
        nodejs()
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
