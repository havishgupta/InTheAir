plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.havish.foreflight"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.havish.foreflight"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // osmdroid for map
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    
    // Kotlin Coroutines for async tasks like network
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // OkHttp for reverse geocoding API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Mapsforge for offline vector maps
    implementation("org.osmdroid:osmdroid-mapsforge:6.1.18")
    implementation("org.mapsforge:mapsforge-map-android:0.13.0")
    implementation("org.mapsforge:mapsforge-themes:0.13.0")
}
