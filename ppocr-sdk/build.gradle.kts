plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.paddle.ocr"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.1")
    // 4.5.3.0 而非 4.5.3：后者在部分 Android 版本存在运行时问题
    // 参考: https://github.com/QuickBirdEng/opencv-android#-releases
    implementation("com.quickbirdstudios:opencv:4.5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
}
