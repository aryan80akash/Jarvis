
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aryan.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aryan.jarvis"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}
