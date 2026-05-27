package io.github.ninbyo02.lami.npu

enum class DevOnlyNpuProcessBoundaryPoint {
    BEFORE_DISPATCH,
    AFTER_DISPATCH,
    AFTER_RESULT_OR_TIMEOUT,
    AFTER_CLEANUP,
    AFTER_10S,
}

enum class DevOnlyNpuProcessBoundaryClassification {
    PROCESS_PRESENT,
    PROCESS_ABSENT_BEFORE_DISPATCH,
    PROCESS_DISAPPEARED_AFTER_DISPATCH,
    PROCESS_DISAPPEARED_AFTER_CLEANUP,
    PROCESS_DISAPPEARED_AFTER_10S,
    PROCESS_STATE_UNKNOWN,
}

data class DevOnlyNpuProcessBoundarySnapshot(
    val point: DevOnlyNpuProcessBoundaryPoint,
    val pid: String?,
    val psOutputPresent: Boolean = pid != null,
    val snapshotCaptured: Boolean = true,
) {
    val processPresent: Boolean
        get() = !pid.isNullOrBlank() || psOutputPresent
}

data class DevOnlyNpuProcessBoundaryDecision(
    val classification: DevOnlyNpuProcessBoundaryClassification,
    val canDispatch: Boolean,
    val processDisappearedSuspect: Boolean,
    val reuseAllowed: Boolean,
    val hiddenPerRunIsolatedRequired: Boolean,
)

object DevOnlyNpuProcessBoundaryPolicy {
    fun evaluate(snapshot: DevOnlyNpuProcessBoundarySnapshot): DevOnlyNpuProcessBoundaryDecision {
        val classification = classify(snapshot)
        val suspect = classification in setOf(
            DevOnlyNpuProcessBoundaryClassification.PROCESS_ABSENT_BEFORE_DISPATCH,
            DevOnlyNpuProcessBoundaryClassification.PROCESS_DISAPPEARED_AFTER_DISPATCH,
            DevOnlyNpuProcessBoundaryClassification.PROCESS_DISAPPEARED_AFTER_CLEANUP,
            DevOnlyNpuProcessBoundaryClassification.PROCESS_DISAPPEARED_AFTER_10S,
        )
        val canDispatch = classification !=
            DevOnlyNpuProcessBoundaryClassification.PROCESS_ABSENT_BEFORE_DISPATCH &&
            classification != DevOnlyNpuProcessBoundaryClassification.PROCESS_STATE_UNKNOWN
        return DevOnlyNpuProcessBoundaryDecision(
            classification = classification,
            canDispatch = canDispatch,
            processDisappearedSuspect = suspect,
            reuseAllowed = !suspect && classification == DevOnlyNpuProcessBoundaryClassification.PROCESS_PRESENT,
            hiddenPerRunIsolatedRequired = suspect,
        )
    }

    private fun classify(snapshot: DevOnlyNpuProcessBoundarySnapshot): DevOnlyNpuProcessBoundaryClassification {
        if (!snapshot.snapshotCaptured) {
            return DevOnlyNpuProcessBoundaryClassification.PROCESS_STATE_UNKNOWN
        }
        if (snapshot.processPresent) {
            return DevOnlyNpuProcessBoundaryClassification.PROCESS_PRESENT
        }
        return when (snapshot.point) {
            DevOnlyNpuProcessBoundaryPoint.BEFORE_DISPATCH ->
                DevOnlyNpuProcessBoundaryClassification.PROCESS_ABSENT_BEFORE_DISPATCH
            DevOnlyNpuProcessBoundaryPoint.AFTER_DISPATCH ->
                DevOnlyNpuProcessBoundaryClassification.PROCESS_DISAPPEARED_AFTER_DISPATCH
            DevOnlyNpuProcessBoundaryPoint.AFTER_RESULT_OR_TIMEOUT,
            DevOnlyNpuProcessBoundaryPoint.AFTER_CLEANUP ->
                DevOnlyNpuProcessBoundaryClassification.PROCESS_DISAPPEARED_AFTER_CLEANUP
            DevOnlyNpuProcessBoundaryPoint.AFTER_10S ->
                DevOnlyNpuProcessBoundaryClassification.PROCESS_DISAPPEARED_AFTER_10S
        }
    }
}
