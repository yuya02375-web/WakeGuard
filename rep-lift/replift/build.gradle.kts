plugins {
    id("com.android.application")
}

android {
    namespace = "jp.replift.training"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.replift.training"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
