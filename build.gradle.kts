// Top-level build file where you can add configuration options common to all sub-projects/modules.
val minimumBuildJava = JavaVersion.VERSION_21
check(JavaVersion.current().isCompatibleWith(minimumBuildJava)) {
    "LAMI requires JDK 21 or newer for Gradle and unit tests. " +
        "Current runtime: ${System.getProperty("java.version")}. Set JAVA_HOME to a JDK 21 installation."
}

plugins {
    id("com.google.devtools.ksp") version "2.3.0" apply false
    id("androidx.room") version "2.7.1" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
