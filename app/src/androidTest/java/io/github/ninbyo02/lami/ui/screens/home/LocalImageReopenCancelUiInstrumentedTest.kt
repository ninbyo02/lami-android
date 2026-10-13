package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.MainActivity
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.screens.settings.InferenceBackendSelection
import io.github.ninbyo02.lami.util.RuntimeFlags
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalImageReopenCancelUiInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var context: Context
    private lateinit var db: ChatDatabase
    private lateinit var resultFile: File
    private val result = JSONObject()
    private var chatId = 0
    private var otherChatId = 0
    private var recoveryChatId = 0
    private lateinit var title: String
    private lateinit var otherTitle: String
    private lateinit var recoveryTitle: String
    private lateinit var mode: String
    private lateinit var backend: PreferredBackendDryRunSetting
    private val seedText = "Image UI persisted fixture"

    private val seed = object : ExternalResource() {
        override fun before() = runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(args.getString("imageUi", "false").toBoolean())
            context = ApplicationProvider.getApplicationContext()
            check(context.packageName.endsWith(".imagebench"))
            mode = args.getString("imageUiMode", "reopen")
            require(mode == "reopen" || mode == "cancel")
            backend = PreferredBackendDryRunSetting.valueOf(args.getString("imageBackend", "NPU"))
            require(mode != "cancel" || backend == PreferredBackendDryRunSetting.NPU)
            RuntimeFlags.isUiTest = true
            db = ChatDatabase.getDatabase(context)
            val preferences = SettingsPreferences(context)
            preferences.saveDeveloperAccessEnabled(true)
            preferences.saveDevEnableQairt244Sm8750NpuRoute(true)
            preferences.saveNpuStandardRouteMode(NpuStandardRouteMode.FULL)
            preferences.setTtsEnabled(false)
            preferences.saveInferenceTarget(InferenceTarget.LOCAL)
            val generic = "/data/local/tmp/lami_gpu_model.litertlm"
            preferences.saveLocalGenericModelInfo("gemma-4-E2B-it.litertlm", generic)
            preferences.saveLocalBaseModelInfo(
                if (backend == PreferredBackendDryRunSetting.NPU) "gemma-4-E2B-it_qualcomm_sm8750.litertlm" else "gemma-4-E2B-it.litertlm",
                if (backend == PreferredBackendDryRunSetting.NPU) "/data/local/tmp/lami_npu_durability_model.litertlm" else generic,
            )
            preferences.saveInferenceBackendSelection(when (backend) {
                PreferredBackendDryRunSetting.NPU -> InferenceBackendSelection.NPU
                PreferredBackendDryRunSetting.CPU -> InferenceBackendSelection.CPU
                else -> InferenceBackendSelection.GPU
            })
            val paths = if (mode == "cancel") {
                val baseline = JSONObject(File(context.filesDir, "multi_image_NPU_8_cmp_seed42_r2.json").readText()).getJSONArray("image_paths")
                List(baseline.length()) { baseline.getString(it) }
            } else {
                prepareLocalImageInputs(context, listOf(Uri.fromFile(File("/data/local/tmp/lami_nasa_AS17-148-22727.jpg"))), persistent = true)
                    .map { it.absolutePath }
            }
            title = "Image UI $mode ${backend.name} ${System.currentTimeMillis()}"
            otherTitle = "Other UI $mode ${backend.name} ${System.currentTimeMillis()}"
            chatId = db.chatDao().insertChat(Chat(title = title)).toInt()
            otherChatId = db.chatDao().insertChat(Chat(title = otherTitle)).toInt()
            recoveryTitle = "Image recovery ${System.currentTimeMillis()}"
            recoveryChatId = db.chatDao().insertChat(Chat(title = recoveryTitle)).toInt()
            val recoveryImage = prepareLocalImageInputs(context,
                listOf(Uri.fromFile(File("/data/local/tmp/lami_nasa_AS17-148-22727.jpg"))), persistent = true).single()
            db.messageDao().insertMessage(Message(chatId = recoveryChatId, message = seedText, isSendbyMe = true,
                attachmentUriString = recoveryImage.savedUriString))
            db.messageDao().insertMessage(Message(chatId = recoveryChatId,
                message = "The image is available for the next question.", isSendbyMe = false))
            val uris = paths.map { File(it).toURI().toString() }
            db.messageDao().insertMessage(Message(chatId = chatId, message = seedText, isSendbyMe = true,
                attachmentUriStringsJson = JSONArray(uris).toString(), attachmentUriString = uris.singleOrNull()))
            db.messageDao().insertMessage(Message(chatId = chatId, message = "The images are available for the next question.", isSendbyMe = false))
            db.messageDao().insertMessage(Message(chatId = otherChatId, message = "Other UI fixture", isSendbyMe = true))
            resultFile = File(context.filesDir, "ui_${mode}_${backend}.json")
            result.put("status", "seeded").put("mode", mode).put("backend", backend.name).put("chat_id", chatId)
                .put("image_count", paths.size).put("all_image_files_valid", paths.all { File(it).isFile })
            putLocalImageRuntimeBuildMetadata(result)
            save()
        }

        override fun after() {
            runBlocking { NpuKotlinConversationProductRoute.reset("ui_test_done") }
            RuntimeFlags.isUiTest = false
        }
    }

    @get:Rule val rules: TestRule = RuleChain.outerRule(seed).around(compose)
    private fun save() = resultFile.writeText(result.toString(2))
    private fun rows(id: Int = chatId): List<Message> = runBlocking { db.messageDao().getAllMessages(id).first() }
    private fun openChat(name: String, id: Int) {
        compose.waitUntil(20_000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithContentDescription("チャット一覧").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("userChatBubble").fetchSemanticsNodes().isNotEmpty()
        }
        result.put("last_requested_chat_id", id)
    }

    @Test fun restoredImageFollowupAndStop() {
        try {
            compose.activity.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("チャット一覧").fetchSemanticsNodes().isNotEmpty() }
            openChat(title, chatId)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("userChatBubble").fetchSemanticsNodes().isNotEmpty() }
            openChat(otherTitle, otherChatId)
            openChat(title, chatId)
            result.put("activity_recreated", true).put("thread_switched_away_and_back", true)
            screenshot("restored")
            val before = rows().map { it.messageID }.toSet()
            val trace = File(context.filesDir, "debug/local_reflection_trace.log")
            val traceOffset = if (trace.isFile) trace.readText().length else 0
            fun recentTrace(): String = if (!trace.isFile) "" else trace.readText().let { it.drop(if (it.length >= traceOffset) traceOffset else 0) }
            compose.onNode(hasSetTextAction() and hasText("Enter your prompt ...")).performTextInput(if (mode == "cancel")
                "Describe each image in detail and explain the differences. English only."
                else "Name the main object in the previous image. English only.")
            val started = SystemClock.elapsedRealtime()
            compose.onNodeWithContentDescription("Send Button").performClick()
            result.put("status", "running"); save()
            compose.waitUntil(10_000) { Regex("\\bchatId=$chatId\\b").containsMatchIn(recentTrace()) }
            if (mode == "cancel") {
                compose.waitUntil(60_000) { recentTrace().contains("engine_created_ms=") || recentTrace().contains("engine_reused=true") }
                Thread.sleep(1500)
                result.put("trace_before_stop", recentTrace().takeLast(5000))
                val stopped = SystemClock.elapsedRealtime()
                compose.onNodeWithContentDescription("Stop Button").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Stop Button").fetchSemanticsNodes().isEmpty() }
                result.put("ui_stop_ms", SystemClock.elapsedRealtime() - stopped)
                if (localImageRunCoordinator.state.value.busy) {
                    assertTrue("Stop must enter cleanup", localImageRunCoordinator.state.value.cleanupPending)
                    compose.onNodeWithContentDescription("Send Button").assertIsNotEnabled()
                    result.put("resend_disabled_during_cleanup", true)
                }
                screenshot("stopped")
                compose.waitUntil(90_000) { !localImageRunCoordinator.state.value.busy }
                result.put("stop_to_ready_ms", SystemClock.elapsedRealtime() - stopped)
                val drainStart = SystemClock.elapsedRealtime()
                runBlocking { withTimeout(90_000) { NpuKotlinConversationProductRoute.reset("ui_stop_drain_measure") } }
                result.put("native_drain_wait_ms", SystemClock.elapsedRealtime() - drainStart)
                Thread.sleep(2000)
                val added = rows().filter { it.messageID !in before && !it.isSendbyMe }
                result.put("assistant_statuses", JSONArray(added.map { it.status })).put("trace_after_stop", recentTrace().takeLast(6000))
                assertTrue("Cancelled reply must not complete", added.isNotEmpty() && added.none { it.status == MessageStatus.COMPLETED })
                assertTrue("Cancellation must be persisted", added.any { it.status == MessageStatus.CANCELLED })
                if (InstrumentationRegistry.getArguments().getString("imageUiEnforceCancelLatency", "false").toBoolean()) {
                    assertTrue("UI stop exceeds 1 second", result.getLong("ui_stop_ms") <= 1_000L)
                    assertTrue("Stop-to-ready exceeds 5 seconds", result.getLong("stop_to_ready_ms") <= 5_000L)
                }
            } else {
                compose.waitUntil(90_000) { rows().any { it.messageID !in before && !it.isSendbyMe && it.status !in MessageStatus.IN_FLIGHT } }
                val added = rows().filter { it.messageID !in before && !it.isSendbyMe }
                result.put("total_ui_ms", SystemClock.elapsedRealtime() - started)
                    .put("assistant_messages", JSONArray(added.map { JSONObject().put("text", it.message).put("status", it.status).put("notes", it.inferenceNotes) }))
                    .put("native_trace", recentTrace().takeLast(6000))
                assertTrue("Restored image must identify Earth", added.any { it.status == MessageStatus.COMPLETED && it.message.contains("earth", true) })
                screenshot("reply")
            }
            if (InstrumentationRegistry.getArguments().getString("imageUiTextRegression", "false").toBoolean()) {
                openChat(otherTitle, otherChatId)
                val beforeText = rows(otherChatId).map { it.messageID }.toSet()
                compose.onNode(hasSetTextAction() and hasText("Enter your prompt ..."))
                    .performTextInput("In one English sentence explain what RAM stores.")
                val textStarted = SystemClock.elapsedRealtime()
                compose.onNodeWithContentDescription("Send Button").performClick()
                compose.waitUntil(90_000) { rows(otherChatId).any { it.messageID !in beforeText && !it.isSendbyMe && it.status !in MessageStatus.IN_FLIGHT } }
                val textReplies = rows(otherChatId).filter { it.messageID !in beforeText && !it.isSendbyMe }
                result.put("text_regression_ms", SystemClock.elapsedRealtime() - textStarted)
                    .put("text_regression_messages", JSONArray(textReplies.map { JSONObject().put("text", it.message).put("status", it.status).put("notes", it.inferenceNotes) }))
                assertTrue("Text-only reply must complete and answer the RAM question", textReplies.any {
                    it.status == MessageStatus.COMPLETED && listOf("memory", "data", "instruction").any { word -> it.message.contains(word, true) }
                })
                screenshot("text_reply")
            }
            if (mode == "cancel" && InstrumentationRegistry.getArguments().getString("imageUiImageRegression", "false").toBoolean()) {
                openChat(recoveryTitle, recoveryChatId)
                val beforeRecovery = rows(recoveryChatId).map { it.messageID }.toSet()
                compose.onNode(hasSetTextAction() and hasText("Enter your prompt ..."))
                    .performTextInput("Name the main object in the previous image. English only.")
                val recoveryStarted = SystemClock.elapsedRealtime()
                compose.onNodeWithContentDescription("Send Button").performClick()
                compose.waitUntil(90_000) { rows(recoveryChatId).any {
                    it.messageID !in beforeRecovery && !it.isSendbyMe && it.status !in MessageStatus.IN_FLIGHT
                } }
                val replies = rows(recoveryChatId).filter { it.messageID !in beforeRecovery && !it.isSendbyMe }
                result.put("image_recovery_ms", SystemClock.elapsedRealtime() - recoveryStarted)
                    .put("image_recovery_messages", JSONArray(replies.map {
                        JSONObject().put("text", it.message).put("status", it.status).put("notes", it.inferenceNotes)
                    }))
                assertTrue("Image inference must recover after cancellation", replies.any {
                    it.status == MessageStatus.COMPLETED && it.message.contains("earth", true)
                })
                screenshot("image_recovery")
            }
            result.put("status", "completed")
        } catch (failure: Throwable) {
            result.put("activity_had_focus_at_failure", compose.activity.hasWindowFocus())
            runCatching { screenshot("failure") }
            runCatching { File(context.filesDir, "ui_${mode}_${backend}_semantics.txt").writeText(compose.onRoot().printToString()) }
            result.put("status", "failed").put("error", failure.stackTraceToString())
            throw failure
        } finally {
            save()
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("gemma_image_ui_result", result.toString())
            })
        }
    }

    private fun screenshot(stage: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.filesDir, "ui_${mode}_${backend}_$stage.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
