package io.github.ninbyo02.lami.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LocalInferenceFailureDiagnosticsTest {
    @Test
    fun `dispatch missing is detected from unsatisfied link error`() {
        val throwable = UnsatisfiedLinkError(
            "dlopen failed: library \"libLiteRtDispatch_Qualcomm.so\" not found",
        )

        val diagnostics = buildLocalInferenceFailureDiagnostics(
            context = RuntimeEnvironment.getApplication(),
            stage = "engine-create",
            throwable = throwable,
            selectedModelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            selectedFallbackPath = "gpu",
        )

        assertEquals("gemma-4-E2B-it_qualcomm_sm8750.litertlm", diagnostics.selectedModelFilename)
        assertTrue(diagnostics.isQualcommModelLikely)
        assertTrue(diagnostics.isSm8750ModelLikely)
        assertTrue(diagnostics.unsatisfiedLinkErrorDetected)
        assertTrue(diagnostics.dlopenFailedDetected)
        assertTrue(diagnostics.dispatchApiMissingLikely)
        assertTrue("libLiteRtDispatch_Qualcomm.so" in diagnostics.missingLibraryNames)
    }

    @Test
    fun `formatted diagnostics include stacktrace and native library sections`() {
        val text = buildLocalInferenceFailureDiagnosticsText(
            context = RuntimeEnvironment.getApplication(),
            stage = "generate-response",
            throwable = IllegalStateException(
                "No usable Dispatch runtime found",
                UnsatisfiedLinkError("cannot locate symbol QnnHtp in libQnnHtp.so"),
            ),
            selectedModelName = "/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            selectedFallbackPath = "gpu",
        )

        assertTrue(text.contains("[Qualcomm Model Failure]"))
        assertTrue(text.contains("failure stage=generate-response"))
        assertTrue(text.contains("No usable Dispatch runtime found=true"))
        assertTrue(text.contains("dispatch api missing likely=true"))
        assertTrue(text.contains("stacktrace head:"))
    }
}
