// El plugin de Android (AGP) va en el classpath raíz junto al de Kotlin (si no, el de Kotlin no lo ve),
// pero solo cuando hay SDK de Android: así :core se compila y prueba en cualquier ordenador.
buildscript {
    val hasSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || file("local.properties").exists()
    repositories { google(); mavenCentral() }
    if (hasSdk) dependencies { classpath("com.android.tools.build:gradle:8.7.3") }
}

plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
