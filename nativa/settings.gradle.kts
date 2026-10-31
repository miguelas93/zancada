// Zancada nativa (Android, Kotlin).
//  :core  cálculos de la carrera en Kotlin puro (sin Android): se prueban en cualquier ordenador
//         contra las mismas carreras y resultados de referencia que la web (../tests).
//  :app   la app Android (Jetpack Compose). Solo se incluye si hay SDK de Android (en GitHub Actions sí).
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}
dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
}
rootProject.name = "zancada"
include(":core")
val hasSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || file("local.properties").exists()
if (hasSdk) include(":app") else println("Sin SDK de Android: solo se compila :core")
