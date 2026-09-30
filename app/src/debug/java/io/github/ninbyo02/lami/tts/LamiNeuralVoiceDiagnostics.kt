package io.github.ninbyo02.lami.tts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal object LamiNeuralVoiceDiagnostics {
    fun start(activity: ComponentActivity, savedInstanceState: Bundle?) {
        // Debug-only, explicit request. Never block activity startup with model inference.
        if (savedInstanceState == null && activity.intent.getBooleanExtra("lami_neural_tts_hai_probe", false)) {
            val root = activity.applicationContext.filesDir.resolve("local_models/lami_tts/prepared_hai")
            val report = activity.applicationContext.filesDir.resolve("neural_tts_hai_probe.txt")
            activity.lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    report.writeText("phrase=はい。 status=started\n")
                    val started = android.os.SystemClock.elapsedRealtime()
                    try {
                        withTimeout(600_000L) {
                            val pcm = LamiVoiceDiagnostic.synthesize(root)
                            report.appendText("synthesis=success samples=${pcm.size} elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}\n")
                            LamiPcmPlayer.play(pcm) { report.appendText("playback=started\n") }
                            report.appendText("playback=complete\n")
                        }
                    } catch (cancelled: CancellationException) {
                        report.appendText("status=cancelled\n")
                        throw cancelled
                    } catch (failure: Exception) {
                        report.appendText("status=failure class=${failure.javaClass.name} message=${failure.message}\n")
                    }
                }
            }
        }
    }
}
