package io.github.ninbyo02.lami.tts

/**
 * The balanced NX733J comparison favored eight frames for its 18-code-point
 * greeting and sixteen for the 40/110-code-point inputs. Keep this conservative
 * split without changing the model's speed, voice, denoising, or sample rate.
 */
internal object LamiSupertonicVocoderWindowPolicy {
    fun select(text: String, configuredFrames: Int, adaptiveWindow: Boolean): Int {
        if (!adaptiveWindow) return configuredFrames
        return if (text.codePointCount(0, text.length) >= 40) 16 else 8
    }
}
