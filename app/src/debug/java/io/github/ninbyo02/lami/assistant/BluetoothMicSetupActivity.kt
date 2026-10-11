package io.github.ninbyo02.lami.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Spinner
import android.widget.TextView

class BluetoothMicSetupActivity : Activity() {
    private lateinit var probe: BluetoothMicProbe
    private lateinit var status: TextView
    private lateinit var record: Button
    private lateinit var preconnect: Button
    private lateinit var repeatRecord: Button
    private lateinit var conversation: Button
    private lateinit var play: Button
    private lateinit var adjustVolume: Switch
    private lateinit var reduceSwitching: Switch
    private lateinit var inputType: Spinner
    private lateinit var cutMilliseconds: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!packageName.endsWith(".assistantprobe")) {
            finish()
            return
        }
        val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        window.decorView.windowInsetsController?.setSystemBarsAppearance(
            if (light) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        val space = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space, space * 2, space, space)
        }
        layout.addView(TextView(this).apply { text = "Bluetoothマイク確認"; textSize = 24f })
        layout.addView(TextView(this).apply { text = "Pioneer SE-MS7BT"; textSize = 16f })
        status = TextView(this).apply {
            text = "停止中"
            textSize = 16f
            setPadding(0, space, 0, space)
        }
        status.minimumHeight = space * 5
        layout.addView(status, LinearLayout.LayoutParams(-1, -2))
        conversation = Button(this).apply {
            text = "会話接続を開始 (60秒・試験)"
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_btn_speak_now, 0, 0, 0)
            setOnClickListener {
                cutMilliseconds.setText("0")
                reduceSwitching.isChecked = false
                this@BluetoothMicSetupActivity.inputType.setSelection(0)
                probe.beginConversation()
            }
        }
        layout.addView(conversation)
        layout.addView(TextView(this).apply { text = "マイク入力"; textSize = 16f })
        inputType = Spinner(this).apply {
            adapter = ArrayAdapter(this@BluetoothMicSetupActivity,
                android.R.layout.simple_spinner_dropdown_item, BluetoothMicInput.entries.map { it.label })
        }
        layout.addView(inputType, LinearLayout.LayoutParams(-1, space * 2))
        reduceSwitching = Switch(this).apply {
            text = "接続切り替えを減らす (試験)"
            textSize = 16f
            setPadding(0, space / 2, 0, space / 2)
        }
        layout.addView(reduceSwitching)
        preconnect = Button(this).apply {
            text = "通話接続を先に作る (10秒・試験)"
            setOnClickListener { probe.preconnect(reduceSwitching.isChecked) }
        }
        layout.addView(preconnect)
        val preferences = getSharedPreferences("bluetooth_mic_probe", MODE_PRIVATE)
        cutMilliseconds = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            textSize = 18f
            setText(preferences.getInt("cut_ms", 0).takeIf { it in 0..BluetoothMicLeadIn.MAX_MS }?.toString() ?: "0")
            contentDescription = "先頭カット (ms)"
        }
        layout.addView(TextView(this).apply {
            text = "先頭カット (ms): 0〜${BluetoothMicLeadIn.MAX_MS}"
            textSize = 16f
            labelFor = cutMilliseconds.id
        })
        layout.addView(cutMilliseconds, LinearLayout.LayoutParams(-1, space * 2))
        record = Button(this).apply {
            text = "録音 (5秒)"
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_btn_speak_now, 0, 0, 0)
            setOnClickListener {
                startRecording()
            }
        }
        layout.addView(record)
        repeatRecord = Button(this).apply {
            text = "同じ接続で3回録音 (試験)"
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_btn_speak_now, 0, 0, 0)
            setOnClickListener {
                cutMilliseconds.setText("0")
                reduceSwitching.isChecked = false
                this@BluetoothMicSetupActivity.inputType.setSelection(0)
                startRecording(3)
            }
        }
        layout.addView(repeatRecord)
        adjustVolume = Switch(this).apply {
            text = "再生音量を補正"
            textSize = 16f
            setPadding(0, space / 2, 0, space / 2)
        }
        layout.addView(adjustVolume)
        play = Button(this).apply {
            text = "録音を再生"
            isEnabled = false
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_media_play, 0, 0, 0)
            setOnClickListener { probe.play(adjustVolume.isChecked) }
        }
        layout.addView(play)
        layout.addView(Button(this).apply {
            text = "停止・録音を破棄"
            setOnClickListener { probe.cancel() }
        })
        layout.addView(Button(this).apply {
            text = "待機画面に戻る"
            setOnClickListener { finish() }
        })
        probe = BluetoothMicProbe(this) { value, busy, available ->
            if (!isDestroyed) {
                status.text = value
                val connected = probe.isConversationActive
                conversation.isEnabled = !busy && !connected
                record.isEnabled = !busy
                preconnect.isEnabled = !busy && !connected
                repeatRecord.isEnabled = !busy && !connected
                play.isEnabled = !busy && available
                play.text = if (connected) "通話接続で再生" else "録音を再生"
                adjustVolume.isEnabled = !busy
                reduceSwitching.isEnabled = !busy && !connected
                inputType.isEnabled = !busy && !connected
                cutMilliseconds.isEnabled = !busy && !connected
                if (busy || connected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun startRecording(repetitions: Int = 1) {
        val cut = BluetoothMicLeadIn.parse(cutMilliseconds.text.toString())
        if (cut == null) {
            cutMilliseconds.error = "0〜${BluetoothMicLeadIn.MAX_MS}の整数を入力してください"
            cutMilliseconds.requestFocus()
            return
        }
        cutMilliseconds.error = null
        cutMilliseconds.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(cutMilliseconds.windowToken, 0)
        getSharedPreferences("bluetooth_mic_probe", MODE_PRIVATE).edit().putInt("cut_ms", cut).apply()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            probe.record(reduceSwitching.isChecked, BluetoothMicInput.entries[inputType.selectedItemPosition], cut, repetitions)
        } else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 61)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 61) status.text =
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "録音の準備完了"
            else "マイクが許可されていません"
    }

    override fun onPause() {
        if (::probe.isInitialized) probe.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }
}
