package io.github.ninbyo02.lami.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.widget.Switch
import android.widget.Spinner
import android.media.MediaRecorder
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothSetupActivityTest {
    @Test
    fun standbyScreen_createsWindowAndShowsControlsWithoutStartingService() {
        val controller = Robolectric.buildActivity(BluetoothAssistSetupActivity::class.java).setup()
        val activity = controller.get()
        assertFalse(activity.isFinishing)
        val labels = labels(activity.window.decorView)
        assertTrue(labels.contains("Bluetooth待機"))
        assertTrue(labels.contains("マイク確認"))
        assertFalse(BluetoothAssistStandbyService.standbyRunning)
        controller.pause().stop().destroy()
    }

    @Test
    fun microphoneScreen_createsWindowWithoutRecordingOrRequestingPermission() {
        val controller = Robolectric.buildActivity(BluetoothMicSetupActivity::class.java).setup()
        val activity = controller.get()
        assertFalse(activity.isFinishing)
        val labels = labels(activity.window.decorView)
        assertTrue(labels.contains("停止中"))
        assertTrue(labels.contains("録音 (5秒)"))
        assertTrue(labels.contains("接続切り替えを減らす (試験)"))
        assertTrue(labels.contains("通話接続を先に作る (10秒・試験)"))
        assertTrue(labels.contains("同じ接続で3回録音 (試験)"))
        assertTrue(labels.contains("会話接続を開始 (60秒・試験)"))
        assertTrue(switches(activity.window.decorView).none { it.isChecked })
        assertEquals("通話用", spinner(activity.window.decorView)?.selectedItem)
        assertEquals("0", editText(activity.window.decorView)?.text.toString())
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        controller.pause().stop().destroy()
    }

    @Test
    fun microphoneInputChoices_areMicrophoneSourcesNotCallCapture() {
        assertEquals(listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION),
            BluetoothMicInput.entries.map { it.source })
    }

    @Test
    fun invalidCut_doesNotRecordOrRequestPermission() {
        val controller = Robolectric.buildActivity(BluetoothMicSetupActivity::class.java).setup()
        val activity = controller.get()
        val cut = editText(activity.window.decorView)!!
        listOf("", "3001").forEach {
            cut.setText(it)
            button(activity.window.decorView, "録音 (5秒)")!!.performClick()
            assertTrue(cut.error != null)
            assertTrue(labels(activity.window.decorView).contains("停止中"))
            assertEquals(null, shadowOf(activity).lastRequestedPermission)
        }
        controller.pause().stop().destroy()
    }

    @Test
    fun validCut_isRememberedWithoutAutoRecordingOnNextVisit() {
        val first = Robolectric.buildActivity(BluetoothMicSetupActivity::class.java).setup()
        editText(first.get().window.decorView)!!.setText("275")
        button(first.get().window.decorView, "録音 (5秒)")!!.performClick()
        first.pause().stop().destroy()
        val second = Robolectric.buildActivity(BluetoothMicSetupActivity::class.java).setup()
        assertEquals("275", editText(second.get().window.decorView)?.text.toString())
        assertTrue(labels(second.get().window.decorView).contains("停止中"))
        assertEquals(null, shadowOf(second.get()).lastRequestedPermission)
        second.pause().stop().destroy()
    }

    @Test
    fun repeatedTrial_usesUncutCommunicationInputAndRequiresPermission() {
        val controller = Robolectric.buildActivity(BluetoothMicSetupActivity::class.java).setup()
        val activity = controller.get()
        editText(activity.window.decorView)!!.setText("3000")
        spinner(activity.window.decorView)!!.setSelection(1)
        switches(activity.window.decorView).first { it.text.toString().startsWith("接続切り替え") }.isChecked = true
        button(activity.window.decorView, "同じ接続で3回録音 (試験)")!!.performClick()
        assertEquals("0", editText(activity.window.decorView)?.text.toString())
        assertEquals("通話用", spinner(activity.window.decorView)?.selectedItem)
        assertFalse(switches(activity.window.decorView).first { it.text.toString().startsWith("接続切り替え") }.isChecked)
        assertTrue(shadowOf(activity).lastRequestedPermission != null)
        assertTrue(labels(activity.window.decorView).contains("停止中"))
        controller.pause().stop().destroy()
    }

    @Test
    fun conversationTrial_withoutHeadsetDoesNotRecordOrRequestPermission() {
        val controller = Robolectric.buildActivity(BluetoothMicSetupActivity::class.java).setup()
        val activity = controller.get()
        editText(activity.window.decorView)!!.setText("3000")
        spinner(activity.window.decorView)!!.setSelection(1)
        switches(activity.window.decorView).first { it.text.toString().startsWith("接続切り替え") }.isChecked = true
        button(activity.window.decorView, "会話接続を開始 (60秒・試験)")!!.performClick()
        assertEquals("0", editText(activity.window.decorView)?.text.toString())
        assertEquals("通話用", spinner(activity.window.decorView)?.selectedItem)
        assertFalse(switches(activity.window.decorView).first { it.text.toString().startsWith("接続切り替え") }.isChecked)
        assertTrue(labels(activity.window.decorView).any { it.contains("Pioneerが接続されていません") })
        assertEquals(null, shadowOf(activity).lastRequestedPermission)
        assertFalse(button(activity.window.decorView, "録音を再生")!!.isEnabled)
        assertTrue(button(activity.window.decorView, "会話接続を開始 (60秒・試験)")!!.isEnabled)
        controller.pause().stop().destroy()
    }

    private fun editText(view: View): EditText? = when (view) {
        is EditText -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { editText(view.getChildAt(it)) }
        else -> null
    }

    private fun button(view: View, text: String): Button? = when (view) {
        is Button -> view.takeIf { it.text.toString() == text }
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { button(view.getChildAt(it), text) }
        else -> null
    }

    private fun labels(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { labels(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun switches(view: View): List<Switch> = when (view) {
        is Switch -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { switches(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun spinner(view: View): Spinner? = when (view) {
        is Spinner -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { spinner(view.getChildAt(it)) }
        else -> null
    }
}
