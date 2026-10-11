package io.github.ninbyo02.lami.asr.foundation.audio

/**
 * A one-shot, module-internal PCM ownership token. It can only be created from an already
 * validated [Pcm16Frame], and either transferred once or zeroized and released.
 */
internal class OwnedPcm16Frame private constructor(
    @get:JvmSynthetic
    val sampleRateHz: Int,
    private var ownedSamples: ShortArray?,
    ownershipMarker: Any,
) {
    init {
        require(PcmOwnershipCapability.isValid(ownershipMarker)) { "invalid PCM ownership capability" }
        validatePcm16Shape(sampleRateHz, checkNotNull(ownedSamples).size)
    }

    @get:JvmSynthetic
    internal val sampleCount: Int
        get() = checkNotNull(ownedSamples) { "PCM ownership was already consumed" }.size

    @JvmSynthetic
    internal fun takeSamples(): ShortArray {
        val samples = checkNotNull(ownedSamples) { "PCM ownership was already consumed" }
        ownedSamples = null
        return samples
    }

    @JvmSynthetic
    internal fun zeroizeAndRelease(): Boolean {
        val samples = ownedSamples ?: return true
        samples.fill(0)
        val verified = samples.all { it == 0.toShort() }
        ownedSamples = null
        return verified
    }

    internal companion object {
        @JvmSynthetic
        fun copyOf(sampleRateHz: Int, samples: ShortArray): OwnedPcm16Frame {
            validatePcm16Shape(sampleRateHz, samples.size)
            return OwnedPcm16Frame(sampleRateHz, samples.copyOf(), PcmOwnershipCapability.token())
        }
    }
}

/**
 * One bounded piece of mono PCM16 audio. Supported rates are 8–48 kHz and one frame is at most
 * one second. Public construction and reads are defensive copies. The module-internal one-shot
 * transfer path moves an already-owned snapshot without creating a simultaneous raw PCM copy.
 */
class Pcm16Frame private constructor(
    val sampleRateHz: Int,
    private val ownedSamples: ShortArray,
    ownershipMarker: Any,
) {
    init {
        require(PcmOwnershipCapability.isValid(ownershipMarker)) { "invalid PCM ownership capability" }
        validatePcm16Shape(sampleRateHz, ownedSamples.size)
    }

    constructor(sampleRateHz: Int, samples: ShortArray) : this(
        sampleRateHz = sampleRateHz,
        ownedSamples = validateAndCopy(sampleRateHz, samples),
        ownershipMarker = PcmOwnershipCapability.token(),
    )

    val sampleCount: Int
        get() = ownedSamples.size

    fun copySamples(): ShortArray = ownedSamples.copyOf()

    @JvmSynthetic
    internal fun copyForOwnershipTransfer(): OwnedPcm16Frame =
        OwnedPcm16Frame.copyOf(sampleRateHz, ownedSamples)

    companion object {
        @JvmSynthetic
        internal fun takeOwnership(owned: OwnedPcm16Frame): Pcm16Frame {
            val sampleRateHz = owned.sampleRateHz
            validatePcm16Shape(sampleRateHz, owned.sampleCount)
            val samples = owned.takeSamples()
            return Pcm16Frame(sampleRateHz, samples, PcmOwnershipCapability.token())
        }

        private fun validateAndCopy(sampleRateHz: Int, samples: ShortArray): ShortArray {
            validatePcm16Shape(sampleRateHz, samples.size)
            return samples.copyOf()
        }
    }
}

private fun validatePcm16Shape(sampleRateHz: Int, sampleCount: Int) {
    require(sampleRateHz in MIN_SAMPLE_RATE_HZ..MAX_SAMPLE_RATE_HZ) {
        "sampleRateHz must be between $MIN_SAMPLE_RATE_HZ and $MAX_SAMPLE_RATE_HZ"
    }
    require(sampleCount > 0) { "samples must not be empty" }
    require(sampleCount <= sampleRateHz) { "frame duration must not exceed one second" }
}

private const val MIN_SAMPLE_RATE_HZ = 8_000
private const val MAX_SAMPLE_RATE_HZ = 48_000
