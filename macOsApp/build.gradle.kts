plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val buildInfo = KmpBuildInfo.read(rootProject)
val generatedPlistFile = layout.buildDirectory.file("generated/Info.plist")

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
                // Link Info.plist (generated so version is embedded at link time)
                freeCompilerArgs +=
                    listOf(
                        "-linker-option",
                        "-sectcreate",
                        "-linker-option",
                        "__TEXT",
                        "-linker-option",
                        "__info_plist",
                        "-linker-option",
                        generatedPlistFile.get().asFile.absolutePath,
                    )
            }
        }
    }

    sourceSets { macosArm64Main.dependencies { implementation(projects.shared) } }
}

// Generate the Info.plist for MacOS
val generateInfoPlist by tasks.registering {
    description = "Generate Info.plist for MacOS"

    // Re-assign to locals so doLast captures Strings, not the build script object.
    val version = buildInfo.version
    val build = buildInfo.build
    val outputFile = generatedPlistFile

    inputs.property("version", version)
    inputs.property("build", build)
    outputs.file(outputFile)

    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key>
                <string>ReapEar</string>
                <key>CFBundleDisplayName</key>
                <string>ReapEar</string>
                <key>CFBundleIdentifier</key>
                <string>com.ethossoftworks.reaperbleiem</string>
                <key>CFBundleVersion</key>
                <string>$build</string>
                <key>CFBundleShortVersionString</key>
                <string>$version</string>
                <key>CFBundleExecutable</key>
                <string>ReapEar</string>
                <key>CFBundleIconFile</key>
                <string>AppIcon</string>
                <key>CFBundlePackageType</key>
                <string>APPL</string>
                <key>NSHighResolutionCapable</key>
                <true/>
                <key>NSBluetoothPeripheralUsageDescription</key>
                <string>This app requires Bluetooth access to connect to nearby devices.</string>
                <key>NSBluetoothAlwaysUsageDescription</key>
                <string>This app requires Bluetooth access to connect to nearby devices.</string>
            </dict>
            </plist>
            """.trimIndent()
        )
    }
}

// CMP on macOS native looks for resources at:
//   $cwd/src/commonMain/composeResources/<path-without-package>
// processedResources has all resources namespaced under their package dirs, so we strip
// the first path segment (the package name) when staging for the run task.
val stageRunResources by tasks.registering(Copy::class) {
    description = "Strips package prefix from processed resources for CMP resolution"
    dependsOn(":macOsApp:macosArm64ProcessResources")
    from(layout.buildDirectory.dir("processedResources/macosArm64/main/composeResources")) {
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("run-resources/src/commonMain/composeResources"))
}

// Assembles a distributable .app bundle from the release executable.
// Prerequisites: place AppIcon.icns at src/macosArm64Main/resources/AppIcon.icns
// Run: ./gradlew :macOsApp:assembleApp
val assembleApp by tasks.registering {
    description = "Assembles MacOS .app bundle"

    val appName = "ReapEar"

    dependsOn("linkReleaseExecutableMacosArm64", generateInfoPlist, stageRunResources)

    // Re-assign to locals so doLast captures Providers/File/String, not the build script object.
    val contentsDir = layout.buildDirectory.dir("$appName.app/Contents")
    val kexeFile = layout.buildDirectory.file("bin/macosArm64/releaseExecutable/macOsApp.kexe")
    val iconFile = file("src/macosArm64Main/resources/AppIcon.icns")
    val plistFile = generatedPlistFile
    // CMP 1.11.0 on macOS native resolves resources relative to CWD via paths like:
    //   {CWD}/src/commonMain/composeResources/{file}
    // Main.kt sets CWD to Contents/Resources/ on bundle launch, so resources go there.
    // stageRunResources already produces the right stripped (no package prefix) structure.
    val strippedResourcesDir = layout.buildDirectory.dir("run-resources/src/commonMain/composeResources")

    inputs.file(kexeFile)
    inputs.file(plistFile)
    inputs.dir(strippedResourcesDir)
    outputs.dir(contentsDir)

    doLast {
        val contents = contentsDir.get().asFile

        val macosDir = contents.resolve("MacOS").apply { mkdirs() }
        kexeFile.get().asFile.copyTo(macosDir.resolve(appName), overwrite = true)
        macosDir.resolve(appName).setExecutable(true)

        plistFile.get().asFile.copyTo(contents.resolve("Info.plist"), overwrite = true)

        val resourcesDir = contents.resolve("Resources").apply { mkdirs() }

        if (iconFile.exists()) {
            iconFile.copyTo(resourcesDir.resolve("AppIcon.icns"), overwrite = true)
        }

        strippedResourcesDir.get().asFile.copyRecursively(
            target = resourcesDir.resolve("src/commonMain/composeResources"),
            overwrite = true,
        )
    }
}

// All link tasks must wait for the plist so -sectcreate has the file ready.
// Run tasks need stageRunResources in the working directory for CMP resource resolution.
tasks.configureEach {
    if (name.matches(Regex("link(Debug|Release)ExecutableMacosArm64"))) {
        dependsOn(generateInfoPlist)
    }
    if (name.matches(Regex("run(Debug|Release)ExecutableMacosArm64"))) {
        dependsOn(stageRunResources)
        (this as? Exec)?.workingDir(layout.buildDirectory.dir("run-resources"))
    }
}