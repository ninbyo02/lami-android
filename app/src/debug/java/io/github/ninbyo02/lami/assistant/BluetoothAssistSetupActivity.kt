package io.github.ninbyo02.lami.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class BluetoothAssistSetupActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private lateinit var duration: Spinner
    private var updating = false
    private var notificationDenied = false
    private val preferences by lazy { getSharedPreferences("bluetooth_standby", MODE_PRIVATE) }
    private val refresh = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

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
        layout.addView(ImageView(this).apply {
            setImageDrawable(packageManager.getApplicationIcon(applicationInfo))
            contentDescription = "LAMI"
        }, LinearLayout.LayoutParams(space * 3, space * 3).apply { gravity = Gravity.CENTER_HORIZONTAL })
        layout.addView(TextView(this).apply {
            text = "Bluetooth待機"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, space, 0, space)
        })
        layout.addView(TextView(this).apply {
            text = "Pioneer SE-MS7BT"
            textSize = 16f
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, space, 0, space)
        }
        status.minimumHeight = space * 4
        layout.addView(status, LinearLayout.LayoutParams(-1, -2))
        layout.addView(TextView(this).apply { text = "待機時間"; textSize = 16f })
        duration = Spinner(this).apply {
            adapter = ArrayAdapter(this@BluetoothAssistSetupActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("5分", "30分", "1時間"))
            val selected = preferences.getInt("minutes", BluetoothStandbyWindow.DEFAULT_MINUTES)
            setSelection(BluetoothStandbyWindow.durations.indexOf(selected).takeIf { it >= 0 } ?: 1)
        }
        layout.addView(duration, LinearLayout.LayoutParams(-1, space * 2))
        toggle = Switch(this).apply {
            text = "Bluetooth待機"
            textSize = 18f
            setPadding(0, space, 0, space)
            setOnCheckedChangeListener { _, checked ->
                if (!updating) {
                    if (checked) {
                        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 51)
                            update()
                        } else startStandby()
                    } else stopService(Intent(this@BluetoothAssistSetupActivity,
                        BluetoothAssistStandbyService::class.java))
                }
            }
        }
        layout.addView(toggle)
        layout.addView(Button(this).apply {
            text = "マイク確認"
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_btn_speak_now, 0, 0, 0)
            setOnClickListener {
                stopService(Intent(this@BluetoothAssistSetupActivity, BluetoothAssistStandbyService::class.java))
                startActivity(Intent(this@BluetoothAssistSetupActivity, BluetoothMicSetupActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            text = "LAMIに戻る"
            setOnClickListener { AssistantLauncher.launchAssist(this@BluetoothAssistSetupActivity) }
        })
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun startStandby() {
        notificationDenied = false
        val minutes = BluetoothStandbyWindow.durations[duration.selectedItemPosition]
        preferences.edit().putInt("minutes", minutes).apply()
        startForegroundService(Intent(this, BluetoothAssistStandbyService::class.java)
            .setAction(BluetoothAssistStandbyService.ACTION_START)
            .putExtra(BluetoothAssistStandbyService.EXTRA_MINUTES, minutes))
    }

    private fun update() {
        updating = true
        toggle.isChecked = BluetoothAssistStandbyService.standbyRunning
        duration.isEnabled = !BluetoothAssistStandbyService.standbyRunning
        val seconds = BluetoothAssistStandbyService.remainingMillis / 1000
        status.text = (if (notificationDenied && !BluetoothAssistStandbyService.standbyRunning)
            "待機通知が許可されていません" else BluetoothAssistStandbyService.statusText) +
            if (BluetoothAssistStandbyService.standbyRunning) "\n残り ${seconds / 60}分${seconds % 60}秒" else ""
        updating = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 51) {
            notificationDenied = grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
            if (!notificationDenied) startStandby()
            update()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
