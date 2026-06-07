import dev.detekt.gradle.Detekt

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.detekt) apply false
}

// To get compose compiler metrics run: ./gradlew :desktop:run -PcomposeCompilerReports=true
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("composeCompilerReports") == "true") {
                freeCompilerArgs.add(
                    "-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${
                        projectDir.resolve(
                            "/build"
                        ).absolutePath
                    }/compose_compiler"
                )
            }
            if (project.findProperty("composeCompilerMetrics") == "true") {
                freeCompilerArgs.add(
                    "-P plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${
                        projectDir.resolve(
                            "/build"
                        ).absolutePath
                    }/compose_compiler"
                )
            }
        }
    }

    pluginManager.withPlugin("dev.detekt") {
        tasks.withType<Detekt>().configureEach {
            reports {
                html.required.set(true)
                sarif.required.set(false)
            }
        }
    }
}

tasks.register<OssDisclaimerGenerator>("generateOssDisclaimer")