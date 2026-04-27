plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvm("desktop")

    androidTarget()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.jnativehook)
                implementation(libs.kable.core)
                implementation(libs.javacv.platform)
                implementation(libs.zxing.core)
                implementation(libs.zxing.javase)
                implementation(libs.slf4j.simple)
                implementation(libs.hid4java)
            }
        }
    }
}

android {
    namespace = "com.woutwerkman.pa.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
