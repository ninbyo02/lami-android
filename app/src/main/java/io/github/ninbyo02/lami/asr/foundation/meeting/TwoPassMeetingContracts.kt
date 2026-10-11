package io.github.ninbyo02.lami.asr.foundation.meeting

import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptStage

data class MeetingAudioArtifact(
    val id: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        require(id.isNotBlank())
        require(durationMillis > 0)
        require(sizeBytes > 0)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

interface MeetingAudioStore {
    suspend fun finish(): MeetingAudioArtifact
    suspend fun discard()
}

fun interface OfflineMeetingAsr {
    suspend fun transcribe(audio: MeetingAudioArtifact): TranscriptRevision
}

class TwoPassTranscriptReconciler {
    fun replace(
        provisional: TranscriptRevision,
        offline: TranscriptRevision,
    ): TranscriptRevision {
        require(provisional.stage == TranscriptStage.FINAL)
        require(offline.stage == TranscriptStage.FINAL)
        require(offline.timing != null)
        return offline.copy(revision = provisional.revision + 1)
    }
}
