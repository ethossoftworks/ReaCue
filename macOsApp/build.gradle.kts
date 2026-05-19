plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }

    applyDefaultHierarchyTemplate()

    macosArm64 {
        binaries {
            executable {
                entryPoint = "main"
                linkerOpts("-lsqlite3")
                runTask?.dependsOn("stageRunResources")
                runTask?.workingDir(layout.buildDirectory.dir("run-resources"))

                // Link Info.plist
                freeCompilerArgs +=
                    listOf(
                        "-linker-option",
                        "-sectcreate",
                        "-linker-option",
                        "__TEXT",
                        "-linker-option",
                        "__info_plist",
                        "-linker-option",
                        "${projectDir}/src/macosArm64Main/resources/Info.plist",
                    )
            }
        }
    }

    sourceSets { macosArm64Main.dependencies { implementation(projects.shared) } }
}

// CMP on macOS native looks for resources at:
//   $cwd/src/commonMain/composeResources/<path-without-package>
// processedResources has all resources namespaced under their package dirs, so we strip
// the first path segment (the package name) when staging for the run task.
tasks.register<Copy>("stageRunResources") {
    dependsOn(":macOsApp:macosArm64ProcessResources")
    from(layout.buildDirectory.dir("processedResources/macosArm64/main/composeResources")) {
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("run-resources/src/commonMain/composeResources"))
}
