plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nearnet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nearnet"
        minSdk = 26
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":SessionLayer"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("io.socket:socket.io-client:1.0.0") {
        exclude(group = "org.json", module = "json")
    }
    // Retrofit (REST client)
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")

// Konwerter JSON (Gson)
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")

// OkHttp (Retrofit używa pod spodem, ale można dodać logging)
    implementation ("com.squareup.okhttp3:okhttp:4.10.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.10.0")


}



