package io.github.ninbyo02.lami.tts

internal object LamiPlaybackUnderrunAccounting {
    fun session(initialCount: Int): Session = Session(initialCount)

    fun observedDelta(initialCount: Int, finalCount: Int): Int {
        validateCount("Initial", initialCount)
        validateCount("Final", finalCount)
        return (finalCount - initialCount).coerceAtLeast(0)
    }

    private fun validateCount(label: String, count: Int) {
        require(count >= 0) { "$label underrun count must be non-negative" }
    }

    class Session internal constructor(initialCount: Int) {
        private var lastCount = initialCount.also { validateCount("Initial", it) }
        var observedUnderruns: Int = 0
            private set

        fun observe(count: Int): Int {
            val delta = observedDelta(lastCount, count)
            lastCount = count
            observedUnderruns = (observedUnderruns.toLong() + delta)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            return observedUnderruns
        }
    }
}
