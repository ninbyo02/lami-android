plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.ninbyo02.lamigeniexbridge"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }
    buildFeatures {
        aidl = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
