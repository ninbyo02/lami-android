package io.github.ninbyo02.lami.assistant

/** Discard the requested prefix as it arrives, retaining only subsequent PCM. */
internal class BluetoothMicLeadIn(milliseconds: Int, sampleRate: Int) {
    var remainingSamples: Int = sampleCount(milliseconds, sampleRate)
        private set

    fun append(block: ShortArray, count: Int, destination: ShortArray, offset: Int): Int {
        require(count in 0..block.size)
        val discarded = minOf(count, remainingSamples)
        val retained = count - discarded
        require(offset in 0..destination.size && retained <= destination.size - offset)
        remainingSamples -= discarded
        block.copyInto(destination, offset, discarded, count)
        return retained
    }

    companion object {
        const val MAX_MS = 3000

        fun parse(value: String): Int? = value.trim().toIntOrNull()?.takeIf { it in 0..MAX_MS }

        fun sampleCount(milliseconds: Int, sampleRate: Int): Int {
            require(milliseconds in 0..MAX_MS)
            require(sampleRate in 1..192_000)
            return (milliseconds.toLong() * sampleRate / 1000).toInt()
        }
    }
}
