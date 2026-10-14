package io.github.ninbyo02.lami.tts

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Explicit-only, silent one-position QNN diagnostic in its own debug process.
 * No normal TTS routing, speech, model mutation, or automatic execution.
 */
class ResidentCpSixteenStepProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG || BuildConfig.APPLICATION_ID != "io.github.ninbyo02.lami.cpdiagnostic") {
            Log.e("ResidentCpSixteenStep", "BLOCKED wrong build or package")
            finish()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        writeReceipt("status=STARTED\naudio_played=false\n")
        val deadline = Runnable {
            if (finished.compareAndSet(false, true)) {
                writeReceipt("status=TIMEOUT\naudio_played=false\n")
                finish()
            }
        }
        handler.postDelayed(deadline, 120_000L)
        Thread({
            val result = try { executePinnedOneStep() } catch (failure: Throwable) {
                "status=FAILED\naudio_played=false\nerror=${failure.javaClass.simpleName}\n" +
                    "detail=${(failure.message ?: "").take(160).replace('\n', ' ')}\n"
            }
            if (finished.compareAndSet(false, true)) {
                handler.removeCallbacks(deadline)
                writeReceipt(result)
                Log.i("ResidentCpSixteenStep", result.lineSequence().first())
                runOnUiThread { finish() }
            }
        }, "resident-cp-one-step").start()
    }

    private fun writeReceipt(result: String) {
        try {
            openFileOutput("resident-cp-sixteen-step-probe.txt", MODE_PRIVATE).bufferedWriter().use {
                it.write(result)
            }
        } catch (failure: Exception) {
            Log.e("ResidentCpSixteenStep", "RECEIPT_WRITE_FAILED", failure)
        }
    }

    private fun executePinnedOneStep(): String {
        val skeleton = File(filesDir, "libQnnHtpV79Skel.so")
        require(skeleton.isFile && skeleton.length() == 11_491_912L &&
            ResidentCpSingleStepFixture.sha256(skeleton) == ResidentCpSingleStepFixture.SKELETON_SHA256) {
            "Pinned DSP skeleton absent or mismatched"
        }
        val input = ResidentCpSingleStepFixture.readPinnedRow(
            File(filesDir, "resident-cp-input-0.f32"),
            ResidentCpSingleStepFixture.INPUT_SHA256
        )
        // Optional explicit per-position rows, never accepted from an untrusted public intent.
        // Without the pinned multi-row fixture, retain the verified fixed-input smoke test.
        val rowsFile = File(filesDir, "resident-cp-16-embeddings.f32")
        val inputRows = if (rowsFile.isFile) {
            val expectedSha = File(filesDir, "resident-cp-16-embeddings.sha256")
            require(expectedSha.isFile) { "Missing 16-row fixture hash" }
            val expected = expectedSha.readText().trim()
            require(expected.matches(Regex("[0-9a-f]{64}")) &&
                rowsFile.length() == 16L * 1024L * 4L &&
                ResidentCpSingleStepFixture.sha256(rowsFile) == expected) {
                "16-row fixture hash or size mismatch"
            }
            ByteBuffer.wrap(rowsFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer().let { buffer ->
                    Array(16) { FloatArray(1024).also(buffer::get) }
                }.also { rows -> require(rows.all { row -> row.all(Float::isFinite) }) }
        } else {
            Array(16) { input.copyOf() }
        }
        val context = File(filesDir, "mtp-qnn-context.bin")
        require(context.isFile) { "Missing pinned QNN context" }
        val spec = ResidentCpContextSpec(
            ResidentCpSingleStepFixture.CONTEXT_SHA256,
            1024, 16, 32, "qairt247-fp32-sm8750"
        )
        // Process-local DSP search path; vendor files and normal LAMI remain untouched.
        Os.setenv(
            "ADSP_LIBRARY_PATH",
            filesDir.absolutePath + ";/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp",
            true
        )
        val output = FloatArray(1024)
        val startedLoad = System.nanoTime()
        ResidentCpRustApplicationApi.create(applicationContext, spec).use { api ->
            val handle = api.loadContext(context.absolutePath)
            try {
                val loadNs = System.nanoTime() - startedLoad
                val startedReset = System.nanoTime()
                api.resetKv(handle)
                val resetNs = System.nanoTime() - startedReset
                val stepDurations = LongArray(16)
                val selectionDurations = LongArray(15)
                val loopStarted = System.nanoTime()
                val headFiles = (0 until 15).map { File(filesDir, "resident-cp-head-$it.f32") }
                val embFiles = (0 until 15).map { File(filesDir, "resident-cp-embedding-$it.f32") }
                val feedbackEnabled = headFiles.any(File::exists) || embFiles.any(File::exists)
                val allTables = (headFiles + embFiles).all(File::isFile)
                require(!feedbackEnabled || (allTables && rowsFile.isFile)) {
                    "Full feedback requires 15 head/embedding pairs and pinned input rows"
                }
                val verifiedTables = if (feedbackEnabled) ResidentCpGreedyFeedback.verifyTables(filesDir)
                    else emptyList()
                val feedbackCodes = mutableListOf<Int>()
                val sharedDraw = ResidentCpGreedyFeedback.SharedDraw()
                for (position in 0 until 16) {
                    val startedStep = System.nanoTime()
                    api.execute(handle, position, inputRows[position], output)
                    stepDurations[position] = System.nanoTime() - startedStep
                    if (position in 1..15 && feedbackEnabled) {
                        val headIndex = position - 1
                        val selectionStarted = System.nanoTime()
                        val (code, selectedEmbedding) = ResidentCpGreedyFeedback.selectVerified(
                            verifiedTables[headIndex].first, verifiedTables[headIndex].second,
                            output, sharedDraw.draw()
                        )
                        feedbackCodes += code
                        if (position < 15) inputRows[position + 1] = selectedEmbedding
                        selectionDurations[headIndex] = System.nanoTime() - selectionStarted
                    }
                }
                val totalLoopNs = System.nanoTime() - loopStarted
                val totalStepNs = stepDurations.sum()
                val totalSelectionNs = selectionDurations.sum()
                check(output.all(Float::isFinite)) { "Non-finite result from native CP" }

                // A position-zero reference must never be compared to position 15.
                val stats = api.stats(handle)
                check(stats.size == 7 && stats[1] == 16L) { "Expected exactly sixteen native executions" }
                val encoded = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
                output.forEach(encoded::putFloat)
                val outputHash = ResidentCpSingleStepFixture.sha256(encoded.array())
                return "status=SIXTEEN_STEPS_RETURNED\npositions_completed=16\n" +
                    "greedy_feedback_enabled=$feedbackEnabled\ncp_codes_complete=${feedbackCodes.size == 15}\ntopk_feedback_codes=${feedbackCodes.joinToString(",")}\n" +
                    "step_durations_ns=${stepDurations.joinToString(",")}\n" +
                    "total_step_ns=$totalStepNs\ntotal_selection_ns=$totalSelectionNs\n" +
                    "total_loop_ns=$totalLoopNs\n" +
                    "context_load_ns=$loadNs\nkv_reset_ns=$resetNs\n" +
                    "last_output_sha256=$outputHash\nnative_stats=${stats.joinToString(",")}\n" +
                    "reference_checked=false\naudio_played=false\n"
            } finally {
                api.release(handle)
            }
        }
    }
}
