plugins {
    id("com.android.application")
}

android {
    namespace = "dev.zapbridge"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.zapbridge"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
}
