plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.oklabs.reelsvoice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.oklabs.reelsvoice"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5-vosk"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
}
