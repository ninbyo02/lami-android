package io.github.ninbyo02.lami.tts

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.io.File

/**
 * Explicit, DUMP-permission-gated isolated debug activity.
 * Starts only on request; port and secret live only in private app files.
 * Never registered in the normal release APK.
 */
class ResidentCpTransportProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG ||
            BuildConfig.APPLICATION_ID != "io.github.ninbyo02.lami.cpdiagnostic") {
            finish()
            return
        }
        Thread({
            val report = try {
                val skeleton = File(filesDir, "libQnnHtpV79Skel.so")
                require(skeleton.length() == 11_491_912L &&
                    ResidentCpSingleStepFixture.sha256(skeleton) ==
                    ResidentCpSingleStepFixture.SKELETON_SHA256)
                Os.setenv("ADSP_LIBRARY_PATH",
                    filesDir.absolutePath + ";/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp",
                    true)
                val spec = ResidentCpContextSpec(
                    ResidentCpSingleStepFixture.CONTEXT_SHA256, 1024, 16, 32,
                    "qairt247-fp32-sm8750")
                ResidentCpRustApplicationApi.create(applicationContext, spec).use { api ->
                    val handle = api.loadContext(File(filesDir, "mtp-qnn-context.bin").absolutePath)
                    try {
                        ResidentCpLoopbackEndpoint(
                            { secret -> ResidentCpAuthenticatedChannel(
                                secret, ResidentCpWireExecutor(api, handle)) },
                            60_000).use { endpoint ->
                            File(filesDir, "resident-cp-transport-port.txt")
                                .writeText(endpoint.port.toString())
                            endpoint.serveOnce { secret ->
                                File(filesDir, "resident-cp-transport-secret.bin").writeBytes(secret)
                            }
                        }
                        "status=ENDED"
                    } finally {
                        api.release(handle)
                    }
                }
            } catch (error: Throwable) {
                Log.e("ResidentCpTransport", "FAILED", error)
                "status=FAILED;error=${error.javaClass.simpleName}"
            } finally {
                File(filesDir, "resident-cp-transport-secret.bin").delete()
                File(filesDir, "resident-cp-transport-port.txt").delete()
            }
            File(filesDir, "resident-cp-transport-receipt.txt").writeText(report)
            Handler(Looper.getMainLooper()).post { finish() }
        }, "resident-cp-transport").start()
    }
}
