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
class ResidentCpSingleStepProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG || BuildConfig.APPLICATION_ID != "io.github.ninbyo02.lami.cpdiagnostic") {
            Log.e("ResidentCpSingleStep", "BLOCKED wrong build or package")
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
                Log.i("ResidentCpSingleStep", result.lineSequence().first())
                runOnUiThread { finish() }
            }
        }, "resident-cp-one-step").start()
    }

    private fun writeReceipt(result: String) {
        try {
            openFileOutput("resident-cp-single-step-probe.txt", MODE_PRIVATE).bufferedWriter().use {
                it.write(result)
            }
        } catch (failure: Exception) {
            Log.e("ResidentCpSingleStep", "RECEIPT_WRITE_FAILED", failure)
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
        val golden = ResidentCpSingleStepFixture.readPinnedRow(
            File(filesDir, "resident-cp-golden-0.f32"),
            ResidentCpSingleStepFixture.GOLDEN_SHA256
        )
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
                val startedStep = System.nanoTime()
                api.execute(handle, 0, input, output)
                val stepNs = System.nanoTime() - startedStep
                check(output.all(Float::isFinite)) { "Non-finite result from native CP" }

                val exact = ResidentCpSingleStepFixture.bitEqual(output, golden)
                val stats = api.stats(handle)
                return "status=STEP_RETURNED\ngolden_bit_equal=$exact\n" +
                    "context_load_ns=$loadNs\nkv_reset_ns=$resetNs\n" +
                    "one_step_ns=$stepNs\nnative_stats=${stats.joinToString(",")}\n" +
                    "audio_played=false\n"
            } finally {
                api.release(handle)
            }
        }
    }
}
