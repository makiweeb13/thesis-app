plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.thesisapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.thesisapp"
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.media3.common)
    implementation(libs.cronet.embedded)
    implementation(libs.tracing.perfetto.handshake)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Retrofit
    implementation(libs.retrofit) // Use the latest version
    implementation(libs.converter.gson) // For Gson parsing

    // OkHttp (Retrofit uses this, good to have explicit control or for logging interceptor)
    implementation(libs.okhttp) // Use the latest version
    implementation(libs.logging.interceptor) // Optional, for logging requests/responses
    implementation(libs.activity.ktx) // Or the latest version

}