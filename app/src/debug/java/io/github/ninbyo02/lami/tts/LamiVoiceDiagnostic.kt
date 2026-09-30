package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

/** Exact prepared phrase only; this is not an arbitrary-text TTS frontend. */
internal object LamiVoiceDiagnostic {
    suspend fun synthesize(root: File): FloatArray {
        val codes = LamiPreparedVoiceSynthesizer.generate(root)
        currentCoroutineContext().ensureActive()
        return Module.load(root.resolve("speech-decoder-fp32-et14.pte").absolutePath, Module.LOAD_MODE_MMAP).use { decoder ->
            val pcm = decoder.forward(EValue.from(Tensor.fromBlob(codes, longArrayOf(1, 16, 31))))
                .single().toTensor().dataAsFloatArray
            currentCoroutineContext().ensureActive()
            require(pcm.size == 59520) { "Unexpected decoder shape: ${pcm.size}" }
            LamiPcmContract.validate(pcm)
            pcm
        }
    }
}
