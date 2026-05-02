package io.github.ninbyo02.lami.ui.screens.home

data class AcceleratorProbeSnapshot(
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceBoard: String?,
    val androidSdk: Int,
    val supportedAbis: List<String>,
    val cpuCoreCount: Int?,
    val cpuAbi: String?,
    val gpuVendor: String?,
    val gpuRenderer: String?,
    val gpuVersion: String?,
    val nnapiAvailable: Boolean,
    val nnapiDeprecatedWarning: Boolean,
    val nnapiDevices: List<String>,
    val probeSource: String,
    val probeError: String? = null,
)
