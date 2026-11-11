package io.github.ninbyo02.lamigeniexprobe

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProbeActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) install(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inset = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(inset, inset, inset, inset)
        }
        layout.addView(TextView(this).apply {
            text = getString(R.string.geniex_model_title)
            textSize = 24f
        })
        status = TextView(this).apply { setPadding(0, inset, 0, inset) }
        layout.addView(status)
        importButton = Button(this).apply {
            text = getString(R.string.geniex_choose_model)
            setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        }
        layout.addView(importButton)
        setContentView(layout)
        showModelStatus()
    }

    private fun showModelStatus() {
        status.text = if (Lfm3ModelPolicy.findModel(this) != null) {
            getString(R.string.geniex_model_ready)
        } else {
            getString(R.string.geniex_model_missing, Lfm3ModelPolicy.REFERENCE_NAME)
        }
    }

    private fun install(uri: Uri) {
        importButton.isEnabled = false
        status.text = getString(R.string.geniex_model_importing)
        activityScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val input = checkNotNull(contentResolver.openInputStream(uri))
                    input.use { Lfm3ModelPolicy.importModel(applicationContext, it) }
                }
                showModelStatus()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status.text = getString(R.string.geniex_model_import_failed, Lfm3ModelPolicy.REFERENCE_NAME)
            } finally {
                importButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}
