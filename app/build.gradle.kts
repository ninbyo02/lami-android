import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import com.android.build.api.variant.BuildConfigField

plugins {
    id("com.google.devtools.ksp")
    id("androidx.room")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

room {
    schemaDirectory("$projectDir/schemas")
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

val liteRtLmAndroidReleaseVersion = "0.11.0"
val liteRtLmAndroidDebugVersion = "0.11.0"
val liteRtLmAndroidNpuExperimentDebugVersion = "0.10.0"
val liteRtLmAndroidGalleryStackExperimentDebugVersion = "0.11.0"
val liteRtLmAndroidGalleryStackGpuProbeDebugVersion = "0.11.0"
val liteRtLmAndroidGpuRuntimeAlignmentProbeDebugVersion = "0.11.0"
val liteRtLmAndroidStandardGpuRuntimeMinimalProbeDebugVersion = "0.11.0"
val liteRtLmAndroidStandardGpuMinimalRuntimeCandidateDebugVersion = "0.11.0"
val liteRtLmAndroidStandardGpuNoConstraintProviderDebugVersion = "0.11.0"
val liteRtLmAndroidGalleryAlignedNpuProbeDebugVersion = "0.11.0"
val liteRtLmAndroidCustomBuildExperimentDebugVersion = "0.11.0"
val liteRtLmAndroidTrueEngineNpuProbeDebugVersion = "0.11.0"
val standardNpuRuntimeEnabled = providers.gradleProperty("lami.standardNpuRuntimeEnabled")
    .map { it.toBooleanStrict() }
    .orElse(false)

android {

    namespace = "io.github.ninbyo02.lami"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.ninbyo02.lami"
        minSdk = 34
        targetSdk = 36
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
        buildConfigField("Boolean", "STANDARD_NPU_RUNTIME_ENABLED", "false")
        buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
        buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
        buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
        buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
        buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
        buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
        buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_FLAVOR", "false")
        buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED", "false")
        buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED", "false")
        buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED", "false")
        buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_HELD_RUN_ONCE_ENABLED", "false")
        buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED", "false")
    }

    flavorDimensions += "dispatchExperiment"
    productFlavors {
        create("standard") {
            dimension = "dispatchExperiment"
            if (standardNpuRuntimeEnabled.get()) {
                applicationIdSuffix = ".npuvalidation"
            }
            buildConfigField("String", "CURRENT_FLAVOR", "\"standard\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"local SDK inputs; packaged only when lami.standardNpuRuntimeEnabled=true\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField(
                "Boolean",
                "STANDARD_NPU_RUNTIME_ENABLED",
                standardNpuRuntimeEnabled.get().toString(),
            )
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_FLAVOR", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_HELD_RUN_ONCE_ENABLED", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED", "false")
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
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
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
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
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
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
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
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
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
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("standardGpuMinimalRuntimeCandidate") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".gpustandardminimal"
            versionNameSuffix = "-standardGpuMinimalRuntimeCandidate"
            buildConfigField("String", "CURRENT_FLAVOR", "\"standardGpuMinimalRuntimeCandidate\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"dev-only standard-like GPU minimal runtime candidate staged in app/src/standardGpuMinimalRuntimeCandidateDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "true")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
        }
        create("standardGpuNoConstraintProvider") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".gpunoconstraint"
            versionNameSuffix = "-standardGpuNoConstraintProvider"
            buildConfigField("String", "CURRENT_FLAVOR", "\"standardGpuNoConstraintProvider\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"dev-only standard GPU runtime stack with only libGemmaModelConstraintProvider.so excluded by packaging\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "true")
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
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
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
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_FLAVOR", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_HELD_RUN_ONCE_ENABLED", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED", "false")
        }
        create("trueEngineNpuProbe") {
            dimension = "dispatchExperiment"
            applicationIdSuffix = ".trueengineprobe"
            versionNameSuffix = "-trueEngineNpuProbe"
            buildConfigField("String", "CURRENT_FLAVOR", "\"trueEngineNpuProbe\"")
            buildConfigField("Boolean", "QUALCOMM_DISPATCH_EXPERIMENT", "false")
            buildConfigField("String", "DISPATCH_RUNTIME_SOURCE", "\"isolated true Engine NPU probe shell; native execution temporarily disabled after startup crash; stack path app/src/trueEngineNpuProbeDebug/jniLibs/arm64-v8a\"")
            buildConfigField("Boolean", "NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED", "false")
            buildConfigField("Boolean", "GALLERY_STACK_EXPERIMENT", "false")
            buildConfigField("Boolean", "GALLERY_STACK_GPU_PROBE", "false")
            buildConfigField("Boolean", "RUNTIME_ALIGNMENT_PROBE", "false")
            buildConfigField("Boolean", "MINIMAL_RUNTIME_PROBE", "false")
            buildConfigField("Boolean", "STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_FLAVOR", "false")
            buildConfigField("Boolean", "STANDARD_GPU_NO_CONSTRAINT_PROVIDER_FLAVOR", "false")
            buildConfigField("Boolean", "CUSTOM_BUILD_EXPERIMENT", "false")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_FLAVOR", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_HELD_RUN_ONCE_ENABLED", "true")
            buildConfigField("Boolean", "TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED", "false")
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
        maybeCreate("standardDebug").apply {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/qairt244StandardDebugJniLibs"))
        }
        maybeCreate("standardRelease").apply {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/qairt244StandardReleaseJniLibs"))
            if (standardNpuRuntimeEnabled.get()) {
                java.srcDir("src/standardNpuRuntime/java")
                manifest.srcFile("src/standardNpuRuntime/AndroidManifest.xml")
            }
        }
        getByName("debug") {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/qnnDirectProbeDebugJniLibs"))
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
        create("standardGpuMinimalRuntimeCandidateDebug") {
            jniLibs.srcDir("src/standardGpuMinimalRuntimeCandidateDebug/jniLibs")
        }
        create("standardGpuNoConstraintProviderDebug") {
            jniLibs.srcDir("src/standardGpuNoConstraintProviderDebug/jniLibs")
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
        create("trueEngineNpuProbeDebug") {
            jniLibs.srcDir("src/trueEngineNpuProbeDebug/jniLibs")
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        if (variantBuilder.productFlavors.any { it.first == "dispatchExperiment" && (it.second == "npuExperiment" || it.second == "galleryStackExperiment" || it.second == "galleryStackGpuProbe" || it.second == "gpuRuntimeAlignmentProbe" || it.second == "standardGpuRuntimeMinimalProbe" || it.second == "standardGpuMinimalRuntimeCandidate" || it.second == "standardGpuNoConstraintProvider" || it.second == "galleryAlignedNpuProbe" || it.second == "customBuildExperiment" || it.second == "trueEngineNpuProbe") }) {
            variantBuilder.enable = false
        }
    }
    onVariants { variant ->
        val flavor = variant.productFlavors.firstOrNull { it.first == "dispatchExperiment" }?.second
        if (
            variant.buildType == "release" &&
            flavor == "standard" &&
            standardNpuRuntimeEnabled.get()
        ) {
            // LiteRT-LM receives ApplicationInfo.nativeLibraryDir, so the explicit
            // local validation candidate must expose real filesystem entries.
            variant.packaging.jniLibs.useLegacyPackaging.set(true)
        }
        if (
            variant.buildType == "release" &&
            flavor == "standard" &&
            !standardNpuRuntimeEnabled.get()
        ) {
            listOf(
                "**/libLiteRtDispatch_Qualcomm.so",
                "**/libLiteRtCompilerPlugin_Qualcomm.so",
                "**/libGemmaModelConstraintProvider.so",
                "**/libQnn*.so",
                "**/libqnn_*.so",
                "**/liblami_qairt244_npu_jni.so",
            ).forEach { pattern ->
                variant.packaging.jniLibs.excludes.add(pattern)
            }
        }
        if (variant.buildType == "debug" && flavor == "standardGpuMinimalRuntimeCandidate") {
            listOf(
                "**/libLiteRt.so",
                "**/liblitertlm_jni.so",
            ).forEach { pattern ->
                variant.packaging.jniLibs.pickFirsts.add(pattern)
            }
            listOf(
                "**/libLiteRtDispatch_Qualcomm.so",
                "**/libLiteRtCompilerPlugin_Qualcomm.so",
                "**/libGemmaModelConstraintProvider.so",
                "**/libQnn*.so",
                "**/libqnn_*.so",
            ).forEach { pattern ->
                variant.packaging.jniLibs.excludes.add(pattern)
            }
        }
        if (variant.buildType == "debug" && flavor == "standardGpuNoConstraintProvider") {
            variant.packaging.jniLibs.excludes.add("**/libGemmaModelConstraintProvider.so")
        }
        val liteRtLmVersion = when {
            variant.buildType == "debug" && flavor == "customBuildExperiment" -> liteRtLmAndroidCustomBuildExperimentDebugVersion
            variant.buildType == "debug" && flavor == "trueEngineNpuProbe" -> liteRtLmAndroidTrueEngineNpuProbeDebugVersion
            variant.buildType == "debug" && flavor == "galleryAlignedNpuProbe" -> liteRtLmAndroidGalleryAlignedNpuProbeDebugVersion
            variant.buildType == "debug" && flavor == "standardGpuNoConstraintProvider" -> liteRtLmAndroidStandardGpuNoConstraintProviderDebugVersion
            variant.buildType == "debug" && flavor == "standardGpuMinimalRuntimeCandidate" -> liteRtLmAndroidStandardGpuMinimalRuntimeCandidateDebugVersion
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
val npuPersistentHolderStubDebugJniSource =
    layout.projectDirectory.file("src/debug/cpp/lami_npu_persistent_holder_stub.cpp")
val trueEngineNpuProbeDebugNativePayloadSource =
    layout.projectDirectory.file("src/trueEngineNpuProbeDebug/cpp/lami_true_engine_npu_probe_payload.cpp")
val qnnDirectProbeDebugJniOutputDir = layout.buildDirectory.dir("generated/qnnDirectProbeDebugJniLibs/arm64-v8a")
val trueEngineNpuProbeDebugNativePayloadOutputDir =
    layout.projectDirectory.dir("src/trueEngineNpuProbeDebug/jniLibs/arm64-v8a")
val qairt244AppJniSmokeSource = layout.projectDirectory.file("src/customBuildExperimentDebug/cpp/lami_qairt244_smoke.cpp")
val qairt244AppJniSmokeOutputDir = layout.projectDirectory.dir("src/customBuildExperimentDebug/jniLibs/arm64-v8a")
val qairt244StandardDebugNativeSourceDir = layout.projectDirectory.dir("src/customBuildExperimentDebug/jniLibs/arm64-v8a")
val qairt244StandardDebugGeneratedJniOutputDir =
    layout.buildDirectory.dir("generated/qairt244StandardDebugJniLibs/arm64-v8a")
val qairt244StandardReleaseGeneratedJniOutputDir =
    layout.buildDirectory.dir("generated/qairt244StandardReleaseJniLibs/arm64-v8a")
val qairt244StandardDebugMergedNativeLibDir =
    layout.buildDirectory.dir("intermediates/merged_native_libs/standardDebug/mergeStandardDebugNativeLibs/out/lib/arm64-v8a")
val qairt244StandardDebugStrippedNativeLibDir =
    layout.buildDirectory.dir("intermediates/stripped_native_libs/standardDebug/stripStandardDebugDebugSymbols/out/lib/arm64-v8a")
val qairt244NativeRunEditablePromptSymbol =
    "Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt"
val qairt244NativeRunEditablePromptSymbolRegex =
    Regex("\\bGLOBAL\\b.*\\bDEFAULT\\b.*\\b" + Regex.escape(qairt244NativeRunEditablePromptSymbol) + "\\b")

val allowMissingQairt244Jni =
    providers.gradleProperty("lami.allowMissingQairt244Jni")
        .map { it.toBooleanStrict() }
        .orElse(false)

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

tasks.register("buildNpuPersistentHolderStubDebugJni") {
    group = "build"
    description = "Builds the debug-only NPU persistent holder JNI stub library."
    inputs.file(npuPersistentHolderStubDebugJniSource)
    outputs.file(qnnDirectProbeDebugJniOutputDir.map { it.file("liblami_npu_persistent_holder_stub.so") })

    doLast {
        val outputDir = qnnDirectProbeDebugJniOutputDir.get().asFile
        outputDir.mkdirs()
        val outputFile = File(outputDir, "liblami_npu_persistent_holder_stub.so")
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
                            npuPersistentHolderStubDebugJniSource.asFile.absolutePath,
                            "-o",
                            outputFile.absolutePath,
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
                            "src/debug/cpp/lami_npu_persistent_holder_stub.cpp",
                            "-o",
                            "build/generated/qnnDirectProbeDebugJniLibs/arm64-v8a/liblami_npu_persistent_holder_stub.so",
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

tasks.register("stageTrueEngineNpuProbeDebugNativeLibs") {
    group = "build"
    description = "Stages trueEngineNpuProbeDebug-only native payloads for create/close-only execution."
    inputs.file(trueEngineNpuProbeDebugNativePayloadSource)
    inputs.files(
        fileTree(qairt244StandardDebugNativeSourceDir) {
            include("*.so")
            exclude("liblami_qairt244_smoke.so")
        },
    )
    outputs.file(trueEngineNpuProbeDebugNativePayloadOutputDir.file("liblami_true_engine_npu_probe_payload.so"))
    outputs.files(
        fileTree(trueEngineNpuProbeDebugNativePayloadOutputDir) {
            include("*.so")
        },
    )

    doLast {
        val outputDir = trueEngineNpuProbeDebugNativePayloadOutputDir.asFile
        outputDir.mkdirs()
        delete(
            fileTree(outputDir) {
                include("*.so")
            },
        )
        copy {
            from(qairt244StandardDebugNativeSourceDir) {
                include("*.so")
                exclude("liblami_qairt244_smoke.so")
            }
            into(outputDir)
        }
        val outputFile = File(outputDir, "liblami_true_engine_npu_probe_payload.so")
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
                            trueEngineNpuProbeDebugNativePayloadSource.asFile.absolutePath,
                            "-o",
                            outputFile.absolutePath,
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
                            "src/trueEngineNpuProbeDebug/cpp/lami_true_engine_npu_probe_payload.cpp",
                            "-o",
                            "src/trueEngineNpuProbeDebug/jniLibs/arm64-v8a/liblami_true_engine_npu_probe_payload.so",
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
            exclude("liblitertlm_jni.so")
        },
    )
    outputs.dir(qairt244StandardDebugGeneratedJniOutputDir)
    inputs.property("allowMissingQairt244Jni", allowMissingQairt244Jni)

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
                exclude("liblitertlm_jni.so")
            }
            into(outputDir)
        }
        val litertLmJni = File(outputDir, "liblami_qairt244_npu_jni.so")
        if (!litertLmJni.isFile && allowMissingQairt244Jni.get()) {
            logger.warn(
                "standardDebug is being assembled as an explicit non-NPU smoke artifact because " +
                    "liblami_qairt244_npu_jni.so is not staged. Do not use this APK as NPU promotion evidence.",
            )
            return@doLast
        }
        require(litertLmJni.isFile) {
            "standardDebug NPU route requires staged separated liblami_qairt244_npu_jni.so with qairt244 custom JNI symbols. Missing: ${litertLmJni.absolutePath}"
        }
        val symbolOutput = ByteArrayOutputStream()
        val symbolError = ByteArrayOutputStream()
        val symbolResult = exec {
            commandLine("readelf", "-Ws", litertLmJni.absolutePath)
            standardOutput = symbolOutput
            errorOutput = symbolError
            isIgnoreExitValue = true
        }
        val symbols = symbolOutput.toString()
        require(
            symbolResult.exitValue == 0 &&
                qairt244NativeRunEditablePromptSymbolRegex.containsMatchIn(symbols),
        ) {
            "standardDebug NPU route requires qairt244 separated liblami_qairt244_npu_jni.so. " +
                "The staged liblami_qairt244_npu_jni.so does not export the GLOBAL JNI nativeRunEditablePrompt symbol; " +
                "rebuild/stage the patched LiteRT-LM artifact documented in docs/qairt244_native_artifact_reproducibility.md. " +
                "file=${litertLmJni.absolutePath} readelf_error=${symbolError.toString().take(400)}"
        }
    }
}

tasks.register("stageQairt244StandardReleaseNativeLibs") {
    group = "build"
    description = "Stages local SM8750 NPU runtime inputs for an explicitly enabled Standard Release candidate."
    inputs.files(
        fileTree(qairt244StandardDebugNativeSourceDir) {
            include("*.so")
            exclude("liblami_qairt244_smoke.so")
            exclude("liblitertlm_jni.so")
        },
    )
    inputs.property("standardNpuRuntimeEnabled", standardNpuRuntimeEnabled)
    outputs.dir(qairt244StandardReleaseGeneratedJniOutputDir)

    doLast {
        val sourceDir = qairt244StandardDebugNativeSourceDir.asFile
        val outputDir = qairt244StandardReleaseGeneratedJniOutputDir.get().asFile
        prepareQairt244StandardDebugBuildOutputsForCopy(
            sourceDir = sourceDir,
            outputDir = outputDir,
            allowedOutputRoots = listOf(qairt244StandardReleaseGeneratedJniOutputDir.get().asFile),
            taskName = name,
        )
        outputDir.mkdirs()
        if (!standardNpuRuntimeEnabled.get()) {
            logger.lifecycle("Standard Release NPU runtime disabled; generated vendor runtime directory is clean.")
            return@doLast
        }
        copy {
            from(sourceDir) {
                include("*.so")
                exclude("liblami_qairt244_smoke.so")
                exclude("liblitertlm_jni.so")
            }
            into(outputDir)
        }
        val npuJni = File(outputDir, "liblami_qairt244_npu_jni.so")
        require(npuJni.isFile) {
            "Enabled Standard Release NPU runtime requires local liblami_qairt244_npu_jni.so: ${npuJni.absolutePath}"
        }
        val symbolOutput = ByteArrayOutputStream()
        val symbolResult = exec {
            commandLine("readelf", "-Ws", npuJni.absolutePath)
            standardOutput = symbolOutput
            isIgnoreExitValue = true
        }
        require(
            symbolResult.exitValue == 0 &&
                qairt244NativeRunEditablePromptSymbolRegex.containsMatchIn(symbolOutput.toString()),
        ) {
            "Enabled Standard Release NPU runtime requires the pinned patched JNI artifact: ${npuJni.absolutePath}"
        }
    }
}

tasks.matching { it.name == "mergeStandardReleaseJniLibFolders" }.configureEach {
    dependsOn("stageQairt244StandardReleaseNativeLibs")
    // The same output directory serves enabled and disabled validation builds.
    // AGP does not reliably snapshot the generated source directory contents,
    // so force a fresh merge to prevent a previous NPU candidate from leaking
    // custom LiteRT/QNN libraries into the normal distributable Release.
    outputs.upToDateWhen { false }
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

tasks.matching { it.name == "packageStandardDebug" }.configureEach {
    dependsOn("overlayQairt244StandardDebugNativeLibs")
    dependsOn("overlayQairt244StandardDebugStrippedNativeLibs")
}

tasks.matching { it.name == "mergeStandardDebugJniLibFolders" }.configureEach {
    dependsOn("stageQairt244StandardDebugNativeLibs")
}

tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
    dependsOn("buildQnnDirectProbeDebugJni")
    dependsOn("buildNpuPersistentHolderStubDebugJni")
}

tasks.matching {
        it.name == "mergeStandardDebugJniLibFolders" ||
        it.name == "mergeNpuExperimentDebugJniLibFolders" ||
        it.name == "mergeGalleryStackExperimentDebugJniLibFolders" ||
        it.name == "mergeGalleryAlignedNpuProbeDebugJniLibFolders" ||
        it.name == "mergeCustomBuildExperimentDebugJniLibFolders"
}.configureEach {
    dependsOn("buildQnnDirectProbeDebugJni")
    dependsOn("buildNpuPersistentHolderStubDebugJni")
}

tasks.matching { it.name == "mergeTrueEngineNpuProbeDebugJniLibFolders" }.configureEach {
    dependsOn("stageTrueEngineNpuProbeDebugNativeLibs")
}

tasks.register("verifyQairt244CustomBuildExperimentDebugNativeLibs") {
    group = "verification"
    description = "Verifies customBuildExperimentDebug uses separated qairt244 NPU JNI with the editable prompt JNI symbol."
    inputs.file(qairt244StandardDebugNativeSourceDir.file("liblami_qairt244_npu_jni.so"))

    doLast {
        val litertLmJni = qairt244StandardDebugNativeSourceDir.file("liblami_qairt244_npu_jni.so").asFile
        require(litertLmJni.isFile) {
            "customBuildExperimentDebug requires staged qairt244 separated liblami_qairt244_npu_jni.so. " +
                "Run build-qairt244-custom-jni and stage-qairt244-custom-jni before install. Missing: ${litertLmJni.absolutePath}"
        }
        val symbolOutput = ByteArrayOutputStream()
        val symbolError = ByteArrayOutputStream()
        val symbolResult = exec {
            commandLine("readelf", "-Ws", litertLmJni.absolutePath)
            standardOutput = symbolOutput
            errorOutput = symbolError
            isIgnoreExitValue = true
        }
        val symbols = symbolOutput.toString()
        require(
            symbolResult.exitValue == 0 &&
                qairt244NativeRunEditablePromptSymbolRegex.containsMatchIn(symbols),
        ) {
            "customBuildExperimentDebug requires qairt244 separated liblami_qairt244_npu_jni.so exporting the GLOBAL JNI editable prompt symbol. " +
                "Run build-qairt244-custom-jni and stage-qairt244-custom-jni to avoid runtime UnsatisfiedLinkError. " +
                "expectedSymbol=$qairt244NativeRunEditablePromptSymbol file=${litertLmJni.absolutePath} " +
                "readelf_error=${symbolError.toString().take(400)}"
        }
        val stringsOutput = ByteArrayOutputStream()
        val stringsError = ByteArrayOutputStream()
        val stringsResult = exec {
            commandLine("strings", litertLmJni.absolutePath)
            standardOutput = stringsOutput
            errorOutput = stringsError
            isIgnoreExitValue = true
        }
        val nativeStrings = stringsOutput.toString()
        val stableSamplerMarkers = listOf(
            "sampler_config_profile=lami_stable_v1",
            "sampler_top_k=40",
            "sampler_top_p=0.9",
            "sampler_temperature=0.3",
            "sampler_seed=42",
            "thinking_control=raw_prompt_answer_only",
        )
        require(
            stringsResult.exitValue == 0 &&
                stableSamplerMarkers.all(nativeStrings::contains),
        ) {
            "customBuildExperimentDebug requires stable NPU sampler markers. " +
                "missing=${stableSamplerMarkers.filterNot(nativeStrings::contains)} " +
                "file=${litertLmJni.absolutePath} strings_error=${stringsError.toString().take(400)}"
        }
    }
}

tasks.matching {
    it.name == "mergeCustomBuildExperimentDebugJniLibFolders"
}.configureEach {
    dependsOn("buildQairt244AppJniSmokeCustomBuildExperimentDebugJni")
    dependsOn("verifyQairt244CustomBuildExperimentDebugNativeLibs")
}

tasks.register("verifyQairt244CustomBuildExperimentDebugApkNpuJni") {
    group = "verification"
    description = "Verifies the packaged custom NPU JNI is byte-identical to the staged stable-sampler artifact."
    dependsOn("assembleCustomBuildExperimentDebug")

    doLast {
        val stagedJni = qairt244StandardDebugNativeSourceDir
            .file("liblami_qairt244_npu_jni.so")
            .asFile
        require(stagedJni.isFile) {
            "staged custom NPU JNI is missing: ${stagedJni.absolutePath}"
        }
        val apkCandidates = fileTree(
            layout.buildDirectory.dir("outputs/apk/customBuildExperiment/debug"),
        ) {
            include("*.apk")
        }.files.sortedBy(File::getName)
        require(apkCandidates.size == 1) {
            "expected one customBuildExperimentDebug APK, found=${apkCandidates.map(File::getName)}"
        }
        val apk = apkCandidates.single()
        val apkJniBytes = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("lib/arm64-v8a/liblami_qairt244_npu_jni.so")
                ?: error("packaged custom NPU JNI is missing from ${apk.absolutePath}")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        val stagedJniBytes = stagedJni.readBytes()
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte) }
        val stagedSha256 = sha256(stagedJniBytes)
        val apkSha256 = sha256(apkJniBytes)
        require(stagedJniBytes.contentEquals(apkJniBytes)) {
            "packaged custom NPU JNI differs from staged artifact: " +
                "staged_sha256=$stagedSha256 apk_sha256=$apkSha256 apk=${apk.absolutePath}"
        }
        logger.lifecycle(
            "qairt244_custom_apk_npu_jni_verified=true sha256=$apkSha256 apk=${apk.absolutePath}",
        )
    }
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

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("DebugJniLibFolders")
}.configureEach {
    dependsOn("buildQnnDirectProbeDebugJni")
    dependsOn("buildNpuPersistentHolderStubDebugJni")
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
    add("standardGpuMinimalRuntimeCandidateImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidStandardGpuMinimalRuntimeCandidateDebugVersion")
    add("standardGpuNoConstraintProviderImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidStandardGpuNoConstraintProviderDebugVersion")
    add("galleryAlignedNpuProbeImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidGalleryAlignedNpuProbeDebugVersion")
    add("customBuildExperimentImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidCustomBuildExperimentDebugVersion")
    add("trueEngineNpuProbeImplementation", "com.google.ai.edge.litertlm:litertlm-android:$liteRtLmAndroidTrueEngineNpuProbeDebugVersion")
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
