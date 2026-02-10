plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.murattarslan.car"
    compileSdk {
        version = release(35)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    implementation("androidx.media:media:1.7.1")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.media3:media3-session:1.9.1")
    implementation("androidx.media3:media3-exoplayer:1.9.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.1")
    implementation("androidx.media3:media3-ui:1.9.1")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}