package io.github.ninbyo02.lami.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.io.FileDescriptor
import java.io.PrintWriter

class JabraAssistKeyService : AccessibilityService() {
    private val policy = JabraAssistKeyPolicy()
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var previousKeyDowns = 0
    private var assistLaunches = 0
    private var lastEligible = false
    private var lastMusicActive = false

    override fun onServiceConnected() {
        Log.i("LamiAssistantButton", "Jabra key filter connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val device = event.device ?: return false
        if (!device.isExternal || !isJabraAssistDevice(device.vendorId, device.productId)) return false

        val musicActive = audioManager?.isMusicActive != false
        val assistant = Settings.Secure.getString(contentResolver, "assistant")
            ?.let(ComponentName::unflattenFromString)
        val eligible = packageName.endsWith(".assistantprobe") &&
            assistant == ComponentName(this, AssistantEntryActivity::class.java) &&
            !musicActive && audioManager?.mode == AudioManager.MODE_NORMAL
        lastEligible = eligible
        lastMusicActive = musicActive
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS &&
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) previousKeyDowns++
        val result = policy.handle(
            event.keyCode, event.action, event.repeatCount, event.deviceId,
            event.downTime, event.eventTime, event.isCanceled, eligible,
        )
        Log.i("LamiAssistantButton", "jabraKey=${event.keyCode} action=${event.action} " +
            "musicActive=$musicActive eligible=$eligible consume=${result.consume} launch=${result.launch}")
        if (result.launch) {
            assistLaunches++
            AssistantLauncher.launchAssist(this)
        }
        return result.consume
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println("JabraAssist previousKeyDowns=$previousKeyDowns assistLaunches=$assistLaunches " +
            "lastEligible=$lastEligible lastMusicActive=$lastMusicActive")
    }

    override fun onInterrupt() = policy.reset()

    override fun onDestroy() {
        policy.reset()
        super.onDestroy()
    }
}
