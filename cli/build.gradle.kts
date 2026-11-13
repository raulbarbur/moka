plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "ar.com.moka.cli.MainKt"
}

// Que :cli:run tome las rutas relativas desde la raiz del repo,
// asi funciona: ./gradlew :cli:run --args="ejemplos/hola.moka"
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
