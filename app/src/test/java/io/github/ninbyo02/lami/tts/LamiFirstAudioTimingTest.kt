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
            requestOrdinal = 0L,
            startedAtNs = 1_000_000_000L,
            startKind = LamiFirstAudioTiming.StartKind.COLD,
        )

        assertEquals(25L, LamiFirstAudioTiming.elapsedMsSince(request, 1_025_999_999L))
        assertEquals(
            "first_audio=requested first_audio_request_id=7 first_audio_request_ordinal=0 first_audio_start=cold first_audio_request_started_at_ns=1000000000 first_audio_at_ns=1000000000 first_audio_elapsed_ms=0 first_audio_elapsed_ns=0 first_audio_clock=elapsedRealtimeNanos first_audio_start_basis=engine_request_ordinal first_audio_request_boundary=preparation_started",
            LamiFirstAudioTiming.requestStartedEvent(request),
        )
        assertEquals(
            "first_audio=started first_audio_request_id=7 first_audio_request_ordinal=0 first_audio_start=cold first_audio_request_started_at_ns=1000000000 first_audio_at_ns=1025999999 first_audio_elapsed_ms=25 first_audio_elapsed_ns=25999999 first_audio_clock=elapsedRealtimeNanos first_audio_boundary=audio_track_play_returned first_audio_start_basis=engine_request_ordinal first_audio_request_boundary=preparation_started",
            LamiFirstAudioTiming.playbackStartedEvent(request, 1_025_999_999L),
        )
    }

    @Test fun requestAndPlaybackEventsShareMonotonicColdWarmContract() {
        val request = LamiFirstAudioTiming.start(
            requestId = 3L,
            requestOrdinal = 1L,
            startedAtNs = 10_000_000L,
            startKind = LamiFirstAudioTiming.StartKind.WARM,
        )
        val requested = eventFields(LamiFirstAudioTiming.requestStartedEvent(request))
        val started = eventFields(LamiFirstAudioTiming.playbackStartedEvent(request, 42_000_000L))

        assertEquals("requested", requested["first_audio"])
        assertEquals("started", started["first_audio"])
        assertEquals(requested["first_audio_request_id"], started["first_audio_request_id"])
        assertEquals(requested["first_audio_request_ordinal"], started["first_audio_request_ordinal"])
        assertEquals(requested["first_audio_start"], started["first_audio_start"])
        assertEquals(requested["first_audio_request_started_at_ns"], started["first_audio_request_started_at_ns"])
        assertEquals("elapsedRealtimeNanos", requested["first_audio_clock"])
        assertEquals("elapsedRealtimeNanos", started["first_audio_clock"])
        assertEquals("audio_track_play_returned", started["first_audio_boundary"])
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
            requestOrdinal = 2L,
            startedAtNs = 2_000_000_000L,
            startKind = LamiFirstAudioTiming.StartKind.WARM,
        )
        val fields = eventFields(LamiFirstAudioTiming.playbackStartedEvent(request, 2_000_999_999L))

        assertEquals("started", fields["first_audio"])
        assertEquals("8", fields["first_audio_request_id"])
        assertEquals("2", fields["first_audio_request_ordinal"])
        assertEquals("warm", fields["first_audio_start"])
        assertEquals("2000000000", fields["first_audio_request_started_at_ns"])
        assertEquals("2000999999", fields["first_audio_at_ns"])
        assertEquals("0", fields["first_audio_elapsed_ms"])
        assertEquals("elapsedRealtimeNanos", fields["first_audio_clock"])
        assertEquals("audio_track_play_returned", fields["first_audio_boundary"])
    }

    @Test fun playbackBoundaryCallbackImmediatelyFollowsAudioTrackPlay() {
        val source = java.io.File(
            "src/debug/java/io/github/ninbyo02/lami/tts/LamiPcmPlayer.kt",
        ).readText()

        assertTrue(Regex("track\\.play\\(\\)\\s+onStarted\\(\\)").containsMatchIn(source))
    }

    @Test fun requestAndEventTimestampsMustBeMonotonic() {
        assertThrows(IllegalArgumentException::class.java) {
            LamiFirstAudioTiming.start(
                requestId = -1L,
                requestOrdinal = 0L,
                startedAtNs = 0L,
                startKind = LamiFirstAudioTiming.StartKind.COLD,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LamiFirstAudioTiming.start(
                requestId = 0L,
                requestOrdinal = -1L,
                startedAtNs = 0L,
                startKind = LamiFirstAudioTiming.StartKind.COLD,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LamiFirstAudioTiming.start(
                requestId = 0L,
                requestOrdinal = 1L,
                startedAtNs = 0L,
                startKind = LamiFirstAudioTiming.StartKind.COLD,
            )
        }
        val request = LamiFirstAudioTiming.start(
            requestId = 0L,
            requestOrdinal = 0L,
            startedAtNs = 10L,
            startKind = LamiFirstAudioTiming.StartKind.COLD,
        )
        assertThrows(IllegalArgumentException::class.java) {
            LamiFirstAudioTiming.playbackStartedEvent(request, 9L)
        }
    }

    @Test fun neuralChatEnginePassesExplicitOrdinalIntoFirstAudioTiming() {
        val source = java.io.File(
            "src/debug/java/io/github/ninbyo02/lami/tts/LamiNeuralChatVoiceEngine.kt",
        ).readText()

        assertTrue(source.contains("val requestOrdinal = requestId - 1L"))
        assertTrue(source.contains("requestOrdinal = requestOrdinal"))
        assertTrue(source.contains("startKindForRequestOrdinal(requestOrdinal)"))
    }

    @Test fun coldAndWarmEventsDeclareEngineRequestOrdinalAsClassificationBasis() {
        for (ordinal in listOf(0L, 1L, 2L)) {
            val request = LamiFirstAudioTiming.start(
                requestId = ordinal + 1L,
                requestOrdinal = ordinal,
                startedAtNs = 10L,
                startKind = LamiFirstAudioTiming.startKindForRequestOrdinal(ordinal),
            )
            for (event in listOf(
                LamiFirstAudioTiming.requestStartedEvent(request),
                LamiFirstAudioTiming.playbackStartedEvent(request, 20L),
            )) {
                val fields = eventFields(event)
                assertEquals("engine_request_ordinal", fields["first_audio_start_basis"])
                assertEquals("preparation_started", fields["first_audio_request_boundary"])
                assertEquals(if (ordinal == 0L) "cold" else "warm", fields["first_audio_start"])
                assertEquals(ordinal.toString(), fields["first_audio_request_ordinal"])
            }
        }
    }

    @Test fun coldAndWarmEventsPreserveElapsedNanosecondsBeforeMillisecondTruncation() {
        for (ordinal in listOf(0L, 1L)) {
            val request = LamiFirstAudioTiming.start(
                requestId = ordinal + 1L,
                requestOrdinal = ordinal,
                startedAtNs = 2_000_000_000L,
                startKind = LamiFirstAudioTiming.startKindForRequestOrdinal(ordinal),
            )
            assertEquals("0", eventFields(LamiFirstAudioTiming.requestStartedEvent(request))["first_audio_elapsed_ns"])
            for (elapsedNs in listOf(0L, 999_999L, 1_000_000L, 25_999_999L)) {
                val fields = eventFields(LamiFirstAudioTiming.playbackStartedEvent(request, request.startedAtNs + elapsedNs))
                assertEquals(elapsedNs.toString(), fields["first_audio_elapsed_ns"])
                assertEquals((elapsedNs / 1_000_000L).toString(), fields["first_audio_elapsed_ms"])
                assertEquals(
                    fields.getValue("first_audio_at_ns").toLong() - fields.getValue("first_audio_request_started_at_ns").toLong(),
                    fields.getValue("first_audio_elapsed_ns").toLong(),
                )
            }
        }
    }

    @Test fun preparedEventCorrelatesColdAndWarmPreparationWithPlayback() {
        for (ordinal in listOf(0L, 1L)) {
            val request = LamiFirstAudioTiming.start(
                requestId = ordinal + 1L,
                requestOrdinal = ordinal,
                startedAtNs = 2_000_000_000L,
                startKind = LamiFirstAudioTiming.startKindForRequestOrdinal(ordinal),
            )
            val preparedAtNs = request.startedAtNs + 999_999L
            val playbackAtNs = preparedAtNs + 50_000_001L
            val prepared = eventFields(LamiFirstAudioTiming.preparedEvent(request, preparedAtNs))
            val started = eventFields(LamiFirstAudioTiming.playbackStartedEvent(request, playbackAtNs))
            assertEquals("prepared", prepared["first_audio"])
            assertEquals("full_pcm_synthesis_returned", prepared["first_audio_boundary"])
            for (key in listOf(
                "first_audio_request_id", "first_audio_request_ordinal", "first_audio_start",
                "first_audio_request_started_at_ns", "first_audio_clock",
                "first_audio_start_basis", "first_audio_request_boundary",
            )) {
                assertEquals(started[key], prepared[key])
            }
            assertEquals(preparedAtNs.toString(), prepared["first_audio_at_ns"])
            assertEquals("999999", prepared["first_audio_elapsed_ns"])
            assertEquals("0", prepared["first_audio_elapsed_ms"])
            assertEquals(
                50_000_001L,
                started.getValue("first_audio_elapsed_ns").toLong() -
                    prepared.getValue("first_audio_elapsed_ns").toLong(),
            )
            assertThrows(IllegalArgumentException::class.java) {
                LamiFirstAudioTiming.preparedEvent(request, request.startedAtNs - 1L)
            }
        }
    }

    @Test fun allColdAndWarmEventsShareRawTimestampAndElapsedContract() {
        for (ordinal in listOf(0L, 1L)) {
            val request = LamiFirstAudioTiming.start(
                requestId = ordinal + 1L,
                requestOrdinal = ordinal,
                startedAtNs = 2_000_000_000L,
                startKind = LamiFirstAudioTiming.startKindForRequestOrdinal(ordinal),
            )
            val events = listOf(
                LamiFirstAudioTiming.requestStartedEvent(request),
                LamiFirstAudioTiming.preparedEvent(request, request.startedAtNs + 999_999L),
                LamiFirstAudioTiming.playbackStartedEvent(request, request.startedAtNs + 1_000_001L),
            ).map(::eventFields)
            assertEquals(request.startedAtNs.toString(), events.first()["first_audio_at_ns"])
            for (fields in events) {
                val elapsedNs = fields.getValue("first_audio_at_ns").toLong() -
                    fields.getValue("first_audio_request_started_at_ns").toLong()
                assertTrue(elapsedNs >= 0L)
                assertEquals(elapsedNs.toString(), fields["first_audio_elapsed_ns"])
                assertEquals((elapsedNs / 1_000_000L).toString(), fields["first_audio_elapsed_ms"])
            }
        }
    }

    @Test fun diagnosticProbesRecordPreparationBeforePostSynthesisWork() {
        val source = java.io.File(
            "src/debug/java/io/github/ninbyo02/lami/tts/LamiNeuralVoiceDiagnostics.kt",
        ).readText()
        val single = source.substringAfter("suspend fun runProbe(")
            .substringBefore("suspend fun runPipelineProbe(")
        val pipeline = source.substringAfter("suspend fun runPipelineProbe(")
            .substringBefore("suspend fun runStreamingProbe(")
        val prepared = "LamiFirstAudioTiming.preparedEvent(firstAudio, SystemClock.elapsedRealtimeNanos())"
        for ((probe, postSynthesisWork) in listOf(
            single to "report.appendText(\"synthesis=success",
            pipeline to "val pcmBytes =",
        )) {
            assertEquals(1, probe.windowed(prepared.length).count { it == prepared })
            assertTrue(probe.indexOf(prepared) < probe.indexOf(postSynthesisWork))
            assertTrue(probe.indexOf(prepared) < probe.indexOf("LamiPcmPlayer.play("))
        }
    }

    @Test fun chatEngineStartsTimingBeforeOptionalDiagnosticSetup() {
        val source = java.io.File(
            "src/debug/java/io/github/ninbyo02/lami/tts/LamiNeuralChatVoiceEngine.kt",
        ).readText()
        val preparation = source.substringAfter("private suspend fun prepareUtterance(text: String): PreparedClip? {")
            .substringBefore("private suspend fun playClip(")
        // A slow trace factory must remain inside the measured preparation interval.
        assertTrue(Regex(
            "^\\s*(?://[^\\n]*\\n\\s*)?val started = SystemClock\\.elapsedRealtimeNanos\\(\\)",
        ).containsMatchIn(preparation))
        assertTrue(preparation.indexOf("val started =") < preparation.indexOf("LamiSpeechTraceGuard.create"))
        assertTrue(preparation.contains("startedAtNs = started,"))
    }

    @Test fun chatEngineCapturesMonotonicEventsAtSynthesisAndPlaybackBoundaries() {
        val source = java.io.File(
            "src/debug/java/io/github/ninbyo02/lami/tts/LamiNeuralChatVoiceEngine.kt",
        ).readText()
        val preparation = source.substringAfter("private suspend fun prepareUtterance(")
            .substringBefore("private suspend fun playClip(")
        // Capture PCM readiness before logging, trace capture, or queue handoff can add delay.
        assertTrue(Regex(
            "val pcm = LamiVoiceDiagnostic\\.synthesizeText[^\\n]+\\n\\s*" +
                "trace\\(firstAudio, LamiFirstAudioTiming\\.preparedEvent\\(firstAudio, " +
                "SystemClock\\.elapsedRealtimeNanos\\(\\)\\)\\)",
        ).containsMatchIn(preparation))
        val playback = source.substringAfter("private suspend fun playClip(")
            .substringBefore("override fun isInCooldown(")
        // The first callback action must sample the clock, before optional trace capture or logging.
        assertTrue(Regex(
            "LamiPcmPlayer\\.play\\(clip\\.pcm\\)\\s*\\{\\s*trace\\(\\s*" +
                "clip\\.firstAudio,\\s*LamiFirstAudioTiming\\.playbackStartedEvent\\(\\s*" +
                "clip\\.firstAudio,\\s*SystemClock\\.elapsedRealtimeNanos\\(\\)",
        ).containsMatchIn(playback))
    }

    private fun eventFields(event: String): Map<String, String> =
        event.split(" ")
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0] to parts[1]
            }
}
