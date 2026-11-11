plugins {
    id("com.android.application")
}

android {
    namespace = "jp.wakeguard.alarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.wakeguard.alarm"
        minSdk = 29
        targetSdk = 36
        versionCode = 200
        versionName = "2.10.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.browser:browser:1.10.0")
}
