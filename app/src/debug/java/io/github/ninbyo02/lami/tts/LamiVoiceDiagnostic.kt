package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

/** Bounded fixed-probe and arbitrary-text synthesis; text requires natural EOS. */
internal object LamiVoiceDiagnostic {
    suspend fun synthesizeText(context: android.content.Context, root: File, text: String, reuseCpWorkspace: Boolean = true, progress: (String) -> Unit = {}): FloatArray = coroutineScope {
        val decoderPreload = async {
            runCatching {
                LamiVoiceDecoderProcess.preloadDynamicDecoder(context, root, progress)
            }
        }
        try {
            val codes = LamiVoiceModuleCache.withSession(root) { session ->
                LamiPreparedVoiceSynthesizer.generateText(root, text, session, reuseCpWorkspace, progress = progress)
            }
            currentCoroutineContext().ensureActive()
            decoderPreload.await().onFailure { failure ->
                progress("metric=decoder_preload status=failed class=${failure.javaClass.simpleName}")
            }
            progress("stage=pcm_decode")
            LamiVoiceDecoderProcess.decode(context, root, codes, progress)
        } finally {
            decoderPreload.cancel()
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
