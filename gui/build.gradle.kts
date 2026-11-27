// GUI-Submaster (Phase 2.5). Eigenes Gradle-Projekt, hängt NICHT von master/ ab.
//
// UNGETESTET (in der Entwicklungsumgebung gab es kein Gradle). TODO:
//  - Kotlin-/Protobuf-/JUnit-Versionen auf die aus master/build.gradle.kts angleichen.
//  - Gradle-Wrapper aus master/ kopieren: gradlew, gradlew.bat, gradle/wrapper/*
//  - Compose-Plugin-Version passend zur Kotlin-Version wählen (siehe Compose-Multiplatform-Doku).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"   // immer wie die Kotlin-Version
    id("org.jetbrains.compose") version "1.10.0"                 // TODO: neueste stabile Version prüfen
    id("com.google.protobuf") version "0.9.4"
}

group = "dev.archx"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.google.protobuf:protobuf-kotlin:4.28.3")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}

// Nur das GUI-Protokoll: die GUI darf showfile.proto & Co. nicht kennen (Modularität).
sourceSets {
    main {
        proto {
            srcDir("../schema")
            include("gui_link.proto")
        }
    }
}

compose.desktop {
    application {
        mainClass = "archx.gui.ui.MainKt"
    }
}
