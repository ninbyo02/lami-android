package io.github.ninbyo02.lami.assistant

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Explicit opt-in UI for the debug-only USB button bridge. */
class JabraUsbSetupActivity : Activity() {
    private val usbManager by lazy { getSystemService(UsbManager::class.java) }
    private lateinit var status: TextView
    private lateinit var cpuTrial: Switch
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private var device: UsbDevice? = null
    private var permissionPending = false
    private var receiverRegistered = false
    private val handler = Handler(android.os.Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (!isDestroyed) {
                updateStatus(if (permissionPending) "Waiting for USB permission"
                    else JabraUsbButtonService.statusText)
                handler.postDelayed(this, 1000)
            }
        }
    }
    private val permissionAction get() = "$packageName.JABRA_USB_PERMISSION"
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val target = device ?: return
            permissionPending = false
            if (usbManager.hasPermission(target)) startButtons()
            else updateStatus("USB permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!packageName.endsWith(".assistantprobe")) {
            finish()
            return
        }
        val spacing = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(spacing, spacing, spacing, spacing)
        }
        layout.addView(ImageView(this).apply {
            setImageDrawable(packageManager.getApplicationIcon(applicationInfo))
            contentDescription = "LAMI"
        }, LinearLayout.LayoutParams(spacing * 3, spacing * 3))
        layout.addView(TextView(this).apply {
            text = "Jabra USB"
            textSize = 24f
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, spacing, 0, spacing)
        }
        layout.addView(status)
        cpuTrial = Switch(this).apply {
            text = "5-minute CPU trial"
            isChecked = false
        }
        layout.addView(cpuTrial)
        connectButton = Button(this).apply {
            text = "ボタン待機を開始"
            setOnClickListener { connect() }
        }
        layout.addView(connectButton)
        disconnectButton = Button(this).apply {
            text = "ボタン待機を停止"
            setOnClickListener {
                startService(Intent(this@JabraUsbSetupActivity, JabraUsbButtonService::class.java)
                    .setAction(JabraUsbButtonService.ACTION_STOP))
                updateStatus("Stopping")
            }
        }
        layout.addView(disconnectButton)
        setContentView(layout)
        updateStatus(JabraUsbButtonService.statusText)
        registerReceiver(permissionReceiver, IntentFilter(permissionAction), RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun connect() {
        val target = usbManager.deviceList.values.firstOrNull {
            isJabraAssistDevice(it.vendorId, it.productId)
        } ?: run {
            updateStatus("Jabra not connected")
            return
        }
        device = target
        if (usbManager.hasPermission(target)) startButtons()
        else {
            permissionPending = true
            updateStatus("Waiting for USB permission")
            usbManager.requestPermission(
                target,
                PendingIntent.getBroadcast(this, 1,
                    Intent(permissionAction).setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
    }

    private fun startButtons() {
        startForegroundService(Intent(this, JabraUsbButtonService::class.java)
            .putExtra(JabraUsbButtonService.EXTRA_CPU_TRIAL, cpuTrial.isChecked))
        updateStatus("Connecting")
    }

    private fun updateStatus(value: String) {
        val target = usbManager.deviceList.values.firstOrNull {
            isJabraAssistDevice(it.vendorId, it.productId)
        }
        val usbStatus = if (target == null) "USB未接続" else
            "USB接続済み：${target.productName ?: "Jabra"}"
        val buttonStatus = when (value) {
            "Connected" -> "ボタン待機中"
            "Connected (5 min)" -> "ボタン待機中（5分間の試験）"
            "Connecting" -> "ボタン待機を開始しています"
            "Stopping" -> "ボタン待機を停止しています"
            "Waiting for USB permission" -> "USB利用許可を待っています"
            "USB permission denied" -> "USB利用が許可されませんでした"
            "Jabra not connected", "Disconnected" -> "ボタン待機は停止中"
            "Trial ended" -> "試験終了・ボタン待機は停止中"
            else -> value
        }
        val centerStatus = if (value.startsWith("Connected")) {
            if (JabraUsbButtonService.centerMusicReserved) "\n中央ボタン：音楽操作"
            else "\n中央ボタン：LAMI起動"
        } else ""
        status.text = "$usbStatus\n$buttonStatus$centerStatus"
        val waiting = value.startsWith("Connected") || value == "Connecting" || value == "Stopping"
        connectButton.isEnabled = target != null && !waiting && !permissionPending
        disconnectButton.isEnabled = waiting
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        if (receiverRegistered) unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }
}
