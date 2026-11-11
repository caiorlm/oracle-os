plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.oracleos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.oracleos.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Phones are ARM64; this also keeps the ZIM engine's other ABIs out of the APK.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // The plant model is memory-mapped straight from the APK.
    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":knowledge"))
    implementation(project(":inference"))
    // Kiwix ZIM engine (libzim/libkiwix, GPL-3.0) for the built-in encyclopedia reader.
    implementation("org.kiwix:libkiwix:2.4.0")
    // On-device plant identification (PlantNet-300K, LiteRT/TFLite).
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.maplibre.gl:android-sdk-opengl:13.7.1")
    // Ed25519 verification of signed data packs (pure Java; Android 9 has no platform Ed25519).
    implementation("net.i2p.crypto:eddsa:0.3.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
