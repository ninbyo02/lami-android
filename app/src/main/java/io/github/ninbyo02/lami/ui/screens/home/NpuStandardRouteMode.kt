package io.github.ninbyo02.lami.ui.screens.home

enum class NpuStandardRouteMode {
    OFF,
    S1_ONLY,
    S2_DB,
    S3_MARKDOWN,
    S4A_PSEUDO_STREAMING,
    FULL;

    fun isS1Enabled(): Boolean = this >= S1_ONLY

    fun isS2Enabled(): Boolean = this >= S2_DB

    fun isS3Enabled(): Boolean = this >= S3_MARKDOWN

    fun isS4AEnabled(): Boolean = this >= S4A_PSEUDO_STREAMING

    fun isS5Enabled(): Boolean = this == FULL
}
