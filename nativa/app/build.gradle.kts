plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.zancada.app"
    compileSdk = 35
    defaultConfig {
        // Fase 1: id distinto para instalarla AL LADO de la beta actual (com.zancada.beta) sin tocar tus datos.
        // En la fase 2 pasará a com.zancada.beta para sustituirla y migrar los datos.
        applicationId = "com.zancada.nativa"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    // Firma fija (la misma clave que la beta, Secret ZANCADA_KEYSTORE_B64) para que cada APK nuevo
    // se instale ENCIMA del anterior sin borrar la app. Sin el secreto (en local) se usa la clave de depuración normal.
    val zKeystore = System.getenv("ZANCADA_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (zKeystore != null) create("zancada") {
            storeFile = zKeystore; storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            if (zKeystore != null) signingConfig = signingConfigs.getByName("zancada")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { abortOnError = true; checkDependencies = true; warningsAsErrors = false }
    // Las rutas recomendadas son el mismo archivo que usa la web (../routes-data.js): se copian al compilar, sin duplicarlas.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/zassets"))
}

val copyRoutes by tasks.registering(Copy::class) {
    from(rootProject.file("../routes-data.js"))
    into(layout.buildDirectory.dir("generated/zassets"))
}
tasks.named("preBuild") { dependsOn(copyRoutes) }

dependencies {
    implementation(project(":core"))
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")   // Lint: la API de permisos necesita Fragment ≥ 1.3
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Mapas: OpenStreetMap (calles) y Esri (satélite), sin clave ni cuenta.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Health Connect: guardar las carreras como ejercicio (solo escritura).
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
}
