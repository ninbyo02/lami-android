package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            "first_audio=requested first_audio_request_id=7 first_audio_start=cold first_audio_request_started_at_ns=1000000000 first_audio_elapsed_ms=0 first_audio_clock=elapsedRealtimeNanos",
            LamiFirstAudioTiming.requestStartedEvent(request),
        )
        assertEquals(
            "first_audio=started first_audio_request_id=7 first_audio_start=cold first_audio_request_started_at_ns=1000000000 first_audio_at_ns=1025999999 first_audio_elapsed_ms=25 first_audio_clock=elapsedRealtimeNanos",
            LamiFirstAudioTiming.playbackStartedEvent(request, 1_025_999_999L),
        )
    }

    @Test fun requestAndPlaybackEventsShareMonotonicColdWarmContract() {
        val request = LamiFirstAudioTiming.start(
            requestId = 3L,
            startedAtNs = 10_000_000L,
            startKind = LamiFirstAudioTiming.StartKind.WARM,
        )
        val requested = eventFields(LamiFirstAudioTiming.requestStartedEvent(request))
        val started = eventFields(LamiFirstAudioTiming.playbackStartedEvent(request, 42_000_000L))

        assertEquals("requested", requested["first_audio"])
        assertEquals("started", started["first_audio"])
        assertEquals(requested["first_audio_request_id"], started["first_audio_request_id"])
        assertEquals(requested["first_audio_start"], started["first_audio_start"])
        assertEquals(requested["first_audio_request_started_at_ns"], started["first_audio_request_started_at_ns"])
        assertEquals("elapsedRealtimeNanos", requested["first_audio_clock"])
        assertEquals("elapsedRealtimeNanos", started["first_audio_clock"])
        assertEquals("0", requested["first_audio_elapsed_ms"])
        assertEquals("32", started["first_audio_elapsed_ms"])
        assertTrue(
            started.getValue("first_audio_at_ns").toLong() >=
                requested.getValue("first_audio_request_started_at_ns").toLong(),
        )
    }

    @Test fun playbackStartedEventPreservesWarmClassificationAndRawMonotonicInstants() {
        val request = LamiFirstAudioTiming.start(
            requestId = 8L,
            startedAtNs = 2_000_000_000L,
            startKind = LamiFirstAudioTiming.StartKind.WARM,
        )
        val fields = eventFields(LamiFirstAudioTiming.playbackStartedEvent(request, 2_000_999_999L))

        assertEquals("started", fields["first_audio"])
        assertEquals("8", fields["first_audio_request_id"])
        assertEquals("warm", fields["first_audio_start"])
        assertEquals("2000000000", fields["first_audio_request_started_at_ns"])
        assertEquals("2000999999", fields["first_audio_at_ns"])
        assertEquals("0", fields["first_audio_elapsed_ms"])
        assertEquals("elapsedRealtimeNanos", fields["first_audio_clock"])
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

    private fun eventFields(event: String): Map<String, String> =
        event.split(" ")
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0] to parts[1]
            }
}
