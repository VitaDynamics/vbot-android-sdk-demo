plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vita.vbot.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vita.vbot.demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.3"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.vita.vbot:vbot-sdk:0.2.3")
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    constraints {
        implementation("androidx.navigationevent:navigationevent:1.0.2")
        implementation("androidx.navigationevent:navigationevent-compose:1.0.2")
    }
}
