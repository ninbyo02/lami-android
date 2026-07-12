package io.github.ninbyo02.lami.ui.startup

import io.github.ninbyo02.lami.ui.screens.settings.LocalBackendRuntimeEvidence

enum class StartupBackend(val displayName: String) {
    NPU("NPU"),
    GPU("GPU"),
    CPU("CPU"),
}

enum class StartupBackendStatus(val label: String) {
    CHECKING("確認中"),
    AVAILABLE("利用可能"),
    UNAVAILABLE("利用不可"),
}

data class StartupBackendCheckItem(
    val backend: StartupBackend,
    val status: StartupBackendStatus,
)

data class StartupBackendCheckSequence(
    val items: List<StartupBackendCheckItem>,
    val timedOut: Boolean = false,
) {
    val canContinue: Boolean
        get() = items.none { it.status == StartupBackendStatus.CHECKING }

    fun item(backend: StartupBackend): StartupBackendCheckItem =
        requireNotNull(items.firstOrNull { it.backend == backend }) {
            "Missing startup backend item: $backend"
        }

    fun resolve(
        backend: StartupBackend,
        available: Boolean,
    ): StartupBackendCheckSequence = copy(
        items = items.map { item ->
            if (item.backend != backend) {
                item
            } else {
                item.copy(
                    status = if (available) {
                        StartupBackendStatus.AVAILABLE
                    } else {
                        StartupBackendStatus.UNAVAILABLE
                    },
                )
            }
        },
    )

    fun timeout(): StartupBackendCheckSequence = copy(
        items = items.map { item ->
            if (item.status == StartupBackendStatus.CHECKING) {
                item.copy(status = StartupBackendStatus.UNAVAILABLE)
            } else {
                item
            }
        },
        timedOut = true,
    )

    companion object {
        fun initial(): StartupBackendCheckSequence = StartupBackendCheckSequence(
            items = StartupBackend.entries.map { backend ->
                StartupBackendCheckItem(
                    backend = backend,
                    status = StartupBackendStatus.CHECKING,
                )
            },
        )
    }
}


enum class StartupPresentation { BACKEND_CHECK_SPLASH, APP_CONTENT }

internal fun initialStartupPresentation(isActivityRecreation: Boolean): StartupPresentation =
    if (isActivityRecreation) StartupPresentation.APP_CONTENT else StartupPresentation.BACKEND_CHECK_SPLASH

data class StartupSplashContract(
    val presentation: StartupPresentation,
    val sequence: StartupBackendCheckSequence,
) {
    fun timeout(): StartupSplashContract = copy(sequence = sequence.timeout())
    fun finish(): StartupSplashContract = copy(presentation = StartupPresentation.APP_CONTENT)

    companion object {
        fun initial(isActivityRecreation: Boolean): StartupSplashContract = StartupSplashContract(
            presentation = initialStartupPresentation(isActivityRecreation),
            sequence = StartupBackendCheckSequence.initial(),
        )
    }
}

internal fun startupBackendAvailability(evidence: LocalBackendRuntimeEvidence): List<Pair<StartupBackend, Boolean>> =
    listOf(
        StartupBackend.NPU to (evidence.npuSupported && evidence.npuHealthy),
        StartupBackend.GPU to (evidence.gpuSupported && evidence.gpuHealthy),
        StartupBackend.CPU to (evidence.cpuSupported && evidence.cpuHealthy),
    )
