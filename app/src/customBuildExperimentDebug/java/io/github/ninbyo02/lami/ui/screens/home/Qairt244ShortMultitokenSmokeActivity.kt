package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import io.github.ninbyo02.lami.BuildConfig

class Qairt244ShortMultitokenSmokeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val resultFile = applicationContext.filesDir.resolve("qairt244_short_multitoken_smoke_result.txt")
        val result = runCatching {
            check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
                "short multi-token smoke Activity is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
            }
            check(intent?.getBooleanExtra("runShortMultitokenSmoke", false) == true) {
                "missing explicit runShortMultitokenSmoke=true"
            }
            Qairt244ShortMultitokenSmoke.run(
                context = applicationContext,
                modelPath = intent?.getStringExtra("model_path").orEmpty(),
                runId = intent?.getStringExtra("run_id").orEmpty(),
            )
        }.getOrElse { throwable ->
            "qairt244_short_multitoken_smoke_v1 kotlin failure class=${throwable.javaClass.name} message=${throwable.message ?: "-"}"
        }
        resultFile.appendText("kotlin_result=$result\n")
        finish()
    }
}
