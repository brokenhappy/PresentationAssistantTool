plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

val compileSpotlightBle by tasks.registering(Exec::class) {
    val swiftSrc = project.rootProject.file("native/macos/SpotlightBle.swift")
    val arch = System.getProperty("os.arch")
    val baseDir = project.layout.buildDirectory.dir("generated/spotlightble")
    val outputDir = project.layout.buildDirectory.dir("generated/spotlightble/darwin-$arch")
    inputs.file(swiftSrc)
    outputs.dir(baseDir)
    onlyIf { System.getProperty("os.name").contains("Mac", ignoreCase = true) }
    doFirst { outputDir.get().asFile.mkdirs() }
    commandLine(
        "swiftc", "-emit-library",
        "-o", outputDir.get().asFile.resolve("libspotlightble.dylib").absolutePath,
        swiftSrc.absolutePath,
        "-framework", "CoreBluetooth",
        "-framework", "Foundation",
    )
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
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopMain by getting {
            resources.srcDirs(compileSpotlightBle.map { it.outputs.files })
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
