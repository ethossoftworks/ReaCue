import org.gradle.kotlin.dsl.support.serviceOf

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
                <string>ReaCue</string>
                <key>CFBundleDisplayName</key>
                <string>ReaCue</string>
                <key>CFBundleIdentifier</key>
                <string>com.ethossoftworks.reaperbleiem</string>
                <key>CFBundleVersion</key>
                <string>$build</string>
                <key>CFBundleShortVersionString</key>
                <string>$version</string>
                <key>CFBundleExecutable</key>
                <string>ReaCue</string>
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

    val appName = "ReaCue"
    val execOps = serviceOf<ExecOperations>()

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

        // Re-sign the assembled bundle with a stable ad-hoc signature. The linker-signed
        // signature on the kexe was made before Resources existed, so it reports "code has no
        // resources but signature indicates they must be present" and macOS kills the app on
        // launch once it's copied (e.g. into /Applications). --force replaces it and seals the
        // Resources + Info.plist. (Still unsigned by a Developer ID, so it's not notarized.)
        execOps.exec { commandLine("codesign", "--force", "--sign", "-", contents.parentFile.absolutePath) }
    }
}

// Packages the assembled .app into a distributable .dmg using create-dmg.
// Requires the tool: brew install create-dmg
// Run: ./gradlew :macOsApp:createDmg
val createDmg by tasks.registering {
    description = "Packages the MacOS .app bundle into a .dmg using create-dmg"

    dependsOn(assembleApp)

    val appName = "ReaCue"
    val version = buildInfo.version
    val execOps = serviceOf<ExecOperations>()

    // assembleApp writes the bundle here.
    val appDir = layout.buildDirectory.dir("$appName.app")
    // Rounded macOS-style icon used only for the DMG volume (the app keeps its own AppIcon.icns).
    val volumeIconFile = file("dmg/VolumeIcon.icns")
    // create-dmg copies the contents of this folder into the image, so it must contain ONLY the
    // .app. create-dmg adds the /Applications drop-link itself, so no outward symlink ever lives
    // in a directory we later delete (this is what previously risked wiping the real /Applications).
    val stagingDir = layout.buildDirectory.dir("dmg-staging")
    val dmgFile = layout.buildDirectory.file("$appName-$version.dmg")

    inputs.dir(appDir)
    inputs.file(volumeIconFile)
    outputs.file(dmgFile)

    doLast {
        val staging = stagingDir.get().asFile
        val dmg = dmgFile.get().asFile

        // Fail early with an actionable message if the tool isn't installed.
        val toolPresent = execOps.exec {
            commandLine("which", "create-dmg")
            isIgnoreExitValue = true
        }.exitValue == 0
        if (!toolPresent) {
            throw GradleException("create-dmg not found on PATH. Install it with: brew install create-dmg")
        }

        // `rm -rf` is symlink-safe; never use File.deleteRecursively on a dir that may hold symlinks.
        execOps.exec { commandLine("rm", "-rf", staging.absolutePath); isIgnoreExitValue = true }
        // create-dmg refuses to overwrite an existing output file.
        dmg.delete()
        staging.mkdirs()

        // Use `ditto`, not Kotlin's copyRecursively: the latter copies file CONTENT only and
        // drops POSIX permissions, so the executable lost its +x bit and the installed app died
        // with "permission denied". ditto preserves permissions, xattrs, and the code signature.
        execOps.exec {
            commandLine("ditto", appDir.get().asFile.absolutePath, staging.resolve("$appName.app").absolutePath)
        }

        // create-dmg builds the rw image, arranges the icon-view window, sets the volume icon,
        // adds the /Applications drop-link, and compresses — all the steps we did by hand before.
        execOps.exec {
            commandLine(
                "create-dmg",
                "--volname", appName,
                "--volicon", volumeIconFile.absolutePath,
                "--icon", "$appName.app", "150", "120",
                "--app-drop-link", "410", "120",
                "--window-size", "560", "380",
                "--no-internet-enable",
                dmg.absolutePath,
                staging.absolutePath,
            )
        }

        execOps.exec { commandLine("rm", "-rf", staging.absolutePath); isIgnoreExitValue = true }

        logger.lifecycle("created: ${dmg.absolutePath}")
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