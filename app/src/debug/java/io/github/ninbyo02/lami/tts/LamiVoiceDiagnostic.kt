package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

/** Exact prepared phrase only; this is not an arbitrary-text TTS frontend. */
internal object LamiVoiceDiagnostic {
    suspend fun synthesizeText(root: File, text: String, progress: (String) -> Unit = {}): FloatArray {
        val codes = LamiPreparedVoiceSynthesizer.generateText(root, text, progress)
        currentCoroutineContext().ensureActive()
        progress("stage=pcm_decode")
        return Module.load(root.resolve("speech-decoder-dynamic-et14.pte").absolutePath, Module.LOAD_MODE_MMAP).use { decoder ->
            val pcm = decoder.forward(EValue.from(Tensor.fromBlob(codes.values, longArrayOf(1, 16, codes.frames.toLong()))))
                .single().toTensor().dataAsFloatArray
            currentCoroutineContext().ensureActive()
            require(pcm.size == codes.frames * 1920) { "Unexpected decoder sample count" }
            LamiPcmContract.validate(pcm)
            pcm
        }
    }

    suspend fun synthesize(root: File, progress: (String) -> Unit = {}): FloatArray {
        val codes = LamiPreparedVoiceSynthesizer.generate(root, progress)
        currentCoroutineContext().ensureActive()
        progress("stage=pcm_decode")
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
