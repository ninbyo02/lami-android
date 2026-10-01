package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

/** Bounded fixed-probe and arbitrary-text synthesis; text requires natural EOS. */
internal object LamiVoiceDiagnostic {
    suspend fun synthesizeText(context: android.content.Context, root: File, text: String, progress: (String) -> Unit = {}): FloatArray =
        LamiVoiceModuleCache.withSession(root) { session ->
            val codes = LamiPreparedVoiceSynthesizer.generateText(root, text, session, progress)
            currentCoroutineContext().ensureActive()
            progress("stage=pcm_decode")
            LamiVoiceDecoderProcess.decode(context, root, codes, progress)
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
