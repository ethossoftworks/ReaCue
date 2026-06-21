plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

android {
    namespace = "com.ethossoftworks.reacue"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val buildInfo = KmpBuildInfo.read(rootProject)

    defaultConfig {
        applicationId = "com.ethossoftworks.reacue"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionName = buildInfo.version
        versionCode = buildInfo.build.toInt()
    }

    buildFeatures { buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    dependencies {
        coreLibraryDesugaring(libs.desugar.jdk.libs)
        implementation(project(":shared"))
        implementation(libs.oskit.kmp)
        implementation(libs.oskit.compose)
        implementation(libs.koin.core)
        implementation(libs.androidx.activity.compose)
        implementation(libs.kotlinx.coroutines.android)
    }
}