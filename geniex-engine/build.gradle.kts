plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.ninbyo02.lamigeniexprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ninbyo02.lamigeniexprobe"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

val genieXSdk = "com.qualcomm.qti:geniex-android:0.10.0"
val genieXSdkArchive by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(project(":geniex-bridge"))
    implementation(genieXSdk)
    add(genieXSdkArchive.name, "$genieXSdk@aar")
    implementation("androidx.activity:activity-ktx:1.10.1")
}

tasks.register<Copy>("stageGenieXSdkAar") {
    from(genieXSdkArchive)
    into(layout.buildDirectory.dir("verification"))
    rename { "geniex-sdk.aar" }
}
