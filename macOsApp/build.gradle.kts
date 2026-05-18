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
