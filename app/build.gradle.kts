plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "me.erista.hshop.thor"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.erista.hshop.thor"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.0.4-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core-scraper"))

    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Image loading with Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Zstandard Compression (for .ZCCI / AzaharPlus support)
    implementation("com.github.luben:zstd-jni:1.5.6-8@aar")

    // Tooling & Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("updateReadmeVersion") {
    description = "Automatically updates the release APK version filename in README.md based on android.defaultConfig.versionName"
    doLast {
        val readmeFile = rootProject.file("README.md")
        if (readmeFile.exists()) {
            val vName = android.defaultConfig.versionName ?: return@doLast
            val content = readmeFile.readText()
            val regex = Regex("""hshop-thor-v\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?\.apk""")
            val updated = content.replace(regex, "hshop-thor-v$vName.apk")
            if (content != updated) {
                readmeFile.writeText(updated)
                println("[Gradle] Automatically updated README.md APK version to: hshop-thor-v$vName.apk")
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("updateReadmeVersion")
}

