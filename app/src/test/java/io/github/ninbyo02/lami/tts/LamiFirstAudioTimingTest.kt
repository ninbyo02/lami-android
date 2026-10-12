package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LamiFirstAudioTimingTest {
    @Test fun requestOrdinalClassifiesOnlyTheFirstRequestAsCold() {
        assertEquals(
            LamiFirstAudioTiming.StartKind.COLD,
            LamiFirstAudioTiming.startKindForRequestOrdinal(0L),
        )
        assertEquals(
            LamiFirstAudioTiming.StartKind.WARM,
            LamiFirstAudioTiming.startKindForRequestOrdinal(1L),
        )
        assertEquals(
            LamiFirstAudioTiming.StartKind.WARM,
            LamiFirstAudioTiming.startKindForRequestOrdinal(99L),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LamiFirstAudioTiming.startKindForRequestOrdinal(-1L)
        }
    }

    @Test fun playbackStartedEventUsesMonotonicElapsedRealtimeNanosContract() {
        val request = LamiFirstAudioTiming.start(
            requestId = 7L,
            startedAtNs = 1_000_000_000L,
            startKind = LamiFirstAudioTiming.StartKind.COLD,
        )

        assertEquals(25L, LamiFirstAudioTiming.elapsedMsSince(request, 1_025_999_999L))
        assertEquals(
            "first_audio=started first_audio_request_id=7 first_audio_start=cold first_audio_elapsed_ms=25 first_audio_clock=elapsedRealtimeNanos",
            LamiFirstAudioTiming.playbackStartedEvent(request, 1_025_999_999L),
        )
    }

    @Test fun playbackStartedEventPreservesWarmClassification() {
        val request = LamiFirstAudioTiming.start(
            requestId = 8L,
            startedAtNs = 2_000_000_000L,
            startKind = LamiFirstAudioTiming.StartKind.WARM,
        )

        assertEquals(
            "first_audio=started first_audio_request_id=8 first_audio_start=warm first_audio_elapsed_ms=0 first_audio_clock=elapsedRealtimeNanos",
            LamiFirstAudioTiming.playbackStartedEvent(request, 2_000_999_999L),
        )
    }

    @Test fun requestAndEventTimestampsMustBeMonotonic() {
        assertThrows(IllegalArgumentException::class.java) {
            LamiFirstAudioTiming.start(
                requestId = -1L,
                startedAtNs = 0L,
                startKind = LamiFirstAudioTiming.StartKind.COLD,
            )
        }
        val request = LamiFirstAudioTiming.start(
            requestId = 0L,
            startedAtNs = 10L,
            startKind = LamiFirstAudioTiming.StartKind.COLD,
        )
        assertThrows(IllegalArgumentException::class.java) {
            LamiFirstAudioTiming.playbackStartedEvent(request, 9L)
        }
    }
}
