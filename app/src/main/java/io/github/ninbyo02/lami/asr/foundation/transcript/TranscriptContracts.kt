package io.github.ninbyo02.lami.asr.foundation.transcript

enum class TranscriptStage { PARTIAL, FINAL, COMMITTED }

data class TranscriptTiming(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
) {
    init {
        require(startedAtMillis >= 0) { "transcript start must be non-negative" }
        require(endedAtMillis >= startedAtMillis) { "transcript end precedes start" }
    }
}

data class TranscriptOrigin(
    val backend: String,
    val modelId: String,
    val modelVersion: String,
) {
    init {
        require(backend.isNotBlank()) { "transcript backend is blank" }
        require(modelId.isNotBlank()) { "transcript model id is blank" }
        require(modelVersion.isNotBlank()) { "transcript model version is blank" }
    }
}

data class TranscriptRevision(
    val text: String,
    val stage: TranscriptStage,
    val revision: Long,
    val timing: TranscriptTiming?,
    val confidence: Float?,
    val origin: TranscriptOrigin,
) {
    init {
        require(text.isNotBlank()) { "transcript text is blank" }
        require(revision >= 0) { "transcript revision must be non-negative" }
        require(confidence == null || confidence in 0.0f..1.0f)
        require(stage != TranscriptStage.COMMITTED || timing != null)
    }

    fun revise(newText: String): TranscriptRevision {
        require(stage != TranscriptStage.PARTIAL)
        require(newText.isNotBlank())
        return copy(
            text = newText,
            stage = TranscriptStage.FINAL,
            revision = revision + 1,
        )
    }

    fun commit(): TranscriptRevision {
        require(stage == TranscriptStage.FINAL)
        return copy(stage = TranscriptStage.COMMITTED)
    }

    companion object {
        fun final(
            text: String,
            timing: TranscriptTiming,
            confidence: Float?,
            origin: TranscriptOrigin,
            revision: Long = 0,
        ): TranscriptRevision = TranscriptRevision(
            text = text,
            stage = TranscriptStage.FINAL,
            revision = revision,
            timing = timing,
            confidence = confidence,
            origin = origin,
        )
    }
}
