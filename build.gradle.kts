plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.25.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.ktor:ktor-client-core:2.3.11")
    implementation("io.ktor:ktor-client-cio:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
//                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            buildTypes.release.proguard {
                // version.set("7.5.0")
                isEnabled.set(false)
            }
            packageName = "OnlySq AI"
            packageVersion = "1.0.0"
            description = "Official app for OnlySq AI API"
            copyright = "© 2023-2026 OnlySq"
            vendor = "OnlySq"
        }
    }
}