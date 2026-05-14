import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Properties

plugins {
    id("com.google.devtools.ksp")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

fun gitShaShort(): String {
    val stdout = ByteArrayOutputStream()
    return try {
        val result = providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
            isIgnoreExitValue = true
        }
        stdout.write(result.standardOutput.asBytes.get())
        stdout.toString().trim().takeIf { it.isNotBlank() } ?: ""
    } catch (e: Exception) {
        // .git がない配布物などで取得できない場合に備えて空文字にする
        ""
    }
}

fun resolveBuildPrNumber(): String {
    val fromProperty = providers.gradleProperty("buildPr").orNull?.trim().orEmpty()
    if (fromProperty.isNotEmpty()) return fromProperty

    val fromEnv = System.getenv("GITHUB_EVENT_NUMBER")?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv

    return ""
}

val liteRtLmAndroidReleaseVersion = "0.10.0"
val liteRtLmAndroidDebugVersion = "0.11.0"

android {

    namespace = "io.github.ninbyo02.lami"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ninbyo02.lami"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val gitSha = gitShaShort()
        val buildPrNumber = resolveBuildPrNumber()
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "BUILD_PR_NUMBER", "\"$buildPrNumber\"")
        buildConfigField("String", "APP_SUBTITLE", "\"LAMI — Lightweight AI for Memory & Interaction\"")
        buildConfigField("String", "LITERTLM_ANDROID_VERSION", "\"$liteRtLmAndroidReleaseVersion\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "LITERTLM_ANDROID_VERSION", "\"$liteRtLmAndroidDebugVersion\"")
        }
        release {
            buildConfigField("String", "LITERTLM_ANDROID_VERSION", "\"$liteRtLmAndroidReleaseVersion\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        getByName("debug") {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/qnnDirectProbeDebugJniLibs"))
        }
    }
}

val qnnNpuArm64LibDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
val requiredQnnRuntimeLibs = listOf(
    "libQnnSystem.so",
    "libQnnHtp.so",
    "libQnnHtpPrepare.so",
)
val qnnNpuModelNameMarkers = listOf("qualcomm", "qnn", "npu", "sm8750", "snapdragon", "htp")

fun collectQnnNpuNativeLibStatus(): QnnNpuNativeLibStatus {
    val libDir = qnnNpuArm64LibDir.asFile
    val libraries = libDir.listFiles()
        ?.filter { it.isFile && it.extension == "so" }
        ?.map(File::getName)
        ?.sorted()
        .orEmpty()
    val missingRuntimeLibs = requiredQnnRuntimeLibs.filterNot(libraries::contains)
    val hasHtpVariant = libraries.any { it.startsWith("libQnnHtpV") && it.endsWith(".so") }
    val dispatchCandidates = libraries.filter { name ->
        name.contains("dispatch", ignoreCase = true) &&
            (name.contains("litert", ignoreCase = true) ||
                name.contains("qnn", ignoreCase = true) ||
                name.contains("qualcomm", ignoreCase = true))
    }
    return QnnNpuNativeLibStatus(
        libDir = libDir,
        libraries = libraries,
        missingRuntimeLibs = missingRuntimeLibs,
        hasHtpVariant = hasHtpVariant,
        dispatchCandidates = dispatchCandidates,
    )
}

data class QnnNpuNativeLibStatus(
    val libDir: File,
    val libraries: List<String>,
    val missingRuntimeLibs: List<String>,
    val hasHtpVariant: Boolean,
    val dispatchCandidates: List<String>,
) {
    val ready: Boolean
        get() = missingRuntimeLibs.isEmpty() && hasHtpVariant && dispatchCandidates.isNotEmpty()
}

fun collectQnnNpuModelStatus(modelPath: String?): QnnNpuModelStatus {
    val fileName = modelPath?.substringAfterLast('/')?.trim().orEmpty()
    if (fileName.isBlank()) {
        return QnnNpuModelStatus(
            fileName = "",
            isLiteRtLm = false,
            matchedMarkers = emptyList(),
        )
    }
    val lowerName = fileName.lowercase()
    return QnnNpuModelStatus(
        fileName = fileName,
        isLiteRtLm = lowerName.endsWith(".litertlm"),
        matchedMarkers = qnnNpuModelNameMarkers.filter(lowerName::contains),
    )
}

data class QnnNpuModelStatus(
    val fileName: String,
    val isLiteRtLm: Boolean,
    val matchedMarkers: List<String>,
) {
    val ready: Boolean
        get() = isLiteRtLm && matchedMarkers.isNotEmpty()
}

tasks.register("printQnnNpuNativeLibStatus") {
    group = "verification"
    description = "Prints local Qualcomm QNN/NPU native library readiness for LiteRT-LM."
    doLast {
        val status = collectQnnNpuNativeLibStatus()

        println("QNN/NPU native library directory: ${status.libDir.absolutePath}")
        println("Packaged .so candidates: ${status.libraries.ifEmpty { listOf("none") }.joinToString(", ")}")
        println("Required QAIRT runtime libs: ${if (status.missingRuntimeLibs.isEmpty()) "present" else "missing ${status.missingRuntimeLibs.joinToString(", ")}"}")
        println("Required HTP skel/variant lib: ${if (status.hasHtpVariant) "present" else "missing libQnnHtpV*.so"}")
        println("LiteRT Qualcomm dispatch API lib: ${status.dispatchCandidates.ifEmpty { listOf("missing") }.joinToString(", ")}")
        println("Readiness: ${if (status.ready) "candidate-ready" else "blocked"}")
    }
}

tasks.register("printQnnNpuReadiness") {
    group = "verification"
    description = "Prints Qualcomm QNN/NPU library and model readiness. Pass -PqnnNpuModelPath=/path/to/model.litertlm."
    doLast {
        val nativeLibStatus = collectQnnNpuNativeLibStatus()
        val modelStatus = collectQnnNpuModelStatus(
            providers.gradleProperty("qnnNpuModelPath").orNull
                ?: System.getenv("QNN_NPU_MODEL_PATH")?.trim(),
        )
        val blockers = buildList {
            if (!nativeLibStatus.ready) add("native-libs")
            if (!modelStatus.ready) add("soc-specific-model")
        }
        println("QNN/NPU native library readiness: ${if (nativeLibStatus.ready) "candidate-ready" else "blocked"}")
        println("QNN/NPU model file: ${modelStatus.fileName.ifBlank { "missing" }}")
        println("QNN/NPU model litertlm: ${modelStatus.isLiteRtLm}")
        println("QNN/NPU model markers: ${modelStatus.matchedMarkers.ifEmpty { listOf("none") }.joinToString(", ")}")
        println("QNN/NPU readiness: ${if (blockers.isEmpty()) "candidate-ready" else "blocked-until-${blockers.joinToString("-")}"}")
    }
}

val qnnDirectProbeDebugJniSource = layout.projectDirectory.file("src/debug/cpp/qnn_direct_probe_debug.cpp")
val qnnDirectProbeDebugJniOutputDir = layout.buildDirectory.dir("generated/qnnDirectProbeDebugJniLibs/arm64-v8a")

fun findAndroidNdkClang(): File? {
    val explicitNdk = listOfNotNull(
        System.getenv("ANDROID_NDK_HOME")?.trim()?.takeIf { it.isNotEmpty() },
        System.getenv("ANDROID_NDK_ROOT")?.trim()?.takeIf { it.isNotEmpty() },
    ).map(::File)
    explicitNdk
        .map { ndkDir -> File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android34-clang++") }
        .firstOrNull { it.isFile }
        ?.let { return it }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { input -> localProperties.load(input) }
    }
    val sdkDir = localProperties.getProperty("sdk.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
        ?: System.getenv("ANDROID_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
        ?: System.getenv("ANDROID_SDK_ROOT")?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
    return sdkDir
        ?.resolve("ndk")
        ?.listFiles()
        ?.sortedByDescending(File::getName)
        ?.map { ndkDir -> File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android34-clang++") }
        ?.firstOrNull { it.isFile }
}

tasks.register("buildQnnDirectProbeDebugJni") {
    group = "build"
    description = "Builds the debug-only QNN direct probe JNI library."
    inputs.file(qnnDirectProbeDebugJniSource)
    outputs.file(qnnDirectProbeDebugJniOutputDir.map { it.file("libqnn_direct_probe_debug.so") })

    doLast {
        val outputDir = qnnDirectProbeDebugJniOutputDir.get().asFile
        outputDir.mkdirs()
        val outputFile = File(outputDir, "libqnn_direct_probe_debug.so")
        val clangArgs = listOf(
            "-shared",
            "-fPIC",
            "-std=c++17",
            "-fno-exceptions",
            "-fno-rtti",
            "-O0",
            "-g",
            "-Wall",
            "-Wextra",
            "-nostdlib++",
            "-Wl,--build-id=sha1",
        )
        val localClang = findAndroidNdkClang()
        if (localClang != null) {
            exec {
                commandLine(
                    listOf(localClang.absolutePath) +
                        clangArgs +
                        listOf(
                            qnnDirectProbeDebugJniSource.asFile.absolutePath,
                            "-o",
                            outputFile.absolutePath,
                            "-llog",
                            "-ldl",
                        ),
                )
            }
        } else {
            val uid = runCatching { Files.getAttribute(projectDir.toPath(), "unix:uid").toString() }
                .getOrDefault("1000")
            val gid = runCatching { Files.getAttribute(projectDir.toPath(), "unix:gid").toString() }
                .getOrDefault("1000")
            exec {
                commandLine(
                    listOf(
                        "docker",
                        "run",
                        "--rm",
                        "--user",
                        "$uid:$gid",
                        "-v",
                        "${projectDir.parentFile.absolutePath}:/work",
                        "-w",
                        "/work/${projectDir.name}",
                        "litert-build:ubuntu22",
                        "/opt/android-sdk/ndk/28.1.13356709/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android34-clang++",
                    ) +
                        clangArgs +
                        listOf(
                            "src/debug/cpp/qnn_direct_probe_debug.cpp",
                            "-o",
                            "build/generated/qnnDirectProbeDebugJniLibs/arm64-v8a/libqnn_direct_probe_debug.so",
                            "-llog",
                            "-ldl",
                        ),
                )
            }
        }
        exec {
            commandLine("readelf", "-d", outputFile.absolutePath)
            isIgnoreExitValue = true
        }
    }
}

tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
    dependsOn("buildQnnDirectProbeDebugJni")
}

tasks.register("copyQnnNpuNativeLibsFromQairt") {
    group = "setup"
    description = "Copies local QAIRT and LiteRT Qualcomm dispatch libraries into app/src/main/jniLibs/arm64-v8a."
    doLast {
        val qairtRoot = System.getenv("QAIRT_ROOT")?.trim().orEmpty()
        require(qairtRoot.isNotEmpty()) {
            "QAIRT_ROOT is required. Example: QAIRT_ROOT=~/qairt/2.34.0.250424 ./gradlew :app:copyQnnNpuNativeLibsFromQairt"
        }
        val qairtLibDir = File(qairtRoot).resolve("lib/aarch64-android")
        require(qairtLibDir.isDirectory) {
            "QAIRT Android library directory not found: ${qairtLibDir.absolutePath}"
        }

        val targetDir = qnnNpuArm64LibDir.asFile
        targetDir.mkdirs()
        val qairtLibraries = qairtLibDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.extension == "so" &&
                    (file.name in requiredQnnRuntimeLibs ||
                        file.name.startsWith("libQnnHtpV") ||
                        file.name.contains("skel", ignoreCase = true))
            }
            .orEmpty()
        require(qairtLibraries.isNotEmpty()) {
            "No QNN/HTP runtime libraries found in ${qairtLibDir.absolutePath}"
        }
        copy {
            from(qairtLibraries)
            into(targetDir)
        }

        val dispatchPath = providers.gradleProperty("litertQualcommDispatchSo").orNull
            ?: System.getenv("LITERT_QUALCOMM_DISPATCH_SO")?.trim()
        if (!dispatchPath.isNullOrBlank()) {
            val dispatchFile = File(dispatchPath)
            require(dispatchFile.isFile && dispatchFile.extension == "so") {
                "LiteRT Qualcomm dispatch API library not found: ${dispatchFile.absolutePath}"
            }
            copy {
                from(dispatchFile)
                into(targetDir)
            }
        } else {
            println("LiteRT Qualcomm dispatch API library not copied. Set -PlitertQualcommDispatchSo=/path/to/lib...so or LITERT_QUALCOMM_DISPATCH_SO.")
        }
        println("Copied ${qairtLibraries.size} QAIRT libraries into ${targetDir.absolutePath}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}


dependencies {
    //Variables
    val navVersion = "2.8.6"
    val roomVersion = "2.7.1"
    val markdown = "0.5.6"

    //Markdown
    implementation("com.github.jeziellago:compose-markdown:$markdown")

    //implemented
    //noinspection UseTomlInstead
    implementation("androidx.navigation:navigation-compose:$navVersion")
    implementation("androidx.room:room-runtime:$roomVersion")

    // See Add the KSP plugin to your project
    ksp("androidx.room:room-compiler:$roomVersion")

    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
//    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // optional - Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:$roomVersion")

    // For APIs
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    debugImplementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidDebugVersion")
    releaseImplementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidReleaseVersion")
    implementation("com.google.mediapipe:tasks-genai:0.10.33")
    

    //Generated
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
