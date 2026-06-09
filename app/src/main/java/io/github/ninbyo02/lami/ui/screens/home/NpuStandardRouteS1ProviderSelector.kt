package io.github.ninbyo02.lami.ui.screens.home

internal object NpuStandardRouteS1ProviderSelector {
    const val REAL_PROVIDER_CLASS_NAME =
        "io.github.ninbyo02.lami.ui.screens.home.RealNpuStandardRouteS1Provider"
    const val REASON_REAL_PROVIDER_UNAVAILABLE = "real_provider_unavailable_for_variant"
    const val REASON_REAL_PROVIDER_INVALID_TYPE = "real_provider_invalid_type"
    const val REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT = "npu_s1_native_route_blocked_for_normal_chat"
    const val NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED = false

    fun defaultProvider(): NpuStandardRouteS1Provider =
        defaultProvider(s1GateEnabled = NpuStandardRouteS1GateConfig.enabled)

    fun defaultProviderForMode(mode: NpuStandardRouteMode): NpuStandardRouteS1Provider =
        defaultProvider(s1GateEnabled = NpuStandardRouteS1GateConfig.isEnabledForMode(mode))

    fun defaultProvider(s1GateEnabled: Boolean): NpuStandardRouteS1Provider =
        if (s1GateEnabled) {
            FailureNpuStandardRouteS1Provider(reason = REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT)
        } else {
            FixedNpuStandardRouteS1Provider()
        }

    fun defaultProviderWithPromotionGate(
        s1GateEnabled: Boolean,
        promotionGate: NpuS1PromotionGateResult,
    ): NpuStandardRouteS1Provider =
        if (s1GateEnabled && !normalChatNativeRouteUnblockAllowed(promotionGate)) {
            FailureNpuStandardRouteS1Provider(reason = REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT)
        } else {
            FixedNpuStandardRouteS1Provider()
        }

    fun normalChatNativeRouteUnblockAllowed(
        promotionGate: NpuS1PromotionGateResult,
    ): Boolean =
        NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED &&
            promotionGate.status == NPU_S1_PROMOTION_GATE_STATUS_PASS &&
            promotionGate.normalChatUnblockAllowed

    fun devDiagnosticProviderForMode(mode: NpuStandardRouteMode): NpuStandardRouteS1Provider =
        if (NpuStandardRouteS1GateConfig.isEnabledForMode(mode)) {
            realProvider()
        } else {
            FixedNpuStandardRouteS1Provider()
        }

    private fun realProvider(): NpuStandardRouteS1Provider =
        runCatching {
            val instance = Class.forName(REAL_PROVIDER_CLASS_NAME)
                .getDeclaredConstructor()
                .newInstance()
            instance as? NpuStandardRouteS1Provider
                ?: FailureNpuStandardRouteS1Provider(reason = REASON_REAL_PROVIDER_INVALID_TYPE)
        }.getOrElse {
            FailureNpuStandardRouteS1Provider(reason = REASON_REAL_PROVIDER_UNAVAILABLE)
        }
}
