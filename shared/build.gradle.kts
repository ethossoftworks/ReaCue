@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.ncorti.ktfmt.gradle.TrailingCommaManagementStrategy
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

dependencies {
    detektPlugins(libs.detektCompose)
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

kotlin {
    KmpBuildInfo.generate(rootProject)

    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }

    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "com.ethossoftworks.ixdlibrary"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }

        androidResources { enable = true }
    }

    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.ethossoftworks.reaperbleiem.shared")
            export(libs.oskit.kmp)
            export(libs.kotlinx.coroutines.core)
        }
    }

    sourceSets {
        all { languageSettings { optIn("kotlin.time.ExperimentalTime") } }

        commonMain {
            kotlin.srcDir("${layout.buildDirectory.asFile.get().absolutePath}/generated/com/outsidesource/kmpbuild")
        }

        commonMain.dependencies {
            api(libs.oskit.kmp)
            api(libs.kotlinx.coroutines.core)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.compose.ui.preview)

            implementation(libs.kermit)
            implementation(libs.oskit.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.network)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.atomicfu)
            implementation(libs.navigationEvent)
            implementation(libs.navigationEvent.compose)
            implementation(libs.kotlinxSerializationCbor)
            implementation(libs.kotlinxImmutableCollections)
            implementation(libs.kotlinxDateTime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.nordic.ble.scanner.kotlin)
            implementation(libs.nordic.ble.client.kotlin)
            implementation(libs.nordic.ble.client)
            implementation(libs.nordic.ble.scanner)
            implementation(libs.nordic.ble.client.ktx)
        }
    }
}

detekt {
    source.setFrom("src/")
    buildUponDefaultConfig = true // preconfigure defaults
    config.setFrom(rootProject.file("detekt.yml"), rootProject.file("detekt-compose.yml"))
}

ktfmt {
    kotlinLangStyle()
    maxWidth.set(120)
    removeUnusedImports.set(true)
    trailingCommaManagementStrategy.set(TrailingCommaManagementStrategy.COMPLETE)
}
