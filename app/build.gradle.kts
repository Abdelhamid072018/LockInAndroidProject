plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lockin.focus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lockin.focus"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
