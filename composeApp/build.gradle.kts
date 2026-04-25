plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
}

kotlin {
    jvm("desktop")

    androidTarget()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation(libs.qrcode.kotlin)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.qrcode.kotlin)
            }
        }
    }
}

android {
    namespace = "com.woutwerkman.pa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.woutwerkman.pa"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.woutwerkman.pa.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg
            )
            packageName = "PresentationAssistant"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.woutwerkman.pa"
            }
        }
    }
}
