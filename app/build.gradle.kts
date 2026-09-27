plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvislite.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvislite.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Networking - used for the Gemini Live WebSocket connection
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing for Gemini Live messages and function-call arguments
    implementation("org.json:json:20240303")

    // Coroutines for async voice/network handling
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // CameraX - lets the assistant take a photo headlessly (no preview UI needed)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")

    // Lets a Service have a lifecycle, which CameraX needs to bind to
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
}
