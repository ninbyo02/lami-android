import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Properties
import com.android.build.api.variant.BuildConfigField

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
val liteRtLmAndroidNpuExperimentDebugVersion = "0.10.0"
val liteRtLmAndroidGalleryStackExperimentDebugVersion = "0.11.0"
val liteRtLmAndroidGalleryStackGpuProbeDebugVersion = "0.11.0"
val liteRtLmAndroidGpuRuntimeAlignmentProbeDebugVersion = "0.11.0"
val liteRtLmAndroidStandardGpuRuntimeMinimalProbeDebugVersion = "0.11.0"
val liteRtLmAndroidGalleryAlignedNpuProbeDebugVersion = "0.11.0"
val liteRtLmAndroidCustomBuildExperimentDebugVersion = "0.11.0"

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
        buildConfigField("String", "CURRENT_FLAVOR", "\"standard\"")
        buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
        buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"none\"")
        buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
        buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
        buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
        buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
        buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
        buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
    }

    flavorDimensions += "dispatchExperiment"
    productFlavors {
        create("standard") {
            dimension = "dispatchExperiment"
            buildConfigField("String", "CURRENT_FLAVOR", "\"standard\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"none\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("npuExperiment") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".npu"
            versionNameSuffix = "-npuExperiment"
            buildConfigField("String", "CURRENT_FLAVOR", "\"npuExperiment\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "true")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"gallery-sm8750 detection-only staged in app/src/npuExperimentDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "true")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("galleryStackExperiment") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".gallerynpu"
            versionNameSuffix = "-galleryStackExperiment"
            buildConfigField("String", "CURRENT_FLAVOR", "\"galleryStackExperiment\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "true")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"gallery-sm8750 full native stack staged in app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "true")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "true")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("galleryStackGpuProbe") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".gallerystackgpu"
            versionNameSuffix = "-galleryStackGpuProbe"
            buildConfigField("String", "CURRENT_FLAVOR", "\"galleryStackGpuProbe\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"dev-only Edge Gallery GPU stack probe staged in app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "true")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("gpuRuntimeAlignmentProbe") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".gpualignment"
            versionNameSuffix = "-gpuRuntimeAlignmentProbe"
            buildConfigField("String", "CURRENT_FLAVOR", "\"gpuRuntimeAlignmentProbe\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"dev-only GPU runtime alignment promotion candidate staged in app/src/gpuRuntimeAlignmentProbeDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "true")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("standardGpuRuntimeMinimalProbe") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".gpuminimalprobe"
            versionNameSuffix = "-standardGpuRuntimeMinimalProbe"
            buildConfigField("String", "CURRENT_FLAVOR", "\"standardGpuRuntimeMinimalProbe\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"dev-only minimal GPU runtime probe using LiteRT/LiteRT-LM core pair only; source set app/src/standardGpuRuntimeMinimalProbeDebug/jniLibs/arm64-v8a is marker-only\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "true")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("galleryAlignedNpuProbe") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".galleryprobe"
            versionNameSuffix = "-galleryAlignedNpuProbe"
            buildConfigField("String", "CURRENT_FLAVOR", "\"galleryAlignedNpuProbe\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "true")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"debug-only Gallery-aligned SM8750 native stack staged in app/src/galleryAlignedNpuProbeDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "true")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "true")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("customBuildExperiment") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".customnpu"
            versionNameSuffix = "-customBuildExperiment"
            buildConfigField("String", "CURRENT_FLAVOR", "\"customBuildExperiment\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "true")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"custom LiteRT-LM v0.11.0 pinned-source native stack staged in app/src/customBuildExperimentDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "true")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "true")
        }
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
        create("standardDebug") {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/qairt244StandardDebugJniLibs"))
        }
        create("npuExperimentDebug") {
            jniLibs.srcDir("src/npuExperimentDebug/jniLibs")
        }
        create("galleryStackExperimentDebug") {
            java.srcDir("src/npuExperimentDebug/java")
            manifest.srcFile("src/npuExperimentDebug/AndroidManifest.xml")
            jniLibs.srcDir("src/galleryStackExperimentDebug/jniLibs")
        }
        create("galleryStackGpuProbeDebug") {
            jniLibs.srcDir("src/galleryStackGpuProbeDebug/jniLibs")
        }
        create("gpuRuntimeAlignmentProbeDebug") {
            jniLibs.srcDir("src/gpuRuntimeAlignmentProbeDebug/jniLibs")
        }
        create("standardGpuRuntimeMinimalProbeDebug") {
            jniLibs.srcDir("src/standardGpuRuntimeMinimalProbeDebug/jniLibs")
        }
        create("galleryAlignedNpuProbeDebug") {
            java.srcDir("src/npuExperimentDebug/java")
            manifest.srcFile("src/npuExperimentDebug/AndroidManifest.xml")
            jniLibs.srcDir("src/galleryAlignedNpuProbeDebug/jniLibs")
        }
        create("customBuildExperimentDebug") {
            java.srcDir("src/npuExperimentDebug/java")
            java.srcDir("src/customBuildExperimentDebug/java")
            manifest.srcFile("src/customBuildExperimentDebug/AndroidManifest.xml")
            jniLibs.srcDir("src/customBuildExperimentDebug/jniLibs")
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        if (variantBuilder.productFlavors.any { it.first == "dispatchExperiment" && (it.second == "npuExperiment" || it.second == "galleryStackExperiment" || it.second == "galleryStackGpuProbe" || it.second == "gpuRuntimeAlignmentProbe" || it.second == "standardGpuRuntimeMinimalProbe" || it.second == "galleryAlignedNpuProbe" || it.second == "customBuildExperiment") }) {
            variantBuilder.enable = false
        }
    }
    onVariants { variant ->
        val flavor = variant.productFlavors.firstOrNull { it.first == "dispatchExperiment" }?.second
        val liteRtLmVersion = when {
            variant.buildType == "debug" && flavor == "customBuildExperiment" -> liteRtLmAndroidCustomBuildExperimentDebugVersion
            variant.buildType == "debug" && flavor == "galleryAlignedNpuProbe" -> liteRtLmAndroidGalleryAlignedNpuProbeDebugVersion
            variant.buildType == "debug" && flavor == "standardGpuRuntimeMinimalProbe" -> liteRtLmAndroidStandardGpuRuntimeMinimalProbeDebugVersion
            variant.buildType == "debug" && flavor == "gpuRuntimeAlignmentProbe" -> liteRtLmAndroidGpuRuntimeAlignmentProbeDebugVersion
            variant.buildType == "debug" && flavor == "galleryStackGpuProbe" -> liteRtLmAndroidGalleryStackGpuProbeDebugVersion
            variant.buildType == "debug" && flavor == "galleryStackExperiment" -> liteRtLmAndroidGalleryStackExperimentDebugVersion
            variant.buildType == "debug" && flavor == "npuExperiment" -> liteRtLmAndroidNpuExperimentDebugVersion
            variant.buildType == "debug" -> liteRtLmAndroidDebugVersion
            else -> liteRtLmAndroidReleaseVersion
        }
        variant.buildConfigFields?.put(
            "LITERTLM_ANDROID_VERSION",
            BuildConfigField("String", "\"$liteRtLmVersion\"", "Resolved LiteRT-LM Android dependency version for this variant"),
        )
    }
}

configurations.matching { configuration ->
    configuration.name.startsWith("standardRelease")
}.configureEach {
    resolutionStrategy.force("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidReleaseVersion")
}

val qnnNpuArm64LibDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
val requiredQnnRuntimeLibs = listOf(
    "libQnnSystem.so",
    "libQnnHtp.so",
    "libQnnHtpPrepare.so",
)
val qnnNpuDispatchExactLib = "libLiteRtDispatch_Qualcomm.so"
val qnnNpuDispatchCandidateNames = listOf(
    qnnNpuDispatchExactLib,
    "libLiteRtDispatchQualcomm.so",
    "liblitert_dispatch_qualcomm.so",
    "libLiteRtDispatch.so",
    "liblitert_dispatch.so",
)
val qnnNpuModelNameMarkers = listOf("qualcomm", "qnn", "npu", "sm8750", "snapdragon", "htp", "qcs")

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
        val lower = name.lowercase()
        qnnNpuDispatchCandidateNames.any { it.equals(name, ignoreCase = true) } ||
            "dispatch" in lower ||
            "litertdispatch" in lower ||
            (("qualcomm" in lower || "qnn" in lower) && "dispatch" in lower)
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
        println("LiteRT Qualcomm dispatch API exact match: ${status.dispatchCandidates.any { it.equals(qnnNpuDispatchExactLib, ignoreCase = true) }}")
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
val qairt244AppJniSmokeSource = layout.projectDirectory.file("src/customBuildExperimentDebug/cpp/lami_qairt244_smoke.cpp")
val qairt244AppJniSmokeOutputDir = layout.projectDirectory.dir("src/customBuildExperimentDebug/jniLibs/arm64-v8a")
val qairt244StandardDebugNativeSourceDir = layout.projectDirectory.dir("src/customBuildExperimentDebug/jniLibs/arm64-v8a")
val qairt244StandardDebugGeneratedJniOutputDir =
    layout.buildDirectory.dir("generated/qairt244StandardDebugJniLibs/arm64-v8a")
val qairt244StandardDebugMergedNativeLibDir =
    layout.buildDirectory.dir("intermediates/merged_native_libs/standardDebug/mergeStandardDebugNativeLibs/out/lib/arm64-v8a")
val qairt244StandardDebugStrippedNativeLibDir =
    layout.buildDirectory.dir("intermediates/stripped_native_libs/standardDebug/stripStandardDebugDebugSymbols/out/lib/arm64-v8a")

fun prepareQairt244StandardDebugBuildOutputForCopy(
    outputFile: File,
    allowedOutputRoots: List<File>,
    taskName: String,
) {
    val canonicalOutput = outputFile.canonicalFile
    val allowed = allowedOutputRoots.any { root ->
        val canonicalRoot = root.canonicalFile
        canonicalOutput == canonicalRoot || canonicalOutput.toPath().startsWith(canonicalRoot.toPath())
    }
    require(allowed) {
        "$taskName refused to modify non-build output file: ${canonicalOutput.absolutePath}"
    }
    val parent = canonicalOutput.parentFile
    parent?.mkdirs()
    if (parent != null && parent.exists() && !parent.canWrite()) {
        parent.setWritable(true, true)
    }
    if (canonicalOutput.exists()) {
        if (!canonicalOutput.canWrite()) {
            canonicalOutput.setWritable(true, true)
        }
        if (!canonicalOutput.delete()) {
            throw GradleException(
                "$taskName failed to delete stale native lib before overlay: ${canonicalOutput.absolutePath}",
            )
        }
        logger.lifecycle("$taskName removed stale native lib before overlay: ${canonicalOutput.absolutePath}")
    }
}

fun prepareQairt244StandardDebugBuildOutputsForCopy(
    sourceDir: File,
    outputDir: File,
    allowedOutputRoots: List<File>,
    taskName: String,
) {
    sourceDir
        .listFiles { file -> file.isFile && file.extension == "so" }
        ?.forEach { sourceFile ->
            prepareQairt244StandardDebugBuildOutputForCopy(
                outputFile = File(outputDir, sourceFile.name),
                allowedOutputRoots = allowedOutputRoots,
                taskName = taskName,
            )
        }
}

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

tasks.register("buildQairt244AppJniSmokeCustomBuildExperimentDebugJni") {
    group = "build"
    description = "Builds the customBuildExperimentDebug-only QAIRT 2.44 app JNI logcat smoke library."
    inputs.file(qairt244AppJniSmokeSource)
    outputs.file(qairt244AppJniSmokeOutputDir.file("liblami_qairt244_smoke.so"))

    doLast {
        val outputDir = qairt244AppJniSmokeOutputDir.asFile
        outputDir.mkdirs()
        val outputFile = File(outputDir, "liblami_qairt244_smoke.so")
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
                            qairt244AppJniSmokeSource.asFile.absolutePath,
                            "-o",
                            outputFile.absolutePath,
                            "-llog",
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
                            "src/customBuildExperimentDebug/cpp/lami_qairt244_smoke.cpp",
                            "-o",
                            "src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblami_qairt244_smoke.so",
                            "-llog",
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

tasks.register("overlayQairt244StandardDebugNativeLibs") {
    group = "build"
    description = "Overlays the existing qairt244 SM8750 custom native stack into standardDebug for the hidden experiment."
    inputs.files(
        fileTree(qairt244StandardDebugGeneratedJniOutputDir) {
            include("*.so")
        },
    )
    dependsOn("stageQairt244StandardDebugNativeLibs")
    dependsOn("mergeStandardDebugNativeLibs")

    doLast {
        val sourceDir = qairt244StandardDebugGeneratedJniOutputDir.get().asFile
        val outputDir = qairt244StandardDebugMergedNativeLibDir.get().asFile
        prepareQairt244StandardDebugBuildOutputsForCopy(
            sourceDir = sourceDir,
            outputDir = outputDir,
            allowedOutputRoots = listOf(qairt244StandardDebugMergedNativeLibDir.get().asFile),
            taskName = name,
        )
        outputDir.mkdirs()
        copy {
            from(sourceDir) {
                include("*.so")
            }
            into(outputDir)
        }
    }
}

tasks.register("stageQairt244StandardDebugNativeLibs") {
    group = "build"
    description = "Stages qairt244 SM8750 native libraries as standardDebug jniLibs inputs for the hidden experiment."
    inputs.files(
        fileTree(qairt244StandardDebugNativeSourceDir) {
            include("*.so")
            exclude("liblami_qairt244_smoke.so")
        },
    )
    outputs.dir(qairt244StandardDebugGeneratedJniOutputDir)

    doLast {
        val outputDir = qairt244StandardDebugGeneratedJniOutputDir.get().asFile
        prepareQairt244StandardDebugBuildOutputsForCopy(
            sourceDir = qairt244StandardDebugNativeSourceDir.asFile,
            outputDir = outputDir,
            allowedOutputRoots = listOf(qairt244StandardDebugGeneratedJniOutputDir.get().asFile),
            taskName = name,
        )
        outputDir.mkdirs()
        copy {
            from(qairt244StandardDebugNativeSourceDir) {
                include("*.so")
                exclude("liblami_qairt244_smoke.so")
            }
            into(outputDir)
        }
    }
}

tasks.register("overlayQairt244StandardDebugStrippedNativeLibs") {
    group = "build"
    description = "Keeps qairt244 SM8750 staged native libraries in standardDebug after AGP strip."
    inputs.files(
        fileTree(qairt244StandardDebugGeneratedJniOutputDir) {
            include("*.so")
        },
    )
    outputs.dir(qairt244StandardDebugStrippedNativeLibDir)
    dependsOn("stageQairt244StandardDebugNativeLibs")
    dependsOn("stripStandardDebugDebugSymbols")

    doLast {
        val sourceDir = qairt244StandardDebugGeneratedJniOutputDir.get().asFile
        val outputDir = qairt244StandardDebugStrippedNativeLibDir.get().asFile
        prepareQairt244StandardDebugBuildOutputsForCopy(
            sourceDir = sourceDir,
            outputDir = outputDir,
            allowedOutputRoots = listOf(qairt244StandardDebugStrippedNativeLibDir.get().asFile),
            taskName = name,
        )
        outputDir.mkdirs()
        copy {
            from(sourceDir) {
                include("*.so")
            }
            into(outputDir)
        }
    }
}

tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
    dependsOn("buildQnnDirectProbeDebugJni")
}

tasks.matching {
        it.name == "mergeStandardDebugJniLibFolders" ||
        it.name == "mergeNpuExperimentDebugJniLibFolders" ||
        it.name == "mergeGalleryStackExperimentDebugJniLibFolders" ||
        it.name == "mergeGalleryAlignedNpuProbeDebugJniLibFolders" ||
        it.name == "mergeCustomBuildExperimentDebugJniLibFolders"
}.configureEach {
    dependsOn("buildQnnDirectProbeDebugJni")
}

tasks.matching { it.name == "mergeStandardDebugJniLibFolders" }.configureEach {
    dependsOn("stageQairt244StandardDebugNativeLibs")
}

tasks.matching {
    it.name == "mergeCustomBuildExperimentDebugJniLibFolders"
}.configureEach {
    dependsOn("buildQairt244AppJniSmokeCustomBuildExperimentDebugJni")
}

tasks.matching {
    it.name == "stripStandardDebugDebugSymbols"
}.configureEach {
    dependsOn("overlayQairt244StandardDebugNativeLibs")
}

tasks.matching { it.name == "packageStandardDebug" }.configureEach {
    dependsOn("overlayQairt244StandardDebugStrippedNativeLibs")
}

tasks.register("dumpStandardDebugApkNativeLibs") {
    group = "verification"
    description = "Dumps final standardDebug APK arm64-v8a native libraries with size, sha256, and likely source."
    dependsOn("assembleStandardDebug")

    doLast {
        exec {
            commandLine(
                "bash",
                rootProject.file("scripts/dump_standard_debug_apk_native_libs.sh").absolutePath,
                layout.buildDirectory.file("outputs/apk/standard/debug/app-standard-debug.apk").get().asFile.absolutePath,
            )
        }
    }
}

tasks.register("compareStandardDebugApkNativeLibsWithEdgeGallery") {
    group = "verification"
    description = "Compares Edge Gallery APK arm64-v8a native libraries with LAMI standardDebug."
    dependsOn("assembleStandardDebug")

    doLast {
        val edgeGalleryApk = providers.gradleProperty("edgeGalleryApk").orNull
            ?: System.getenv("EDGE_GALLERY_APK")?.trim()
        require(!edgeGalleryApk.isNullOrBlank()) {
            "Set -PedgeGalleryApk=/path/to/gallery.apk or EDGE_GALLERY_APK=/path/to/gallery.apk"
        }
        exec {
            commandLine(
                "bash",
                rootProject.file("scripts/compare_edge_gallery_lami_apk_native_libs.sh").absolutePath,
                edgeGalleryApk,
                layout.buildDirectory.file("outputs/apk/standard/debug/app-standard-debug.apk").get().asFile.absolutePath,
            )
        }
    }
}

afterEvaluate {
    if (tasks.findByName("compileDebugKotlin") == null && tasks.findByName("compileStandardDebugKotlin") != null) {
        tasks.register("compileDebugKotlin") {
            group = "build"
            description = "Compatibility alias for the standard debug Kotlin compile task."
            dependsOn("compileStandardDebugKotlin")
        }
    }
    if (tasks.findByName("assembleDebug") == null && tasks.findByName("assembleStandardDebug") != null) {
        tasks.register("assembleDebug") {
            group = "build"
            description = "Compatibility alias for the standard debug assemble task."
            dependsOn("assembleStandardDebug")
        }
    }
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
    add("standardImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidDebugVersion")
    add("npuExperimentImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidNpuExperimentDebugVersion")
    add("galleryStackExperimentImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidGalleryStackExperimentDebugVersion")
    add("galleryStackGpuProbeImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidGalleryStackGpuProbeDebugVersion")
    add("gpuRuntimeAlignmentProbeImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidGpuRuntimeAlignmentProbeDebugVersion")
    add("standardGpuRuntimeMinimalProbeImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidStandardGpuRuntimeMinimalProbeDebugVersion")
    add("galleryAlignedNpuProbeImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidGalleryAlignedNpuProbeDebugVersion")
    add("customBuildExperimentImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidCustomBuildExperimentDebugVersion")
    releaseImplementation("com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidReleaseVersion")
    implementation("com.qualcomm.qti:qnn-runtime:2.34.0")
    implementation("com.qualcomm.qti:qnn-litert-delegate:2.34.0")
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
