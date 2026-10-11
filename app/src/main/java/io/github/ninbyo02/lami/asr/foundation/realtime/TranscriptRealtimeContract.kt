package io.github.ninbyo02.lami.asr.foundation.realtime

import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptRevision
import io.github.ninbyo02.lami.asr.foundation.transcript.TranscriptStage

sealed interface TranscriptRealtimeEvent {
    val transcript: TranscriptRevision

    data class Partial(
        override val transcript: TranscriptRevision,
    ) : TranscriptRealtimeEvent {
        init {
            require(transcript.stage == TranscriptStage.PARTIAL)
        }
    }

    data class Final(
        override val transcript: TranscriptRevision,
    ) : TranscriptRealtimeEvent {
        init {
            require(transcript.stage == TranscriptStage.FINAL)
        }
    }

    data class Committed(
        override val transcript: TranscriptRevision,
    ) : TranscriptRealtimeEvent {
        init {
            require(transcript.stage == TranscriptStage.COMMITTED)
        }
    }
}

fun interface CommittedTranscriptSink {
    suspend fun submit(transcript: TranscriptRevision)
}

class TranscriptCommitController(
    private val sink: CommittedTranscriptSink,
) {
    suspend fun commit(finalTranscript: TranscriptRevision): TranscriptRevision {
        require(finalTranscript.stage == TranscriptStage.FINAL)
        val committed = finalTranscript.commit()
        sink.submit(committed)
        return committed
    }
}
