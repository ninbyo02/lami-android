package io.github.ninbyo02.lami.ui.screens.home

internal object NativeStubNpuPersistentHolderApi : NpuPersistentHolderApi {
    override fun createHolder(request: NpuPersistentHolderCreateRequest): NpuPersistentHolderApiResult {
        val nativeSummary = Qairt244ShortMultitokenSmoke.createStandardRouteAdapterHolder(request)
        return parseNpuPersistentHolderNativeStubResult(nativeSummary)
    }

    override fun runOnce(request: NpuPersistentHolderRunRequest): NpuPersistentHolderApiResult {
        val nativeSummary = Qairt244ShortMultitokenSmoke.runStandardRouteAdapterHolderOnce(request)
        return parseNpuPersistentHolderNativeStubResult(
            nativeSummary = nativeSummary,
            fallbackHolderId = request.holderId,
        )
    }

    override fun closeHolder(request: NpuPersistentHolderCloseRequest): NpuPersistentHolderApiResult {
        val nativeSummary = Qairt244ShortMultitokenSmoke.closeStandardRouteAdapterHolder(request)
        return parseNpuPersistentHolderNativeStubResult(
            nativeSummary = nativeSummary,
            fallbackHolderId = request.holderId,
        )
    }

    override fun getDiagnostics(holderId: String): NpuPersistentHolderApiDiagnostics {
        val nativeSummary = Qairt244ShortMultitokenSmoke.getStandardRouteAdapterHolderDiagnostics(holderId)
        return parseNpuPersistentHolderNativeStubResult(
            nativeSummary = nativeSummary,
            fallbackHolderId = holderId,
        ).diagnostics
    }
}
