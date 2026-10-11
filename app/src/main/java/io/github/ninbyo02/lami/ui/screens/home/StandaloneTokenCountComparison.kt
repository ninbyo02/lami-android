package io.github.ninbyo02.lami.ui.screens.home

import java.util.concurrent.CancellationException

internal data class StandaloneTokenCounts(val input: Int, val output: Int, val loadNs: Long = 0, val countNs: Long = 0)
internal data class StandaloneTokenComparison(val status: String, val counts: StandaloneTokenCounts? = null)

/** Diagnostic only: the return value must never replace the authoritative snapshot. */
internal fun compareStandaloneTokenCounts(
    enabled: Boolean,
    expectedInput: Int?,
    expectedOutput: Int?,
    count: () -> StandaloneTokenCounts,
): StandaloneTokenComparison {
    if (!enabled) return StandaloneTokenComparison("disabled")
    if (expectedInput == null || expectedOutput == null) return StandaloneTokenComparison("baseline-unavailable")
    return try {
        val result = count()
        require(result.input >= 0 && result.output >= 0 && result.loadNs >= 0 && result.countNs >= 0)
        StandaloneTokenComparison(
            if (result.input == expectedInput && result.output == expectedOutput) "match" else "mismatch", result,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: LinkageError) {
        StandaloneTokenComparison("native-unavailable")
    } catch (_: Exception) {
        StandaloneTokenComparison("failed")
    }
}
