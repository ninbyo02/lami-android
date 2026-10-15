package io.github.ninbyo02.lami.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Opt-in capture with caller-supplied monotonic milliseconds. No hardware emulation. */
internal class LamiSpeechTraceCapture(
    private val directory: File, private val requestId: String, text: String,
    private val modelSha256: String, private val runtimeSha256: String,
    private val clockMs: () -> Long,
) {
    private val start = clockMs()
    private val inputHash = sha(text.toByteArray(Charsets.UTF_8))
    private val events = mutableListOf<Pair<String, Long>>("request_started" to 0L)
    private var ended = false

    init {
        require(requestId.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        require(modelSha256.matches(Regex("[0-9a-f]{64}")))
        require(runtimeSha256.matches(Regex("[0-9a-f]{64}")))
        require(!directory.exists()) { "Capture directory already exists" }
        require(directory.mkdirs()) { "Cannot create capture directory" }
        val metadata = "request_id=" + requestId + "\ninput_sha256=" + inputHash +
            "\nmodel_sha256=" + modelSha256 + "\nruntime_sha256=" + runtimeSha256 + "\n"
        File(directory, "request.properties").writeText(metadata)
        journal("request_started", 0L)
    }

    @Synchronized fun event(stage: String) {
        check(!ended)
        require(stage in setOf("synthesis_started", "pcm_ready", "playback_started"))
        val stages = listOf("request_started", "synthesis_started", "pcm_ready", "playback_started")
        require(stages.indexOf(stage) == stages.indexOf(events.last().first) + 1)
        val elapsed = clockMs() - start
        require(elapsed >= events.last().second)
        journal(stage, elapsed)
        events += stage to elapsed
    }

    @Synchronized fun success(pcm: FloatArray) {
        check(!ended)
        require(events.any { it.first == "pcm_ready" })
        require(pcm.isNotEmpty() && pcm.size <= MAX_PCM_BYTES / 4)
        require(pcm.all { it.isFinite() })
        val buffer = ByteBuffer.allocate(Math.multiplyExact(pcm.size, 4)).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { buffer.putFloat(it) }
        finish("success", null, buffer.array(), pcm.size)
    }

    @Synchronized fun failure(stage: String) {
        check(!ended)
        require(stage in setOf("load", "synthesis", "playback", "cancelled"))
        finish("failure", stage, null, null)
    }

    private fun journal(stage: String, elapsed: Long) {
        val line = "{\"stage\":\"" + stage + "\",\"elapsed_ms\":" + elapsed + "}\n"
        File(directory, "events.jsonl").appendText(line)
    }

    private fun finish(status: String, failureStage: String?, bytes: ByteArray?, samples: Int?) {
        val elapsed = clockMs() - start
        require(elapsed >= events.last().second)
        if (bytes != null) File(directory, "output.f32").writeBytes(bytes)
        val eventJson = (events + ("request_ended" to elapsed)).joinToString(",") { (stage, ms) ->
            "{\"stage\":\"" + stage + "\",\"elapsed_ms\":" + ms + "}"
        }
        val pcmJson = if (bytes == null) "null" else
            "{\"path\":\"output.f32\",\"format\":\"float32le\",\"sample_rate\":24000,\"samples\":" + samples + ",\"sha256\":\"" + sha(bytes) + "\"}"
        val failureJson = failureStage?.let { "\"" + it + "\"" } ?: "null"
        val json = "{\"schema_version\":1,\"request_id\":\"" + requestId + "\",\"input_sha256\":\"" + inputHash +
            "\",\"model_sha256\":\"" + modelSha256 + "\",\"runtime_sha256\":\"" + runtimeSha256 +
            "\",\"status\":\"" + status + "\",\"failure_stage\":" + failureJson +
            ",\"events\":[" + eventJson + "],\"pcm\":" + pcmJson + "}"
        require(json.toByteArray(Charsets.UTF_8).size +
            File(directory, "events.jsonl").length() + File(directory, "request.properties").length() < 16 * 1024)
        val temp = File(directory, "trace.json.tmp")
        temp.writeText(json + "\n")
        check(temp.renameTo(File(directory, "trace.json")))
        ended = true
        journal("request_ended", elapsed)
    }

    companion object {
        const val MAX_PCM_BYTES = 4 * 1024 * 1024
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
