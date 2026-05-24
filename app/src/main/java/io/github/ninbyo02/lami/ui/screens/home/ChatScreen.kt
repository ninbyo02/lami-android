package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.app.ActivityManager
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import io.github.ninbyo02.lami.local.buildLocalInferenceFailureDiagnosticsText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.R
import io.github.ninbyo02.lami.UiState
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.toInferenceStats
import io.github.ninbyo02.lami.db.entity.isInferenceStatsMissing
import io.github.ninbyo02.lami.db.entity.TitleSource
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.tts.AndroidTtsController
import io.github.ninbyo02.lami.ui.common.LocalAppSnackbarHostState
import io.github.ninbyo02.lami.ui.common.PROJECT_SNACKBAR_SHORT_MS
import io.github.ninbyo02.lami.ui.components.HeaderAvatar
import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.ui.components.InferenceTargetIcon
import io.github.ninbyo02.lami.ui.components.LamiHeaderStatus
import io.github.ninbyo02.lami.ui.components.LocalInferenceEngineState
import io.github.ninbyo02.lami.ui.screens.settings.DEFAULT_CHAT_LAMI_AVATAR_SIZE_DP
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.screens.settings.MAX_CHAT_LAMI_AVATAR_SIZE_DP
import io.github.ninbyo02.lami.ui.screens.settings.MIN_CHAT_LAMI_AVATAR_SIZE_DP
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.ui.model.ContextWindowFetchState
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.text.MarkdownCodeRepair
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import io.github.ninbyo02.lami.ui.text.processEdgeGalleryCompatibleMarkdown
import io.github.ninbyo02.lami.ui.theme.LamiTypographyTokens
import io.github.ninbyo02.lami.ui.util.formatOutputTokens
import io.github.ninbyo02.lami.ui.util.formatInferenceTime
import io.github.ninbyo02.lami.ui.util.formatFinishReason
import io.github.ninbyo02.lami.ui.util.formatGenerationDuration
import io.github.ninbyo02.lami.ui.util.formatTimeToFirstToken
import io.github.ninbyo02.lami.ui.util.formatImageInputCount
import io.github.ninbyo02.lami.ui.util.formatModelLoadDuration
import io.github.ninbyo02.lami.ui.util.formatModelName
import io.github.ninbyo02.lami.ui.util.formatPromptEvalDuration
import io.github.ninbyo02.lami.ui.util.formatTokenPerSec
import io.github.ninbyo02.lami.ui.util.formatTotalTokens
import io.github.ninbyo02.lami.util.RuntimeFlags
import io.github.ninbyo02.lami.viewmodels.LamiState
import io.github.ninbyo02.lami.viewmodels.LamiStatus
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

private val ComposerMinHeight = 44.dp
private val ComposerPillRadius = ComposerMinHeight / 2
private val ComposerButtonSize = 44.dp
private val ComposerButtonVisualSize = ComposerButtonSize - 8.dp
private val ComposerButtonIconSize = 20.dp
private val ComposerButtonIconVisualSize = ComposerButtonIconSize - 4.dp
private val ComposerBottomGapHeight = 8.dp
private val TopGradientOverlayHeight = 24.dp
private val TopGradientOverlayTopOffset = 34.dp
// DEBUG: 上部グラデーションの視認確認で 4dp 上へずらす（調整完了後に 0.dp へ戻しやすくする）
private val TopGradientOverlayYOffset = (-4).dp
private val ChatListTopGapFromGradientBottom = 24.dp
private val EmptyNewConversationBaseTopPadding = 12.dp
// Empty / New conversation のメッセージ開始位置を sprite 直下に近づける補正。
// 現在のレイアウトは gradient bottom を anchor にしているため、
// gradient → sprite bottom の視覚差分をここで補正している。
// UI調整用パラメータなので、位置調整はこの値のみ変更する。
private val EmptyNewConversationTopAdjust = (-120).dp
private val SpriteMessageGap = 16.dp
// メッセージ間の縦余白は初回ペアも含めて常に同値で統一する
private val ChatMessageVerticalGap = 8.dp
private const val MaxComposerAttachments = 10
private const val LOCAL_INFERENCE_PROBE_PROMPT = "hi"
private const val LOCAL_INIT_TIMEOUT_MS = 3000L
private const val LOCAL_GENERATE_TIMEOUT_MS = 30000L
private const val LOCAL_RESPONDING_PLACEHOLDER_DELAY_MS = 350L
private const val TTS_HEADER_TALKING_GRACE_MS = 900L
// DEV専用のsession async PoCは今回のPoC検証のため一時的にON（判定は internal file のみで実施）。
private const val ENABLE_DEV_LLM_SESSION_ASYNC_POC = true
private const val LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT = "one-shot"
private const val LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_ASYNC_POC = "session-async-poc"
private const val DEV_LLM_SESSION_ASYNC_POC_PROMPT = "1+1を短く答えてください。"
private const val DEV_LLM_SESSION_ASYNC_POC_TIMEOUT_MS = 10_000L
private const val LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_FLOW = "official-flow"
private const val LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_BLOCKING = "official-blocking"
private const val LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_LEGACY = "session-legacy"
private const val DEV_UI_DEBUG_MODE = false
private const val DEV_STREAMING_RENDER_TAIL_LIMIT_ENABLED = true
private const val DEV_STREAMING_RENDER_TAIL_LIMIT_CHARS = 4000
private const val DEV_USE_HELD_PATH_ONLY = false
private const val LOCAL_UI_APPEND_DEBOUNCE_MS = 0L
private const val LOCAL_STREAMING_WHITESPACE_LOG_TAG = "LocalWsTrace"

private enum class LocalExecutionPath(
    val sourceLabel: String,
    val officialFlowAttempted: Boolean,
    val officialFlowUsed: Boolean,
    val usesOfficialConversationApi: Boolean,
) {
    HELD_OFFICIAL_FLOW(
        sourceLabel = "held-official-flow",
        officialFlowAttempted = true,
        officialFlowUsed = true,
        usesOfficialConversationApi = true,
    ),
    HELD_OFFICIAL_BLOCKING(
        sourceLabel = "held-official-blocking",
        officialFlowAttempted = true,
        officialFlowUsed = false,
        usesOfficialConversationApi = true,
    ),
    OFFICIAL_FLOW(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_FLOW,
        officialFlowAttempted = true,
        officialFlowUsed = true,
        usesOfficialConversationApi = true,
    ),
    OFFICIAL_BLOCKING(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_BLOCKING,
        officialFlowAttempted = true,
        officialFlowUsed = false,
        usesOfficialConversationApi = true,
    ),
    ONE_SHOT(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT,
        officialFlowAttempted = false,
        officialFlowUsed = false,
        usesOfficialConversationApi = false,
    ),
    SESSION_LEGACY(
        sourceLabel = LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_LEGACY,
        officialFlowAttempted = false,
        officialFlowUsed = false,
        usesOfficialConversationApi = false,
    );

    companion object {
        fun fromSourceLabel(raw: String?): LocalExecutionPath? {
            val normalized = raw?.trim().orEmpty()
            return values().firstOrNull { it.sourceLabel == normalized }
        }

        fun fromClosePath(raw: String?): LocalExecutionPath? {
            val normalized = raw?.trim().orEmpty()
            return when {
                normalized.contains("held-official-flow") -> HELD_OFFICIAL_FLOW
                normalized.contains("held-official-blocking") -> HELD_OFFICIAL_BLOCKING
                normalized.contains("official-flow") -> OFFICIAL_FLOW
                normalized.contains("official-blocking") -> OFFICIAL_BLOCKING
                normalized.contains("legacy") -> SESSION_LEGACY
                else -> null
            }
        }
    }
}

private enum class LocalLiteRtProbeResult {
    SUCCESS,
    API_NOT_CONNECTED,
    CREATE_METHOD_NOT_FOUND,
    CREATE_FAILED,
    GENERATE_METHOD_NOT_FOUND,
    GENERATE_FAILED,
}

private data class LocalInferenceInitializationResult(
    val state: LocalInferenceEngineState,
    val probeResult: LocalLiteRtProbeResult?,
)

private data class LocalInferenceRunResult(
    val state: LocalInferenceEngineState,
    val response: String? = null,
    val trace: LocalInferenceTrace = LocalInferenceTrace(),
    val closeLifecycleSummary: RunCloseLifecycleSummary? = null,
    val runnerWhitespaceTraceText: String? = null,
)

private data class LocalModelResolution(
    val modelPath: String,
    val displayName: String,
    val backendKey: String,
    val cacheDirPath: String,
) {
    val engineKey: HeldEngineKey
        get() = HeldEngineKey(
            modelPath = modelPath,
            backendKey = backendKey,
            cacheDirPath = cacheDirPath,
        )
}

internal enum class LocalStatsAvailability {
    AVAILABLE_NOW,
    DERIVABLE_NOW,
    API_CANDIDATE_ONLY,
    NOT_FOUND,
}

internal enum class LocalStreamingApiProbeResult {
    ASYNC_API_NOT_FOUND,
    LISTENER_API_NOT_FOUND,
    SESSION_API_NOT_FOUND,
    ASYNC_INVOKE_FAILED,
    LISTENER_INVOKE_FAILED,
    SESSION_CREATE_FAILED,
    ASYNC_INVOKE_SUCCEEDED,
    LISTENER_INVOKE_SUCCEEDED,
    SESSION_CREATE_SUCCEEDED,
}

internal data class LocalStatsCandidateProbe(
    val availability: LocalStatsAvailability,
    val signature: String? = null,
    val returnTypeName: String? = null,
    val valueSummary: String? = null,
)

internal data class LocalInferenceTrace(
    val createMethodSignature: String? = null,
    val optionsBuildPath: String? = null,
    val generateMethodSignature: String? = null,
    val streamingCandidateDetected: Boolean? = null,
    val localModelDisplayName: String? = null,
    val mediaPipeProbeModelPath: String? = null,
    val modelNameProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val finishReasonProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val outputTokenProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val loadTimeProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val wallClockLoadDurationNs: Long? = null,
    val wallClockTotalInferenceDurationNs: Long? = null,
    val localTraceStartElapsedRealtimeMs: Long? = null,
    val localTraceFirstResponseElapsedRealtimeMs: Long? = null,
    val localTraceCompletedElapsedRealtimeMs: Long? = null,
    val promptEvalTimeProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val evalTimeProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val firstTokenProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val estimatedTokenProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val asyncApiProbeResult: LocalStreamingApiProbeResult? = null,
    val asyncApiSignature: String? = null,
    val listenerApiProbeResult: LocalStreamingApiProbeResult? = null,
    val listenerApiSignature: String? = null,
    val sessionApiProbeResult: LocalStreamingApiProbeResult? = null,
    val sessionApiSignature: String? = null,
    val sessionGenerateSignature: String? = null,
    val sessionAsyncSignature: String? = null,
    val sessionStreamingSignature: String? = null,
    val sessionTokenSignature: String? = null,
    val sessionPromptTokens: Int? = null,
    val sessionResponseTokens: Int? = null,
    val sessionTotalTokens: Int? = null,
    val measuredTokenSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
    val sessionTokenProbeErrorStage: String? = null,
    val sessionTokenProbeErrorClassName: String? = null,
    val sessionListenerSignature: String? = null,
    val sessionLifecycleSignature: String? = null,
    val sessionAsyncPocAttempted: Boolean = false,
    val sessionAsyncPocCreateSucceeded: Boolean = false,
    val sessionAsyncPocMethodSignature: String? = null,
    val sessionAsyncPocFutureClassName: String? = null,
    val sessionAsyncPocResponseLength: Int? = null,
    val sessionAsyncPocResponseHead: String? = null,
    val selectedAssistantResponseSource: String? = null,
    val selectedAssistantResponseHead: String? = null,
    val oneShotResponseHead: String? = null,
    val sessionAsyncPocSelectedCandidateHead: String? = null,
    val sessionAsyncPocCloseSucceeded: Boolean? = null,
    val sessionAsyncPocErrorStage: String? = null,
    val sessionAsyncPocErrorClassName: String? = null,
    val sessionAsyncPocErrorMessage: String? = null,
    val assistantUpdateCount: Int = 0,
    val streamedCharsPerSecond: Double? = null,
    val appendBatchSizeAvg: Double? = null,
    val appendEventsPerSecond: Double? = null,
    val composeRecomposeEstimate: Int? = null,
    val markdownRepairCount: Int? = null,
    val uiAppendDebounceMs: Long? = null,
    val firstNonEmptyAssistantChunkSeen: Boolean = false,
    val assistantStreamedToUi: Boolean = false,
    val realPartialReceived: Boolean = false,
    val realPartialChunkCount: Int = 0,
    val officialFlowAttempted: Boolean = false,
    val officialFlowUsed: Boolean = false,
    val officialFlowFallbackReason: String? = null,
    val officialConversationApiAvailable: Boolean? = null,
    val officialFlowChunkCount: Int = 0,
    val officialChunkCount: Int = 0,
    val officialChunkIntervalAvgMs: Double? = null,
    val officialChunkIntervalMaxMs: Long? = null,
    val officialChunkIntervalMinMs: Long? = null,
    val officialChunkFirstToLastMs: Long? = null,
    val officialChunkCharsAvg: Double? = null,
    val officialChunkCharsMax: Int? = null,
    val officialChunkCharsMin: Int? = null,
    val officialChunkEmptyCount: Int = 0,
    val officialChunkNonEmptyCount: Int = 0,
    val officialChunkEventsPerSecond: Double? = null,
    val officialChunkCharsPerSecond: Double? = null,
    val requestedPreferredBackend: String? = null,
    val appliedPreferredBackend: String? = null,
    val preferredBackendApplyResult: String? = null,
    val preferredBackendHookReached: Boolean? = null,
    val preferredBackendHookSource: String? = null,
    val preferredBackendApplyError: String? = null,
    val preferredBackendApplyBuilderClass: String? = null,
    val preferredBackendApplyMethodCandidates: List<String> = emptyList(),
    val preferredBackendApplyBackendEnumCandidates: List<String> = emptyList(),
    val preferredBackendApplyNotSupportedReason: String? = null,
    val heldEngineCreatePath: String? = null,
    val llmInferenceCreateMethod: String? = null,
    val optionsBuilderSource: String? = null,
    val preferredBackendHookEligible: Boolean? = null,
    val preferredBackendHookMissingReason: String? = null,
    val preferredBackendRequiresEngineRecreate: Boolean? = null,
    val preferredBackendEngineRecreateReason: String? = null,
    val holderInstanceHash: Int? = null,
    val heldEngineHash: Int? = null,
    val holderAppInForeground: Boolean? = null,
    val holderLastAcquireAction: String? = null,
    val holderLastLifecycleEventReason: String? = null,
    val holderLastLifecycleDecisionAction: String? = null,
    val heldEngineRecreateRequestCount: Int? = null,
    val heldEngineWasPresentAtRunStart: Boolean? = null,
    val heldEngineCreatedDuringRun: Boolean? = null,
    val holderLastRecreateResult: String? = null,
    val holderLastRecreateReason: String? = null,
    val holderHasHeldEngineBeforeRecreate: Boolean? = null,
    val holderHasHeldEngineAfterRecreate: Boolean? = null,
    val lastHeldEngineCreateReason: String? = null,
    val lastHeldEngineCreateSource: String? = null,
    val lastHeldEngineCreateAtElapsedMs: Long? = null,
    val lastHeldEngineCreateRequestedPreferredBackend: String? = null,
    val lastHeldEngineCreateStackHint: String? = null,
    val realPartialHookAttempted: Boolean = false,
    val realPartialHookAttached: Boolean = false,
    val realPartialCallbackCount: Int = 0,
    val localFailureDiagnosticsText: String? = null,
)

private data class LocalStreamingUiMetricsSnapshot(
    val streamedCharsPerSecond: Double?,
    val appendBatchSizeAvg: Double?,
    val appendEventsPerSecond: Double?,
    val composeRecomposeEstimate: Int?,
    val markdownRepairCount: Int,
    val uiAppendDebounceMs: Long,
)

private class LocalStreamingUiMetrics {
    private var firstAppendElapsedMs: Long? = null
    private var lastAppendElapsedMs: Long? = null
    private var lastText: String? = null
    private var appendEventCount: Int = 0
    private var appendedCharCount: Int = 0
    private var renderUpdateCount: Int = 0
    private var markdownRepairCount: Int = 0

    fun reset() {
        firstAppendElapsedMs = null
        lastAppendElapsedMs = null
        lastText = null
        appendEventCount = 0
        appendedCharCount = 0
        renderUpdateCount = 0
        markdownRepairCount = 0
    }

    fun recordAppend(text: String, nowElapsedMs: Long) {
        if (text.isBlank() || text == lastText) return
        val previous = lastText
        val appendedChars = when {
            previous != null && text.startsWith(previous) -> text.length - previous.length
            previous == null -> text.length
            else -> text.length
        }.coerceAtLeast(0)
        firstAppendElapsedMs = firstAppendElapsedMs ?: nowElapsedMs
        lastAppendElapsedMs = nowElapsedMs
        lastText = text
        appendEventCount += 1
        appendedCharCount += appendedChars
    }

    fun recordRenderUpdate() {
        renderUpdateCount += 1
    }

    fun recordMarkdownRepair() {
        markdownRepairCount += 1
    }

    fun snapshot(): LocalStreamingUiMetricsSnapshot {
        val firstMs = firstAppendElapsedMs
        val lastMs = lastAppendElapsedMs
        val elapsedSeconds = if (firstMs != null && lastMs != null) {
            ((lastMs - firstMs).coerceAtLeast(1L)).toDouble() / 1000.0
        } else {
            null
        }
        return LocalStreamingUiMetricsSnapshot(
            streamedCharsPerSecond = elapsedSeconds?.takeIf { appendEventCount > 0 }
                ?.let { appendedCharCount.toDouble() / it },
            appendBatchSizeAvg = appendEventCount.takeIf { it > 0 }
                ?.let { appendedCharCount.toDouble() / it.toDouble() },
            appendEventsPerSecond = elapsedSeconds?.takeIf { appendEventCount > 0 }
                ?.let { appendEventCount.toDouble() / it },
            composeRecomposeEstimate = renderUpdateCount.takeIf { it > 0 } ?: appendEventCount.takeIf { it > 0 },
            markdownRepairCount = markdownRepairCount,
            uiAppendDebounceMs = LOCAL_UI_APPEND_DEBOUNCE_MS,
        )
    }
}

private fun LocalInferenceTrace.withStreamingUiMetrics(
    snapshot: LocalStreamingUiMetricsSnapshot,
): LocalInferenceTrace {
    return copy(
        streamedCharsPerSecond = snapshot.streamedCharsPerSecond,
        appendBatchSizeAvg = snapshot.appendBatchSizeAvg,
        appendEventsPerSecond = snapshot.appendEventsPerSecond,
        composeRecomposeEstimate = snapshot.composeRecomposeEstimate,
        markdownRepairCount = snapshot.markdownRepairCount,
        uiAppendDebounceMs = snapshot.uiAppendDebounceMs,
    )
}

private fun LocalInferenceTrace.withOfficialChunkMetrics(
    snapshot: LocalOfficialChunkMetricsSnapshot?,
): LocalInferenceTrace {
    if (snapshot == null) return this
    return copy(
        officialChunkCount = snapshot.officialChunkCount,
        officialChunkIntervalAvgMs = snapshot.officialChunkIntervalAvgMs,
        officialChunkIntervalMaxMs = snapshot.officialChunkIntervalMaxMs,
        officialChunkIntervalMinMs = snapshot.officialChunkIntervalMinMs,
        officialChunkFirstToLastMs = snapshot.officialChunkFirstToLastMs,
        officialChunkCharsAvg = snapshot.officialChunkCharsAvg,
        officialChunkCharsMax = snapshot.officialChunkCharsMax,
        officialChunkCharsMin = snapshot.officialChunkCharsMin,
        officialChunkEmptyCount = snapshot.officialChunkEmptyCount,
        officialChunkNonEmptyCount = snapshot.officialChunkNonEmptyCount,
        officialChunkEventsPerSecond = snapshot.officialChunkEventsPerSecond,
        officialChunkCharsPerSecond = snapshot.officialChunkCharsPerSecond,
    )
}

private data class LocalSessionTokenProbeResult(
    val promptTokens: Int? = null,
    val responseTokens: Int? = null,
    val totalTokens: Int? = null,
    val errorStage: String? = null,
    val errorClassName: String? = null,
)

private data class LocalLiteRtGeneratedResponse(
    val response: String? = null,
    val trace: LocalInferenceTrace = LocalInferenceTrace(),
    val closeLifecycleSummary: RunCloseLifecycleSummary? = null,
)

private data class LocalLiteRtOptionsBuildResult(
    val options: Any,
    val buildPath: String,
)

private enum class TopPaddingMode {
    NewConversation,
    ExistingConversation,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    navHostController: NavHostController,
    viewModel: OllamaViewModel,
    chatId: Int? = null,
) {

    val uiState by viewModel.uiState.collectAsState()
    val chats by viewModel.chats.collectAsState()
    var effectiveChatId by rememberSaveable { mutableStateOf<Int?>(chatId) }
    var isCreatingChat by rememberSaveable { mutableStateOf(false) }
    var suppressAutoNewChat by rememberSaveable { mutableStateOf(false) }
    var suppressChatContentWhileClosingDrawer by rememberSaveable { mutableStateOf(false) }
    var pendingNavigateChatId by rememberSaveable { mutableStateOf<Int?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    var userPrompt: String by rememberSaveable { mutableStateOf("") }
    var prompt: String by remember { mutableStateOf("") }
    val allChatsState = effectiveChatId?.let {
        viewModel.allMessages(it)
            .map { messages -> messages as List<Message>? }
            .collectAsState(initial = null)
    }
    val allChatsOrNull = allChatsState?.value
    var toggle by remember { mutableStateOf(false) }
    var placeholder by rememberSaveable { mutableStateOf("Enter your prompt ...") }
    var attachSheetOpen by rememberSaveable { mutableStateOf(false) }
    var expandDialogOpen by rememberSaveable { mutableStateOf(false) }
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val lamiAnimationStatus by viewModel.lamiAnimationStatus.collectAsState()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
    val animationEpochMs by viewModel.animationEpochMs.collectAsState()
    val latestInferenceStats by viewModel.latestInferenceStats.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val snackbarHostState = LocalAppSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val mediaPipeProbeContext = context.applicationContext ?: context
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }
    val localInferenceEngineHolder = remember(context.applicationContext) {
        LocalInferenceEngineHolder.getInstance(context.applicationContext)
    }
    val preferredBackendDryRunSetting by settingsPreferences.preferredBackendDryRunSettingFlow.collectAsState(
        initial = PreferredBackendDryRunSetting.DEFAULT,
    )
    val markdownStreamingMode by settingsPreferences.markdownStreamingModeFlow.collectAsState(
        initial = MarkdownStreamingMode.DEFAULT,
    )
    val localStreamingRunner = remember(
        context.applicationContext,
        settingsPreferences,
        preferredBackendDryRunSetting,
        markdownStreamingMode,
    ) {
        DefaultLocalStreamingRunner<LocalInferenceRunResult>(
            timeoutMs = LOCAL_GENERATE_TIMEOUT_MS,
        ) { runPrompt, runLocalBaseModelFilePath, runLocalBaseModelDisplayName, runResolvedModelPath, runCacheDirPath, runMediaPipeProbeContext, onPartial ->
            appendLocalReflectionTrace(
                context = context.applicationContext,
                message = "UPSTREAM before-runLocalInferenceOnceEntry",
            )
            runLocalInferenceOnceEntry(
                context = context.applicationContext,
                settingsPreferences = settingsPreferences,
                localBaseModelFilePath = runLocalBaseModelFilePath,
                localBaseModelDisplayName = runLocalBaseModelDisplayName,
                resolvedModelPath = runResolvedModelPath,
                resolvedCacheDirPath = runCacheDirPath,
                mediaPipeProbeContext = runMediaPipeProbeContext,
                preferredBackendDryRunSetting = preferredBackendDryRunSetting,
                markdownStreamingMode = markdownStreamingMode,
                prompt = runPrompt,
                onPartial = onPartial,
            )
        }
    }
    val savedChatLamiAvatarSizeDp by settingsPreferences.chatLamiAvatarSizeDpFlow.collectAsState(
        initial = DEFAULT_CHAT_LAMI_AVATAR_SIZE_DP,
    )
    val devEnableStreamingSentenceTts by settingsPreferences.devEnableStreamingSentenceTtsFlow.collectAsState(
        initial = true,
    )
    val devEnableQairt244Sm8750NpuRoute by settingsPreferences.devEnableQairt244Sm8750NpuRouteFlow.collectAsState(
        initial = false,
    )
    val ttsEnabled by settingsPreferences.ttsEnabledFlow.collectAsState(
        initial = true,
    )
    val clipboardManager = LocalClipboardManager.current
    val ttsController = remember { AndroidTtsController(context.applicationContext) }
    val isTtsSpeaking by ttsController.isSpeaking.collectAsState()
    var keepTtsTalkingInHeader by remember(effectiveChatId) { mutableStateOf(false) }
    var selectedImageUriStrings by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingAssistantImageInputCount by rememberSaveable { mutableStateOf<Int?>(null) }
    val savedInferenceTarget by settingsPreferences.inferenceTargetFlow.collectAsState(initial = InferenceTarget.LOCAL)
    val savedInferenceStatsDisplayMode by settingsPreferences.inferenceStatsDisplayModeFlow.collectAsState(
        initial = InferenceStatsDisplayMode.SIMPLE,
    )
    var selectedInferenceTarget by rememberSaveable { mutableStateOf(InferenceTarget.LOCAL) }
    var isLocalInferenceRunning by rememberSaveable { mutableStateOf(false) }
    val localBaseModelFilePath by settingsPreferences.localBaseModelFilePathFlow.collectAsState(initial = null)
    val localBaseModelDisplayName by settingsPreferences.localBaseModelDisplayNameFlow.collectAsState(initial = null)
    LaunchedEffect(savedInferenceTarget) {
        selectedInferenceTarget = savedInferenceTarget
    }
    LaunchedEffect(selectedInferenceTarget, effectiveChatId) {
        if (selectedInferenceTarget != InferenceTarget.LOCAL) {
            effectiveChatId?.let { currentChatId ->
                localInferenceEngineHolder.notifyLifecycleEvent(
                    reason = "backend-changed",
                    chatId = currentChatId,
                )
            }
        }
    }
    var localInferenceEngineState by rememberSaveable {
        mutableStateOf(LocalInferenceEngineState.UNINITIALIZED)
    }
    // composer fullscreen viewer は回転（構成変更）で閉じないよう Saveable で保持する。
    // Uri は Saveable ではないため String で保持し、表示時に Uri.parse で復元する。
    var composerViewerUriStrings by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var composerViewerInitialIndex by rememberSaveable { mutableStateOf(0) }
    val selectedImageUris = selectedImageUriStrings.map(Uri::parse)
    LaunchedEffect(localBaseModelFilePath) {
        if (localBaseModelFilePath.isNullOrBlank()) {
            localInferenceEngineState = LocalInferenceEngineState.UNINITIALIZED
        }
        if (shouldApplyHeldEngineModelPath(localBaseModelFilePath)) {
            localInferenceEngineHolder.clearIfModelChanged(localBaseModelFilePath?.trim().orEmpty())
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MaxComposerAttachments),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val imageUris = uris.filter { uri ->
            context.contentResolver.getType(uri)?.startsWith("image/") == true
        }
        if (imageUris.size != uris.size) {
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = "画像のみ添付できます",
                    duration = SnackbarDuration.Short,
                )
            }
        }
        selectedImageUriStrings = (selectedImageUriStrings + imageUris.map(Uri::toString))
            .distinct()
            .take(MaxComposerAttachments)
    }
    val errorMessage = (uiState as? UiState.Error)?.errorMessage
    val remoteStreamingResponseText = (uiState as? UiState.Streaming)?.partialText
    var localStreamingResponseText by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    var showDelayedLocalRespondingPlaceholder by remember(effectiveChatId) { mutableStateOf(false) }
    var localStopRequested by remember(effectiveChatId) { mutableStateOf(false) }
    var didReceiveRealLocalPartial by remember(effectiveChatId) { mutableStateOf(false) }
    var realLocalPartialChunkCount by remember(effectiveChatId) { mutableStateOf(0) }
    var localInferenceJob by remember(effectiveChatId) { mutableStateOf<Job?>(null) }
    var remoteStopRequested by remember(effectiveChatId) { mutableStateOf(false) }
    var remoteRequestJob by remember(effectiveChatId) { mutableStateOf<Job?>(null) }
    var streamingAssistantMessageId by remember(effectiveChatId) { mutableStateOf<Int?>(null) }
    var devDebugText by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    var devHeldStateText by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    var devCloseLifecycleText by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    var devWhitespaceTraceText by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    var devRunnerWhitespaceTraceText by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    val streamingResponseText = localStreamingResponseText ?: remoteStreamingResponseText
    var streamingResponseTextForRender by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    val isLocalRunningRaw = isLocalInferenceRunning
    val isServerRunning =
        !remoteStopRequested &&
            (
                remoteRequestJob?.isActive == true ||
                    uiState is UiState.Loading ||
                    uiState is UiState.Streaming
                )
    val isServerRunningRaw = isServerRunning
    val isStopRequested = localStopRequested || remoteStopRequested
    val isLocalRunningUi = isLocalRunningRaw && !isStopRequested
    val isServerRunningUi = isServerRunningRaw && !isStopRequested
    val isInferenceRunningUi = isLocalRunningUi || isServerRunningUi
    val isLocalTtsPlayingUi =
        selectedInferenceTarget == InferenceTarget.LOCAL &&
            isTtsPlaying &&
            !isStopRequested
    val isTtsPlayingForHeaderUi = isTtsSpeaking || isLocalTtsPlayingUi || keepTtsTalkingInHeader
    val isHeaderRunningUi = isInferenceRunningUi || isTtsPlayingForHeaderUi
    val isServerLoadingUi = uiState is UiState.Loading && isServerRunningUi
    LaunchedEffect(
        isLocalInferenceRunning,
        localStopRequested,
        streamingAssistantMessageId,
        localStreamingResponseText,
    ) {
        showDelayedLocalRespondingPlaceholder = false
        if (
            !isLocalInferenceRunning ||
            localStopRequested ||
            streamingAssistantMessageId != null ||
            !localStreamingResponseText.isNullOrBlank()
        ) {
            return@LaunchedEffect
        }
        delay(LOCAL_RESPONDING_PLACEHOLDER_DELAY_MS)
        if (
            isLocalInferenceRunning &&
            !localStopRequested &&
            streamingAssistantMessageId == null &&
            localStreamingResponseText.isNullOrBlank()
        ) {
            showDelayedLocalRespondingPlaceholder = true
        }
    }
    val headerStatusTitleOverride = when {
        isHeaderRunningUi -> "Responding..."
        isStopRequested -> "Ready"
        else -> null
    }
    val showLocalRespondingAssistantRow =
        isLocalRunningUi &&
            streamingAssistantMessageId == null &&
            showDelayedLocalRespondingPlaceholder
    val localRespondingAssistantRowMessage = if (
        localInferenceEngineState == LocalInferenceEngineState.PREPARING &&
        localStreamingResponseText.isNullOrBlank()
    ) {
        "モデルを読み込み中…"
    } else {
        "応答中..."
    }
    val lamiStatusForChatUi = if (isHeaderRunningUi) lamiAnimationStatus else LamiStatus.READY
    val lamiUiState by viewModel.lamiUiState.collectAsState()
    val lamiHeaderStateForChatUi = if (isHeaderRunningUi) lamiUiState.state else LamiState.Idle
    val effectiveLamiStatusForChatUi = when {
        isStopRequested -> LamiStatus.READY
        isTtsPlayingForHeaderUi -> LamiStatus.TALKING
        isServerRunningUi -> lamiStatusForChatUi
        isLocalRunningUi -> when (lamiStatusForChatUi) {
            LamiStatus.READY,
            LamiStatus.DEGRADED,
            LamiStatus.NO_MODELS,
            LamiStatus.OFFLINE,
            LamiStatus.ERROR,
            -> LamiStatus.CONNECTING
            else -> lamiStatusForChatUi
        }
        else -> LamiStatus.READY
    }
    val effectiveLamiHeaderStateForChatUi = when {
        isStopRequested -> LamiState.Idle
        isTtsPlayingForHeaderUi -> {
            LamiState.Speaking(1)
        }
        isServerRunningUi -> lamiHeaderStateForChatUi
        isLocalRunningUi -> if (lamiHeaderStateForChatUi == LamiState.Idle) LamiState.Thinking else lamiHeaderStateForChatUi
        else -> LamiState.Idle
    }
    // NOTE: debug-only top gradient adjustments. Default OFF.
    val debugTopGradientOrange = false
    val debugTopGradientDownshift = 32.dp
    val debugOverlayEnabled = false
    val topGradientBottomDp = TopGradientOverlayTopOffset + TopGradientOverlayYOffset + TopGradientOverlayHeight
    val chatListTopPaddingDp = topGradientBottomDp + ChatListTopGapFromGradientBottom
    var measuredTopGradientBottomPx by remember { mutableStateOf<Float?>(null) }
    var measuredSpriteBottomPx by remember { mutableStateOf<Float?>(null) }
    var measuredContentTopPx by remember { mutableStateOf<Float?>(null) }
    var openLamiControlRequestKey by remember { mutableStateOf(0) }
    val measuredTopGradientBottomDp = with(LocalDensity.current) { (measuredTopGradientBottomPx ?: 0f).toDp() }
    val effectiveTopGradientBottomDp = if (measuredTopGradientBottomPx != null) measuredTopGradientBottomDp else topGradientBottomDp
    val topPaddingModeMap = remember {
        mutableStateMapOf<Int, TopPaddingMode>()
    }
    val fixedEmptyNewAnchorTopPaddingByChatId = remember {
        mutableStateMapOf<Int, Dp>()
    }
    val lastUserMessageCountByChatId = remember {
        mutableStateMapOf<Int, Int>()
    }
    var measuredComposerTopY by remember { mutableStateOf(0f) }
    var overlayRootTopY by remember { mutableStateOf(0f) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var chatSearchQuery by rememberSaveable { mutableStateOf("") }
    val sortedChats = remember(chats) { chats.sortedByDescending { it.chatId } }
    val filteredChats = remember(sortedChats, chatSearchQuery) {
        filterChatsByTitle(sortedChats, chatSearchQuery)
    }
    var latestMessagePreviewByChatId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var currentSpeakingAssistantMessageId by remember { mutableStateOf<Int?>(null) }
    var stopButtonOwnerAssistantMessageId by remember(effectiveChatId) { mutableStateOf<Int?>(null) }
    var stopButtonOwnerSetAtMs by remember(effectiveChatId) { mutableStateOf<Long?>(null) }
    var streamingSpeechBuffer by remember(effectiveChatId) { mutableStateOf("") }
    var streamingSpeechLastConsumedLength by remember(effectiveChatId) { mutableStateOf(0) }
    var streamingSpeechStartedForMessageId by remember(effectiveChatId) { mutableStateOf<Int?>(null) }
    var isStreamingSentencePlaybackActive by remember(effectiveChatId) { mutableStateOf(false) }
    var pendingStopButtonOwnerClearJob by remember(effectiveChatId) { mutableStateOf<Job?>(null) }
    var suppressReplayAssistantMessageId by remember(effectiveChatId) { mutableStateOf<Int?>(null) }
    var pendingReplaySuppressClearJob by remember(effectiveChatId) { mutableStateOf<Job?>(null) }
    var stopUiCooldownAssistantMessageId by remember(effectiveChatId) { mutableStateOf<Int?>(null) }
    var pendingStopUiCooldownClearJob by remember(effectiveChatId) { mutableStateOf<Job?>(null) }
    var suppressedTtsAssistantMessageId by remember(effectiveChatId) { mutableStateOf<Int?>(null) }
    var ttsTapGuardEpoch by remember(effectiveChatId) { mutableStateOf(0L) }
    var streamingGuardEpoch by remember(effectiveChatId) { mutableStateOf(0L) }
    var selectedInferenceStats by remember { mutableStateOf<InferenceStats?>(null) }
    var selectedLocalTraceForDevSheet by remember { mutableStateOf<LocalInferenceTrace?>(null) }
    var selectedAssistantMessageTextForStatsSheet by remember { mutableStateOf<String?>(null) }
    var selectedPromptMessageTextForStatsSheet by remember { mutableStateOf<String?>(null) }
    val immediateInferenceStatsByMessageId = remember(effectiveChatId) {
        mutableStateMapOf<Int, InferenceStats>()
    }
    var latestLocalTraceForDev by remember { mutableStateOf<LocalInferenceTrace?>(null) }
    var showInferenceStatsSheet by remember { mutableStateOf(false) }
    var preferredBackendManualRecreateInProgress by remember { mutableStateOf(false) }
    var preferredBackendManualRecreateResult by remember { mutableStateOf("none") }
    var preferredBackendManualRecreateReason by remember { mutableStateOf("user-requested") }
    var devUiAliveSeconds by remember(effectiveChatId) { mutableStateOf(0) }
    var assistantUpdateCountForDev by remember { mutableStateOf(0) }
    var firstNonEmptyAssistantChunkSeenForDev by remember { mutableStateOf(false) }
    var lastStreamingAssistantChunkForDev by remember { mutableStateOf<String?>(null) }
    var lastPersistedStreamingAssistantText by remember(effectiveChatId) { mutableStateOf<String?>(null) }
    val localStreamingUiMetricsForDev = remember(effectiveChatId) { LocalStreamingUiMetrics() }
    val streamingAssistantPersistMutex = remember(effectiveChatId) { Mutex() }

    LaunchedEffect(isLocalInferenceRunning, streamingResponseText) {
        if (!BuildConfig.DEBUG || !isLocalInferenceRunning) return@LaunchedEffect
        val currentChunk = streamingResponseText?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (currentChunk != lastStreamingAssistantChunkForDev) {
            assistantUpdateCountForDev += 1
            firstNonEmptyAssistantChunkSeenForDev = true
            lastStreamingAssistantChunkForDev = currentChunk
            localStreamingUiMetricsForDev.recordAppend(
                text = currentChunk,
                nowElapsedMs = SystemClock.elapsedRealtime(),
            )
        }
    }
    LaunchedEffect(streamingResponseText, isInferenceRunningUi) {
        val latestText = streamingResponseText
        if (latestText.isNullOrEmpty()) {
            streamingResponseTextForRender = latestText
            return@LaunchedEffect
        }

        val previousRendered = streamingResponseTextForRender.orEmpty()
        val appendedDelta = if (latestText.startsWith(previousRendered)) {
            latestText.substring(previousRendered.length)
        } else {
            latestText
        }
        val shouldRefreshRenderText = shouldRefreshRender(
            prev = previousRendered,
            next = latestText,
            isStreaming = isInferenceRunningUi,
        )

        if (shouldRefreshRenderText) {
            if (BuildConfig.DEBUG && isLocalInferenceRunning) {
                localStreamingUiMetricsForDev.recordRenderUpdate()
            }
            streamingResponseTextForRender = latestText
        }
    }
    LaunchedEffect(effectiveChatId, BuildConfig.DEBUG) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        devUiAliveSeconds = 0
        while (true) {
            delay(1000)
            devUiAliveSeconds += 1
        }
    }
    val devStreamingTailLimitEnabled = BuildConfig.DEBUG && DEV_STREAMING_RENDER_TAIL_LIMIT_ENABLED
    val isStreamingRenderActive =
        isInferenceRunningUi || !localStreamingResponseText.isNullOrBlank()
    val assistantRenderTailLimitChars = DEV_STREAMING_RENDER_TAIL_LIMIT_CHARS
    val streamingResponseTextForDisplay = (
        streamingResponseTextForRender ?: streamingResponseText
        )?.let { renderText ->
        val shouldTrimForRender = devStreamingTailLimitEnabled || isStreamingRenderActive
        if (!shouldTrimForRender) return@let sanitizeAssistantMessageForDisplay(renderText)
        buildAssistantDisplayText(
            originalMessage = renderText,
            tailLimitChars = assistantRenderTailLimitChars,
        ).text
    }
    val devStreamingDisplayLineCount = remember(streamingResponseTextForDisplay) {
        streamingResponseTextForDisplay?.lineSequence()?.count() ?: 0
    }

    fun logStreamTrace(message: String) {
        Log.i("ChatScreen", message)
        appendLocalReflectionTrace(context, message)
    }

    fun debugLocalUiTrace(label: String, extra: String = "") {
        if (!BuildConfig.DEBUG) return
        val suffix = if (extra.isNotBlank()) " $extra" else ""
        val message = "[LOCAL_UI] $label$suffix"
        Log.i("ChatScreen", message)
        appendLocalReflectionTrace(context.applicationContext, message)
    }

    fun resetStreamingSpeechState(clearPlaybackFlag: Boolean = true) {
        streamingSpeechBuffer = ""
        streamingSpeechLastConsumedLength = 0
        streamingSpeechStartedForMessageId = null
        if (clearPlaybackFlag) {
            isStreamingSentencePlaybackActive = false
        }
    }

    fun stopTtsWithCleanup(
        suppressedMessageId: Int?,
        armTapGuards: Boolean,
    ) {
        suppressedTtsAssistantMessageId = suppressedMessageId

        ttsController.stop()
        viewModel.stopTtsPlayback()
        resetStreamingSpeechState()
        currentSpeakingAssistantMessageId = null
        stopButtonOwnerAssistantMessageId = null
        stopButtonOwnerSetAtMs = null

        pendingStopButtonOwnerClearJob?.cancel()
        pendingStopButtonOwnerClearJob = null
        pendingStopUiCooldownClearJob?.cancel()
        pendingStopUiCooldownClearJob = null
        pendingReplaySuppressClearJob?.cancel()
        pendingReplaySuppressClearJob = null

        if (armTapGuards) {
            ttsTapGuardEpoch += 1
            val guardEpoch = ttsTapGuardEpoch

            if (suppressedMessageId != null) {
                stopUiCooldownAssistantMessageId = suppressedMessageId
                suppressReplayAssistantMessageId = suppressedMessageId

                pendingStopUiCooldownClearJob = coroutineScope.launch {
                    delay(250)
                    if (
                        ttsTapGuardEpoch == guardEpoch &&
                        stopUiCooldownAssistantMessageId == suppressedMessageId
                    ) {
                        stopUiCooldownAssistantMessageId = null
                    }
                }

                pendingReplaySuppressClearJob = coroutineScope.launch {
                    delay(300)
                    if (
                        ttsTapGuardEpoch == guardEpoch &&
                        suppressReplayAssistantMessageId == suppressedMessageId
                    ) {
                        suppressReplayAssistantMessageId = null
                    }
                }
            } else {
                stopUiCooldownAssistantMessageId = null
                suppressReplayAssistantMessageId = null
            }
        } else {
            stopUiCooldownAssistantMessageId = null
            suppressReplayAssistantMessageId = null
        }
    }

    fun resetStreamingAssistantPlaceholderId(reason: String) {
        streamingGuardEpoch += 1
        val previousId = streamingAssistantMessageId
        if (previousId != null) {
            logStreamTrace("STREAM reset placeholder id from $previousId to null reason=$reason")
        }
        streamingAssistantMessageId = null
        lastPersistedStreamingAssistantText = null
    }

    fun cleanupDevQairt244NpuUiState(reason: String) {
        localStreamingResponseText = null
        showDelayedLocalRespondingPlaceholder = false
        resetStreamingSpeechState()
        resetStreamingAssistantPlaceholderId(reason = reason)
        pendingStopButtonOwnerClearJob?.cancel()
        pendingStopButtonOwnerClearJob = null
        pendingStopUiCooldownClearJob?.cancel()
        pendingStopUiCooldownClearJob = null
        pendingReplaySuppressClearJob?.cancel()
        pendingReplaySuppressClearJob = null
        suppressReplayAssistantMessageId = null
        stopUiCooldownAssistantMessageId = null
        currentSpeakingAssistantMessageId = null
        stopButtonOwnerAssistantMessageId = null
        stopButtonOwnerSetAtMs = null
        didReceiveRealLocalPartial = false
        realLocalPartialChunkCount = 0
        localStopRequested = false
        localInferenceEngineState = LocalInferenceEngineState.READY
        viewModel.resetUiState()
        isLocalInferenceRunning = false
        localInferenceJob = null
        File(context.applicationContext.filesDir, "qairt244_dev_npu_ui_cleanup_state.txt").writeText(
            listOf(
                "reason=$reason",
                "ui_cleanup_is_local_inference_running=false",
                "ui_cleanup_local_job_active=false",
                "ui_cleanup_local_stop_requested=false",
                "ui_cleanup_duplicate_guard=false",
                "ui_cleanup_streaming_assistant_placeholder=false",
                "ui_cleanup_stop_owner=false",
                "ui_cleanup_show_delayed_local_responding_placeholder=false",
                "ui_cleanup_local_inference_engine_state=READY",
                "ui_cleanup_reset_ui_state_called=true",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    suspend fun resolveLocalPreparingUiState(): LocalInferenceEngineState {
        val hasHeldEngine = localInferenceEngineHolder.getDevDiagnosticSnapshot().heldEngineHash != null
        return if (hasHeldEngine) {
            LocalInferenceEngineState.READY
        } else {
            LocalInferenceEngineState.PREPARING
        }
    }

    fun isTtsSuppressedForAssistant(messageId: Int?): Boolean {
        return messageId != null && suppressedTtsAssistantMessageId == messageId
    }

    suspend fun upsertStreamingAssistantPlaceholder(chatId: Int, response: String): Int? {
        val normalizedResponse = response.trim()
        if (normalizedResponse.isBlank()) return streamingAssistantMessageId

        val existingId = streamingAssistantMessageId
        if (existingId != null && lastPersistedStreamingAssistantText == normalizedResponse) {
            logStreamTrace("STREAM placeholder skip sameText")
            return existingId
        }
        if (existingId == null) {
            val placeholderMessage = createAssistantMessage(
                chatId = chatId,
                response = normalizedResponse,
            )
            val insertedId = viewModel.insertAssistantMessageAndReturnId(placeholderMessage).toInt()
            streamingAssistantMessageId = insertedId
            lastPersistedStreamingAssistantText = normalizedResponse
            streamingSpeechStartedForMessageId = insertedId
            currentSpeakingAssistantMessageId = insertedId
            if (!isTtsSuppressedForAssistant(insertedId)) {
                stopButtonOwnerAssistantMessageId = insertedId
                stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
            }
            logStreamTrace("STREAM placeholder insert id=$insertedId")
            return insertedId
        }

        val existingMessage = viewModel.getMessageById(existingId)
        if (existingMessage?.message == normalizedResponse) {
            lastPersistedStreamingAssistantText = normalizedResponse
            logStreamTrace("STREAM placeholder skip sameText")
            return existingId
        }
        val updateTarget = existingMessage?.copy(message = normalizedResponse)
            ?: createAssistantMessage(chatId = chatId, response = normalizedResponse).copy(messageID = existingId)
        viewModel.updateMessage(updateTarget)
        lastPersistedStreamingAssistantText = normalizedResponse
        streamingSpeechStartedForMessageId = existingId
        currentSpeakingAssistantMessageId = existingId
        if (!isTtsSuppressedForAssistant(existingId)) {
            stopButtonOwnerAssistantMessageId = existingId
            stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
        }
        logStreamTrace("STREAM placeholder update id=$existingId len=${normalizedResponse.length}")
        return existingId
    }

    suspend fun upsertStreamingAssistantPlaceholderSerialized(chatId: Int, response: String): Int? {
        return streamingAssistantPersistMutex.withLock {
            upsertStreamingAssistantPlaceholder(chatId = chatId, response = response)
        }
    }

    suspend fun finalizeStreamingAssistantMessage(
        chatId: Int,
        response: String,
        latestInferenceStats: InferenceStats? = null,
        localSourceSummary: String? = null,
        imageInputCount: Int? = null,
        generationTimeMs: Long? = null,
    ): Int? {
        // finalize 経路は「保存してよい最終本文」のみを受け取る想定。
        val finalizedResponseForPersist = buildFinalizedStreamingResponseForPersist(
            response = response,
            markdownStreamingMode = markdownStreamingMode,
            onMarkdownRepair = {
                if (BuildConfig.DEBUG) {
                    localStreamingUiMetricsForDev.recordMarkdownRepair()
                }
            },
        )
        if (finalizedResponseForPersist.isBlank()) return streamingAssistantMessageId
        if (finalizedResponseForPersist == "コード生成中…") {
            logStreamTrace("STREAM final skip displayOnlyText")
            return streamingAssistantMessageId
        }
        // finalize後の本文をUI表示系にも反映し、streaming途中本文の残留を防ぐ。
        streamingResponseTextForRender = finalizedResponseForPersist

        val finalPayload = createAssistantMessage(
            chatId = chatId,
            response = finalizedResponseForPersist,
            latestInferenceStats = latestInferenceStats,
            localSourceSummary = localSourceSummary,
            imageInputCount = imageInputCount,
            generationTimeMs = generationTimeMs,
        )
        val existingId = streamingAssistantMessageId
        logStreamTrace("STREAM final path existingId=$existingId")
        if (existingId == null) {
            val insertedId = viewModel.insertAssistantMessageAndReturnId(finalPayload).toInt()
            lastPersistedStreamingAssistantText = finalizedResponseForPersist
            if (latestInferenceStats != null) {
                immediateInferenceStatsByMessageId[insertedId] = latestInferenceStats
            }
            logStreamTrace("STREAM final insert id=$insertedId fallbackNoPlaceholder=true")
            return insertedId
        }

        val existingMessage = viewModel.getMessageById(existingId)
        val updatedMessage = if (existingMessage != null) {
            existingMessage.copy(
                message = finalPayload.message,
                completionTokens = finalPayload.completionTokens,
                generationTimeMs = finalPayload.generationTimeMs,
                generationDurationNs = finalPayload.generationDurationNs,
                evalDurationNs = finalPayload.evalDurationNs,
                loadDurationNs = finalPayload.loadDurationNs,
                promptEvalDurationNs = finalPayload.promptEvalDurationNs,
                modelName = finalPayload.modelName,
                inputTokens = finalPayload.inputTokens,
                totalTokens = finalPayload.totalTokens,
                tokensPerSecond = finalPayload.tokensPerSecond,
                inferenceTimeSec = finalPayload.inferenceTimeSec,
                finishReason = finalPayload.finishReason,
                localSourceSummary = finalPayload.localSourceSummary,
                timeToFirstTokenMs = finalPayload.timeToFirstTokenMs,
                imageInputCount = finalPayload.imageInputCount,
            )
        } else {
            finalPayload.copy(messageID = existingId)
        }
        viewModel.updateMessage(updatedMessage)
        lastPersistedStreamingAssistantText = finalizedResponseForPersist
        if (latestInferenceStats != null) {
            immediateInferenceStatsByMessageId[existingId] = latestInferenceStats
        }
        logStreamTrace("STREAM final update id=$existingId")
        return existingId
    }

    suspend fun finalizeStreamingAssistantMessageSerialized(
        chatId: Int,
        response: String,
        latestInferenceStats: InferenceStats? = null,
        localSourceSummary: String? = null,
        imageInputCount: Int? = null,
        generationTimeMs: Long? = null,
    ): Int? {
        return streamingAssistantPersistMutex.withLock {
            finalizeStreamingAssistantMessage(
                chatId = chatId,
                response = response,
                latestInferenceStats = latestInferenceStats,
                localSourceSummary = localSourceSummary,
                imageInputCount = imageInputCount,
                generationTimeMs = generationTimeMs,
            )
        }
    }

    fun sanitizeTextForTts(text: String): String {
        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("☺", "")
            .replace("☻", "")
            .replace("*", "")
            .lineSequence()
            .joinToString("\n") { line ->
                line.replace(Regex("[ \\t]+"), " ").trimEnd()
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (normalized.length < 2) return ""
        if (normalized.all { !it.isLetterOrDigit() }) return ""
        return normalized
    }

    fun sanitizeStreamingTextForTts(text: String): String {
        var insideFencedCodeBlock = false
        val filtered = text
            .lineSequence()
            .map { it.trim() }
            .filterNot { line ->
                if (line.contains("```")) {
                    insideFencedCodeBlock = !insideFencedCodeBlock
                    true
                } else {
                    insideFencedCodeBlock ||
                        line.isBlank() ||
                        line == "コード生成中…" ||
                        line.matches(Regex("^[`*_#>\\-\\s]+$")) ||
                        line.matches(Regex("^[\\p{Punct}\\s]+$")) ||
                        line.matches(Regex(".*[{}();=<>\\[\\]].*"))
                }
            }
            .joinToString(separator = " ")
        return sanitizeTextForTts(filtered)
    }

    suspend fun maybeReleaseHeldEngineForTtsPlayback() {
        val memorySnapshot = withContext(Dispatchers.Default) {
            captureTtsMemorySnapshot(context.applicationContext)
        }
        val decision = decideHeldEngineReleaseForTts(memorySnapshot)
        if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
            devDebugText = buildTtsMemoryDecisionDebugText(
                snapshot = memorySnapshot,
                decision = decision,
            )
        }
        if (decision.shouldReleaseHeldEngine) {
            withContext(Dispatchers.IO) {
                localInferenceEngineHolder.notifyLifecycleEvent(reason = "tts-playback")
            }
        }
    }

    fun consumeStreamingSentenceAndSpeak(fullText: String) {
        if (!ttsEnabled) return
        if (fullText.contains("```") || fullText.contains("```python") || fullText.contains("```bash")) {
            isStreamingSentencePlaybackActive = false
            streamingSpeechLastConsumedLength = fullText.length
            streamingSpeechStartedForMessageId = null
            viewModel.stopTtsPlayback()
            return
        }
        val targetMessageId = streamingSpeechStartedForMessageId
        if (targetMessageId != null && suppressedTtsAssistantMessageId == targetMessageId) return
        if (fullText.length < streamingSpeechLastConsumedLength) {
            streamingSpeechLastConsumedLength = 0
        }
        streamingSpeechBuffer = fullText
        if (streamingSpeechLastConsumedLength >= fullText.length) return
        val remaining = fullText.substring(streamingSpeechLastConsumedLength)
        val sentenceBreakIndex = findStreamingTtsBreakIndex(remaining)
        if (sentenceBreakIndex < 0) return
        val speakTarget = remaining.substring(0, sentenceBreakIndex + 1)
        val normalized = sanitizeStreamingTextForTts(speakTarget)
        if (normalized.isNotEmpty() && !ttsController.isInCooldown()) {
            streamingSpeechStartedForMessageId?.let { messageId ->
                currentSpeakingAssistantMessageId = messageId
                if (!isTtsSuppressedForAssistant(messageId)) {
                    stopButtonOwnerAssistantMessageId = messageId
                    stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
                }
            }
            isStreamingSentencePlaybackActive = true
            ttsController.speakQueued(normalized)
        }
        streamingSpeechLastConsumedLength += sentenceBreakIndex + 1
    }

    fun speakStreamingTailIfNeeded(fullText: String) {
        if (!ttsEnabled) return
        if (fullText.contains("```") || fullText.contains("```python") || fullText.contains("```bash")) {
            isStreamingSentencePlaybackActive = false
            streamingSpeechLastConsumedLength = fullText.length
            streamingSpeechStartedForMessageId = null
            viewModel.stopTtsPlayback()
            return
        }
        val targetMessageId = streamingSpeechStartedForMessageId
        if (targetMessageId != null && suppressedTtsAssistantMessageId == targetMessageId) return
        val safeConsumed = streamingSpeechLastConsumedLength.coerceIn(0, fullText.length)
        val remaining = fullText.substring(safeConsumed)
        val normalized = sanitizeStreamingTextForTts(remaining)
        if (normalized.isNotEmpty() && !ttsController.isInCooldown()) {
            streamingSpeechStartedForMessageId?.let { messageId ->
                currentSpeakingAssistantMessageId = messageId
                if (!isTtsSuppressedForAssistant(messageId)) {
                    stopButtonOwnerAssistantMessageId = messageId
                    stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
                }
            }
            isStreamingSentencePlaybackActive = true
            ttsController.speakQueued(normalized)
        }
    }

    val effectiveStreamingSentenceTtsEnabled = ttsEnabled && devEnableStreamingSentenceTts

    LaunchedEffect(
        effectiveStreamingSentenceTtsEnabled,
        isInferenceRunningUi,
        streamingResponseTextForRender,
        streamingResponseText,
    ) {
        if (!effectiveStreamingSentenceTtsEnabled || !isInferenceRunningUi) return@LaunchedEffect
        val fullText = streamingResponseText ?: streamingResponseTextForRender ?: return@LaunchedEffect
        if (fullText.isBlank()) return@LaunchedEffect
        consumeStreamingSentenceAndSpeak(fullText)
    }

    LaunchedEffect(effectiveStreamingSentenceTtsEnabled) {
        if (!effectiveStreamingSentenceTtsEnabled) {
            resetStreamingSpeechState()
        }
    }

    LaunchedEffect(ttsEnabled) {
        if (!ttsEnabled) {
            stopTtsWithCleanup(
                suppressedMessageId = null,
                armTapGuards = false,
            )
        }
    }

    LaunchedEffect(
        isTtsSpeaking,
        isTtsPlaying,
        isInferenceRunningUi,
        isStreamingSentencePlaybackActive,
        stopButtonOwnerAssistantMessageId,
    ) {
        if (isTtsSpeaking || isTtsPlaying) {
            keepTtsTalkingInHeader = true
            return@LaunchedEffect
        }
        val hasActiveTtsContext =
            isStreamingSentencePlaybackActive ||
                stopButtonOwnerAssistantMessageId != null
        if (!isInferenceRunningUi || !hasActiveTtsContext) {
            keepTtsTalkingInHeader = false
            return@LaunchedEffect
        }

        keepTtsTalkingInHeader = true
        delay(TTS_HEADER_TALKING_GRACE_MS)
        if (!isTtsSpeaking && !isTtsPlaying) {
            keepTtsTalkingInHeader = false
        }
    }

    val latestIsInferenceRunningUi by rememberUpdatedState(isInferenceRunningUi)
    val latestIsTtsSpeaking by rememberUpdatedState(isTtsSpeaking)

    DisposableEffect(ttsController) {
        ttsController.setOnPlaybackStateChanged { isPlaying ->
            viewModel.onTtsPlaybackChanged(isPlaying)
            if (isPlaying) {
                pendingStopButtonOwnerClearJob?.cancel()
                pendingStopButtonOwnerClearJob = null
                return@setOnPlaybackStateChanged
            }
            if (!isPlaying) {
                currentSpeakingAssistantMessageId = null
                if (isStreamingSentencePlaybackActive && latestIsInferenceRunningUi) {
                    return@setOnPlaybackStateChanged
                }
                pendingStopButtonOwnerClearJob?.cancel()
                pendingStopButtonOwnerClearJob = coroutineScope.launch {
                    delay(220)
                    if (!latestIsTtsSpeaking && !latestIsInferenceRunningUi && !ttsController.isSpeaking.value) {
                        isStreamingSentencePlaybackActive = false
                        stopButtonOwnerAssistantMessageId = null
                        stopButtonOwnerSetAtMs = null
                    }
                }
            }
        }
        onDispose {
            viewModel.stopTtsPlayback()
            pendingStopButtonOwnerClearJob?.cancel()
            pendingStopButtonOwnerClearJob = null
            pendingReplaySuppressClearJob?.cancel()
            pendingReplaySuppressClearJob = null
            pendingStopUiCooldownClearJob?.cancel()
            pendingStopUiCooldownClearJob = null
            suppressReplayAssistantMessageId = null
            stopUiCooldownAssistantMessageId = null
            currentSpeakingAssistantMessageId = null
            stopButtonOwnerAssistantMessageId = null
            stopButtonOwnerSetAtMs = null
            resetStreamingSpeechState()
            ttsController.shutdown()
        }
    }

    LaunchedEffect(
        isTtsSpeaking,
        isInferenceRunningUi,
        isStreamingSentencePlaybackActive,
        stopButtonOwnerAssistantMessageId,
    ) {
        val ownerMessageId = stopButtonOwnerAssistantMessageId ?: return@LaunchedEffect
        if (isTtsSpeaking || ttsController.isSpeaking.value || isInferenceRunningUi) return@LaunchedEffect

        val ownerAgeMs = SystemClock.elapsedRealtime() - (stopButtonOwnerSetAtMs ?: 0L)
        if (ownerAgeMs < 450L) {
            delay(450L - ownerAgeMs)
        }
        delay(120L)

        if (
            stopButtonOwnerAssistantMessageId == ownerMessageId &&
            !isTtsSpeaking &&
            !ttsController.isSpeaking.value &&
            !isInferenceRunningUi
        ) {
            isStreamingSentencePlaybackActive = false
            currentSpeakingAssistantMessageId = null
            stopButtonOwnerAssistantMessageId = null
            stopButtonOwnerSetAtMs = null
        }
    }

    LaunchedEffect(chatId) {
        pendingStopButtonOwnerClearJob?.cancel()
        pendingStopButtonOwnerClearJob = null
        pendingReplaySuppressClearJob?.cancel()
        pendingReplaySuppressClearJob = null
        pendingStopUiCooldownClearJob?.cancel()
        pendingStopUiCooldownClearJob = null
        suppressReplayAssistantMessageId = null
        stopUiCooldownAssistantMessageId = null
        currentSpeakingAssistantMessageId = null
        stopButtonOwnerAssistantMessageId = null
        stopButtonOwnerSetAtMs = null
        resetStreamingSpeechState()
        resetStreamingAssistantPlaceholderId(reason = "chat-change")
        effectiveChatId?.let { currentChatId ->
            localInferenceEngineHolder.resetConversation(
                chatId = currentChatId,
                reason = "chat-change",
            )
        }
        if (chatId != null) {
            suppressAutoNewChat = false
            suppressChatContentWhileClosingDrawer = false
        }
    }

    LaunchedEffect(pendingNavigateChatId) {
        val targetChatId = pendingNavigateChatId ?: return@LaunchedEffect
        try {
            if (drawerState.isOpen) {
                runCatching { drawerState.close() }
            }
            if (RuntimeFlags.isUiTestRuntime()) {
                yield()
            }
            navHostController.navigate(Routes.chat(targetChatId)) {
                launchSingleTop = true
            }
        } finally {
            pendingNavigateChatId = null
            suppressChatContentWhileClosingDrawer = false
        }
    }

    LaunchedEffect(chatId, chats, pendingNavigateChatId) {
        val resolvedChatId = resolveDefaultChatId(chatId, chats)
        if (pendingNavigateChatId == null) {
            effectiveChatId = resolvedChatId
        }

        if (
            pendingNavigateChatId == null &&
            shouldAutoCreateNewChat(suppressAutoNewChat, resolvedChatId, isCreatingChat)
        ) {
            isCreatingChat = true
            val newChatId = viewModel.insertChatAndReturnId(
                Chat(title = "New chat", titleSource = TitleSource.TEMP)
            )
            effectiveChatId = newChatId
            pendingNavigateChatId = newChatId
        }

        if (resolvedChatId != null) {
            isCreatingChat = false
            suppressAutoNewChat = false
        }
    }

    LaunchedEffect(sortedChats) {
        if (sortedChats.isEmpty()) {
            latestMessagePreviewByChatId = emptyMap()
            return@LaunchedEffect
        }
        val latestMessages = viewModel.getLatestMessagesByChatIds(sortedChats.map { it.chatId })
        latestMessagePreviewByChatId = latestMessages.associate { latestMessage ->
            latestMessage.chatId to formatChatPreview(latestMessage.message)
        }
    }

    LaunchedEffect(availableModels, selectedModel) {
        if (availableModels.size == 1) {
            val singleModelName = availableModels.first().name
            if (selectedModel != singleModelName) {
                viewModel.updateSelectedModel(singleModelName)
            }
        }
    }

    LaunchedEffect(uiState, effectiveChatId) {
        if (toggle) {
            val currentChatId = effectiveChatId
            val guardEpoch = streamingGuardEpoch
            when (uiState) {
                is UiState.Success -> {
                    if (remoteStopRequested) {
                        placeholder = "Enter your prompt..."
                        pendingAssistantImageInputCount = null
                        toggle = false
                        remoteRequestJob = null
                        resetStreamingAssistantPlaceholderId(reason = "stop")
                        viewModel.resetUiState()
                        return@LaunchedEffect
                    }
                    val response = (uiState as UiState.Success).outputText
                    var assistantId: Int? = null
                    if (currentChatId != null) {
                        if (guardEpoch != streamingGuardEpoch) return@LaunchedEffect
                        assistantId = finalizeStreamingAssistantMessageSerialized(
                            chatId = currentChatId,
                            response = response,
                            latestInferenceStats = latestInferenceStats,
                            imageInputCount = pendingAssistantImageInputCount,
                        )
                        if (assistantId != null) {
                            streamingSpeechStartedForMessageId = assistantId
                        }
                    }
                    placeholder = "Enter your prompt..."
                    pendingAssistantImageInputCount = null
                    toggle = false
                    remoteRequestJob = null
                    resetStreamingAssistantPlaceholderId(reason = "success")
                    viewModel.resetUiState()
                    yield()
                    if (effectiveStreamingSentenceTtsEnabled) {
                        maybeReleaseHeldEngineForTtsPlayback()
                        speakStreamingTailIfNeeded(response)
                        resetStreamingSpeechState(clearPlaybackFlag = false)
                    } else if (
                        ttsEnabled &&
                        assistantId != null &&
                        suppressedTtsAssistantMessageId != assistantId &&
                        !ttsController.isInCooldown()
                    ) {
                        sanitizeTextForTts(response).takeIf { it.isNotEmpty() }?.let { speechText ->
                            currentSpeakingAssistantMessageId = assistantId
                            if (!isTtsSuppressedForAssistant(assistantId)) {
                                stopButtonOwnerAssistantMessageId = assistantId
                                stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
                            }
                            maybeReleaseHeldEngineForTtsPlayback()
                            ttsController.speak(speechText)
                        }
                    }
                    resetStreamingSpeechState()
                }

                is UiState.Error -> {
                    if (remoteStopRequested) {
                        placeholder = "Enter your prompt..."
                        pendingAssistantImageInputCount = null
                        toggle = false
                        remoteRequestJob = null
                        resetStreamingAssistantPlaceholderId(reason = "stop")
                        viewModel.resetUiState()
                        return@LaunchedEffect
                    }
                    if (currentChatId != null) {
                        if (guardEpoch != streamingGuardEpoch) return@LaunchedEffect
                        val assistantId = finalizeStreamingAssistantMessageSerialized(
                            chatId = currentChatId,
                            response = (uiState as UiState.Error).errorMessage,
                        )
                        if (assistantId != null) {
                            streamingSpeechStartedForMessageId = assistantId
                        }
                    }
                    placeholder = "Enter your prompt..."
                    pendingAssistantImageInputCount = null
                    toggle = false
                    remoteRequestJob = null
                    resetStreamingSpeechState()
                    resetStreamingAssistantPlaceholderId(reason = "error")
                    viewModel.resetUiState()
                }

                is UiState.Streaming -> {
                    if (remoteStopRequested) {
                        placeholder = "Enter your prompt..."
                        pendingAssistantImageInputCount = null
                        toggle = false
                        remoteRequestJob = null
                        resetStreamingSpeechState()
                        resetStreamingAssistantPlaceholderId(reason = "stop")
                        viewModel.resetUiState()
                    } else {
                        val partialText = (uiState as UiState.Streaming).partialText.trim()
                        if (currentChatId != null && partialText.isNotBlank()) {
                            if (guardEpoch != streamingGuardEpoch) return@LaunchedEffect
                            upsertStreamingAssistantPlaceholderSerialized(
                                chatId = currentChatId,
                                response = partialText,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(lamiUiState.lastInteractionTimeMs, lamiUiState.state) {
        val referenceTime = lamiUiState.lastInteractionTimeMs
        val idleTimeoutMs = 6_000L
        delay(idleTimeoutMs)
        viewModel.moveToIdleIfStale(referenceTime, idleTimeoutMs)
    }

    LaunchedEffect(errorMessage, remoteStopRequested) {
        if (errorMessage == null) return@LaunchedEffect
        if (remoteStopRequested && isStopCancellationLikeMessage(errorMessage)) {
            Log.i("ChatScreen", "Suppressed snackbar for remote stop cancellation: $errorMessage")
            return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(
            message = errorMessage,
            duration = SnackbarDuration.Short,
            actionLabel = "ERROR"
        )
    }

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imeOnlyPx = (imeBottomPx - navBottomPx).coerceAtLeast(0)
    val bottomDp = with(density) { imeOnlyPx.toDp() }

    val createNewChatAndNavigate: () -> Unit = {
        coroutineScope.launch {
            val newChatId = viewModel.insertChatAndReturnId(
                Chat(title = "New chat", titleSource = TitleSource.TEMP)
            )
            effectiveChatId = newChatId
            pendingNavigateChatId = newChatId
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // drawerBg は必ず @Composable スコープ（Home() 内）で評価すること
            val drawerBg = MaterialTheme.colorScheme.surface
            val drawerColor = drawerBg

            // Golden ratio based midpoint (1/φ)
            val fadeMidPos = 0.618f
            val fadeMidAlpha = 0.55f
            val fadeMaxAlpha = 0.68f

            // 下端フェード（透明→濃い）
            val bottomFadeStops = arrayOf(
                0.0f to drawerColor.copy(alpha = 0.0f),
                fadeMidPos to drawerColor.copy(alpha = fadeMidAlpha),
                1.0f to drawerColor.copy(alpha = fadeMaxAlpha),
            )

            // 上端フェード（濃い→透明）※ bottom の反転
            val topFadeStops = arrayOf(
                0.0f to drawerColor.copy(alpha = fadeMaxAlpha),
                fadeMidPos to drawerColor.copy(alpha = fadeMidAlpha),
                1.0f to drawerColor.copy(alpha = 0.0f),
            )
            ModalDrawerSheet(
                // 上：Drawer 側のデフォルト safe drawing inset を無効化して検索窓の先頭位置を詰める
                windowInsets = WindowInsets(0, 0, 0, 0),
                drawerContainerColor = drawerBg,
                drawerTonalElevation = 0.dp,
            ) {
                val newChatButtonHeight = 40.dp
                val newChatListTopGap = 0.dp
                // 下端フェード：帯感を減らすため高さを少し詰める（overlayのみでレイアウトは壊さない）
                val drawerBottomFadeHeight = 32.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth()
                        // 上：詰めすぎ防止のため最小限の top padding を残す
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    DrawerSearchPill(
                        value = chatSearchQuery,
                        onValueChange = { chatSearchQuery = it },
                        onClear = { chatSearchQuery = "" },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // 上：検索ピルと New chat ボタンの間隔を 8dp 維持する
                            .padding(top = 8.dp)
                    ) {
                        if (filteredChats.isEmpty()) {
                            Text(
                                text = "該当なし",
                                // 上：New chat ボタン下から空状態メッセージを表示する
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = newChatButtonHeight + newChatListTopGap + 12.dp,
                                    bottom = 12.dp,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    top = newChatButtonHeight + newChatListTopGap,
                                    // 最終行がフェードに被らないように bottom padding を増やす
                                    bottom = 12.dp + drawerBottomFadeHeight,
                                )
                            ) {
                                items(filteredChats, key = { it.chatId }) { chat ->
                                    val previewText = latestMessagePreviewByChatId[chat.chatId].orEmpty()
                                    TextButton(
                                        onClick = {
                                            suppressChatContentWhileClosingDrawer = true
                                            suppressAutoNewChat = true
                                            pendingNavigateChatId = chat.chatId
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = chat.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (previewText.isNotEmpty()) {
                                                Text(
                                                    text = previewText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 下端フェードは帯に見えないよう max alpha を落として midpoint を前倒し
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(drawerBottomFadeHeight)
                                .drawBehind {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = bottomFadeStops
                                        )
                                    )
                                }
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                // New chat ボタン下端より 8dp 下までフェードを伸ばし、透明側の境目をさらに目立たせない
                                // ※スレッド開始位置は維持（LazyColumn の contentPadding.top は変更しない）
                                .height(newChatButtonHeight + 6.dp)
                                .drawBehind {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = topFadeStops
                                        )
                                    )
                                }
                        )
                        ElevatedButton(
                            onClick = createNewChatAndNavigate,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .zIndex(1f)
                                .height(newChatButtonHeight),
                            elevation = ButtonDefaults.elevatedButtonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp,
                                focusedElevation = 6.dp,
                                hoveredElevation = 6.dp,
                                disabledElevation = 0.dp,
                            )
                        ) {
                            Text("New chat")
                        }
                    }
                }
            }
        }
    ) {
    if (suppressChatContentWhileClosingDrawer) {
        Box(modifier = Modifier.fillMaxSize())
    } else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
    Scaffold(
        // 上部の自動 Insets を無効化し、TopAppBar 側でのみ安全領域を制御する
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
            val topAppBarContainerColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topAppBarContainerColor)
                    .zIndex(1f)
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = topAppBarContainerColor),
                    // TopAppBar の自動 Insets は無効化し、余白発生を防ぐ
                    windowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // Chats 画面とヘッダー位置を揃えるため下余白を統一
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            measuredSpriteBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                        }
                    ) {
                        HeaderAvatar(
                            baseUrl = baseUrl,
                            selectedModel = selectedModel,
                            lastError = errorMessage,
                            lamiStatus = effectiveLamiStatusForChatUi,
                            lamiState = effectiveLamiHeaderStateForChatUi,
                            availableModels = availableModels,
                            initialAvatarSize = savedChatLamiAvatarSizeDp.dp,
                            minAvatarSize = MIN_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                            maxAvatarSize = MAX_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                            onSelectModel = { modelName ->
                                viewModel.onUserInteraction()
                                viewModel.updateSelectedModel(modelName)
                            },
                            onNavigateSettings = { navHostController.navigate(Routes.SETTINGS) },
                            selectedInferenceTarget = selectedInferenceTarget,
                            onSelectInferenceTarget = { target ->
                                selectedInferenceTarget = target
                                coroutineScope.launch {
                                    settingsPreferences.saveInferenceTarget(target)
                                }
                            },
                            localInferenceEngineState = localInferenceEngineState,
                            debugOverlayEnabled = false,
                            syncEpochMs = animationEpochMs,
                            openControlRequestKey = openLamiControlRequestKey,
                        )
                    }
                    // ヘッダー内の最小間隔だけ確保して左余白を増やさない
                    Spacer(modifier = Modifier.size(2.dp))
                        LamiHeaderStatus(
                            baseUrl = baseUrl,
                            selectedModel = selectedModel,
                            lastError = errorMessage,
                        lamiStatus = effectiveLamiStatusForChatUi,
                        lamiState = effectiveLamiHeaderStateForChatUi,
                        availableModels = availableModels,
                        onSelectModel = { modelName ->
                            viewModel.onUserInteraction()
                            viewModel.updateSelectedModel(modelName)
                        },
                            onNavigateSettings = { navHostController.navigate(Routes.SETTINGS) },
                            selectedInferenceTarget = selectedInferenceTarget,
                            localBaseModelDisplayName = localBaseModelDisplayName,
                            onSelectInferenceTarget = { target ->
                                selectedInferenceTarget = target
                                coroutineScope.launch {
                                    settingsPreferences.saveInferenceTarget(target)
                                }
                            },
                            localInferenceEngineState = localInferenceEngineState,
                            debugOverlayEnabled = false,
                            syncEpochMs = animationEpochMs,
                            initialAvatarSize = savedChatLamiAvatarSizeDp.dp,
                        minAvatarSize = MIN_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                        maxAvatarSize = MAX_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                        // title 内で HeaderAvatar を表示しているため二重表示を防ぐ
                        showAvatar = false,
                        onOpenControl = {
                            viewModel.onUserInteraction()
                            openLamiControlRequestKey += 1
                        },
                        statusTitleOverride = headerStatusTitleOverride,
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    viewModel.onUserInteraction()
                    coroutineScope.launch { drawerState.open() }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "チャット一覧",
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = {
                    viewModel.onUserInteraction()
                    navHostController.navigate(Routes.SETTINGS)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "設定",
                        modifier = Modifier.size(26.dp)
                    )
                }
                    },
                )
            }
        }, bottomBar = {
        val textMeasurer = rememberTextMeasurer()
        val maxComposerLines = 6
        val composerTextStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            fontSynthesis = FontSynthesis.None,
            fontFamily = FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface
        )
        val overlayBase = MaterialTheme.colorScheme.background
        val composerBottomGradientEnabled = true

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // IME 分のみを下余白に反映し、非表示時の余白は 0dp にする
                .padding(bottom = bottomDp)
                .onGloballyPositioned { coordinates ->
                    overlayRootTopY = coordinates.positionInRoot().y
                }
                .let { modifier ->
                    if (composerBottomGradientEnabled) {
                        modifier.drawWithContent {
                            val localTop = (measuredComposerTopY - overlayRootTopY).coerceAtLeast(0f)
                            val overlayHeight = (size.height - localTop).coerceAtLeast(1f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to overlayBase.copy(alpha = 0.0f),
                                        0.5f to overlayBase.copy(alpha = 0.5f),
                                        1.0f to overlayBase.copy(alpha = 1.0f)
                                    ),
                                    startY = localTop,
                                    endY = localTop + overlayHeight
                                ),
                                topLeft = Offset(0f, localTop),
                                size = Size(size.width, overlayHeight)
                            )

                            drawContent()
                        }
                    } else {
                        modifier
                    }
                }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Box {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 17.dp)
                    ) {
                    // Surface 内の実幅から固定要素（左右 Spacer/左右ボタン）と TextField 内部余白を差し引く
                    val availableTextWidthDp =
                        maxWidth - 0.dp - ComposerButtonSize - ComposerButtonSize - 0.dp - (4.dp * 2)
                    val availableTextWidthPx = with(density) {
                        availableTextWidthDp.coerceAtLeast(0.dp).toPx().roundToInt().coerceAtLeast(1)
                    }
                    val measuredLines by remember(userPrompt, availableTextWidthPx, composerTextStyle) {
                        derivedStateOf {
                            if (userPrompt.isEmpty()) {
                                1
                            } else {
                                textMeasurer.measure(
                                    text = AnnotatedString(userPrompt),
                                    style = composerTextStyle,
                                    softWrap = true,
                                    maxLines = maxComposerLines,
                                    overflow = TextOverflow.Clip,
                                    constraints = Constraints(maxWidth = availableTextWidthPx)
                                ).lineCount.coerceIn(1, maxComposerLines)
                            }
                        }
                    }
                    val composerShape = RoundedCornerShape(ComposerPillRadius)
                    Surface(
                        shape = composerShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                            .onGloballyPositioned { coordinates ->
                                measuredComposerTopY = coordinates.positionInRoot().y
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (selectedImageUris.isNotEmpty()) {
                                    AttachmentPreviewRow(
                                        uris = selectedImageUris,
                                        onOpen = { index ->
                                            composerViewerUriStrings = selectedImageUriStrings.toList()
                                            composerViewerInitialIndex = index
                                        },
                                        onRemoveAt = { removeIndex ->
                                            selectedImageUriStrings = selectedImageUriStrings.filterIndexed { index, _ ->
                                                index != removeIndex
                                            }
                                        },
                                        inComposer = true,
                                    )
                                    // サムネイルと入力行を視認分離する最小限の余白
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = ComposerMinHeight),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // 左ボタンを外側へ寄せるための最小余白
                                    Spacer(modifier = Modifier.width(0.dp))

                                    IconButton(
                                        onClick = { attachSheetOpen = true },
                                        modifier = Modifier
                                            .size(ComposerButtonSize)
                                            .align(Alignment.Bottom)
                                            .clip(CircleShape)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(ComposerButtonVisualSize)
                                                .clip(CircleShape)
                                                .background(Color.LightGray.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Tools",
                                                modifier = Modifier.size(ComposerButtonIconVisualSize)
                                            )
                                        }
                                    }

                                    BasicTextField(
                                        value = userPrompt,
                                        onValueChange = { newValue ->
                                            userPrompt = newValue
                                            viewModel.onUserInteraction()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .align(Alignment.CenterVertically)
                                            .heightIn(min = 44.dp, max = 180.dp),
                                        singleLine = false,
                                        maxLines = maxComposerLines,
                                        textStyle = composerTextStyle,
                                        interactionSource = interactionSource,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { innerTextField ->
                                            OutlinedTextFieldDefaults.DecorationBox(
                                                value = userPrompt,
                                                innerTextField = innerTextField,
                                                enabled = true,
                                                singleLine = false,
                                                visualTransformation = VisualTransformation.None,
                                                interactionSource = interactionSource,
                                                placeholder = {
                                                    Text(
                                                        placeholder,
                                                        style = LamiTypographyTokens.chatPlaceholder(),
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Color.Transparent,
                                                    focusedBorderColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedContainerColor = Color.Transparent
                                                ),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                                            )
                                        }
                                    )

                                    IconButton(
                                        enabled = if (isInferenceRunningUi) {
                                            true
                                        } else {
                                            !selectedModel.isNullOrBlank() &&
                                                (userPrompt.isNotEmpty() || selectedImageUriStrings.isNotEmpty())
                                        },
                                        onClick = {
                                            viewModel.onUserInteraction()
                                                if (isInferenceRunningUi) {
                                                    if (isLocalRunningRaw) {
                                                        localStopRequested = true
                                                        localInferenceJob?.cancel()
                                                        localInferenceJob = null
                                                        effectiveChatId?.let { currentChatId ->
                                                            coroutineScope.launch {
                                                                localInferenceEngineHolder.resetConversation(
                                                                chatId = currentChatId,
                                                                reason = "stop",
                                                            )
                                                        }
                                                    }
                                                    localStreamingResponseText = null
                                                    didReceiveRealLocalPartial = false
                                                    realLocalPartialChunkCount = 0
                                                    isLocalInferenceRunning = false
                                                    stopTtsWithCleanup(
                                                        suppressedMessageId = stopButtonOwnerAssistantMessageId
                                                            ?: currentSpeakingAssistantMessageId
                                                            ?: streamingSpeechStartedForMessageId,
                                                        armTapGuards = false,
                                                    )
                                                    resetStreamingAssistantPlaceholderId(reason = "stop")
                                                    return@IconButton
                                                }
                                                if (isServerRunningRaw) {
                                                    remoteStopRequested = true
                                                    remoteRequestJob?.cancel()
                                                    viewModel.cancelRemoteRequest()
                                                    remoteRequestJob = null
                                                    pendingAssistantImageInputCount = null
                                                    placeholder = "Enter your prompt..."
                                                    toggle = false
                                                    stopTtsWithCleanup(
                                                        suppressedMessageId = stopButtonOwnerAssistantMessageId
                                                            ?: currentSpeakingAssistantMessageId
                                                            ?: streamingSpeechStartedForMessageId,
                                                        armTapGuards = false,
                                                    )
                                                    resetStreamingAssistantPlaceholderId(reason = "stop")
                                                    viewModel.resetUiState()
                                                    return@IconButton
                                                }
                                            }
                                            if (selectedModel.isNullOrBlank()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar(
                                                        message = "モデルを選択してください",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                                return@IconButton
                                            }

                                            Log.i("ChatScreen", "send entry selectedInferenceTarget=$selectedInferenceTarget")
                                            when (selectedInferenceTarget) {
                                                InferenceTarget.SERVER -> {
                                                    val currentChatId = effectiveChatId
                                                    if (currentChatId != null) {
                                                        val requestPrompt = userPrompt
                                                        val requestAttachmentUris = selectedImageUris
                                                        pendingAssistantImageInputCount = requestAttachmentUris.size
                                                        if (requestPrompt.isNotEmpty() || requestAttachmentUris.isNotEmpty()) {
                                                            placeholder = "I'm thinking ... "
                                                            toggle = true
                                                        }
                                                        stopTtsWithCleanup(
                                                            suppressedMessageId = stopButtonOwnerAssistantMessageId
                                                                ?: currentSpeakingAssistantMessageId
                                                                ?: streamingSpeechStartedForMessageId,
                                                            armTapGuards = false,
                                                        )
                                                        prompt = requestPrompt
                                                        remoteStopRequested = false
                                                        remoteRequestJob = coroutineScope.launch {
                                                            try {
                                                                viewModel.sendPrompt(
                                                                    prompt = requestPrompt,
                                                                    model = selectedModel,
                                                                    attachmentUris = requestAttachmentUris,
                                                                    context = context.applicationContext,
                                                                    onAttachmentPrepared = { savedAttachmentUriStrings ->
                                                                        if (requestPrompt.isNotEmpty() || !savedAttachmentUriStrings.isNullOrEmpty()) {
                                                                            val attachmentJson = savedAttachmentUriStrings
                                                                                ?.takeIf { it.isNotEmpty() }
                                                                                ?.toAttachmentUriStringsJson()
                                                                            viewModel.insert(
                                                                                Message(
                                                                                    chatId = currentChatId,
                                                                                    message = requestPrompt,
                                                                                    isSendbyMe = true,
                                                                                    attachmentUriString = savedAttachmentUriStrings?.singleOrNull(),
                                                                                    attachmentUriStringsJson = attachmentJson,
                                                                                )
                                                                            )
                                                                        }
                                                                    },
                                                                )
                                                            } finally {
                                                                remoteRequestJob = null
                                                            }
                                                        }
                                                        prompt = ""
                                                        userPrompt = ""
                                                        selectedImageUriStrings = emptyList()
                                                    } else {
                                                        placeholder = "Setting up a new chat ..."
                                                    }
                                                }

                                                InferenceTarget.LOCAL -> {
                                                    if (isLocalInferenceRunning || localInferenceJob?.isActive == true) return@IconButton
                                                    if (selectedImageUriStrings.isNotEmpty()) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.currentSnackbarData?.dismiss()
                                                            snackbarHostState.showSnackbar(
                                                                message = "ローカル推論では画像入力はまだ未対応です",
                                                                duration = SnackbarDuration.Short,
                                                            )
                                                        }
                                                        return@IconButton
                                                    }
                                                    val requestPrompt = userPrompt
                                                    if (requestPrompt.isBlank()) return@IconButton
                                                    if (
                                                        BuildConfig.CUSTOM_BUILD_EXPERIMENT &&
                                                        devEnableQairt244Sm8750NpuRoute
                                                    ) {
                                                        // DEV-only experiment: when the toggle is OFF, execution falls through to the unchanged local route.
                                                        isLocalInferenceRunning = true
                                                        debugLocalUiTrace(
                                                            label = "DEV_QAIRT244_SM8750_NPU_SEND_TAPPED",
                                                            extra = "effectiveChatId=$effectiveChatId promptLength=${requestPrompt.length}",
                                                        )
                                                        prompt = ""
                                                        userPrompt = ""
                                                        selectedImageUriStrings = emptyList()
                                                        showDelayedLocalRespondingPlaceholder = false
                                                        localInferenceEngineState = LocalInferenceEngineState.READY
                                                        localStopRequested = false
                                                        stopTtsWithCleanup(
                                                            suppressedMessageId = stopButtonOwnerAssistantMessageId
                                                                ?: currentSpeakingAssistantMessageId
                                                                ?: streamingSpeechStartedForMessageId,
                                                            armTapGuards = false,
                                                        )
                                                        localInferenceJob = coroutineScope.launch {
                                                            var currentChatId = effectiveChatId
                                                            if (currentChatId == null) {
                                                                isCreatingChat = true
                                                                try {
                                                                    val newChatId = withContext(Dispatchers.IO) {
                                                                        viewModel.insertChatAndReturnId(
                                                                            Chat(title = "New chat", titleSource = TitleSource.TEMP)
                                                                        )
                                                                    }
                                                                    effectiveChatId = newChatId
                                                                    pendingNavigateChatId = newChatId
                                                                    currentChatId = newChatId
                                                                } finally {
                                                                    isCreatingChat = false
                                                                }
                                                            }
                                                            val resolvedChatId = currentChatId
                                                            withContext(Dispatchers.IO) {
                                                                viewModel.insert(
                                                                    Message(
                                                                        chatId = resolvedChatId,
                                                                        message = requestPrompt,
                                                                        isSendbyMe = true,
                                                                    )
                                                                )
                                                            }
                                                            isLocalInferenceRunning = true
                                                            localStreamingResponseText = null
                                                            showDelayedLocalRespondingPlaceholder = false
                                                            try {
                                                                val devResult = withContext(Dispatchers.IO) {
                                                                    runDevQairt244Sm8750NpuChatScreenRouteViaReflection(
                                                                        context = context.applicationContext,
                                                                        prompt = requestPrompt,
                                                                    )
                                                                }
                                                                val assistantText = devResult.assistantMessage.ifBlank {
                                                                    if (devResult.success) {
                                                                        devResult.output
                                                                    } else {
                                                                        "DEV NPU route failed: ${devResult.reasonCode}"
                                                                    }
                                                                }
                                                                val stats = devResult.toInferenceStats()
                                                                val sourceSummary = devResult.toLocalSourceSummary()
                                                                devDebugText = sourceSummary
                                                                if (!localStopRequested) {
                                                                    val assistantId = withContext(Dispatchers.IO) {
                                                                        viewModel.insertAssistantMessageAndReturnId(
                                                                            createAssistantMessage(
                                                                                chatId = resolvedChatId,
                                                                                response = assistantText,
                                                                                latestInferenceStats = stats,
                                                                                localSourceSummary = sourceSummary,
                                                                                generationTimeMs = devResult.elapsedMs,
                                                                            )
                                                                        ).toInt()
                                                                    }
                                                                    if (assistantId > 0) {
                                                                        immediateInferenceStatsByMessageId[assistantId] = stats
                                                                    }
                                                                }
                                                                cleanupDevQairt244NpuUiState(reason = "dev-qairt244-finish")
                                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                                snackbarHostState.showSnackbar(
                                                                    message = if (devResult.success) {
                                                                        "DEV SM8750 NPU route success"
                                                                    } else {
                                                                        "DEV NPU route failed: ${devResult.reasonCode}"
                                                                    },
                                                                    duration = SnackbarDuration.Short,
                                                                )
                                                            } catch (exception: Exception) {
                                                                devDebugText = listOf(
                                                                    "selected_route=qairt244_sm8750_dev_npu",
                                                                    "failure_stage=ui_exception",
                                                                    "stop_reason=${exception.javaClass.simpleName}",
                                                                    "required_sm8750_model_path=false",
                                                                    "fallback_used=false",
                                                                    "normal_ui_route_connected=false",
                                                                    "message=${exception.message.orEmpty()}",
                                                                ).joinToString("\n")
                                                                if (!localStopRequested) {
                                                                    withContext(Dispatchers.IO) {
                                                                        viewModel.insertAssistantMessageAndReturnId(
                                                                            createAssistantMessage(
                                                                                chatId = resolvedChatId,
                                                                                response = "DEV NPU route failed: ${exception.javaClass.simpleName}",
                                                                                localSourceSummary = devDebugText,
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                                cleanupDevQairt244NpuUiState(reason = "dev-qairt244-exception")
                                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                                snackbarHostState.showSnackbar(
                                                                    message = "DEV NPU route failed: ${exception.javaClass.simpleName}",
                                                                    duration = SnackbarDuration.Short,
                                                                )
                                                            } finally {
                                                                cleanupDevQairt244NpuUiState(reason = "dev-qairt244-finally")
                                                            }
                                                        }
                                                        return@IconButton
                                                    }
                                                    debugLocalUiTrace(
                                                        label = "LOCAL_UI_SEND_TAPPED",
                                                        extra = "selectedInferenceTarget=$selectedInferenceTarget effectiveChatId=$effectiveChatId userPromptLength=${userPrompt.length}",
                                                    )
                                                    prompt = ""
                                                    userPrompt = ""
                                                    selectedImageUriStrings = emptyList()
                                                    showDelayedLocalRespondingPlaceholder = false
                                                    localInferenceEngineState = LocalInferenceEngineState.READY
                                                    localStopRequested = false
                                                    debugLocalUiTrace(
                                                        label = "LOCAL_UI_INPUT_CLEARED",
                                                        extra = "effectiveChatId=$effectiveChatId pendingNavigateChatId=$pendingNavigateChatId userPromptLengthAfterClear=${userPrompt.length}",
                                                    )
                                                    stopTtsWithCleanup(
                                                        suppressedMessageId = stopButtonOwnerAssistantMessageId
                                                            ?: currentSpeakingAssistantMessageId
                                                            ?: streamingSpeechStartedForMessageId,
                                                        armTapGuards = false,
                                                    )
                                                    localInferenceJob = coroutineScope.launch {
                                                        debugLocalUiTrace(
                                                            label = "LOCAL_UI_LAUNCH_ENTER",
                                                            extra = "effectiveChatId=$effectiveChatId pendingNavigateChatId=$pendingNavigateChatId isCreatingChat=$isCreatingChat",
                                                        )
                                                        var currentChatId = effectiveChatId
                                                        if (currentChatId == null) {
                                                            isCreatingChat = true
                                                            try {
                                                                val newChatId = withContext(Dispatchers.IO) {
                                                                    viewModel.insertChatAndReturnId(
                                                                        Chat(title = "New chat", titleSource = TitleSource.TEMP)
                                                                    )
                                                                }
                                                                effectiveChatId = newChatId
                                                                pendingNavigateChatId = newChatId
                                                                currentChatId = newChatId
                                                            } finally {
                                                                isCreatingChat = false
                                                            }
                                                        }
                                                        val resolvedChatId = currentChatId
                                                        debugLocalUiTrace(
                                                            label = "LOCAL_UI_USER_INSERT_START",
                                                            extra = "resolvedChatId=$resolvedChatId requestPromptLength=${requestPrompt.length}",
                                                        )
                                                        withContext(Dispatchers.IO) {
                                                            viewModel.insert(
                                                                Message(
                                                                    chatId = resolvedChatId,
                                                                    message = requestPrompt,
                                                                    isSendbyMe = true,
                                                                )
                                                            )
                                                        }
                                                        debugLocalUiTrace(
                                                            label = "LOCAL_UI_USER_INSERT_DONE",
                                                            extra = "resolvedChatId=$resolvedChatId requestPromptLength=${requestPrompt.length}",
                                                        )
                                                        appendLocalReflectionTrace(
                                                            context = context.applicationContext,
                                                            message = "UPSTREAM local-branch-enter selectedTarget=LOCAL",
                                                        )
                                                        if (isLocalInferenceRunning) return@launch
                                                        localStopRequested = false
                                                        didReceiveRealLocalPartial = false
                                                        realLocalPartialChunkCount = 0
                                                        localStreamingResponseText = null
                                                        showDelayedLocalRespondingPlaceholder = false
                                                        isLocalInferenceRunning = true
                                                        try {
                                                            localInferenceEngineState = resolveLocalPreparingUiState()
                                                            localStreamingResponseText = null
                                                            showDelayedLocalRespondingPlaceholder = false
                                                            didReceiveRealLocalPartial = false
                                                            realLocalPartialChunkCount = 0
                                                            assistantUpdateCountForDev = 0
                                                            firstNonEmptyAssistantChunkSeenForDev = false
                                                            lastStreamingAssistantChunkForDev = null
                                                            localStreamingUiMetricsForDev.reset()
                                                            if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                                                devRunnerWhitespaceTraceText = null
                                                            }
                                                            val localRunGuardEpoch = streamingGuardEpoch
                                                            val localRunStartedAtMs = SystemClock.elapsedRealtime()
                                                            val localRunStartedAtNs = SystemClock.elapsedRealtimeNanos()
                                                            var measuredModelLoadDurationNs: Long? = null
                                                            appendLocalReflectionTrace(
                                                                context = context.applicationContext,
                                                                message = "UPSTREAM local-exec-start inferenceTarget=LOCAL promptLength=${requestPrompt.length} hasLocalModelPath=${!localBaseModelFilePath.isNullOrBlank()}",
                                                            )
                                                            var mediaPipeProbeModelPathForRun: String? = null
                                                            val runResult = withContext(Dispatchers.Default) {
                                                                val modelResolution = resolveLocalModelResolutionOrNull(
                                                                    context = context.applicationContext,
                                                                    settingsPreferences = settingsPreferences,
                                                                    localBaseModelFilePath = localBaseModelFilePath,
                                                                    localBaseModelDisplayName = localBaseModelDisplayName,
                                                                )
                                                                val useHeldPathOnlyForDev = BuildConfig.DEBUG && DEV_USE_HELD_PATH_ONLY
                                                                appendLocalReflectionTrace(
                                                                    context = context.applicationContext,
                                                                    message = "UPSTREAM held-only mode enabled=$useHeldPathOnlyForDev",
                                                                )
                                                                if (modelResolution == null) {
                                                                    appendLocalReflectionTrace(
                                                                        context = context.applicationContext,
                                                                        message = "UPSTREAM held-skip reason=model-path-unresolved",
                                                                    )
                                                                    if (useHeldPathOnlyForDev) {
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "UPSTREAM held-only fail reason=resolved-model-path-null",
                                                                        )
                                                                        LocalInferenceRunResult(
                                                                            state = LocalInferenceEngineState.ERROR,
                                                                            response = "DEV held path failure: model path unresolved",
                                                                        )
                                                                    } else {
                                                                        LocalInferenceRunResult(state = LocalInferenceEngineState.UNINITIALIZED)
                                                                    }
                                                                } else {
                                                                    val resolvedModelPath = modelResolution.modelPath
                                                                mediaPipeProbeModelPathForRun = resolvedModelPath
                                                                val modelPathTail = resolvedModelPath.substringAfterLast('/')
                                                                var legacyFallbackReason: String? = null
                                                                var heldAcquireFailureStage: String? = null
                                                                var heldAcquireFailureClassName: String? = null
                                                                var heldAcquireFailureMessage: String? = null
                                                                var heldOfficialHelperProgress: String? = null
                                                                var heldFailureDiagnosticsText: String? = null
                                                                appendLocalReflectionTrace(
                                                                    context = context.applicationContext,
                                                                    message = "UPSTREAM held-acquire start modelPathTail=$modelPathTail",
                                                                )
                                                                val heldEngine = if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                                                    val diagnosticResult = localInferenceEngineHolder.acquireWithDiagnostic(
                                                                        engineKey = modelResolution.engineKey,
                                                                        preferredBackendDryRunSetting = preferredBackendDryRunSetting,
                                                                        appendTrace = { message ->
                                                                            if (message.startsWith("UPSTREAM official-helper") || message.startsWith("UPSTREAM held-create")) {
                                                                                heldOfficialHelperProgress = message
                                                                            }
                                                                            appendLocalReflectionTrace(
                                                                                context = context.applicationContext,
                                                                                message = message,
                                                                            )
                                                                        },
                                                                    )
                                                                    heldAcquireFailureStage = diagnosticResult.failureStage
                                                                    heldAcquireFailureClassName = diagnosticResult.failureClassName
                                                                    heldAcquireFailureMessage = diagnosticResult.failureMessage
                                                                    heldFailureDiagnosticsText = diagnosticResult.failureDiagnosticsText
                                                                    if (!useHeldPathOnlyForDev && diagnosticResult.engine == null) {
                                                                        legacyFallbackReason = "held-acquire-failed"
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "UPSTREAM legacy-fallback reason=$legacyFallbackReason",
                                                                        )
                                                                    }
                                                                    diagnosticResult.engine
                                                                } else {
                                                                    runCatching {
                                                                        localInferenceEngineHolder.acquireOrCreate(
                                                                            engineKey = modelResolution.engineKey,
                                                                            context = context.applicationContext,
                                                                            preferredBackendDryRunSetting = preferredBackendDryRunSetting,
                                                                            appendTrace = { message ->
                                                                                appendLocalReflectionTrace(
                                                                                    context = context.applicationContext,
                                                                                    message = message,
                                                                                )
                                                                            },
                                                                        )
                                                                    }.getOrElse {
                                                                        heldFailureDiagnosticsText = buildLocalInferenceFailureDiagnosticsText(
                                                                            context = context.applicationContext,
                                                                            stage = "holder-acquire",
                                                                            throwable = it,
                                                                            selectedModelName = modelResolution.modelPath,
                                                                            selectedFallbackPath = "gpu",
                                                                        )
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "HELD ACQUIRE ERROR: ${it.message}",
                                                                        )
                                                                        if (!useHeldPathOnlyForDev) {
                                                                            legacyFallbackReason = "held-acquire-failed"
                                                                            appendLocalReflectionTrace(
                                                                                context = context.applicationContext,
                                                                                message = "UPSTREAM legacy-fallback reason=$legacyFallbackReason",
                                                                            )
                                                                        }
                                                                        null
                                                                    }
                                                                }
                                                                if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE && heldEngine == null) {
                                                                    coroutineScope.launch {
                                                                        devDebugText = buildString {
                                                                            append("DEV HELD FAILURE\n")
                                                                            append("modelPath=").append(resolvedModelPath).append("\n")
                                                                            append("held=").append(false).append("\n")
                                                                            append("heldHash=").append(-1).append("\n")
                                                                            append("useCount=").append(-1).append("\n")
                                                                            append("stage=").append(heldAcquireFailureStage ?: "unknown").append("\n")
                                                                            append("class=").append(heldAcquireFailureClassName ?: "unknown").append("\n")
                                                                            append("message=").append(heldAcquireFailureMessage ?: "no message").append("\n")
                                                                            append("helper=").append(heldOfficialHelperProgress ?: "not-started").append("\n")
                                                                            heldFailureDiagnosticsText?.let {
                                                                                append(it).append("\n")
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                                                    coroutineScope.launch {
                                                                        devHeldStateText = buildString {
                                                                            append("HELD ENGINE STATE\n")
                                                                            append("modelPath=").append(resolvedModelPath).append("\n")
                                                                            append("backendKey=").append(modelResolution.backendKey).append("\n")
                                                                            append("cacheDirPath=").append(modelResolution.cacheDirPath).append("\n")
                                                                            append("heldExists=").append(heldEngine != null).append("\n")
                                                                            append("useCount=").append(heldEngine?.useCount ?: -1).append("\n")
                                                                            append("heldHash=").append(heldEngine?.hashCode() ?: -1).append("\n")
                                                                        }
                                                                    }
                                                                }
                                                                heldEngine?.let { held ->
                                                                    measuredModelLoadDurationNs =
                                                                        (SystemClock.elapsedRealtimeNanos() - localRunStartedAtNs)
                                                                            .coerceAtLeast(0L)
                                                                    appendLocalReflectionTrace(
                                                                        context = context.applicationContext,
                                                                        message = "UPSTREAM held-acquire success namespace=${held.namespace} modelPathTail=$modelPathTail engineClass=${held::class.java.name}",
                                                                    )
                                                                    coroutineScope.launch { devDebugText = null }
                                                                    appendLocalReflectionTrace(
                                                                        context = context.applicationContext,
                                                                        message = "UPSTREAM held-run start modelPathTail=$modelPathTail",
                                                                    )
                                                                    val heldRunResult = runWithHeldEngine(
                                                                        heldEngine = held,
                                                                        engineHolder = localInferenceEngineHolder,
                                                                        chatId = currentChatId,
                                                                        prompt = requestPrompt,
                                                                        localModelDisplayName = modelResolution.displayName,
                                                                        mediaPipeProbeModelPath = mediaPipeProbeModelPathForRun,
                                                                        mediaPipeProbeContext = mediaPipeProbeContext,
                                                                        markdownStreamingMode = markdownStreamingMode,
                                                                        onPartial = { partial ->
                                                                            if (localStopRequested) return@runWithHeldEngine
                                                                            val normalizedPartial = normalizeStreamingPartialForRender(
                                                                                partial = partial,
                                                                                markdownStreamingMode = markdownStreamingMode,
                                                                            )
                                                                            val debugText = buildString {
                                                                                appendLine("=== WS TRACE ===")
                                                                                appendLine("RAW:")
                                                                                appendLine(partial.replace(" ", "␠").replace("\n", "\\n"))
                                                                                appendLine("----")
                                                                                appendLine("STREAM_NORMALIZED:")
                                                                                appendLine(normalizedPartial.replace(" ", "␠").replace("\n", "\\n"))
                                                                                appendLine("----")
                                                                                appendLine("LEN: ${partial.length} -> ${normalizedPartial.length}")
                                                                                appendLine("SPACES: ${partial.count { it == ' ' }} -> ${normalizedPartial.count { it == ' ' }}")
                                                                                appendLine("NL: ${partial.count { it == '\n' }} -> ${normalizedPartial.count { it == '\n' }}")
                                                                            }
                                                                            if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                                                                coroutineScope.launch {
                                                                                    devWhitespaceTraceText = debugText
                                                                                }
                                                                            }
                                                                            logLocalStreamingWhitespace(
                                                                                stage = "ChatScreen#held.onPartial",
                                                                                raw = partial,
                                                                                normalized = normalizedPartial,
                                                                            )
                                                                            if (normalizedPartial.isBlank()) return@runWithHeldEngine
                                                                            coroutineScope.launch {
                                                                                if (localRunGuardEpoch != streamingGuardEpoch) return@launch
                                                                                if (localStopRequested) return@launch
                                                                                didReceiveRealLocalPartial = true
                                                                                realLocalPartialChunkCount += 1
                                                                                logLocalStreamingWhitespace(
                                                                                    stage = "ChatScreen#held.localStreamingResponseText",
                                                                                    raw = partial,
                                                                                    normalized = normalizedPartial,
                                                                                )
                                                                                showDelayedLocalRespondingPlaceholder = false
                                                                                localStreamingResponseText = normalizedPartial
                                                                                upsertStreamingAssistantPlaceholderSerialized(
                                                                                    chatId = currentChatId,
                                                                                    response = normalizedPartial,
                                                                                )
                                                                            }
                                                                        },
                                                                        appendTrace = { message ->
                                                                            appendLocalReflectionTrace(
                                                                                context = context.applicationContext,
                                                                                message = message,
                                                                            )
                                                                        },
                                                                        onFailureDiagnostics = { diagnostics ->
                                                                            heldFailureDiagnosticsText = diagnostics
                                                                        },
                                                                    )
                                                                    if (heldRunResult != null) {
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "UPSTREAM held-run success responseLength=${heldRunResult.responseText.length} partialCount=${heldRunResult.partialCount} officialFlowUsed=${heldRunResult.officialFlowUsed} namespace=${heldRunResult.namespace}",
                                                                        )
                                                                        coroutineScope.launch { devDebugText = null }
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "UPSTREAM held-run final source=${if (heldRunResult.officialFlowUsed) "held-official-flow" else "held-official-blocking"} closePath=${heldRunResult.closeLifecycleSummary?.path ?: "none"}",
                                                                        )
                                                                        heldRunResult.toLocalInferenceRunResult()
                                                                    } else {
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "UPSTREAM held-run null",
                                                                        )
                                                                        if (useHeldPathOnlyForDev) {
                                                                            appendLocalReflectionTrace(
                                                                                context = context.applicationContext,
                                                                                message = "UPSTREAM held-only fail reason=held-run-null",
                                                                            )
                                                                            LocalInferenceRunResult(
                                                                                state = LocalInferenceEngineState.ERROR,
                                                                                response = "DEV held path failure: held run returned null",
                                                                                trace = LocalInferenceTrace(
                                                                                    localModelDisplayName = modelResolution.displayName,
                                                                                    mediaPipeProbeModelPath = modelResolution.modelPath,
                                                                                    localFailureDiagnosticsText = heldFailureDiagnosticsText,
                                                                                ),
                                                                            )
                                                                        } else {
                                                                            legacyFallbackReason = "held-run-null"
                                                                            appendLocalReflectionTrace(
                                                                                context = context.applicationContext,
                                                                                message = "UPSTREAM legacy-fallback reason=$legacyFallbackReason",
                                                                            )
                                                                            appendLocalReflectionTrace(
                                                                                context = context.applicationContext,
                                                                                message = "UPSTREAM legacy-run start reason=$legacyFallbackReason",
                                                                            )
                                                                            val legacyRunResult = localStreamingRunner.run(
                                                                                prompt = requestPrompt,
                                                                                localBaseModelFilePath = modelResolution.modelPath,
                                                                                localBaseModelDisplayName = modelResolution.displayName,
                                                                                resolvedModelPath = modelResolution.modelPath,
                                                                                cacheDirPath = modelResolution.cacheDirPath,
                                                                                mediaPipeProbeContext = mediaPipeProbeContext,
                                                                                onPartial = legacyPartial@{ partial ->
                                                                                    if (localStopRequested) return@legacyPartial
                                                                                    val normalizedPartial = normalizeStreamingPartialForRender(
                                                                                        partial = partial,
                                                                                        markdownStreamingMode = markdownStreamingMode,
                                                                                    )
                                                                                    val debugText = buildString {
                                                                                        appendLine("=== WS TRACE ===")
                                                                                        appendLine("RAW:")
                                                                                        appendLine(partial.replace(" ", "␠").replace("\n", "\\n"))
                                                                                        appendLine("----")
                                                                                        appendLine("STREAM_NORMALIZED:")
                                                                                        appendLine(normalizedPartial.replace(" ", "␠").replace("\n", "\\n"))
                                                                                        appendLine("----")
                                                                                        appendLine("LEN: ${partial.length} -> ${normalizedPartial.length}")
                                                                                        appendLine("SPACES: ${partial.count { it == ' ' }} -> ${normalizedPartial.count { it == ' ' }}")
                                                                                        appendLine("NL: ${partial.count { it == '\n' }} -> ${normalizedPartial.count { it == '\n' }}")
                                                                                    }
                                                                                    if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                                                                        coroutineScope.launch {
                                                                                            devWhitespaceTraceText = debugText
                                                                                        }
                                                                                    }
                                                                                    logLocalStreamingWhitespace(
                                                                                        stage = "ChatScreen#legacy.onPartial",
                                                                                        raw = partial,
                                                                                        normalized = normalizedPartial,
                                                                                    )
                                                                                    if (normalizedPartial.isBlank()) return@legacyPartial
                                                                                    coroutineScope.launch {
                                                                                        if (localRunGuardEpoch != streamingGuardEpoch) return@launch
                                                                                        if (localStopRequested) return@launch
                                                                                        didReceiveRealLocalPartial = true
                                                                                        realLocalPartialChunkCount += 1
                                                                                        logLocalStreamingWhitespace(
                                                                                            stage = "ChatScreen#legacy.localStreamingResponseText",
                                                                                            raw = partial,
                                                                                            normalized = normalizedPartial,
                                                                                        )
                                                                                        showDelayedLocalRespondingPlaceholder = false
                                                                                        localStreamingResponseText = normalizedPartial
                                                                                        upsertStreamingAssistantPlaceholderSerialized(
                                                                                            chatId = currentChatId,
                                                                                            response = normalizedPartial,
                                                                                        )
                                                                                    }
                                                                                },
                                                                            )
                                                                            appendLocalReflectionTrace(
                                                                                context = context.applicationContext,
                                                                                message = "UPSTREAM legacy-run finish state=${legacyRunResult?.state ?: "null"} responseLength=${legacyRunResult?.response?.length ?: -1}",
                                                                            )
                                                                            legacyRunResult
                                                                        }
                                                                    }
                                                                } ?: run {
                                                                    if (useHeldPathOnlyForDev) {
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "UPSTREAM held-only fail reason=acquire-failed",
                                                                        )
                                                                        if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                                                            val failReason = buildString {
                                                                                append("DEV HELD FAILURE\n")
                                                                                append("modelPath=").append(resolvedModelPath).append("\n")
                                                                                append("held=").append(heldEngine != null).append("\n")
                                                                                append("heldHash=").append(heldEngine?.hashCode() ?: -1).append("\n")
                                                                                append("useCount=").append(heldEngine?.useCount ?: -1).append("\n")
                                                                                heldFailureDiagnosticsText?.let {
                                                                                    append(it).append("\n")
                                                                                }
                                                                            }
                                                                            coroutineScope.launch { devDebugText = failReason }
                                                                        }
                                                                        return@run LocalInferenceRunResult(
                                                                            state = LocalInferenceEngineState.ERROR,
                                                                            response = "DEV held path failure: acquire failed",
                                                                            trace = LocalInferenceTrace(
                                                                                localModelDisplayName = modelResolution.displayName,
                                                                                mediaPipeProbeModelPath = modelResolution.modelPath,
                                                                                localFailureDiagnosticsText = heldFailureDiagnosticsText,
                                                                            ),
                                                                        )
                                                                    }
                                                                    if (legacyFallbackReason == null) {
                                                                        legacyFallbackReason = "held-not-attempted"
                                                                        appendLocalReflectionTrace(
                                                                            context = context.applicationContext,
                                                                            message = "UPSTREAM legacy-fallback reason=$legacyFallbackReason",
                                                                        )
                                                                    }
                                                                    appendLocalReflectionTrace(
                                                                        context = context.applicationContext,
                                                                        message = "UPSTREAM legacy-run start reason=$legacyFallbackReason",
                                                                    )
                                                                    val legacyRunResult = localStreamingRunner.run(
                                                                        prompt = requestPrompt,
                                                                        localBaseModelFilePath = modelResolution.modelPath,
                                                                        localBaseModelDisplayName = modelResolution.displayName,
                                                                        resolvedModelPath = modelResolution.modelPath,
                                                                        cacheDirPath = modelResolution.cacheDirPath,
                                                                        mediaPipeProbeContext = mediaPipeProbeContext,
                                                                        onPartial = legacyPartial@{ partial ->
                                                                            if (localStopRequested) return@legacyPartial
                                                                            val normalizedPartial = normalizeStreamingPartialForRender(
                                                                                partial = partial,
                                                                                markdownStreamingMode = markdownStreamingMode,
                                                                            )
                                                                            val debugText = buildString {
                                                                                appendLine("=== WS TRACE ===")
                                                                                appendLine("RAW:")
                                                                                appendLine(partial.replace(" ", "␠").replace("\n", "\\n"))
                                                                                appendLine("----")
                                                                                appendLine("STREAM_NORMALIZED:")
                                                                                appendLine(normalizedPartial.replace(" ", "␠").replace("\n", "\\n"))
                                                                                appendLine("----")
                                                                                appendLine("LEN: ${partial.length} -> ${normalizedPartial.length}")
                                                                                appendLine("SPACES: ${partial.count { it == ' ' }} -> ${normalizedPartial.count { it == ' ' }}")
                                                                                appendLine("NL: ${partial.count { it == '\n' }} -> ${normalizedPartial.count { it == '\n' }}")
                                                                            }
                                                                            if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                                                                coroutineScope.launch {
                                                                                    devWhitespaceTraceText = debugText
                                                                                }
                                                                            }
                                                                            logLocalStreamingWhitespace(
                                                                                stage = "ChatScreen#legacyDirect.onPartial",
                                                                                raw = partial,
                                                                                normalized = normalizedPartial,
                                                                            )
                                                                            if (normalizedPartial.isBlank()) return@legacyPartial
                                                                            coroutineScope.launch {
                                                                                if (localRunGuardEpoch != streamingGuardEpoch) return@launch
                                                                                if (localStopRequested) return@launch
                                                                                didReceiveRealLocalPartial = true
                                                                                realLocalPartialChunkCount += 1
                                                                                logLocalStreamingWhitespace(
                                                                                    stage = "ChatScreen#legacyDirect.localStreamingResponseText",
                                                                                    raw = partial,
                                                                                    normalized = normalizedPartial,
                                                                                )
                                                                                showDelayedLocalRespondingPlaceholder = false
                                                                                localStreamingResponseText = normalizedPartial
                                                                                upsertStreamingAssistantPlaceholderSerialized(
                                                                                    chatId = currentChatId,
                                                                                    response = normalizedPartial,
                                                                                )
                                                                            }
                                                                        },
                                                                    )
                                                                    appendLocalReflectionTrace(
                                                                        context = context.applicationContext,
                                                                        message = "UPSTREAM legacy-run finish state=${legacyRunResult?.state ?: "null"} responseLength=${legacyRunResult?.response?.length ?: -1}",
                                                                    )
                                                                    legacyRunResult
                                                                }
                                                                }
                                                            }
                                                            localInferenceEngineState = runResult?.state
                                                                ?: LocalInferenceEngineState.ERROR
                                                            val localGenerationTimeMs =
                                                                (SystemClock.elapsedRealtime() - localRunStartedAtMs).coerceAtLeast(0L)
                                                            val runResultWithUiTrace = normalizeLocalInferenceRunResult(
                                                                runResult?.copy(
                                                                    trace = runResult.trace.copy(
                                                                        wallClockLoadDurationNs = runResult.trace.wallClockLoadDurationNs
                                                                            ?: measuredModelLoadDurationNs,
                                                                        mediaPipeProbeModelPath = runResult.trace.mediaPipeProbeModelPath
                                                                            ?: mediaPipeProbeModelPathForRun,
                                                                        assistantUpdateCount = assistantUpdateCountForDev,
                                                                        firstNonEmptyAssistantChunkSeen = firstNonEmptyAssistantChunkSeenForDev,
                                                                        assistantStreamedToUi = assistantUpdateCountForDev >= 2,
                                                                        realPartialReceived = didReceiveRealLocalPartial,
                                                                        realPartialChunkCount = realLocalPartialChunkCount,
                                                                    ).withStreamingUiMetrics(localStreamingUiMetricsForDev.snapshot()),
                                                                )
                                                            )
                                                            if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE && runResultWithUiTrace != null) {
                                                                devCloseLifecycleText = buildCloseLifecycleText(runResultWithUiTrace.closeLifecycleSummary)
                                                                    ?: "CLOSE LIFECYCLE\nsummary=none"
                                                                devRunnerWhitespaceTraceText = runResultWithUiTrace.runnerWhitespaceTraceText
                                                            }
                                                            val inventoryState = runResultWithUiTrace?.state ?: LocalInferenceEngineState.ERROR
                                                            val inventoryResponseChars = runResultWithUiTrace?.response?.length ?: -1
                                                            val timedOut = runResultWithUiTrace == null
                                                            Log.i(
                                                                "ChatScreen",
                                                                "LOCAL inference run entry completed. state=${runResultWithUiTrace?.state ?: LocalInferenceEngineState.ERROR}, responseBlank=${runResultWithUiTrace?.response.isNullOrBlank()}, responseLength=${runResultWithUiTrace?.response?.length ?: -1}, responseHead=${runResultWithUiTrace?.response?.take(80)}, timedOut=${runResultWithUiTrace == null}",
                                                            )
                                                            Log.i(
                                                                "ChatScreen",
                                                                "LOCAL stats inventory: generationTimeMs=$localGenerationTimeMs, responseChars=$inventoryResponseChars, state=$inventoryState, timedOut=$timedOut, responseBlank=${runResultWithUiTrace?.response.isNullOrBlank()}, streamingCandidate=${runResultWithUiTrace?.trace?.streamingCandidateDetected}, createPath=${runResultWithUiTrace?.trace?.createMethodSignature != null}, optionsBuildPath=${runResultWithUiTrace?.trace?.optionsBuildPath}, generateMethod=${runResultWithUiTrace?.trace?.generateMethodSignature}",
                                                            )
                                                            latestLocalTraceForDev = runResultWithUiTrace?.trace
                                                            logLocalStatsInventoryClassification(runResult = runResultWithUiTrace)
                                                            val initialState = runResultWithUiTrace?.state
                                                            val initialResponse = runResultWithUiTrace?.response.orEmpty()
                                                            val initialResponseBlank = sanitizeLocalAssistantResponse(initialResponse).isBlank()
                                                            val initialTimedOut = runResultWithUiTrace == null
                                                            val initialResponseLength = initialResponse.length
                                                            val initialTracePresent = runResultWithUiTrace?.trace != null
                                                            Log.i(
                                                                "ChatScreen",
                                                                "LOCAL compare initial: effectiveChatId=$effectiveChatId, initialState=$initialState, initialTimedOut=$initialTimedOut, initialResponseBlank=$initialResponseBlank, initialResponseLength=$initialResponseLength, initialTracePresent=$initialTracePresent, localInferenceEngineState=$localInferenceEngineState, isLocalInferenceRunning=$isLocalInferenceRunning",
                                                            )
                                                            val needsStateGrace = runResultWithUiTrace == null ||
                                                                initialState == null ||
                                                                initialState == LocalInferenceEngineState.PREPARING
                                                            val resolvedState = if (needsStateGrace) {
                                                                Log.i(
                                                                    "ChatScreen",
                                                                    "LOCAL state grace check before recheck: initialState=$initialState, initialResponseBlank=$initialResponseBlank, timedOut=${runResultWithUiTrace == null}, running=$isLocalInferenceRunning, chatId=$effectiveChatId",
                                                                )
                                                                delay(350L)
                                                                val recheckedState = runResultWithUiTrace?.state ?: localInferenceEngineState.takeIf {
                                                                    it == LocalInferenceEngineState.READY || it == LocalInferenceEngineState.PREPARING
                                                                }
                                                                val recheckedResponseBlank = sanitizeLocalAssistantResponse(runResultWithUiTrace?.response.orEmpty()).isBlank()
                                                                Log.i(
                                                                    "ChatScreen",
                                                                    "LOCAL state grace check after recheck: recheckedState=$recheckedState, recheckedResponseBlank=$recheckedResponseBlank, timedOut=${runResultWithUiTrace == null}, running=$isLocalInferenceRunning, chatId=$effectiveChatId",
                                                                )
                                                                recheckedState
                                                            } else {
                                                                initialState
                                                            }
                                                            val assistantResponse = sanitizeLocalAssistantResponse(initialResponse)
                                                            var resolvedAssistantResponse = assistantResponse
                                                            if (resolvedState == LocalInferenceEngineState.READY) {
                                                                if (resolvedAssistantResponse.isBlank()) {
                                                                    delay(250L)
                                                                    val fallbackUiResponse = localStreamingResponseText?.trim().orEmpty()
                                                                    resolvedAssistantResponse = sanitizeLocalAssistantResponse(
                                                                        assistantResponse.ifBlank { fallbackUiResponse }
                                                                    )
                                                                    if (resolvedAssistantResponse.isBlank()) {
                                                                        Log.e(
                                                                            "ChatScreen",
                                                                            "LOCAL blank response after grace: assistantBlank=${assistantResponse.isBlank()}, uiBlank=${fallbackUiResponse.isBlank()}, uiLen=${fallbackUiResponse.length}, running=$isLocalInferenceRunning, chatId=$effectiveChatId",
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            val recheckedRunResult = runResultWithUiTrace
                                                            val recheckedTimedOut = recheckedRunResult == null
                                                            val recheckedResponseBlank =
                                                                sanitizeLocalAssistantResponse(recheckedRunResult?.response.orEmpty()).isBlank()
                                                            val recheckedResponseLength = recheckedRunResult?.response?.length ?: -1
                                                            val recheckedTracePresent = recheckedRunResult?.trace != null
                                                            val resolvedAssistantBlank = resolvedAssistantResponse.isBlank()
                                                            val streamingUiLength = localStreamingResponseText?.length ?: 0
                                                            Log.i(
                                                                "ChatScreen",
                                                                "LOCAL compare recheck: effectiveChatId=$effectiveChatId, recheckedState=$resolvedState, recheckedTimedOut=$recheckedTimedOut, recheckedResponseBlank=$recheckedResponseBlank, recheckedResponseLength=$recheckedResponseLength, recheckedTracePresent=$recheckedTracePresent, resolvedAssistantBlank=$resolvedAssistantBlank, streamingUiLength=$streamingUiLength",
                                                            )
                                                            if (resolvedState == LocalInferenceEngineState.READY && resolvedAssistantResponse.isNotBlank()) {
                                                                    val resolvedRunResult = runResultWithUiTrace
                                                                    var resolvedTrace = resolvedRunResult?.trace
                                                                    var localStats = if (resolvedTrace != null) {
                                                                        buildLocalInferenceStatsFromTrace(
                                                                            trace = resolvedTrace,
                                                                            generationTimeMs = localGenerationTimeMs,
                                                                            responseCharCount = resolvedAssistantResponse.length,
                                                                            responseText = resolvedAssistantResponse,
                                                                            fallbackTimeToFirstTokenMs = localGenerationTimeMs,
                                                                        )
                                                                    } else {
                                                                        null
                                                                    }
                                                                    var rawSourceSummary =
                                                                        if (resolvedTrace != null && localStats != null) {
                                                                            buildLocalSourceSummaryText(
                                                                                trace = resolvedTrace,
                                                                                stats = localStats,
                                                                            )
                                                                        } else {
                                                                            null
                                                                        }
                                                                    var localSourceSummary =
                                                                        resolvedTrace?.selectedAssistantResponseSource
                                                                            ?.takeIf { it.isNotBlank() }
                                                                            ?: rawSourceSummary
                                                                    Log.i(
                                                                        "ChatScreen",
                                                                        "LOCAL compare success: successState=$resolvedState, successResponseLength=${resolvedAssistantResponse.length}, tracePresent=${resolvedTrace != null}, localStatsPresent=${localStats != null}, localSourceSummaryPresent=${localSourceSummary != null}, effectiveChatId=$effectiveChatId",
                                                                    )
                                                                    Log.i(
                                                                        "ChatScreen",
                                                                        "LOCAL assistant insert payload length=${resolvedAssistantResponse.length}, head=${resolvedAssistantResponse.take(80)}",
                                                                    )
                                                                    appendLocalReflectionTrace(
                                                                        context = context.applicationContext,
                                                                        message = "UPSTREAM before-createAssistantMessage localResponseBlank=${resolvedAssistantResponse.isBlank()} generationTimeMs=$localGenerationTimeMs",
                                                                    )
                                                                    if (localStopRequested) {
                                                                        Log.i("ChatScreen", "LOCAL stop requested: suppress assistant apply before stream")
                                                                        localStreamingResponseText = null
                                                                        showDelayedLocalRespondingPlaceholder = false
                                                                        resetStreamingSpeechState()
                                                                        resetStreamingAssistantPlaceholderId(reason = "stop")
                                                                        return@launch
                                                                    }
                                                                    if (!didReceiveRealLocalPartial) {
                                                                        Log.i(
                                                                            "ChatScreen",
                                                                            "LOCAL pseudo-stream start: replay final response text to UI for debug comparison",
                                                                        )
                                                                        streamLocalAssistantPreviewTextToUi(
                                                                            responseText = resolvedAssistantResponse,
                                                                            onChunk = { chunk ->
                                                                                if (localStopRequested) return@streamLocalAssistantPreviewTextToUi
                                                                                logLocalStreamingWhitespace(
                                                                                    stage = "ChatScreen#preview.onChunk.raw",
                                                                                    raw = chunk,
                                                                                )
                                                                                showDelayedLocalRespondingPlaceholder = false
                                                                                localStreamingResponseText = chunk
                                                                                val normalizedChunk = chunk.trim()
                                                                                logLocalStreamingWhitespace(
                                                                                    stage = "ChatScreen#preview.onChunk.trim",
                                                                                    raw = chunk,
                                                                                    normalized = normalizedChunk,
                                                                                )
                                                                                if (normalizedChunk.isBlank()) return@streamLocalAssistantPreviewTextToUi
                                                                                coroutineScope.launch {
                                                                                    if (localRunGuardEpoch != streamingGuardEpoch) return@launch
                                                                                    if (localStopRequested) return@launch
                                                                                    upsertStreamingAssistantPlaceholderSerialized(
                                                                                        chatId = currentChatId,
                                                                                        response = normalizedChunk,
                                                                                    )
                                                                                }
                                                                            },
                                                                        )
                                                                    } else {
                                                                        Log.i(
                                                                            "ChatScreen",
                                                                            "LOCAL pseudo-stream skipped: real partial already received count=$realLocalPartialChunkCount",
                                                                        )
                                                                    }
                                                                    resolvedTrace = resolvedTrace
                                                                        ?.copy(
                                                                            assistantUpdateCount = assistantUpdateCountForDev,
                                                                            firstNonEmptyAssistantChunkSeen = firstNonEmptyAssistantChunkSeenForDev,
                                                                            assistantStreamedToUi = assistantUpdateCountForDev >= 2,
                                                                            realPartialReceived = didReceiveRealLocalPartial,
                                                                            realPartialChunkCount = realLocalPartialChunkCount,
                                                                        )
                                                                        ?.withStreamingUiMetrics(localStreamingUiMetricsForDev.snapshot())
                                                                    latestLocalTraceForDev = resolvedTrace
                                                                    localStats = if (resolvedTrace != null) {
                                                                        buildLocalInferenceStatsFromTrace(
                                                                            trace = resolvedTrace,
                                                                            generationTimeMs = localGenerationTimeMs,
                                                                            responseCharCount = resolvedAssistantResponse.length,
                                                                            responseText = resolvedAssistantResponse,
                                                                            fallbackTimeToFirstTokenMs = localGenerationTimeMs,
                                                                        )
                                                                    } else {
                                                                        null
                                                                    }
                                                                    rawSourceSummary =
                                                                        if (resolvedTrace != null && localStats != null) {
                                                                            buildLocalSourceSummaryText(
                                                                                trace = resolvedTrace,
                                                                                stats = localStats,
                                                                            )
                                                                        } else {
                                                                            null
                                                                        }
                                                                    localSourceSummary =
                                                                        resolvedTrace?.selectedAssistantResponseSource
                                                                            ?.takeIf { it.isNotBlank() }
                                                                            ?: rawSourceSummary
                                                                    if (localStopRequested) {
                                                                        Log.i("ChatScreen", "LOCAL stop requested: suppress assistant apply before insert")
                                                                        localStreamingResponseText = null
                                                                        showDelayedLocalRespondingPlaceholder = false
                                                                        resetStreamingSpeechState()
                                                                        resetStreamingAssistantPlaceholderId(reason = "stop")
                                                                        return@launch
                                                                    }
                                                                    if (localRunGuardEpoch != streamingGuardEpoch) return@launch
                                                                    val assistantId = finalizeStreamingAssistantMessageSerialized(
                                                                        chatId = currentChatId,
                                                                        response = resolvedAssistantResponse,
                                                                        latestInferenceStats = localStats,
                                                                        localSourceSummary = localSourceSummary,
                                                                        generationTimeMs = localGenerationTimeMs,
                                                                    )
                                                                    latestLocalTraceForDev = resolvedTrace
                                                                        ?.withStreamingUiMetrics(localStreamingUiMetricsForDev.snapshot())
                                                                        ?: latestLocalTraceForDev
                                                                    if (assistantId != null) {
                                                                        streamingSpeechStartedForMessageId = assistantId
                                                                    }
                                                                    localStreamingResponseText = null
                                                                    showDelayedLocalRespondingPlaceholder = false
                                                                    resetStreamingAssistantPlaceholderId(reason = "success")
                                                                    isLocalInferenceRunning = false
                                                                    yield()
                                                                    if (effectiveStreamingSentenceTtsEnabled && !localStopRequested) {
                                                                        maybeReleaseHeldEngineForTtsPlayback()
                                                                        speakStreamingTailIfNeeded(resolvedAssistantResponse)
                                                                        resetStreamingSpeechState(clearPlaybackFlag = false)
                                                                    } else if (
                                                                        ttsEnabled &&
                                                                        !localStopRequested &&
                                                                        assistantId != null &&
                                                                        suppressedTtsAssistantMessageId != assistantId &&
                                                                        !ttsController.isInCooldown()
                                                                    ) {
                                                                        sanitizeTextForTts(resolvedAssistantResponse).takeIf { it.isNotEmpty() }?.let { speechText ->
                                                                            currentSpeakingAssistantMessageId = assistantId
                                                                            if (!isTtsSuppressedForAssistant(assistantId)) {
                                                                                stopButtonOwnerAssistantMessageId = assistantId
                                                                                stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
                                                                            }
                                                                            maybeReleaseHeldEngineForTtsPlayback()
                                                                            ttsController.speak(speechText)
                                                                        }
                                                                    }
                                                                    return@launch
                                                            }
                                                            localStreamingResponseText = null
                                                            showDelayedLocalRespondingPlaceholder = false
                                                            resetStreamingAssistantPlaceholderId(reason = "error")
                                                            isLocalInferenceRunning = false
                                                            localInferenceEngineHolder.resetConversation(
                                                                chatId = currentChatId,
                                                                reason = "error",
                                                            )
                                                            Log.e(
                                                                "ChatScreen",
                                                                "LOCAL compare failure: failureState=$resolvedState, failureTimedOut=$recheckedTimedOut, failureResponseBlank=$resolvedAssistantBlank, failureResponseLength=${resolvedAssistantResponse.length}, failureTracePresent=$recheckedTracePresent, effectiveChatId=$effectiveChatId, isLocalInferenceRunning=$isLocalInferenceRunning",
                                                            )
                                                            snackbarHostState.currentSnackbarData?.dismiss()
                                                            val dismissJob = launch {
                                                                delay(PROJECT_SNACKBAR_SHORT_MS)
                                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                            }
                                                            snackbarHostState.showSnackbar(
                                                                message = when (resolvedState) {
                                                                    null -> "ローカル推論エンジンの確認がタイムアウトしました"
                                                                    LocalInferenceEngineState.READY -> "ローカル推論の応答取得に失敗しました"
                                                                    LocalInferenceEngineState.UNINITIALIZED -> "ローカル基本モデルが未設定です"
                                                                    LocalInferenceEngineState.ERROR -> "ローカル推論の応答取得に失敗しました"
                                                                    LocalInferenceEngineState.PREPARING -> "ローカル推論エンジンを準備中です"
                                                                },
                                                                duration = SnackbarDuration.Short,
                                                            )
                                                            dismissJob.cancel()
                                                        } catch (exception: Exception) {
                                                            localStreamingResponseText = null
                                                            showDelayedLocalRespondingPlaceholder = false
                                                            resetStreamingSpeechState()
                                                            resetStreamingAssistantPlaceholderId(reason = "error")
                                                            effectiveChatId?.let { chatId ->
                                                                localInferenceEngineHolder.resetConversation(
                                                                    chatId = chatId,
                                                                    reason = "error",
                                                                )
                                                            }
                                                            didReceiveRealLocalPartial = false
                                                            realLocalPartialChunkCount = 0
                                                            isLocalInferenceRunning = false
                                                            Log.e(
                                                                "ChatScreen",
                                                                "LOCAL inference execution failed",
                                                                exception,
                                                            )
                                                            snackbarHostState.currentSnackbarData?.dismiss()
                                                            snackbarHostState.showSnackbar(
                                                                message = "ローカル推論の応答取得に失敗しました",
                                                                duration = SnackbarDuration.Short,
                                                            )
                                                        } finally {
                                                            localStreamingResponseText = null
                                                            showDelayedLocalRespondingPlaceholder = false
                                                            resetStreamingSpeechState()
                                                            resetStreamingAssistantPlaceholderId(reason = "local-finish")
                                                            didReceiveRealLocalPartial = false
                                                            realLocalPartialChunkCount = 0
                                                            isLocalInferenceRunning = false
                                                            localInferenceJob = null
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(ComposerButtonSize)
                                            .align(Alignment.Bottom)
                                            .clip(CircleShape)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(ComposerButtonVisualSize)
                                                .clip(CircleShape)
                                                .background(Color.LightGray.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isInferenceRunningUi) {
                                                    Icons.Filled.Stop
                                                } else {
                                                    Icons.Filled.ArrowUpward
                                                },
                                                contentDescription = if (isInferenceRunningUi) {
                                                    "Stop Button"
                                                } else {
                                                    "Send Button"
                                                },
                                                modifier = Modifier.size(ComposerButtonIconVisualSize)
                                            )
                                        }
                                    }

                                    // 右ボタンを外側へ寄せるための最小余白
                                    Spacer(modifier = Modifier.width(0.dp))
                                }
                            }

                        if (measuredLines >= 5) {
                            IconButton(
                                onClick = { expandDialogOpen = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInFull,
                                    contentDescription = "Expand"
                                )
                            }
                        }

                        }
                    }
                }

                }
            }
            // 入力欄の背景外に透明な 8dp ギャップを確保する
            Spacer(
                modifier = Modifier
                    .height(ComposerBottomGapHeight)
            )
        }

        if (expandDialogOpen) {
            Dialog(onDismissRequest = { expandDialogOpen = false }) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("全体表示", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { expandDialogOpen = false }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close expand dialog"
                                )
                            }
                        }
                        OutlinedTextField(
                            value = userPrompt,
                            onValueChange = {
                                userPrompt = it
                                viewModel.onUserInteraction()
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 360.dp),
                            singleLine = false,
                            maxLines = 16,
                            placeholder = { Text("ここで全文を編集") }
                        )
                        TextButton(
                            onClick = { expandDialogOpen = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // TopAppBar 配下への潜り込みを防ぐため、Scaffold の上下 inset をここで一元適用
                .padding(
                    top = paddingValues.calculateTopPadding()
                )
                .onGloballyPositioned { coordinates ->
                    measuredContentTopPx = coordinates.positionInRoot().y
                }
                // LazyColumn 側で Insets を二重適用しないよう、この階層で消費する
                .consumeWindowInsets(paddingValues)
        ) {
            val contentModifier = Modifier
                .fillMaxSize()

            if (effectiveChatId == null) {
                Column(
                    modifier = contentModifier.padding(top = effectiveTopGradientBottomDp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (isCreatingChat) "Creating new chat..." else "Preparing chat...")
                }
            } else if (allChatsOrNull == null) {
                Column(
                    modifier = contentModifier.padding(top = effectiveTopGradientBottomDp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Loading messages...")
                }
            } else {
                val currentChatId = effectiveChatId
                val messagesForListBase: List<Message> = allChatsOrNull
                val streamingResponseTextForRenderValue = streamingResponseTextForDisplay
                val shouldShowTransientAssistantRow =
                    currentChatId != null &&
                        streamingAssistantMessageId == null &&
                        !streamingResponseTextForRenderValue.isNullOrBlank() &&
                        streamingResponseTextForRenderValue.trim() != lastPersistedStreamingAssistantText
                val messagesForList: List<Message> = if (shouldShowTransientAssistantRow) {
                    logStreamTrace("STREAM ui transient row enabled")
                    messagesForListBase + Message(
                        chatId = currentChatId,
                        message = streamingResponseTextForRenderValue,
                        isSendbyMe = false,
                    )
                } else {
                    if (!streamingResponseTextForRenderValue.isNullOrBlank()) {
                        when {
                            streamingAssistantMessageId != null -> {
                                Log.i(
                                    "ChatScreen",
                                    "STREAM ui transient row suppressed placeholderId=$streamingAssistantMessageId",
                                )
                            }

                            streamingResponseTextForRenderValue.trim() == lastPersistedStreamingAssistantText -> {
                                Log.i(
                                    "ChatScreen",
                                    "STREAM ui transient row suppressed persistedTextMatched",
                                )
                            }
                        }
                    }
                    messagesForListBase
                }
                LaunchedEffect(effectiveChatId, messagesForList.size, messagesForList.lastOrNull()?.messageID) {
                    if (!BuildConfig.DEBUG) return@LaunchedEffect
                    val assistantMessages = messagesForList.filterNot { it.isSendbyMe }
                    val missingCount = assistantMessages.count { it.isInferenceStatsMissing() }
                    val storedCount = assistantMessages.size - missingCount
                    Log.d(
                        "InferenceStatsAudit",
                        "chatId=$effectiveChatId assistant=${assistantMessages.size} stored=$storedCount missing=$missingCount",
                    )
                }
                val isListForCurrentChatForUi =
                    currentChatId != null &&
                        (messagesForListBase.isEmpty() || messagesForListBase.all { it.chatId == currentChatId })
                val latestAssistantIndex = messagesForList.indexOfLast { !it.isSendbyMe }

                if (!isListForCurrentChatForUi) {
                    Box(modifier = contentModifier)
                } else {
                    key(effectiveChatId) {
                        val anchor = computeLatestUserAnchor(messagesForList)
                        // 仕上げチェック: 初回のみ anchor を使い、それ以降は Saveable な復元位置を優先する
                        val listState = rememberSaveable(effectiveChatId, saver = LazyListState.Saver) {
                            LazyListState(firstVisibleItemIndex = anchor)
                        }
                        // LazyColumn tail layout:
                        // [messages...] + [assistant_streaming_indicator?] + [composer_spacer]
                        val hasLoadingTailItem = isServerLoadingUi

                        val lastContentIndex = remember(messagesForList.size, hasLoadingTailItem) {
                            val lastMessageIndex = messagesForList.lastIndex
                            if (lastMessageIndex < 0) {
                                -1
                            } else {
                                if (hasLoadingTailItem) lastMessageIndex + 1 else lastMessageIndex
                            }
                        }
                        val fabScrollTargetIndex = remember(lastContentIndex) {
                            if (lastContentIndex < 0) {
                                -1
                            } else {
                                // FAB押下時は composer_spacer まで送って、最新回答末尾が見える位置へ寄せる
                                lastContentIndex + 1
                            }
                        }
                        val isNearBottom by remember(listState, lastContentIndex) {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val visibleItems = layoutInfo.visibleItemsInfo
                                val nearBottomEpsilonPx = 24
                                if (lastContentIndex < 0) {
                                    true
                                } else {
                                    val lastVisibleContentItem =
                                        visibleItems.lastOrNull { it.index <= lastContentIndex }
                                    lastVisibleContentItem != null &&
                                        (lastVisibleContentItem.offset + lastVisibleContentItem.size) <=
                                        (layoutInfo.viewportEndOffset + nearBottomEpsilonPx)
                                }
                            }
                        }
                        val shouldShowScrollToBottomFab by remember(
                            listState,
                            latestAssistantIndex,
                            messagesForList.size,
                        ) {
                            derivedStateOf {
                                if (messagesForList.isEmpty()) {
                                    false
                                } else {
                                    val targetMessageIndex =
                                        if (latestAssistantIndex >= 0) {
                                            latestAssistantIndex
                                        } else {
                                            messagesForList.lastIndex
                                        }
                                    if (targetMessageIndex < 0) {
                                        false
                                    } else {
                                        val layoutInfo = listState.layoutInfo
                                        val targetMessageItem =
                                            layoutInfo.visibleItemsInfo.lastOrNull {
                                                it.index == targetMessageIndex
                                            }
                                        val nearBottomEpsilonPx = 24
                                        if (targetMessageItem == null) {
                                            true
                                        } else {
                                            val targetMessageBottom =
                                                targetMessageItem.offset + targetMessageItem.size
                                            targetMessageBottom >
                                                (layoutInfo.viewportEndOffset + nearBottomEpsilonPx)
                                        }
                                    }
                                }
                            }
                        }
                        var isNearBottomSnapshot by remember(effectiveChatId) { mutableStateOf(true) }
                        var autoFollowEnabled by remember(effectiveChatId) { mutableStateOf(true) }
                        var previousMessageCount by remember(effectiveChatId) { mutableStateOf(-1) }
                        var lastAppliedAnchor by remember(effectiveChatId) { mutableStateOf(anchor) }
                        var suppressFollowOnce by remember(effectiveChatId) { mutableStateOf(false) }

                        LaunchedEffect(effectiveChatId) {
                            previousMessageCount = messagesForList.size
                            lastAppliedAnchor = computeLatestUserAnchor(messagesForList)
                            suppressFollowOnce = true
                            autoFollowEnabled = true
                        }

                        LaunchedEffect(listState) {
                            snapshotFlow { listState.isScrollInProgress to isNearBottom }
                                .collect { (isScrolling, nearBottom) ->
                                    isNearBottomSnapshot = nearBottom
                                    if (isScrolling && !nearBottom) {
                                        autoFollowEnabled = false
                                    }
                                    if (nearBottom) {
                                        autoFollowEnabled = true
                                    }
                                }
                        }

                        LaunchedEffect(effectiveChatId, allChatsOrNull.size) {
                            val currentChatId = effectiveChatId ?: return@LaunchedEffect
                            val allChats = allChatsOrNull
                            val isListForCurrentChat =
                                allChats.isEmpty() ||
                                    allChats.all { it.chatId == currentChatId }

                            if (!isListForCurrentChat) return@LaunchedEffect

                            if (allChats.isEmpty()) {
                                lastUserMessageCountByChatId[currentChatId] = 0
                                return@LaunchedEffect
                            }

                            val userCount = allChats.count { it.isSendbyMe }
                            val previousUserCount = lastUserMessageCountByChatId[currentChatId]

                            // 初回表示時は記録のみ（スクロールしない）
                            if (previousUserCount == null) {
                                lastUserMessageCountByChatId[currentChatId] = userCount
                                return@LaunchedEffect
                            }

                            // 仕上げチェック: scrollToItem はユーザー送信が増えた時のみ実行
                            if (userCount > previousUserCount) {
                                val newAnchor = computeLatestUserAnchor(allChats)
                                listState.scrollToItem(newAnchor)
                            }

                            lastUserMessageCountByChatId[currentChatId] = userCount
                        }

                        LaunchedEffect(listState.isScrollInProgress) {
                            if (listState.isScrollInProgress) {
                                viewModel.onUserInteraction()
                            }
                        }

                        LaunchedEffect(effectiveChatId, messagesForList) {
                            try {
                                val currentChatId = effectiveChatId ?: return@LaunchedEffect

                                // 初期同期ガード
                                if (previousMessageCount == -1) {
                                    previousMessageCount = messagesForList.size
                                    lastAppliedAnchor = computeLatestUserAnchor(messagesForList)
                                    return@LaunchedEffect
                                }

                                val isListForCurrentChat =
                                    messagesForList.isEmpty() ||
                                        messagesForList.all { it.chatId == currentChatId }

                                if (!isListForCurrentChat) return@LaunchedEffect
                                val currentMessageCount = messagesForList.size
                                val appended = currentMessageCount > previousMessageCount
                                val currentAnchor = computeLatestUserAnchor(messagesForList)
                                val followSuppressedByAnchorUpdate = currentAnchor != lastAppliedAnchor

                                if (followSuppressedByAnchorUpdate) {
                                    lastAppliedAnchor = currentAnchor
                                    suppressFollowOnce = true
                                }

                                if (messagesForList.isNotEmpty()) {
                                    val lastIndex = messagesForList.lastIndex
                                    if (appended && isNearBottomSnapshot && autoFollowEnabled && !suppressFollowOnce && lastIndex >= 0) {
                                        listState.scrollToItem(lastIndex)
                                    }
                                }

                                previousMessageCount = currentMessageCount

                                if (messagesForList.isEmpty()) return@LaunchedEffect

                                if (!topPaddingModeMap.containsKey(currentChatId)) {
                                    val firstIsUser =
                                        messagesForList.firstOrNull()?.isSendbyMe == true

                                    topPaddingModeMap[currentChatId] =
                                        if (firstIsUser) {
                                            TopPaddingMode.NewConversation
                                        } else {
                                            TopPaddingMode.ExistingConversation
                                        }
                                }
                            } finally {
                                suppressFollowOnce = false
                            }
                        }
                        val mode = topPaddingModeMap[effectiveChatId]
                            ?: TopPaddingMode.ExistingConversation
                        val resolvedGradientStartTopPaddingDp =
                            effectiveTopGradientBottomDp + EmptyNewConversationBaseTopPadding
                        val resolvedSpriteAnchorTopPaddingDp =
                            if (measuredSpriteBottomPx != null && measuredContentTopPx != null) {
                                with(LocalDensity.current) {
                                    val spriteAnchorDeltaPx =
                                        (measuredSpriteBottomPx!! - measuredContentTopPx!!)
                                            .coerceAtLeast(0f)
                                            .roundToInt()
                                    spriteAnchorDeltaPx.toDp()
                                } + SpriteMessageGap
                            } else {
                                null
                            }
                        val fixedEmptyNewAnchorTopPaddingDp =
                            effectiveChatId?.let { currentChatId ->
                                fixedEmptyNewAnchorTopPaddingByChatId[currentChatId]
                            }
                        LaunchedEffect(effectiveChatId, resolvedSpriteAnchorTopPaddingDp) {
                            val currentChatId = effectiveChatId ?: return@LaunchedEffect
                            val resolved = resolvedSpriteAnchorTopPaddingDp ?: return@LaunchedEffect
                            if (!fixedEmptyNewAnchorTopPaddingByChatId.containsKey(currentChatId)) {
                                fixedEmptyNewAnchorTopPaddingByChatId[currentChatId] = resolved
                            }
                        }
                        val emptyNewConversationAnchorTopPaddingDp =
                            ((fixedEmptyNewAnchorTopPaddingDp
                                ?: resolvedSpriteAnchorTopPaddingDp)
                                ?: (resolvedGradientStartTopPaddingDp + EmptyNewConversationTopAdjust))
                                .coerceAtLeast(0.dp)
                        val messageListTopPaddingDp = when {
                            // Empty / New は共通アンカーを利用する
                            messagesForList.isEmpty() -> emptyNewConversationAnchorTopPaddingDp
                            mode == TopPaddingMode.NewConversation ->
                                emptyNewConversationAnchorTopPaddingDp
                            // Existing は従来どおり会話一覧の top gap を利用する
                            else -> chatListTopPaddingDp
                        }
                        Box(modifier = contentModifier) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                // 入力欄の背後まで本文を描画し、ガター領域を透明表示にする
                                contentPadding = PaddingValues(
                                    // 新規/既存の初期判定で top padding を固定し、会話途中で切り替えないことでジャンプを防ぐ
                                    top = messageListTopPaddingDp,
                                    start = 0.dp,
                                    end = 0.dp,
                                    bottom = 0.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(ChatMessageVerticalGap),
                                state = listState,
                            ) {
                                if (messagesForList.isEmpty()) {
                                    item(key = "empty-state") {
                                        PlainAssistantMessage(
                                            message = "ラミィがお手伝いします。\n今日は何をしますか？",
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 10.dp)
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        items = messagesForList,
                                        key = { _, message -> message.messageID.takeIf { it != 0 } ?: "${message.chatId}-${message.message}" }
                                    ) { index, message ->
                                        if (message.isSendbyMe) {
                                            ChatBubble(
                                                message = message.message,
                                                isSentByMe = message.isSendbyMe,
                                                attachmentUriString = message.attachmentUriString,
                                                attachmentUriStringsJson = message.attachmentUriStringsJson,
                                            )
                                        } else {
                                            val persistedMessageInferenceStats = message.toInferenceStats()
                                            val messageInferenceStats =
                                                persistedMessageInferenceStats
                                                    ?: immediateInferenceStatsByMessageId[message.messageID]
                                            val canShowTtsActions = ttsEnabled
                                            val isPersistedStreamingAssistantRow =
                                                streamingAssistantMessageId != null &&
                                                    message.messageID == streamingAssistantMessageId
                                            val isProvisionalStreamingAssistantRow =
                                                streamingAssistantMessageId == null &&
                                                    index == messagesForList.lastIndex
                                            val isStreamingMessageRow =
                                                isInferenceRunningUi &&
                                                    (
                                                        isPersistedStreamingAssistantRow ||
                                                            isProvisionalStreamingAssistantRow
                                                        )
                                            val assistantDisplayMessage = buildAssistantDisplayText(
                                                originalMessage = message.message,
                                                tailLimitChars = if (devStreamingTailLimitEnabled || isStreamingMessageRow) {
                                                    assistantRenderTailLimitChars
                                                } else {
                                                    Int.MAX_VALUE
                                                },
                                            ).text
                                            PlainAssistantMessage(
                                                message = assistantDisplayMessage,
                                                isStreaming = isStreamingMessageRow,
                                                showMessageActions = true,
                                                isReplaying =
                                                    canShowTtsActions &&
                                                        (stopButtonOwnerAssistantMessageId == message.messageID ||
                                                            stopUiCooldownAssistantMessageId == message.messageID),
                                                onReplayClick = if (canShowTtsActions) {
                                                    {
                                                    if (suppressReplayAssistantMessageId == message.messageID) {
                                                        return@PlainAssistantMessage
                                                    }
                                                    if (stopUiCooldownAssistantMessageId == message.messageID) {
                                                        return@PlainAssistantMessage
                                                    }

                                                    ttsTapGuardEpoch += 1

                                                    pendingStopUiCooldownClearJob?.cancel()
                                                    pendingStopUiCooldownClearJob = null
                                                    if (stopUiCooldownAssistantMessageId == message.messageID) {
                                                        stopUiCooldownAssistantMessageId = null
                                                    }

                                                    pendingReplaySuppressClearJob?.cancel()
                                                    pendingReplaySuppressClearJob = null
                                                    if (suppressReplayAssistantMessageId == message.messageID) {
                                                        suppressReplayAssistantMessageId = null
                                                    }

                                                    if (suppressedTtsAssistantMessageId == message.messageID) {
                                                        suppressedTtsAssistantMessageId = null
                                                    }
                                                    val speechText = sanitizeTextForTts(message.message)
                                                    if (speechText.isEmpty()) {
                                                        return@PlainAssistantMessage
                                                    }
                                                    isStreamingSentencePlaybackActive = false
                                                    currentSpeakingAssistantMessageId = message.messageID
                                                    stopButtonOwnerAssistantMessageId = message.messageID
                                                    stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
                                                    coroutineScope.launch {
                                                        maybeReleaseHeldEngineForTtsPlayback()
                                                        ttsController.speak(speechText)
                                                    }
                                                }
                                                } else {
                                                    null
                                                },
                                                onStopReplayClick = if (canShowTtsActions) {
                                                    {
                                                    stopTtsWithCleanup(
                                                        suppressedMessageId = message.messageID,
                                                        armTapGuards = true,
                                                    )
                                                }
                                                } else {
                                                    null
                                                },
                                                onCopyAllClick = {
                                                    clipboardManager.setText(AnnotatedString(message.message))
                                                },
                                                inferenceStats = messageInferenceStats,
                                                onInferenceStatsClick = messageInferenceStats?.let {
                                                    {
                                                        selectedInferenceStats = it
                                                        selectedLocalTraceForDevSheet = latestLocalTraceForDev
                                                        selectedAssistantMessageTextForStatsSheet = message.message
                                                        selectedPromptMessageTextForStatsSheet =
                                                            messagesForList.getOrNull(index - 1)
                                                                ?.takeIf { it.isSendbyMe }
                                                                ?.message
                                                        showInferenceStatsSheet = true
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                if (showLocalRespondingAssistantRow) {
                                    item(key = "local_responding_indicator") {
                                        val localRespondingMessage = buildAssistantDisplayText(
                                            originalMessage = localRespondingAssistantRowMessage,
                                            tailLimitChars = assistantRenderTailLimitChars,
                                        ).text
                                        PlainAssistantMessage(
                                            message = localRespondingMessage,
                                            isStreaming = true,
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 10.dp)
                                        )
                                    }
                                }
                                if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE && devWhitespaceTraceText != null) {
                                    item(key = "dev_whitespace_trace") {
                                        val whitespaceTraceText = devWhitespaceTraceText!!
                                        CopyableDebugBlock(
                                            text = whitespaceTraceText,
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(whitespaceTraceText))
                                                coroutineScope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar(
                                                        message = "WS TRACE をコピーしました",
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                                if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE && devRunnerWhitespaceTraceText != null) {
                                    item(key = "dev_runner_whitespace_trace") {
                                        val runnerWhitespaceTraceText = devRunnerWhitespaceTraceText!!
                                        CopyableDebugBlock(
                                            text = runnerWhitespaceTraceText,
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(runnerWhitespaceTraceText))
                                                coroutineScope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar(
                                                        message = "RUNNER WS TRACE をコピーしました",
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                                if (isServerLoadingUi) {
                                    item(key = "assistant_streaming_indicator") {
                                        AssistantStreamingIndicator()
                                    }
                                }
                                item(key = "composer_spacer") {
                                    // IME 表示中でも末尾メッセージへ到達できるよう、既存の IME 分だけ末尾余白へ加算する
                                    Spacer(modifier = Modifier.height(ComposerMinHeight + ComposerBottomGapHeight + bottomDp))
                                }
                            }

                            if (shouldShowScrollToBottomFab) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        if (fabScrollTargetIndex >= 0) {
                                            autoFollowEnabled = true
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(fabScrollTargetIndex)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        // 入力欄と下部グラデーションの上に重ねるため、末尾ガターより上へ配置する
                                        .padding(end = 16.dp, bottom = ComposerMinHeight + ComposerBottomGapHeight + bottomDp + 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "最新へ"
                                    )
                                }
                            }
                            if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
                                Text(
                                    text = buildString {
                                        append("DEV stream")
                                        append("\nrawLen=")
                                        append(streamingResponseText?.length ?: 0)
                                        append(" renderLen=")
                                        append(streamingResponseTextForRender?.length ?: 0)
                                        append(" localLen=")
                                        append(localStreamingResponseText?.length ?: 0)
                                        append("\nstreaming=")
                                        append(isInferenceRunningUi)
                                        append(" lines=")
                                        append(devStreamingDisplayLineCount)
                                        append(" alive=")
                                        append(devUiAliveSeconds)
                                        append("s")
                                        append("\nuiTailLimit=")
                                        append(devStreamingTailLimitEnabled)
                                        append(" (")
                                        append(DEV_STREAMING_RENDER_TAIL_LIMIT_CHARS)
                                        append(")")
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(
                                            start = 8.dp,
                                            bottom = ComposerMinHeight + ComposerBottomGapHeight + bottomDp + 8.dp,
                                        )
                                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = Color.White.copy(alpha = 0.92f),
                                )
                            }
                        }
                    }
                }
            }
        }

            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // エラーバナーの上端だけは詰めて、他方向の余白を維持
                        .padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    ElevatedButton(onClick = { viewModel.loadAvailableModels() }) {
                        Text("再試行")
                    }
                }
            }

        }

        val topColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // 上部グラデーションはスクロール領域と独立した画面固定オーバーレイとして描画する
                .zIndex(10f)
                // 上部グラデーションの見た目サイズは維持し、表示位置のみ固定する
                .offset(y = TopGradientOverlayTopOffset + TopGradientOverlayYOffset + debugTopGradientDownshift)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // IME の表示有無に関係なく上部グラデの高さを固定する
                    .height(TopGradientOverlayHeight)
                    .onGloballyPositioned { coordinates ->
                        measuredTopGradientBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                    }
                    .clipToBounds()
                    .background(
                        brush = run {
                            // 既存挙動を維持しつつ、デバッグ時のみ先頭カラーをオレンジ系に差し替える。
                            val debugTint = if (debugTopGradientOrange) MaterialTheme.colorScheme.tertiary else null
                            val topGradientColor = debugTint ?: topColor
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to topGradientColor.copy(alpha = 1.0f),
                                    0.5f to topColor.copy(alpha = 0.6f),
                                    1.0f to topColor.copy(alpha = 0.0f)
                                )
                            )
                        }
                    )
            )
        }
    }

    if (showInferenceStatsSheet && selectedInferenceStats != null) {
        val stats = selectedInferenceStats
        val inferenceStatsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            sheetState = inferenceStatsSheetState,
            onDismissRequest = {
                showInferenceStatsSheet = false
                selectedInferenceStats = null
                selectedLocalTraceForDevSheet = null
                selectedAssistantMessageTextForStatsSheet = null
                selectedPromptMessageTextForStatsSheet = null
            },
        ) {
            stats?.let {
                InferenceStatsSheetContent(
                    stats = it,
                    initialDisplayMode = savedInferenceStatsDisplayMode,
                    onDisplayModeChange = { mode ->
                        coroutineScope.launch {
                            settingsPreferences.saveInferenceStatsDisplayMode(mode)
                        }
                    },
                    localTraceForDev = selectedLocalTraceForDevSheet,
                    assistantText = selectedAssistantMessageTextForStatsSheet,
                    promptText = selectedPromptMessageTextForStatsSheet,
                    devHeldStateText = if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) devHeldStateText else null,
                    devCloseLifecycleText = if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) devCloseLifecycleText else null,
                    devDebugText = if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) devDebugText else null,
                    preferredBackendDryRunSetting = preferredBackendDryRunSetting,
                    markdownStreamingMode = markdownStreamingMode,
                    showDevManualEngineRecreate = BuildConfig.DEBUG,
                    manualEngineRecreateBusy = preferredBackendManualRecreateInProgress,
                    manualEngineRecreateResult = preferredBackendManualRecreateResult,
                    manualEngineRecreateReason = preferredBackendManualRecreateReason,
                    manualEngineRecreateEnabled = !isInferenceRunningUi && !isTtsSpeaking && !isStreamingSentencePlaybackActive && !preferredBackendManualRecreateInProgress,
                    onManualEngineRecreate = {
                        val blocked = isInferenceRunningUi || isTtsSpeaking || isStreamingSentencePlaybackActive || preferredBackendManualRecreateInProgress
                        if (blocked) {
                            preferredBackendManualRecreateResult = "blocked-busy"
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("生成中は再作成できません")
                            }
                            return@InferenceStatsSheetContent
                        }
                        coroutineScope.launch {
                            preferredBackendManualRecreateInProgress = true
                            preferredBackendManualRecreateReason = "user-requested"
                            val succeeded = localInferenceEngineHolder.requestRecreateForDev(
                                reason = preferredBackendManualRecreateReason,
                                appendTrace = { appendLocalReflectionTrace(context.applicationContext, it) },
                            )
                            val recreateSnapshot = localInferenceEngineHolder.getDevDiagnosticSnapshot()
                            preferredBackendManualRecreateInProgress = false
                            preferredBackendManualRecreateResult = if (succeeded) "success" else "failed"
                            latestLocalTraceForDev = (latestLocalTraceForDev ?: LocalInferenceTrace()).copy(
                                holderInstanceHash = recreateSnapshot.holderInstanceHash,
                                heldEngineHash = recreateSnapshot.heldEngineHash,
                                heldEngineRecreateRequestCount = recreateSnapshot.recreateRequestCount,
                                holderLastRecreateResult = recreateSnapshot.lastRecreateResult,
                                holderLastRecreateReason = recreateSnapshot.lastRecreateReason,
                                holderHasHeldEngineBeforeRecreate = recreateSnapshot.hasHeldEngineBeforeRecreate,
                                holderHasHeldEngineAfterRecreate = recreateSnapshot.hasHeldEngineAfterRecreate,
                                lastHeldEngineCreateReason = recreateSnapshot.lastHeldEngineCreateReason,
                                lastHeldEngineCreateSource = recreateSnapshot.lastHeldEngineCreateSource,
                                lastHeldEngineCreateAtElapsedMs = recreateSnapshot.lastHeldEngineCreateAtElapsedMs,
                                lastHeldEngineCreateRequestedPreferredBackend = recreateSnapshot.lastHeldEngineCreateRequestedPreferredBackend,
                                lastHeldEngineCreateStackHint = recreateSnapshot.lastHeldEngineCreateStackHint,
                            )
                            snackbarHostState.showSnackbar(
                                if (succeeded) {
                                    "次回推論でローカルエンジンを再作成します"
                                } else {
                                    "ローカルエンジン再作成要求に失敗しました"
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    if (attachSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { attachSheetOpen = false },
        ) {
            ListItem(
                modifier = Modifier.clickable {
                    attachSheetOpen = false
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                headlineContent = { Text("Attach image") },
            )
            ListItem(
                modifier = Modifier.clickable { attachSheetOpen = false },
                headlineContent = { Text("Paste from clipboard") },
            )
            ListItem(
                modifier = Modifier.clickable { attachSheetOpen = false },
                headlineContent = { Text("Settings") },
            )
        }
    }

    composerViewerUriStrings?.let { uriStrings ->
        if (uriStrings.isEmpty()) {
            composerViewerUriStrings = null
            composerViewerInitialIndex = 0
        } else {
            val attachmentUris = uriStrings.map(Uri::parse)
            val safeIndex = composerViewerInitialIndex.coerceIn(0, attachmentUris.lastIndex.coerceAtLeast(0))
            AttachmentFullscreenViewer(
                attachmentUris = attachmentUris,
                initialIndex = safeIndex,
                onDismiss = {
                    composerViewerUriStrings = null
                    composerViewerInitialIndex = 0
                },
            )
        }
    }
}
}
}



internal fun normalizeStreamingPartialForRender(
    partial: String,
    markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
): String {
    return when (markdownStreamingMode) {
        MarkdownStreamingMode.LAMI_RECOVERY_V1 -> partial.trim()
        MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> processEdgeGalleryCompatibleMarkdown(partial)
    }
}

internal fun buildFinalizedStreamingResponseForPersist(
    response: String,
    markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
    onMarkdownRepair: (() -> Unit)? = null,
): String {
    val normalizedFinalText = response.trim()
    if (markdownStreamingMode == MarkdownStreamingMode.EDGE_GALLERY_COMPAT) {
        return processEdgeGalleryCompatibleMarkdown(normalizedFinalText).trim()
    }
    val repaired = MarkdownCodeRepair.repair(normalizedFinalText).trim()
    if (repaired != normalizedFinalText) {
        onMarkdownRepair?.invoke()
    }
    if (!normalizedFinalText.endsWith("#\n```") || repaired.endsWith("#\n```")) {
        return repaired
    }
    return repaired.replace(Regex("\\n```$"), "\n#\n```")
}

private fun previewForDevLog(
    text: String,
    maxLength: Int = 2000,
): String {
    if (text.length <= maxLength) return text
    val headLength = maxLength / 2
    val tailLength = maxLength - headLength
    val omittedCount = text.length - maxLength
    val head = text.take(headLength)
    val tail = text.takeLast(tailLength)
    return buildString {
        appendLine(head)
        appendLine("...<omitted $omittedCount chars>...")
        append(tail)
    }
}
fun shouldRefreshRender(
    prev: String,
    next: String,
    isStreaming: Boolean,
): Boolean {
    if (next.isEmpty()) return true
    if (!isStreaming) return true
    if (prev.isEmpty()) return true
    val appendedDelta = if (next.startsWith(prev)) {
        next.substring(prev.length)
    } else {
        next
    }
    val deltaTrimmedStart = appendedDelta.trimStart()
    return appendedDelta.contains('\n') ||
        appendedDelta.length >= 32 ||
        deltaTrimmedStart.startsWith("```") ||
        isPythonFusionStart(deltaTrimmedStart)
}

internal fun findStreamingTtsBreakIndex(remaining: String): Int {
    val sentenceBreakIndex = remaining.lastIndexOfAny(charArrayOf('。', '！', '？', '\n'))
    if (sentenceBreakIndex >= 0) return sentenceBreakIndex
    return remaining.lastIndexOfAny(charArrayOf('、', ',', '，', ';', '；', ':', '：'))
}

private suspend fun initializeLocalInferenceEngineEntry(
    context: Context,
    settingsPreferences: SettingsPreferences,
    localBaseModelFilePath: String?,
): LocalInferenceInitializationResult {
    val modelResolution = resolveLocalModelResolutionOrNull(
        context = context,
        settingsPreferences = settingsPreferences,
        localBaseModelFilePath = localBaseModelFilePath,
        localBaseModelDisplayName = null,
    ) ?: return LocalInferenceInitializationResult(
        state = LocalInferenceEngineState.UNINITIALIZED,
        probeResult = null,
    )

    val probeResult = loadLocalInferenceEngine(context = context, modelPath = modelResolution.modelPath)
    val state = if (probeResult == LocalLiteRtProbeResult.SUCCESS) {
        LocalInferenceEngineState.READY
    } else {
        LocalInferenceEngineState.ERROR
    }
    return LocalInferenceInitializationResult(state = state, probeResult = probeResult)
}

private suspend fun runLocalInferenceOnceEntry(
    context: Context,
    settingsPreferences: SettingsPreferences,
    localBaseModelFilePath: String?,
    localBaseModelDisplayName: String?,
    resolvedModelPath: String? = null,
    resolvedCacheDirPath: String? = null,
    mediaPipeProbeContext: Context? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
    prompt: String,
    onPartial: (String) -> Unit = {},
): LocalInferenceRunResult {
    val localTraceStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM runLocalInferenceOnceEntry-entry promptLength=${prompt.length} localBaseModelFilePathPresent=${!localBaseModelFilePath.isNullOrBlank()} localBaseModelDisplayName=${localBaseModelDisplayName ?: "null"}",
    )
    val modelResolution = if (!resolvedModelPath.isNullOrBlank()) {
        LocalModelResolution(
            modelPath = resolvedModelPath,
            displayName = resolveLocalModelDisplayName(localBaseModelDisplayName, resolvedModelPath),
            backendKey = LOCAL_LITERT_BACKEND_KEY,
            cacheDirPath = resolvedCacheDirPath ?: buildLiteRtCacheDirPath(context),
        )
    } else {
        resolveLocalModelResolutionOrNull(
            context = context,
            settingsPreferences = settingsPreferences,
            localBaseModelFilePath = localBaseModelFilePath,
            localBaseModelDisplayName = localBaseModelDisplayName,
        )
    } ?: run {
        appendLocalReflectionTrace(
            context = context,
            message = "UPSTREAM resolved-local-model-path success=false",
        )
        return LocalInferenceRunResult(state = LocalInferenceEngineState.UNINITIALIZED)
    }
    val modelPath = modelResolution.modelPath
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM resolved-local-model-path success=true modelPathTail=${modelPath.substringAfterLast('/')}",
    )
    val officialConversationApiProbe = probeLocalOfficialConversationApi()
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM official-conversation-api available=${officialConversationApiProbe.isAvailable} namespace=${officialConversationApiProbe.namespace ?: "none"} fallbackNamespaceMatched=${officialConversationApiProbe.fallbackNamespaceMatched} conversationClass=${officialConversationApiProbe.conversationClassFound} createConversation=${officialConversationApiProbe.createConversationMethodFound} sendMessageAsync=${officialConversationApiProbe.sendMessageAsyncMethodFound} sendMessageAsyncFlow=${officialConversationApiProbe.sendMessageAsyncReturnsFlow} messageClass=${officialConversationApiProbe.messageClassFound}",
    )
    var officialFlowAttempted = false
    var officialFlowUsed = false
    var officialFlowFallbackReason: String? = null
    var officialFlowChunkCount = 0
    var officialFlowObservedPartialCount = 0
    var preferredBackendApplyResult: PreferredBackendApplyResult? = null
    var localFailureDiagnosticsText: String? = null
    val emitFinal: (String?) -> Unit = { result ->
        if (!result.isNullOrBlank()) {
            onPartial(result)
        }
    }

    if (officialConversationApiProbe.isAvailable) {
        officialFlowAttempted = true
        appendLocalReflectionTrace(
            context = context,
            message = "UPSTREAM official-flow-streaming attempt",
        )
        val officialResult = tryRunOfficialLiteRtFlowStreaming(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = modelResolution.cacheDirPath,
            mediaPipeProbeContext = mediaPipeProbeContext,
            preferredBackendDryRunSetting = preferredBackendDryRunSetting,
            markdownStreamingMode = markdownStreamingMode,
            onPreferredBackendApplied = { result -> preferredBackendApplyResult = result },
            onPartial = { partial ->
                officialFlowObservedPartialCount += 1
                onPartial(partial)
            },
            appendTrace = { traceMessage ->
                appendLocalReflectionTrace(context = context, message = traceMessage)
            },
            onFallbackReason = { reasonCode ->
                officialFlowFallbackReason = reasonCode
            },
            onFailureDiagnostics = { diagnostics ->
                localFailureDiagnosticsText = diagnostics
            },
        )
        val officialResponse = officialResult?.response?.trim().orEmpty()
        val officialSucceeded = officialResponse.isNotBlank()
        officialFlowUsed = officialSucceeded
        officialFlowChunkCount = officialResult?.partialCount ?: 0
        if (officialSucceeded) {
            officialFlowFallbackReason = null
        } else if (officialFlowFallbackReason == null) {
            officialFlowFallbackReason = "empty_official_response"
        }
        if (!officialSucceeded && officialFlowObservedPartialCount == 0) {
            appendLocalReflectionTrace(
                context = context,
                message = "UPSTREAM fallback reason=no_partial_emitted",
            )
            val fallbackGenerated = generateLiteRtResponseViaReflection(
                context = context,
                modelPath = modelPath,
                localModelDisplayName = modelResolution.displayName,
                prompt = prompt,
                onPartial = onPartial,
            )
            val fallback = fallbackGenerated.response?.trim()
            if (!fallback.isNullOrBlank()) {
                appendLocalReflectionTrace(
                    context = context,
                    message = "UPSTREAM fallback used length=${fallback.length}",
                )
                emitFinal(fallback)
                return LocalInferenceRunResult(
                    state = LocalInferenceEngineState.READY,
                    response = fallback,
                    trace = LocalInferenceTrace(
                        localModelDisplayName = modelResolution.displayName,
                        localTraceStartElapsedRealtimeMs = localTraceStartElapsedRealtimeMs,
                        localTraceFirstResponseElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        localTraceCompletedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        selectedAssistantResponseSource = LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT,
                        officialFlowAttempted = officialFlowAttempted,
                        officialFlowUsed = officialFlowUsed,
                        officialFlowFallbackReason = officialFlowFallbackReason,
                        officialConversationApiAvailable = officialConversationApiProbe.isAvailable,
                        officialFlowChunkCount = officialFlowChunkCount,
                        preferredBackendHookReached = preferredBackendApplyResult?.preferredBackendHookReached,
                        preferredBackendHookSource = preferredBackendApplyResult?.preferredBackendHookSource,
                        requestedPreferredBackend = preferredBackendApplyResult?.requestedPreferredBackend,
                        appliedPreferredBackend = preferredBackendApplyResult?.appliedPreferredBackend,
                        preferredBackendApplyResult = preferredBackendApplyResult?.preferredBackendApplyResult,
                        preferredBackendApplyError = preferredBackendApplyResult?.preferredBackendApplyError,
                        preferredBackendApplyBuilderClass = preferredBackendApplyResult?.preferredBackendApplyBuilderClass,
                        preferredBackendApplyMethodCandidates = preferredBackendApplyResult?.preferredBackendApplyMethodCandidates.orEmpty(),
                        preferredBackendApplyBackendEnumCandidates = preferredBackendApplyResult?.preferredBackendApplyBackendEnumCandidates.orEmpty(),
                        preferredBackendApplyNotSupportedReason = preferredBackendApplyResult?.preferredBackendApplyNotSupportedReason,
                        localFailureDiagnosticsText = localFailureDiagnosticsText ?: fallbackGenerated.trace.localFailureDiagnosticsText,
                        ),
                    closeLifecycleSummary = ensureSuccessCloseLifecycleSummary(
                        summary = fallbackGenerated.closeLifecycleSummary,
                        path = "chat-fallback-official-flow-success",
                    ),
                )
            } else {
                appendLocalReflectionTrace(
                    context = context,
                    message = "UPSTREAM fallback failed blankOrNull",
                )
            }
        }
        appendLocalReflectionTrace(
            context = context,
            message = "UPSTREAM official-flow-streaming success=$officialSucceeded responseLength=${officialResponse.length} partialCount=${officialResult?.partialCount ?: 0}",
        )
        if (officialSucceeded) {
                return LocalInferenceRunResult(
                    state = LocalInferenceEngineState.READY,
                    response = officialResponse,
                trace = LocalInferenceTrace(
                    localModelDisplayName = modelResolution.displayName,
                    localTraceStartElapsedRealtimeMs = localTraceStartElapsedRealtimeMs,
                    localTraceFirstResponseElapsedRealtimeMs = officialResult?.firstNonEmptyPartialElapsedRealtimeMs?.let {
                        localTraceStartElapsedRealtimeMs + it
                    },
                    localTraceCompletedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    selectedAssistantResponseSource = LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_FLOW,
                    officialFlowAttempted = officialFlowAttempted,
                    officialFlowUsed = officialFlowUsed,
                    officialFlowFallbackReason = officialFlowFallbackReason,
                    officialConversationApiAvailable = officialConversationApiProbe.isAvailable,
                    officialFlowChunkCount = officialFlowChunkCount,
                    preferredBackendHookReached = preferredBackendApplyResult?.preferredBackendHookReached,
                    preferredBackendHookSource = preferredBackendApplyResult?.preferredBackendHookSource,
                    requestedPreferredBackend = preferredBackendApplyResult?.requestedPreferredBackend,
                    appliedPreferredBackend = preferredBackendApplyResult?.appliedPreferredBackend,
                    preferredBackendApplyResult = preferredBackendApplyResult?.preferredBackendApplyResult,
                    preferredBackendApplyError = preferredBackendApplyResult?.preferredBackendApplyError,
                    preferredBackendApplyBuilderClass = preferredBackendApplyResult?.preferredBackendApplyBuilderClass,
                    preferredBackendApplyMethodCandidates = preferredBackendApplyResult?.preferredBackendApplyMethodCandidates.orEmpty(),
                    preferredBackendApplyBackendEnumCandidates = preferredBackendApplyResult?.preferredBackendApplyBackendEnumCandidates.orEmpty(),
                    preferredBackendApplyNotSupportedReason = preferredBackendApplyResult?.preferredBackendApplyNotSupportedReason,
                    measuredTokenSnapshot = officialResult?.measuredTokenSnapshot,
                    localFailureDiagnosticsText = localFailureDiagnosticsText,
                ).withOfficialChunkMetrics(officialResult?.officialChunkMetrics),
                closeLifecycleSummary = ensureSuccessCloseLifecycleSummary(
                    summary = officialResult?.closeLifecycleSummary,
                    path = "chat-official-flow-success",
                ),
            )
        }
        appendLocalReflectionTrace(
            context = context,
            message = "UPSTREAM official-flow-streaming fallback reason=${officialFlowFallbackReason ?: "empty_official_response"}",
        )
        appendLocalReflectionTrace(
            context = context,
            message = "UPSTREAM official-blocking attempt",
        )
        val blockingResult = tryRunOfficialLiteRtBlockingConversation(
            prompt = prompt,
            modelPath = modelPath,
            cacheDirPath = modelResolution.cacheDirPath,
            mediaPipeProbeContext = mediaPipeProbeContext,
            preferredBackendDryRunSetting = preferredBackendDryRunSetting,
            onPreferredBackendApplied = { result -> preferredBackendApplyResult = result },
            appendTrace = { traceMessage ->
                appendLocalReflectionTrace(context = context, message = traceMessage)
            },
            onFallbackReason = { reasonCode ->
                if (officialFlowFallbackReason == null) {
                    officialFlowFallbackReason = reasonCode
                }
            },
            onFailureDiagnostics = { diagnostics ->
                localFailureDiagnosticsText = diagnostics
            },
        )
        val blockingResponse = blockingResult?.response?.trim().orEmpty()
        if (blockingResponse.isNotBlank()) {
            return LocalInferenceRunResult(
                state = LocalInferenceEngineState.READY,
                response = blockingResponse,
                trace = LocalInferenceTrace(
                    localModelDisplayName = modelResolution.displayName,
                    localTraceStartElapsedRealtimeMs = localTraceStartElapsedRealtimeMs,
                    localTraceFirstResponseElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    localTraceCompletedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    selectedAssistantResponseSource = LOCAL_ASSISTANT_RESPONSE_SOURCE_OFFICIAL_BLOCKING,
                    officialFlowAttempted = officialFlowAttempted,
                    officialFlowUsed = officialFlowUsed,
                    officialFlowFallbackReason = officialFlowFallbackReason,
                    officialConversationApiAvailable = officialConversationApiProbe.isAvailable,
                    officialFlowChunkCount = officialFlowChunkCount,
                    preferredBackendHookReached = preferredBackendApplyResult?.preferredBackendHookReached,
                    preferredBackendHookSource = preferredBackendApplyResult?.preferredBackendHookSource,
                    requestedPreferredBackend = preferredBackendApplyResult?.requestedPreferredBackend,
                    appliedPreferredBackend = preferredBackendApplyResult?.appliedPreferredBackend,
                    preferredBackendApplyResult = preferredBackendApplyResult?.preferredBackendApplyResult,
                    preferredBackendApplyError = preferredBackendApplyResult?.preferredBackendApplyError,
                    preferredBackendApplyBuilderClass = preferredBackendApplyResult?.preferredBackendApplyBuilderClass,
                    preferredBackendApplyMethodCandidates = preferredBackendApplyResult?.preferredBackendApplyMethodCandidates.orEmpty(),
                    preferredBackendApplyBackendEnumCandidates = preferredBackendApplyResult?.preferredBackendApplyBackendEnumCandidates.orEmpty(),
                    preferredBackendApplyNotSupportedReason = preferredBackendApplyResult?.preferredBackendApplyNotSupportedReason,
                    measuredTokenSnapshot = blockingResult?.measuredTokenSnapshot,
                    localFailureDiagnosticsText = localFailureDiagnosticsText,
                ),
                closeLifecycleSummary = ensureSuccessCloseLifecycleSummary(
                    summary = blockingResult?.closeLifecycleSummary,
                    path = "chat-official-blocking-success",
                ),
            )
        }
        appendLocalReflectionTrace(
            context = context,
            message = "UPSTREAM official-blocking fallback reason=${officialFlowFallbackReason ?: "empty_official_response"}",
        )
    } else {
        officialFlowFallbackReason = "api_unavailable"
        appendLocalReflectionTrace(
            context = context,
            message = "UPSTREAM official-flow-streaming fallback reason=api_unavailable",
        )
    }

    appendLocalReflectionTrace(context = context, message = "UPSTREAM legacy start")
    appendLocalReflectionTrace(context = context, message = "UPSTREAM before-generateLiteRtResponseViaReflection")
    val generated = generateLiteRtResponseViaReflection(
        context = context,
        modelPath = modelPath,
        localModelDisplayName = modelResolution.displayName,
        prompt = prompt,
        onPartial = onPartial,
    )
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM legacy returnClass=${generated.response?.javaClass?.name ?: "null"} rawLength=${generated.response?.length ?: -1}",
    )
    val response = generated.response?.trim()
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM legacy sanitizedLength=${response?.length ?: -1} selectedFinalLength=${response?.length ?: 0} blankOrNull=${response.isNullOrBlank()}",
    )
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM after-generateLiteRtResponseViaReflection responseNull=${response == null} responseLength=${response?.length ?: -1}",
    )
    val resolvedSource = when (generated.trace.selectedAssistantResponseSource) {
        LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT -> LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT
        else -> LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_LEGACY
    }
    val preferredBackendApplyMethodCandidates = preferredBackendApplyResult?.preferredBackendApplyMethodCandidates
    val preferredBackendApplyBackendEnumCandidates = preferredBackendApplyResult?.preferredBackendApplyBackendEnumCandidates
    val traceWithOfficialFlow = generated.trace.copy(
        selectedAssistantResponseSource = resolvedSource.takeIf { !response.isNullOrBlank() },
        officialFlowAttempted = officialFlowAttempted,
        officialFlowUsed = officialFlowUsed,
        officialFlowFallbackReason = officialFlowFallbackReason,
        officialConversationApiAvailable = officialConversationApiProbe.isAvailable,
        officialFlowChunkCount = officialFlowChunkCount,
        preferredBackendHookReached = preferredBackendApplyResult?.preferredBackendHookReached ?: generated.trace.preferredBackendHookReached,
        preferredBackendHookSource = preferredBackendApplyResult?.preferredBackendHookSource ?: generated.trace.preferredBackendHookSource,
        requestedPreferredBackend = preferredBackendApplyResult?.requestedPreferredBackend ?: generated.trace.requestedPreferredBackend,
        appliedPreferredBackend = preferredBackendApplyResult?.appliedPreferredBackend ?: generated.trace.appliedPreferredBackend,
        preferredBackendApplyResult = preferredBackendApplyResult?.preferredBackendApplyResult ?: generated.trace.preferredBackendApplyResult,
        preferredBackendApplyError = preferredBackendApplyResult?.preferredBackendApplyError ?: generated.trace.preferredBackendApplyError,
        preferredBackendApplyBuilderClass = preferredBackendApplyResult?.preferredBackendApplyBuilderClass ?: generated.trace.preferredBackendApplyBuilderClass,
        preferredBackendApplyMethodCandidates = if (!preferredBackendApplyMethodCandidates.isNullOrEmpty()) preferredBackendApplyMethodCandidates else generated.trace.preferredBackendApplyMethodCandidates,
        preferredBackendApplyBackendEnumCandidates = if (!preferredBackendApplyBackendEnumCandidates.isNullOrEmpty()) preferredBackendApplyBackendEnumCandidates else generated.trace.preferredBackendApplyBackendEnumCandidates,
        preferredBackendApplyNotSupportedReason = preferredBackendApplyResult?.preferredBackendApplyNotSupportedReason ?: generated.trace.preferredBackendApplyNotSupportedReason,
        localFailureDiagnosticsText = generated.trace.localFailureDiagnosticsText ?: localFailureDiagnosticsText,
    )
    emitFinal(response)
    return if (response.isNullOrBlank()) {
        LocalInferenceRunResult(
            state = LocalInferenceEngineState.ERROR,
            trace = traceWithOfficialFlow,
            closeLifecycleSummary = generated.closeLifecycleSummary,
        )
    } else {
        LocalInferenceRunResult(
            state = LocalInferenceEngineState.READY,
            response = response,
            trace = traceWithOfficialFlow,
            closeLifecycleSummary = ensureSuccessCloseLifecycleSummary(
                summary = generated.closeLifecycleSummary,
                path = "chat-legacy-success",
            ),
        )
    }
}

private fun HeldEngineRunResult.toLocalInferenceRunResult(): LocalInferenceRunResult {
    val executionPath = if (officialFlowUsed) {
        LocalExecutionPath.HELD_OFFICIAL_FLOW
    } else {
        LocalExecutionPath.HELD_OFFICIAL_BLOCKING
    }
    val resolvedState = if (responseText.isNotBlank()) {
        LocalInferenceEngineState.READY
    } else {
        LocalInferenceEngineState.ERROR
    }
    return LocalInferenceRunResult(
        state = resolvedState,
        response = responseText,
        trace = LocalInferenceTrace(
            localModelDisplayName = localModelDisplayName,
            localTraceStartElapsedRealtimeMs = startElapsedRealtimeMs,
            localTraceFirstResponseElapsedRealtimeMs = firstPartialElapsedRealtimeMs,
            localTraceCompletedElapsedRealtimeMs = completedElapsedRealtimeMs,
            selectedAssistantResponseSource = executionPath.sourceLabel,
            officialFlowAttempted = executionPath.officialFlowAttempted,
            officialFlowUsed = executionPath.officialFlowUsed,
            officialFlowFallbackReason = null,
            officialConversationApiAvailable = namespace.isNotBlank(),
            officialFlowChunkCount = partialCount,
            measuredTokenSnapshot = measuredTokenSnapshot,
            heldEngineCreatePath = heldEngineCreatePath,
            llmInferenceCreateMethod = llmInferenceCreateMethod,
            optionsBuilderSource = optionsBuilderSource,
            preferredBackendHookEligible = preferredBackendHookEligible,
            preferredBackendHookMissingReason = preferredBackendHookMissingReason,
            holderInstanceHash = holderInstanceHash,
            heldEngineHash = heldEngineHash,
            holderAppInForeground = holderAppInForeground,
            holderLastAcquireAction = holderLastAcquireAction,
            holderLastLifecycleEventReason = holderLastLifecycleEventReason,
            holderLastLifecycleDecisionAction = holderLastLifecycleDecisionAction,
            heldEngineRecreateRequestCount = heldEngineRecreateRequestCount,
            heldEngineWasPresentAtRunStart = heldEngineWasPresentAtRunStart,
            heldEngineCreatedDuringRun = heldEngineCreatedDuringRun,
            lastHeldEngineCreateReason = lastHeldEngineCreateReason,
            lastHeldEngineCreateSource = lastHeldEngineCreateSource,
            lastHeldEngineCreateAtElapsedMs = lastHeldEngineCreateAtElapsedMs,
            lastHeldEngineCreateRequestedPreferredBackend = lastHeldEngineCreateRequestedPreferredBackend,
            lastHeldEngineCreateStackHint = lastHeldEngineCreateStackHint,
            requestedPreferredBackend = lastHeldEngineCreateRequestedPreferredBackend,
            appliedPreferredBackend = lastHeldEngineCreateAppliedPreferredBackend,
            preferredBackendApplyResult = lastHeldEngineCreatePreferredBackendApplyResult,
            preferredBackendHookReached = lastHeldEngineCreatePreferredBackendHookReached,
            preferredBackendHookSource = lastHeldEngineCreatePreferredBackendHookSource,
            preferredBackendApplyBuilderClass = lastHeldEngineCreatePreferredBackendApplyBuilderClass,
            preferredBackendApplyBackendEnumCandidates = lastHeldEngineCreatePreferredBackendApplyBackendEnumCandidates,
            localFailureDiagnosticsText = failureDiagnosticsText,
        ).withOfficialChunkMetrics(officialChunkMetrics),
        closeLifecycleSummary = if (resolvedState == LocalInferenceEngineState.READY) {
            ensureSuccessCloseLifecycleSummary(
                summary = closeLifecycleSummary,
                path = if (officialFlowUsed) "chat-held-official-flow-success" else "chat-held-official-blocking-success",
            )
        } else {
            closeLifecycleSummary
        },
        runnerWhitespaceTraceText = runnerWhitespaceTraceText,
    )
}

private fun normalizeLocalInferenceRunResult(result: LocalInferenceRunResult?): LocalInferenceRunResult? {
    if (result == null) return null
    val executionPath = LocalExecutionPath.fromSourceLabel(result.trace.selectedAssistantResponseSource)
        ?: LocalExecutionPath.fromClosePath(result.closeLifecycleSummary?.path)
    val usesOfficialApi = executionPath?.usesOfficialConversationApi == true
    val officialFlowUsed = executionPath?.officialFlowUsed ?: result.trace.officialFlowUsed
    val officialFlowAttempted = when {
        executionPath != null -> executionPath.officialFlowAttempted
        officialFlowUsed -> true
        else -> result.trace.officialFlowAttempted
    }
    val officialFlowFallbackReason = if (officialFlowUsed) {
        null
    } else {
        result.trace.officialFlowFallbackReason
    }
    val normalizedTrace = result.trace.copy(
        selectedAssistantResponseSource = executionPath?.sourceLabel
            ?: result.trace.selectedAssistantResponseSource,
        officialFlowAttempted = officialFlowAttempted,
        officialFlowUsed = officialFlowUsed,
        officialFlowFallbackReason = officialFlowFallbackReason,
        officialConversationApiAvailable = when {
            result.trace.officialConversationApiAvailable != null -> result.trace.officialConversationApiAvailable
            usesOfficialApi -> true
            else -> null
        },
        outputTokenProbe = normalizeStatsProbeAvailability(
            probe = result.trace.outputTokenProbe,
            derivableNow = result.trace.sessionResponseTokens != null,
            usesOfficialApi = usesOfficialApi,
        ),
        evalTimeProbe = normalizeStatsProbeAvailability(
            probe = result.trace.evalTimeProbe,
            derivableNow = result.trace.localTraceStartElapsedRealtimeMs != null &&
                result.trace.localTraceCompletedElapsedRealtimeMs != null,
            usesOfficialApi = usesOfficialApi,
        ),
        firstTokenProbe = normalizeStatsProbeAvailability(
            probe = result.trace.firstTokenProbe,
            derivableNow = result.trace.localTraceStartElapsedRealtimeMs != null &&
                result.trace.localTraceFirstResponseElapsedRealtimeMs != null,
            usesOfficialApi = usesOfficialApi,
        ),
    )
    return result.copy(trace = normalizedTrace)
}

private fun normalizeStatsProbeAvailability(
    probe: LocalStatsCandidateProbe,
    derivableNow: Boolean,
    usesOfficialApi: Boolean,
): LocalStatsCandidateProbe {
    if (probe.availability != LocalStatsAvailability.NOT_FOUND) return probe
    return when {
        derivableNow -> probe.copy(availability = LocalStatsAvailability.DERIVABLE_NOW)
        usesOfficialApi -> probe.copy(availability = LocalStatsAvailability.API_CANDIDATE_ONLY)
        else -> probe
    }
}

private fun ensureSuccessCloseLifecycleSummary(
    summary: RunCloseLifecycleSummary?,
    path: String,
): RunCloseLifecycleSummary {
    return summary ?: RunCloseLifecycleSummary(
        path = path,
        successReturned = true,
    )
}

private fun buildCloseLifecycleText(summary: RunCloseLifecycleSummary?): String? {
    if (summary == null) return null
    fun formatOutcome(label: String, outcome: RunCloseTargetOutcome?): String {
        if (outcome == null) return "$label=status=none"
        return buildString {
            append(label).append("=status=").append(outcome.status)
            append(" strategy=").append(outcome.strategy ?: "none")
            append(" class=").append(outcome.targetClassName ?: "null")
            if (!outcome.errorClassName.isNullOrBlank()) {
                append(" error=").append(outcome.errorClassName)
            }
            if (!outcome.message.isNullOrBlank()) {
                append(" message=").append(outcome.message)
            }
        }
    }
    return buildString {
        append("CLOSE LIFECYCLE\n")
        append("path=").append(summary.path).append("\n")
        append("successReturned=").append(summary.successReturned).append("\n")
        append(formatOutcome("conversation", summary.conversationOutcome)).append("\n")
        append(formatOutcome("engine", summary.engineOutcome)).append("\n")
        append(formatOutcome("session", summary.sessionOutcome)).append("\n")
        append(formatOutcome("inference", summary.inferenceOutcome))
        summary.notes?.takeIf { it.isNotBlank() }?.let { note ->
            append("\nnotes=").append(note)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private suspend fun LocalInferenceEngineHolder.acquireOrCreate(
    engineKey: HeldEngineKey,
    context: Context,
    appendTrace: ((String) -> Unit)? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
): HeldLocalEngine {
    return acquire(
        engineKey = engineKey,
        appendTrace = appendTrace,
        preferredBackendDryRunSetting = preferredBackendDryRunSetting,
    )
}

private suspend fun resolveLocalBaseModelPathOrNull(
    settingsPreferences: SettingsPreferences,
    localBaseModelFilePath: String?,
): String? {
    val validPathFromSettings = runCatching {
        settingsPreferences.getValidLocalBaseModelPathOrNull()
    }.getOrElse {
        Log.e("ChatScreen", "Failed to resolve valid local base model path from settings", it)
        return null
    }
    if (validPathFromSettings != null) {
        return validPathFromSettings
    }

    val fallbackPath = localBaseModelFilePath?.takeIf { it.isNotBlank() } ?: return null
    val fallbackFile = File(fallbackPath)
    return fallbackPath.takeIf {
        fallbackFile.isFile &&
            fallbackFile.canRead() &&
            fallbackFile.name.endsWith(".litertlm", ignoreCase = true)
    }
}

private const val LOCAL_LITERT_BACKEND_KEY = "text=GPU/vision=GPU/audio=CPU"

private fun buildLiteRtCacheDirPath(context: Context): String = context.cacheDir.absolutePath

private fun resolveLocalModelDisplayName(
    localBaseModelDisplayName: String?,
    modelPath: String,
): String {
    val normalizedDisplayName = localBaseModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedDisplayName != null) return normalizedDisplayName
    return File(modelPath).name.removeSuffix(".litertlm")
}

internal fun shouldApplyHeldEngineModelPath(localBaseModelFilePath: String?): Boolean {
    return !localBaseModelFilePath.isNullOrBlank()
}

private fun captureTtsMemorySnapshot(context: Context): TtsMemorySnapshot {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val systemMemoryInfo = ActivityManager.MemoryInfo()
    val hasSystemMemoryInfo = runCatching {
        activityManager?.getMemoryInfo(systemMemoryInfo)
        activityManager != null
    }.getOrDefault(false)
    val processMemoryInfo = Debug.MemoryInfo()
    runCatching {
        Debug.getMemoryInfo(processMemoryInfo)
    }
    return TtsMemorySnapshot(
        lowMemory = hasSystemMemoryInfo && systemMemoryInfo.lowMemory,
        availableMemoryMb = if (hasSystemMemoryInfo) systemMemoryInfo.availMem / (1024L * 1024L) else null,
        thresholdMemoryMb = if (hasSystemMemoryInfo) systemMemoryInfo.threshold / (1024L * 1024L) else null,
        appTotalPssMb = kbToMb(processMemoryInfo.totalPss),
        appNativePssMb = kbToMb(processMemoryInfo.nativePss),
    )
}

internal fun buildTtsMemoryDecisionDebugText(
    snapshot: TtsMemorySnapshot,
    decision: TtsMemoryReleaseDecision,
): String = buildString {
    appendLine("DEV TTS MEMORY")
    append("decision=").append(if (decision.shouldReleaseHeldEngine) "release-held-engine" else "keep-held-engine").appendLine()
    append("reason=").append(decision.reason).appendLine()
    append("lowMemory=").append(snapshot.lowMemory).appendLine()
    append("availableMemoryMb=").append(snapshot.availableMemoryMb ?: "unknown").appendLine()
    append("thresholdMemoryMb=").append(snapshot.thresholdMemoryMb ?: "unknown").appendLine()
    append("appTotalPssMb=").append(snapshot.appTotalPssMb ?: "unknown").appendLine()
    append("appNativePssMb=").append(snapshot.appNativePssMb ?: "unknown")
}

private suspend fun resolveLocalModelResolutionOrNull(
    context: Context,
    settingsPreferences: SettingsPreferences,
    localBaseModelFilePath: String?,
    localBaseModelDisplayName: String?,
): LocalModelResolution? {
    val modelPath = resolveLocalBaseModelPathOrNull(
        settingsPreferences = settingsPreferences,
        localBaseModelFilePath = localBaseModelFilePath,
    ) ?: return null
    return LocalModelResolution(
        modelPath = modelPath,
        displayName = resolveLocalModelDisplayName(localBaseModelDisplayName, modelPath),
        backendKey = LOCAL_LITERT_BACKEND_KEY,
        cacheDirPath = buildLiteRtCacheDirPath(context),
    )
}

private fun loadLocalInferenceEngine(
    context: Context,
    modelPath: String,
): LocalLiteRtProbeResult {
    val modelFile = File(modelPath)
    if (!modelFile.isFile || !modelFile.canRead()) {
        Log.w("ChatScreen", "Local model file is not readable. path=$modelPath")
        return LocalLiteRtProbeResult.CREATE_FAILED
    }

    return runCatching {
        tryLoadLiteRtLmViaReflection(context = context, modelPath = modelPath)
    }.getOrElse {
        Log.e("ChatScreen", "Failed to load local inference engine. path=$modelPath", it)
        LocalLiteRtProbeResult.CREATE_FAILED
    }
}

private fun tryLoadLiteRtLmViaReflection(
    context: Context,
    modelPath: String,
): LocalLiteRtProbeResult {
    val llmInferenceClass = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
    }.getOrElse { throwable ->
        Log.w("ChatScreen", "LiteRT-LM class not found: API is not connected in this build.", throwable)
        return LocalLiteRtProbeResult.API_NOT_CONNECTED
    }

    val createFromOptionsMethod = llmInferenceClass.methods.firstOrNull { method ->
        method.name == "createFromOptions" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == Context::class.java
    } ?: run {
        Log.w("ChatScreen", "LiteRT-LM createFromOptions(Context, Options) method not found.")
        return LocalLiteRtProbeResult.CREATE_METHOD_NOT_FOUND
    }
    Log.i("ChatScreen", "LiteRT-LM createFromOptions found: ${createFromOptionsMethod.toGenericString()}")

    val optionsClass = createFromOptionsMethod.parameterTypes[1]
    Log.i("ChatScreen", "LiteRT-LM options class: ${optionsClass.name}")
    val options = runCatching {
        buildLiteRtOptionsViaReflection(optionsClass = optionsClass, modelPath = modelPath)
    }.getOrElse { throwable ->
        if (throwable is NoSuchMethodException) {
            Log.w("ChatScreen", "LiteRT-LM options/builder method not found.", throwable)
            return LocalLiteRtProbeResult.CREATE_METHOD_NOT_FOUND
        }
        Log.e("ChatScreen", "LiteRT-LM options build failed.", throwable)
        return LocalLiteRtProbeResult.CREATE_FAILED
    }

    val created = runCatching {
        createFromOptionsMethod.invoke(null, context, options)
    }.getOrElse { throwable ->
        Log.e("ChatScreen", "LiteRT-LM createFromOptions invocation failed.", throwable)
        return LocalLiteRtProbeResult.CREATE_FAILED
    }
    Log.i("ChatScreen", "LiteRT-LM createFromOptions invoke succeeded.")

    if (created == null) {
        Log.w("ChatScreen", "LiteRT-LM createFromOptions returned null instance.")
        return LocalLiteRtProbeResult.CREATE_FAILED
    }

    val hasStreamingApiCandidate = probeLiteRtStreamingApiViaReflection()
    if (hasStreamingApiCandidate) {
        Log.i("ChatScreen", "LiteRT-LM streaming API candidate detected via reflection probe.")
    } else {
        Log.i("ChatScreen", "LiteRT-LM streaming API candidate not detected in this build.")
    }

    return try {
        tryCheckLiteRtLmGenerateViaReflection(created)
    } finally {
        runCatching {
            (created as? AutoCloseable)?.close()
        }
    }
}

private fun generateLiteRtResponseViaReflection(
    context: Context,
    modelPath: String,
    localModelDisplayName: String?,
    prompt: String,
    onPartial: (String) -> Unit = {},
): LocalLiteRtGeneratedResponse {
    var trace = LocalInferenceTrace(
        localModelDisplayName = localModelDisplayName,
        mediaPipeProbeModelPath = modelPath,
        localTraceStartElapsedRealtimeMs = SystemClock.elapsedRealtime(),
    )
    val modelPathTail = modelPath.substringAfterLast('/')
    Log.i(
        "ChatScreen",
        "LOCAL reflection entry: promptLength=${prompt.length}, model=${localModelDisplayName ?: "null"}",
    )
    appendLocalReflectionTrace(
        context = context,
        message = "entry promptLength=${prompt.length} model=${localModelDisplayName ?: "null"} modelPathTail=$modelPathTail",
    )
    val llmInferenceClass = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
    }.getOrElse { throwable ->
        trace = trace.copy(
            localFailureDiagnosticsText = buildLocalInferenceFailureDiagnosticsText(
                context = context,
                stage = "engine-create",
                throwable = throwable,
                selectedModelName = modelPath,
                selectedFallbackPath = "none",
            ),
        )
        Log.i("ChatScreen", "LOCAL reflection early-return: llm class load failed")
        appendLocalReflectionTrace(context = context, message = "early-return reason=llm-class-load-failed")
        Log.w("ChatScreen", "LiteRT-LM class not found for response generation.", throwable)
        return LocalLiteRtGeneratedResponse(trace = trace)
    }

    val createFromOptionsMethod = llmInferenceClass.methods.firstOrNull { method ->
        method.name == "createFromOptions" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == Context::class.java
    } ?: run {
        Log.i("ChatScreen", "LOCAL reflection early-return: createFromOptions method not found")
        appendLocalReflectionTrace(context = context, message = "early-return reason=createFromOptions-method-not-found")
        Log.w("ChatScreen", "LiteRT-LM createFromOptions(Context, Options) method not found for response generation.")
        return LocalLiteRtGeneratedResponse(trace = trace)
    }
    trace = trace.copy(createMethodSignature = createFromOptionsMethod.toGenericString())
    appendLocalReflectionTrace(
        context = context,
        message = "createFromOptions method signature=${createFromOptionsMethod.toGenericString()}",
    )
    val optionsBuildResult = runCatching {
        buildLiteRtOptionsViaReflection(
            optionsClass = createFromOptionsMethod.parameterTypes[1],
            modelPath = modelPath,
        )
    }.getOrElse { throwable ->
        trace = trace.copy(
            localFailureDiagnosticsText = buildLocalInferenceFailureDiagnosticsText(
                context = context,
                stage = "engine-create",
                throwable = throwable,
                selectedModelName = modelPath,
                selectedFallbackPath = "none",
            ),
        )
        Log.i("ChatScreen", "LOCAL reflection early-return: options build failed")
        appendLocalReflectionTrace(context = context, message = "early-return reason=options-build-failed")
        Log.e("ChatScreen", "LiteRT-LM options build failed for response generation.", throwable)
        return LocalLiteRtGeneratedResponse(trace = trace)
    }
    trace = trace.copy(optionsBuildPath = optionsBuildResult.buildPath)
    Log.i("ChatScreen", "LOCAL reflection options-built: buildPath=${optionsBuildResult.buildPath}")
    appendLocalReflectionTrace(context = context, message = "options-build success path=${optionsBuildResult.buildPath}")

    val loadStartNs = SystemClock.elapsedRealtimeNanos()
    Log.i("ChatScreen", "LOCAL reflection before-createFromOptions")
    appendLocalReflectionTrace(context = context, message = "createFromOptions invoke-start")
    val inferenceInstance = runCatching {
        createFromOptionsMethod.invoke(null, context, optionsBuildResult.options)
    }.getOrElse { throwable ->
        trace = trace.copy(
            localFailureDiagnosticsText = buildLocalInferenceFailureDiagnosticsText(
                context = context,
                stage = "engine-create",
                throwable = throwable,
                selectedModelName = modelPath,
                selectedFallbackPath = "none",
            ),
        )
        Log.i("ChatScreen", "LOCAL reflection early-return: createFromOptions invocation failed")
        appendLocalReflectionTrace(context = context, message = "early-return reason=createFromOptions-invocation-failed")
        Log.e("ChatScreen", "LiteRT-LM createFromOptions invocation failed for response generation.", throwable)
        return LocalLiteRtGeneratedResponse(trace = trace)
    } ?: run {
        Log.i("ChatScreen", "LOCAL reflection early-return: createFromOptions returned null")
        appendLocalReflectionTrace(context = context, message = "early-return reason=createFromOptions-returned-null")
        Log.w("ChatScreen", "LiteRT-LM createFromOptions returned null instance for response generation.")
        return LocalLiteRtGeneratedResponse(trace = trace)
    }
    Log.i("ChatScreen", "LOCAL reflection after-createFromOptions: inferenceClass=${inferenceInstance.javaClass.name}")
    val wallClockLoadDurationNs = (SystemClock.elapsedRealtimeNanos() - loadStartNs).coerceAtLeast(0L)
    trace = trace.copy(wallClockLoadDurationNs = wallClockLoadDurationNs)
    appendLocalReflectionTrace(
        context = context,
        message = "createFromOptions success inferenceClass=${inferenceInstance.javaClass.name} wallClockLoadDurationNs=$wallClockLoadDurationNs",
    )

    Log.i("ChatScreen", "LOCAL reflection before-streaming-probe")
    val streamingCandidateDetected = probeLiteRtStreamingApiViaReflection()
    Log.i("ChatScreen", "LOCAL reflection after-streaming-probe: detected=$streamingCandidateDetected")
    val listenerProbe = findSetResultListenerCandidate(inferenceClass = inferenceInstance.javaClass)
    Log.i("ChatScreen", "LOCAL reflection listener-probe: result=${listenerProbe.result}, signature=${listenerProbe.signature}")
    val asyncProbe = findGenerateResponseAsyncCandidate(inferenceClass = inferenceInstance.javaClass)
    Log.i("ChatScreen", "LOCAL reflection async-probe: result=${asyncProbe.result}, signature=${asyncProbe.signature}")
    val sessionProbe = findSessionApiCandidate(inferenceClass = inferenceInstance.javaClass)
    Log.i("ChatScreen", "LOCAL reflection session-probe: result=${sessionProbe.result}, signature=${sessionProbe.signature}")
    val sessionMethodInventory = inspectLlmInferenceSessionMethods()
    val sessionAsyncPocResult = tryCallLlmInferenceSessionGenerateResponseAsyncForDev(
        context = context,
        inferenceInstance = inferenceInstance,
        prompt = prompt,
    )
    trace = trace.copy(
        streamingCandidateDetected = streamingCandidateDetected,
        listenerApiProbeResult = listenerProbe.result,
        listenerApiSignature = listenerProbe.signature,
        asyncApiProbeResult = asyncProbe.result,
        asyncApiSignature = asyncProbe.signature,
        sessionApiProbeResult = sessionProbe.result,
        sessionApiSignature = sessionProbe.signature,
        sessionGenerateSignature = sessionMethodInventory.generateSignature,
        sessionAsyncSignature = sessionMethodInventory.asyncSignature,
        sessionStreamingSignature = sessionMethodInventory.streamingSignature,
        sessionTokenSignature = sessionMethodInventory.tokenSignature,
        sessionListenerSignature = sessionMethodInventory.listenerSignature,
        sessionLifecycleSignature = sessionMethodInventory.lifecycleSignature,
        sessionAsyncPocAttempted = sessionAsyncPocResult.attempted,
        sessionAsyncPocCreateSucceeded = sessionAsyncPocResult.createSucceeded,
        sessionAsyncPocMethodSignature = sessionAsyncPocResult.asyncMethodSignature,
        sessionAsyncPocFutureClassName = sessionAsyncPocResult.futureClassName,
        sessionAsyncPocResponseLength = sessionAsyncPocResult.responseLength,
        sessionAsyncPocResponseHead = sessionAsyncPocResult.responseHead,
        localTraceFirstResponseElapsedRealtimeMs = sessionAsyncPocResult.localTraceFirstResponseElapsedRealtimeMs,
        sessionAsyncPocCloseSucceeded = sessionAsyncPocResult.closeSucceeded,
        sessionAsyncPocErrorStage = sessionAsyncPocResult.errorStage,
        sessionAsyncPocErrorClassName = sessionAsyncPocResult.errorClassName,
        sessionAsyncPocErrorMessage = sessionAsyncPocResult.errorMessage,
    )
    Log.i(
        "ChatScreen",
        "LOCAL streaming probe: detected=$streamingCandidateDetected, listener=${trace.listenerApiProbeResult}, async=${trace.asyncApiProbeResult}, session=${trace.sessionApiProbeResult}",
    )
    Log.i(
        "ChatScreen",
        "LOCAL streaming signatures: listener=${trace.listenerApiSignature ?: "—"}, async=${trace.asyncApiSignature ?: "—"}, session=${trace.sessionApiSignature ?: "—"}, sessionGenerate=${trace.sessionGenerateSignature ?: "—"}, sessionAsync=${trace.sessionAsyncSignature ?: "—"}, sessionStreaming=${trace.sessionStreamingSignature ?: "—"}, sessionToken=${trace.sessionTokenSignature ?: "—"}",
    )
    appendLocalReflectionTrace(
        context = context,
        message = "streaming-probe detected=$streamingCandidateDetected listener=${trace.listenerApiProbeResult} async=${trace.asyncApiProbeResult} session=${trace.sessionApiProbeResult} listenerSig=${trace.listenerApiSignature ?: "—"} asyncSig=${trace.asyncApiSignature ?: "—"} sessionSig=${trace.sessionApiSignature ?: "—"} sessionGenerateSig=${trace.sessionGenerateSignature ?: "—"} sessionAsyncSig=${trace.sessionAsyncSignature ?: "—"} sessionStreamingSig=${trace.sessionStreamingSignature ?: "—"} sessionTokenSig=${trace.sessionTokenSignature ?: "—"}",
    )
    var successReached = false
    var generatedResponse: LocalLiteRtGeneratedResponse? = null
    var closeOutcome: RunCloseTargetOutcome? = null
    try {
        val generated = generateLiteRtStringResponseOnceViaReflection(
            context = context,
            inferenceInstance = inferenceInstance,
            prompt = prompt,
            trace = trace,
            sessionAsyncPocResult = sessionAsyncPocResult,
            onPartial = onPartial,
        )
        Log.i(
            "ChatScreen",
            "LOCAL reflection exit: selectedSource=${generated.trace.selectedAssistantResponseSource}, streamingDetected=$streamingCandidateDetected",
        )
        when (generated.trace.selectedAssistantResponseSource) {
            LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_ASYNC_POC -> appendLocalReflectionTrace(
                context = context,
                message = "DEV_POC exit source=session-async",
            )
            else -> appendLocalReflectionTrace(
                context = context,
                message = "DEV_POC exit source=oneshot-fallback",
            )
        }
        successReached = true
        generatedResponse = generated
    } finally {
        closeOutcome = tryCloseWithOutcome(
            label = "inference",
            target = inferenceInstance,
            appendTrace = { traceMessage ->
                appendLocalReflectionTrace(context = context, message = traceMessage)
            },
            path = "legacy-reflection",
        )
    }
    val closeSummary = RunCloseLifecycleSummary(
        path = "legacy-reflection",
        successReturned = successReached,
        sessionOutcome = RunCloseTargetOutcome(
            label = "session",
            targetClassName = null,
            strategy = null,
            status = "none",
            errorClassName = null,
            message = null,
        ),
        inferenceOutcome = closeOutcome,
    )
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM close-summary path=legacy-reflection successReturned=${closeSummary.successReturned}",
    )
    appendLocalReflectionTrace(
        context = context,
        message = "UPSTREAM legacy final source=legacy-reflection closePath=${closeSummary.path}",
    )
    val generatedResult = generatedResponse
    return generatedResult.copy(closeLifecycleSummary = closeSummary)
}

@Throws(NoSuchMethodException::class)
private fun buildLiteRtOptionsViaReflection(
    optionsClass: Class<*>,
    modelPath: String,
): LocalLiteRtOptionsBuildResult {
    var buildPath = "builderMethod"
    val builder = optionsClass.methods.firstOrNull { method ->
        method.name == "builder" &&
            method.parameterTypes.isEmpty() &&
            java.lang.reflect.Modifier.isStatic(method.modifiers)
    }?.let { method ->
        Log.i("ChatScreen", "LiteRT-LM builder factory found: ${method.toGenericString()}")
        runCatching { method.invoke(null) }.getOrElse { throwable ->
            throw IllegalStateException("LiteRT-LM builder() invoke failed.", throwable)
        }
    } ?: run {
        buildPath = "builderConstructor"
        val builderClass = optionsClass.declaredClasses.firstOrNull { it.simpleName == "Builder" }
            ?: throw NoSuchMethodException("LiteRT-LM Options.Builder class not found.")
        Log.i("ChatScreen", "LiteRT-LM builder class found: ${builderClass.name}")
        val constructor = builderClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() }
            ?: throw NoSuchMethodException("LiteRT-LM Options.Builder() constructor not found.")
        runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrElse { throwable ->
            throw IllegalStateException("LiteRT-LM Options.Builder() invoke failed.", throwable)
        }
    }

    val builderClass = builder.javaClass
    val modelPathSetter = listOf("setModelPath", "setModelFilePath", "setLlmModelPath")
        .firstNotNullOfOrNull { methodName ->
            builderClass.methods.firstOrNull { method ->
                method.name == methodName &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
        } ?: throw NoSuchMethodException("LiteRT-LM modelPath setter not found.")
    Log.i("ChatScreen", "LiteRT-LM modelPath setter found: ${modelPathSetter.toGenericString()}")

    val configuredBuilder = runCatching {
        modelPathSetter.invoke(builder, modelPath) ?: builder
    }.getOrElse { throwable ->
        throw IllegalStateException("LiteRT-LM modelPath setter invoke failed.", throwable)
    }

    var optionalConfiguredBuilder = configuredBuilder
    fun applyOptionalSetter(
        methodName: String,
        expectedType: Class<*>,
        value: Any,
    ) {
        val setter = optionalConfiguredBuilder.javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == expectedType
        }
        if (setter == null) {
            Log.i("ChatScreen", "LiteRT-LM optional setter not found: $methodName(${expectedType.simpleName})")
            return
        }
        optionalConfiguredBuilder = runCatching {
            setter.invoke(optionalConfiguredBuilder, value) ?: optionalConfiguredBuilder
        }.onSuccess {
            Log.i("ChatScreen", "LiteRT-LM optional setter applied: $methodName(${expectedType.simpleName})=$value")
        }.getOrElse { throwable ->
            Log.w("ChatScreen", "LiteRT-LM optional setter invoke failed: $methodName(${expectedType.simpleName})", throwable)
            optionalConfiguredBuilder
        }
    }

    applyOptionalSetter(methodName = "setMaxTokens", expectedType = Int::class.javaPrimitiveType!!, value = 16)
    applyOptionalSetter(methodName = "setMaxTokens", expectedType = Int::class.javaObjectType, value = 16)
    applyOptionalSetter(methodName = "setMaxOutputTokens", expectedType = Int::class.javaPrimitiveType!!, value = 16)
    applyOptionalSetter(methodName = "setMaxOutputTokens", expectedType = Int::class.javaObjectType, value = 16)
    applyOptionalSetter(methodName = "setTopK", expectedType = Int::class.javaPrimitiveType!!, value = 1)
    applyOptionalSetter(methodName = "setTopK", expectedType = Int::class.javaObjectType, value = 1)
    applyOptionalSetter(methodName = "setTemperature", expectedType = Float::class.javaPrimitiveType!!, value = 0.0f)
    applyOptionalSetter(methodName = "setTemperature", expectedType = Float::class.javaObjectType, value = 0.0f)
    applyOptionalSetter(methodName = "setRandomSeed", expectedType = Int::class.javaPrimitiveType!!, value = 1)
    applyOptionalSetter(methodName = "setRandomSeed", expectedType = Int::class.javaObjectType, value = 1)
    applyOptionalSetter(
        methodName = "setEnableVisionModality",
        expectedType = Boolean::class.javaPrimitiveType!!,
        value = false,
    )
    applyOptionalSetter(
        methodName = "setEnableVisionModality",
        expectedType = Boolean::class.javaObjectType,
        value = false,
    )
    applyOptionalSetter(
        methodName = "setEnableAudioModality",
        expectedType = Boolean::class.javaPrimitiveType!!,
        value = false,
    )
    applyOptionalSetter(
        methodName = "setEnableAudioModality",
        expectedType = Boolean::class.javaObjectType,
        value = false,
    )

    listOf("setPreferredBackend", "setBackend", "setPreferredDelegate").forEach { methodName ->
        val backendLikeSetter = optionalConfiguredBuilder.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == 1
        }
        when {
            backendLikeSetter == null -> {
                Log.i("ChatScreen", "LiteRT-LM optional setter not found: $methodName(?)")
            }
            backendLikeSetter.parameterTypes[0] == String::class.java -> {
                optionalConfiguredBuilder = runCatching {
                    backendLikeSetter.invoke(optionalConfiguredBuilder, "CPU") ?: optionalConfiguredBuilder
                }.onSuccess {
                    Log.i("ChatScreen", "LiteRT-LM optional setter applied: $methodName(String)=CPU")
                }.getOrElse { throwable ->
                    Log.w("ChatScreen", "LiteRT-LM optional setter invoke failed: $methodName(String)", throwable)
                    optionalConfiguredBuilder
                }
            }
            backendLikeSetter.parameterTypes[0] == Int::class.javaPrimitiveType ||
                backendLikeSetter.parameterTypes[0] == Int::class.javaObjectType -> {
                optionalConfiguredBuilder = runCatching {
                    backendLikeSetter.invoke(optionalConfiguredBuilder, 0) ?: optionalConfiguredBuilder
                }.onSuccess {
                    Log.i("ChatScreen", "LiteRT-LM optional setter applied: $methodName(Int)=0")
                }.getOrElse { throwable ->
                    Log.w("ChatScreen", "LiteRT-LM optional setter invoke failed: $methodName(Int)", throwable)
                    optionalConfiguredBuilder
                }
            }
            else -> {
                Log.i(
                    "ChatScreen",
                    "LiteRT-LM optional setter skipped: $methodName(${backendLikeSetter.parameterTypes[0].name})",
                )
            }
        }
    }

    // --- generation length tuning (safe optional) ---
    applyOptionalSetter(
        methodName = "setMaxTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    applyOptionalSetter(
        methodName = "setMaxOutputTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    // 一部API互換用（念のため）
    applyOptionalSetter(
        methodName = "maxTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    applyOptionalSetter(
        methodName = "maxOutputTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    val buildMethod = optionalConfiguredBuilder.javaClass.methods.firstOrNull { method ->
        method.name == "build" && method.parameterTypes.isEmpty()
    } ?: throw NoSuchMethodException("LiteRT-LM build() method not found.")

    val builtOptions = runCatching {
        buildMethod.invoke(optionalConfiguredBuilder)
    }.getOrElse { throwable ->
        throw IllegalStateException("LiteRT-LM build() invoke failed.", throwable)
    } ?: throw IllegalStateException("LiteRT-LM build() returned null.")
    Log.i("ChatScreen", "LiteRT-LM options build succeeded.")
    return LocalLiteRtOptionsBuildResult(options = builtOptions, buildPath = buildPath)
}

private fun probeLiteRtStreamingApiViaReflection(): Boolean {
    val candidateMethodNames = listOf(
        "generateResponseAsync",
        "setResultListener",
        "addListener",
        "registerListener",
        "setResponseListener",
        "setPartialResultListener",
        "setTokenListener",
        "sendMessageAsync",
        "createChat",
        "setProgressListener",
        "addResultListener",
        "createSession",
        "createSessionFromOptions",
    )
    val targetClassNames = listOf(
        "com.google.mediapipe.tasks.genai.llminference.LlmInference",
        "com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession",
    )
    val detectedSignatures = mutableListOf<String>()

    targetClassNames.forEach { className ->
        val targetClass = runCatching { Class.forName(className) }.onFailure { throwable ->
            Log.i("ChatScreen", "LiteRT-LM streaming probe skipped class=$className", throwable)
        }.getOrNull() ?: return@forEach

        val classMethods = targetClass.methods
        candidateMethodNames.forEach { candidateMethodName ->
            val method = classMethods.firstOrNull { it.name == candidateMethodName } ?: return@forEach
            val parameterTypeNames = method.parameterTypes.joinToString(prefix = "[", postfix = "]") { it.name }
            val signature = method.toGenericString()
            Log.i(
                "ChatScreen",
                "LiteRT-LM streaming candidate class=${targetClass.name}, method=${method.name}, parameterTypes=$parameterTypeNames, signature=$signature",
            )
            detectedSignatures += signature
        }
    }

    if (detectedSignatures.isEmpty()) {
        Log.i(
            "ChatScreen",
            "LiteRT-LM streaming probe completed: no candidate methods found. candidates=$candidateMethodNames",
        )
        return false
    }

    Log.i("ChatScreen", "LiteRT-LM streaming probe completed: detected=${detectedSignatures.size}")
    return true
}

private data class LocalStreamingApiProbeOutcome(
    val result: LocalStreamingApiProbeResult,
    val signature: String? = null,
)

private data class SessionMethodInventory(
    val generateSignature: String? = null,
    val asyncSignature: String? = null,
    val streamingSignature: String? = null,
    val tokenSignature: String? = null,
    val listenerSignature: String? = null,
    val lifecycleSignature: String? = null,
)

private data class DevSessionAsyncPocResult(
    val attempted: Boolean = false,
    val createSucceeded: Boolean = false,
    val asyncMethodSignature: String? = null,
    val futureClassName: String? = null,
    val responseText: String? = null,
    val responseLength: Int? = null,
    val responseHead: String? = null,
    val localTraceFirstResponseElapsedRealtimeMs: Long? = null,
    val closeSucceeded: Boolean? = null,
    val errorStage: String? = null,
    val errorClassName: String? = null,
    val errorMessage: String? = null,
)

private fun inspectLlmInferenceSessionMethods(): SessionMethodInventory {
    val sessionClass = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession")
    }.getOrNull() ?: return SessionMethodInventory()
    val methods = sessionClass.methods.toList()
    val cancelLikeKeywords = listOf("cancel", "close", "stop", "abort")

    fun firstSignatureMatching(includeAny: List<String>, excludeAny: List<String> = emptyList()): String? {
        val found = methods.firstOrNull { method ->
            val lowerName = method.name.lowercase(Locale.ROOT)
            includeAny.any { keyword -> lowerName.contains(keyword) } &&
                excludeAny.none { keyword -> lowerName.contains(keyword) }
        } ?: return null
        val parameterTypes = found.parameterTypes.joinToString(prefix = "[", postfix = "]") { it.simpleName }
        return "${found.name} $parameterTypes :: ${found.toGenericString()}"
    }

    return SessionMethodInventory(
        generateSignature = firstSignatureMatching(
            includeAny = listOf("generate"),
            excludeAny = cancelLikeKeywords,
        ),
        asyncSignature = firstSignatureMatching(
            includeAny = listOf("async"),
            excludeAny = cancelLikeKeywords,
        ),
        streamingSignature = firstSignatureMatching(
            includeAny = listOf("stream", "partial", "chunk"),
            excludeAny = cancelLikeKeywords,
        ),
        tokenSignature = firstSignatureMatching(includeAny = listOf("token", "tokens")),
        listenerSignature = firstSignatureMatching(includeAny = listOf("listener", "callback")),
        lifecycleSignature =
            firstSignatureMatching(includeAny = listOf("close"))
                ?: firstSignatureMatching(includeAny = listOf("reset"))
                ?: firstSignatureMatching(includeAny = listOf("cancel"))
                ?: firstSignatureMatching(includeAny = listOf("stop"))
                ?: firstSignatureMatching(includeAny = listOf("abort")),
    )
}

private fun findSetResultListenerCandidate(
    inferenceClass: Class<*>,
): LocalStreamingApiProbeOutcome {
    val listenerMethodNames = listOf(
        "setResultListener",
        "addResultListener",
        "addListener",
        "registerListener",
        "setResponseListener",
        "setPartialResultListener",
        "setTokenListener",
        "setCallback",
        "addCallback",
    )
    val listenerMethod = inferenceClass.methods.firstOrNull { method ->
        val lowerName = method.name.lowercase(Locale.ROOT)
        (listenerMethodNames.any { it.equals(method.name, ignoreCase = true) } ||
            lowerName.contains("listener") ||
            lowerName.contains("callback")) &&
            method.parameterTypes.size == 1
    } ?: return LocalStreamingApiProbeOutcome(LocalStreamingApiProbeResult.LISTENER_API_NOT_FOUND)
    return LocalStreamingApiProbeOutcome(
        result = LocalStreamingApiProbeResult.LISTENER_INVOKE_FAILED,
        signature = listenerMethod.toGenericString(),
    )
}

private fun findGenerateResponseAsyncCandidate(
    inferenceClass: Class<*>,
): LocalStreamingApiProbeOutcome {
    val asyncMethodNames = listOf(
        "generateResponseAsync",
        "sendMessageAsync",
        "chatAsync",
        "generateAsync",
    )
    val asyncMethod = inferenceClass.methods.firstOrNull { method ->
        val lowerName = method.name.lowercase(Locale.ROOT)
        val hasAsyncNameHint = asyncMethodNames.any { it.equals(method.name, ignoreCase = true) } ||
            lowerName.contains("async") ||
            lowerName.contains("future")
        hasAsyncNameHint &&
            method.parameterTypes.isNotEmpty() &&
            (method.parameterTypes[0] == String::class.java ||
                method.parameterTypes[0] == CharSequence::class.java)
    } ?: return LocalStreamingApiProbeOutcome(LocalStreamingApiProbeResult.ASYNC_API_NOT_FOUND)

    return LocalStreamingApiProbeOutcome(
        result = LocalStreamingApiProbeResult.ASYNC_INVOKE_FAILED,
        signature = asyncMethod.toGenericString(),
    )
}

private fun findSessionApiCandidate(
    inferenceClass: Class<*>,
): LocalStreamingApiProbeOutcome {
    val sessionClass = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession")
    }.getOrNull()
    val createMethodsOnInference = inferenceClass.methods.filter { method ->
        val lowerName = method.name.lowercase(Locale.ROOT)
        method.name == "createSession" ||
            method.name == "createSessionFromOptions" ||
            method.name == "createChat" ||
            (lowerName.contains("session") && (lowerName.contains("create") || lowerName.contains("open"))) ||
            (lowerName.contains("chat") && lowerName.contains("create"))
    }
    if (sessionClass == null && createMethodsOnInference.isEmpty()) {
        return LocalStreamingApiProbeOutcome(LocalStreamingApiProbeResult.SESSION_API_NOT_FOUND)
    }

    val sessionSignature = sessionClass?.methods?.firstOrNull { method ->
        method.name == "createFromOptions" || method.name == "create"
    }?.toGenericString()
    return LocalStreamingApiProbeOutcome(
        result = LocalStreamingApiProbeResult.SESSION_CREATE_FAILED,
        signature = sessionSignature ?: createMethodsOnInference.firstOrNull()?.toGenericString(),
    )
}

private fun tryCreateLlmInferenceSessionViaReflectionForDev(
    inferenceInstance: Any,
): Pair<Any, String?> {
    val sessionClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession")
    val createMethods = sessionClass.methods.filter { method ->
        method.name == "createFromOptions" && java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
    val createMethod = createMethods.firstOrNull { method ->
        method.parameterTypes.size == 1 &&
            method.parameterTypes[0].isAssignableFrom(inferenceInstance.javaClass)
    } ?: createMethods.firstOrNull { method ->
        method.parameterTypes.size == 2 &&
            method.parameterTypes[0].isAssignableFrom(inferenceInstance.javaClass)
    } ?: throw NoSuchMethodException("LlmInferenceSession.createFromOptions(...) not found")

    val created = when (createMethod.parameterTypes.size) {
        1 -> createMethod.invoke(null, inferenceInstance)
        2 -> {
            val optionsClass = createMethod.parameterTypes[1]
            val options = buildSessionOptionsViaReflectionForDev(optionsClass)
            createMethod.invoke(null, inferenceInstance, options)
        }
        else -> throw NoSuchMethodException("Unsupported createFromOptions signature: ${createMethod.toGenericString()}")
    } ?: throw IllegalStateException("LlmInferenceSession.createFromOptions returned null")
    return created to createMethod.toGenericString()
}

private fun buildSessionOptionsViaReflectionForDev(
    optionsClass: Class<*>,
): Any {
    val builderFactory = optionsClass.methods.firstOrNull { method ->
        method.name == "builder" &&
            method.parameterTypes.isEmpty() &&
            java.lang.reflect.Modifier.isStatic(method.modifiers)
    }
    val builder = if (builderFactory != null) {
        builderFactory.invoke(null)
    } else {
        val builderClass = optionsClass.declaredClasses.firstOrNull { it.simpleName == "Builder" }
            ?: throw NoSuchMethodException("Session options Builder class not found")
        val constructor = builderClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() }
            ?: throw NoSuchMethodException("Session options Builder() constructor not found")
        constructor.isAccessible = true
        constructor.newInstance()
    } ?: throw IllegalStateException("Session options builder instance is null")

    val buildMethod = builder.javaClass.methods.firstOrNull { method ->
        method.name == "build" && method.parameterTypes.isEmpty()
    } ?: throw NoSuchMethodException("Session options build() not found")
    return buildMethod.invoke(builder)
        ?: throw IllegalStateException("Session options build() returned null")
}

private fun tryCloseLlmInferenceSessionViaReflection(
    sessionInstance: Any?,
): Boolean {
    if (sessionInstance == null) return false
    val closeMethod = sessionInstance.javaClass.methods.firstOrNull { method ->
        method.name == "close" && method.parameterTypes.isEmpty()
    } ?: return false
    closeMethod.invoke(sessionInstance)
    return true
}

private fun tryProbeLlmSessionTokensViaReflection(
    inferenceInstance: Any,
    prompt: String,
    response: String,
): LocalSessionTokenProbeResult {
    var sessionInstance: Any? = null
    return try {
        sessionInstance = tryCreateLlmInferenceSessionViaReflectionForDev(inferenceInstance).first
        val sizeMethod = sessionInstance.javaClass.methods.firstOrNull { method ->
            method.name == "sizeInTokens" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        } ?: return LocalSessionTokenProbeResult(errorStage = "size_method_not_found")
        val promptTokens = (sizeMethod.invoke(sessionInstance, prompt) as? Number)?.toInt()
            ?: return LocalSessionTokenProbeResult(errorStage = "prompt_result_not_number")
        val responseTokens = (sizeMethod.invoke(sessionInstance, response) as? Number)?.toInt()
            ?: return LocalSessionTokenProbeResult(errorStage = "response_result_not_number")
        LocalSessionTokenProbeResult(
            promptTokens = promptTokens,
            responseTokens = responseTokens,
            totalTokens = promptTokens + responseTokens,
        )
    } catch (throwable: Throwable) {
        LocalSessionTokenProbeResult(
            errorStage = "exception",
            errorClassName = throwable.javaClass.name,
        )
    } finally {
        runCatching {
            tryCloseLlmInferenceSessionViaReflection(sessionInstance)
        }
    }
}

private fun sanitizeDevSessionAsyncPocResponse(raw: String): String {
    val normalized = raw
        .replace("<end_of_turn>", "")
        .replace("\r\n", "\n")
    val compactBlankLines = normalized.replace(Regex("\n{3,}"), "\n\n")
    val cleanedLines = buildList {
        var previous: String? = null
        compactBlankLines.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed == previous && trimmed.isNotEmpty()) return@forEach
            add(trimmed)
            previous = trimmed
        }
    }
    val sanitized = cleanedLines.joinToString("\n").trim()
    return sanitized.ifEmpty { raw.trim() }
}

private fun sanitizeDebugTraceHead(raw: String?): String? {
    if (raw == null) return null
    val sanitized = sanitizeDevSessionAsyncPocResponse(raw)
    val base = if (sanitized.isNotEmpty()) sanitized else raw.trim()
    return base.take(80)
}

private suspend fun streamLocalAssistantPreviewTextToUi(
    responseText: String,
    onChunk: (String) -> Unit,
) {
    val trimmed = responseText.trim()
    logLocalStreamingWhitespace(
        stage = "ChatScreen#preview.input",
        raw = responseText,
        normalized = trimmed,
    )
    if (trimmed.isEmpty()) return
    var previousChunk: String? = null
    var emittedChunkCount = 0
    val step = 12
    var endIndex = step
    while (endIndex <= trimmed.length) {
        val chunk = trimmed.substring(0, endIndex)
        if (chunk.isNotEmpty() && chunk != previousChunk) {
            logLocalStreamingWhitespace(
                stage = "ChatScreen#preview.emitChunk",
                raw = chunk,
            )
            withContext(Dispatchers.Main.immediate) {
                onChunk(chunk)
            }
            emittedChunkCount += 1
            previousChunk = chunk
        }
        if (endIndex < trimmed.length) {
            delay(16L)
        }
        endIndex += step
    }
    if (previousChunk != trimmed) {
        logLocalStreamingWhitespace(
            stage = "ChatScreen#preview.emitFinal",
            raw = trimmed,
        )
        withContext(Dispatchers.Main.immediate) {
            onChunk(trimmed)
        }
        emittedChunkCount += 1
    }
    if (BuildConfig.DEBUG) {
        Log.d(
            "ChatScreen",
            "LOCAL pseudo-stream complete: emittedChunkCount=$emittedChunkCount, finalLength=${trimmed.length}",
        )
    }
}

private fun sanitizeOneShotShortAnswerResponse(prompt: String, raw: String): String {
    val shortAnswerKeywords = listOf(
        "短く", "短文", "一言", "最短", "簡潔", "短く答えて", "短く回答",
        "答えだけ", "回答だけ", "一語", "一行", "すぐ答えて", "端的に",
        "簡単に", "シンプルに", "手短に",
    )
    if (!shortAnswerKeywords.any { prompt.contains(it) }) return raw

    return runCatching {
        val normalized = sanitizeDevSessionAsyncPocResponse(raw)
        val segments = normalized
            .split("。", "!", "！", "?", "？", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (segments.isEmpty()) return@runCatching raw

        val shortDirectAnswer = segments.firstOrNull { candidate ->
            Regex("^\\d+(です)?。?$").matches(candidate)
        }
        if (shortDirectAnswer != null) return@runCatching shortDirectAnswer

        val emojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]")
        val sanitized = segments.firstOrNull { candidate ->
            candidate.length <= 20 &&
                !emojiRegex.containsMatchIn(candidate) &&
                !candidate.contains("ありがとうございます") &&
                !candidate.contains("かしこまり") &&
                !candidate.contains("承知") &&
                !candidate.contains("算数") &&
                !candidate.contains("問題") &&
                !candidate.contains("ですね")
        }
        sanitized ?: raw
    }.getOrDefault(raw)
}


private fun shouldUseDevSessionAsyncPocResponse(
    prompt: String,
    pocResponse: String,
    oneShotResponse: String,
): Boolean {
    val trimmedResponse = pocResponse.trim()
    val trimmedOneShotResponse = oneShotResponse.trim()
    if (trimmedResponse.isBlank()) return false
    if (trimmedResponse.contains("<end_of_turn>")) return false
    if (trimmedResponse.length > maxOf(oneShotResponse.length * 2, 120)) return false

    val nonBlankLines = trimmedResponse.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (nonBlankLines.size > 3) return false
    if (nonBlankLines.distinct().size != nonBlankLines.size) return false

    val shortAnswerKeywords = listOf(
        "短く", "短文", "一言", "最短", "簡潔", "短く答えて", "短く回答",
        "答えだけ", "回答だけ", "一語", "一行", "すぐ答えて", "端的に",
    )
    val repeatedFeatureTokens = listOf("答えは", "算数", "ですね", "2です", "ありがとうございます")
    val hasRepeatedFeatureToken = repeatedFeatureTokens.any { token ->
        Regex(Regex.escape(token)).findAll(trimmedResponse).count() >= 2
    }
    val sentenceCount = trimmedResponse
        .split("。", "!", "！", "?", "？")
        .map { it.trim() }
        .count { it.isNotEmpty() }
    val shortPhrases = trimmedResponse
        .split("\n", "。", "!", "！", "?", "？", "、", ",", "　", " ")
        .map { it.trim() }
        .filter { it.length in 2..8 }
    val hasRepeatedShortPhrase = shortPhrases.groupingBy { it }.eachCount().any { it.value >= 2 }
    if (hasRepeatedFeatureToken || hasRepeatedShortPhrase) return false

    val isShortAnswerPrompt = shortAnswerKeywords.any { prompt.contains(it) }
    if (isShortAnswerPrompt) {
        val hasEmoji = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]").containsMatchIn(trimmedResponse)
        val forbiddenPolitePhrases = listOf(
            "かしこまり", "承知", "ありがとうございます", "算数", "問題",
            "お手伝い", "お答え", "ですね", "ます",
        )
        if (trimmedResponse.contains("<end_of_turn>")) return false
        if (trimmedResponse.contains('\n')) return false
        if (trimmedResponse.length > 12) return false
        if (sentenceCount > 1) return false
        if (hasEmoji) return false
        if (forbiddenPolitePhrases.any { trimmedResponse.contains(it) }) return false
        if (trimmedOneShotResponse.isNotEmpty() && trimmedResponse.length > trimmedOneShotResponse.length) return false
    }

    return true
}


private data class LocalAssistantResponseSelection(
    val responseText: String,
    val source: String,
)

private fun selectLocalAssistantResponse(
    prompt: String,
    oneShotResponse: String,
    sessionAsyncPocResult: DevSessionAsyncPocResult,
): LocalAssistantResponseSelection {
    if (!ENABLE_DEV_LLM_SESSION_ASYNC_POC) {
        return LocalAssistantResponseSelection(
            responseText = oneShotResponse,
            source = LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT,
        )
    }
    val pocResponse = sessionAsyncPocResult.responseText
    val shouldUsePocResponse =
        pocResponse != null &&
            shouldUseDevSessionAsyncPocResponse(
                prompt = prompt,
                pocResponse = pocResponse,
                oneShotResponse = oneShotResponse,
            )
    return if (shouldUsePocResponse) {
        LocalAssistantResponseSelection(
            responseText = pocResponse,
            source = LOCAL_ASSISTANT_RESPONSE_SOURCE_SESSION_ASYNC_POC,
        )
    } else {
        LocalAssistantResponseSelection(
            responseText = oneShotResponse,
            source = LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT,
        )
    }
}

private fun tryCallLlmInferenceSessionGenerateResponseAsyncForDev(
    context: Context,
    inferenceInstance: Any,
    prompt: String,
): DevSessionAsyncPocResult {
    appendLocalReflectionTrace(
        context = context,
        message = "DEV_POC entry enabled=$ENABLE_DEV_LLM_SESSION_ASYNC_POC",
    )
    if (!ENABLE_DEV_LLM_SESSION_ASYNC_POC) {
        appendLocalReflectionTrace(context = context, message = "DEV_POC skipped reason=flag-off")
        return DevSessionAsyncPocResult()
    }

    var sessionInstance: Any? = null
    var closeSucceeded: Boolean? = null
    val result = try {
        appendLocalReflectionTrace(
            context = context,
            message = "DEV_POC create-session-start promptLength=${prompt.length}",
        )
        val (createdSession, createMethodSignature) = tryCreateLlmInferenceSessionViaReflectionForDev(inferenceInstance)
        sessionInstance = createdSession
        appendLocalReflectionTrace(
            context = context,
            message = "DEV_POC create-session-success method=${createMethodSignature ?: "unknown"}",
        )

        appendLocalReflectionTrace(context = context, message = "DEV_POC add-query-start promptLength=${prompt.length}")
        val addQueryChunkMethod = createdSession.javaClass.methods.firstOrNull { method ->
            method.name == "addQueryChunk" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        } ?: throw NoSuchMethodException("addQueryChunk(String) not found")
        addQueryChunkMethod.invoke(createdSession, DEV_LLM_SESSION_ASYNC_POC_PROMPT)
        appendLocalReflectionTrace(
            context = context,
            message = "DEV_POC add-query-success method=${addQueryChunkMethod.toGenericString()}",
        )

        appendLocalReflectionTrace(context = context, message = "DEV_POC async-start")
        val asyncMethod = createdSession.javaClass.methods.firstOrNull { method ->
            method.name == "generateResponseAsync" && method.parameterTypes.isEmpty()
        } ?: throw NoSuchMethodException("generateResponseAsync() not found")
        val future = asyncMethod.invoke(createdSession)
            ?: throw IllegalStateException("generateResponseAsync() returned null")
        appendLocalReflectionTrace(
            context = context,
            message = "DEV_POC async-future-created method=${asyncMethod.toGenericString()}",
        )

        val futureClassName = future.javaClass.name
        val timeoutGetMethod = future.javaClass.methods.firstOrNull { method ->
            method.name == "get" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == Long::class.javaPrimitiveType &&
                method.parameterTypes[1] == TimeUnit::class.java
        }
        val responseAny = if (timeoutGetMethod != null) {
            timeoutGetMethod.invoke(future, DEV_LLM_SESSION_ASYNC_POC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } else {
            val getMethod = future.javaClass.methods.firstOrNull { method ->
                method.name == "get" && method.parameterTypes.isEmpty()
            } ?: throw NoSuchMethodException("Future get(...) method not found")
            getMethod.invoke(future)
        }
        val rawResponseText = (responseAny as? String) ?: responseAny?.toString()
        val sanitizedResponseText = rawResponseText?.let(::sanitizeDevSessionAsyncPocResponse)
        appendLocalReflectionTrace(
            context = context,
            message = "DEV_POC async-success responseLength=${sanitizedResponseText?.length ?: 0}",
        )
        DevSessionAsyncPocResult(
            attempted = true,
            createSucceeded = true,
            asyncMethodSignature = asyncMethod.toGenericString(),
            futureClassName = futureClassName,
            responseText = sanitizedResponseText,
            responseLength = sanitizedResponseText?.length,
            responseHead = sanitizedResponseText?.take(60),
            localTraceFirstResponseElapsedRealtimeMs =
                if (!sanitizedResponseText.isNullOrBlank()) SystemClock.elapsedRealtime() else null,
        )
    } catch (throwable: Throwable) {
        val root = throwable.cause ?: throwable
        val errorClassSimpleName = if (throwable is java.lang.reflect.InvocationTargetException) {
            throwable.javaClass.simpleName
        } else {
            root.javaClass.simpleName
        }
        val errorClassName = if (throwable is java.lang.reflect.InvocationTargetException) {
            throwable.javaClass.name
        } else {
            root.javaClass.name
        }
        val stage = when {
            root is ClassNotFoundException -> "create"
            root.message?.contains("createFromOptions", ignoreCase = true) == true -> "create"
            root.message?.contains("addQueryChunk", ignoreCase = true) == true -> "addQueryChunk"
            root.message?.contains("generateResponseAsync", ignoreCase = true) == true -> "generateResponseAsync"
            root is TimeoutException -> "futureGetTimeout"
            root.message?.contains("Future get", ignoreCase = true) == true -> "futureGet"
            else -> "unknown"
        }
        when (stage) {
            "create" -> appendLocalReflectionTrace(
                context = context,
                message = "DEV_POC create-session-failed errorStage=$stage errorClass=$errorClassSimpleName",
            )
            "addQueryChunk" -> appendLocalReflectionTrace(
                context = context,
                message = "DEV_POC add-query-failed errorStage=$stage errorClass=$errorClassSimpleName",
            )
            else -> appendLocalReflectionTrace(
                context = context,
                message = "DEV_POC async-failed errorStage=$stage errorClass=$errorClassSimpleName",
            )
        }
        DevSessionAsyncPocResult(
            attempted = true,
            createSucceeded = sessionInstance != null,
            errorStage = stage,
            errorClassName = errorClassName,
            errorMessage = root.message?.take(120),
        )
    } finally {
        appendLocalReflectionTrace(context = context, message = "DEV_POC close-start")
        closeSucceeded = runCatching {
            tryCloseLlmInferenceSessionViaReflection(sessionInstance)
        }.onSuccess { succeeded ->
            if (succeeded) {
                appendLocalReflectionTrace(context = context, message = "DEV_POC close-success")
            } else {
                appendLocalReflectionTrace(
                    context = context,
                    message = "DEV_POC close-failed errorStage=close errorClass=CloseMethodNotFoundOrNullSession",
                )
            }
        }.onFailure { throwable ->
            val root = throwable.cause ?: throwable
            appendLocalReflectionTrace(
                context = context,
                message = "DEV_POC close-failed errorStage=close errorClass=${root.javaClass.simpleName}",
            )
        }.getOrDefault(false)
    }
    return result.copy(closeSucceeded = closeSucceeded)
}

private fun tryCheckLiteRtLmGenerateViaReflection(
    inferenceInstance: Any,
): LocalLiteRtProbeResult {
    val candidateMethodNames = listOf("generateResponse", "generate", "infer")
    val candidateMethods = candidateMethodNames.flatMap { methodName ->
        inferenceInstance.javaClass.methods.filter { method ->
            method.name == methodName &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        }
    }
    if (candidateMethods.isEmpty()) {
        Log.w("ChatScreen", "LiteRT-LM generate-like method not found. candidates=$candidateMethodNames")
        return LocalLiteRtProbeResult.GENERATE_METHOD_NOT_FOUND
    }

    candidateMethods.forEach { method ->
        val invoked = runCatching {
            method.invoke(inferenceInstance, LOCAL_INFERENCE_PROBE_PROMPT)
        }.onFailure { throwable ->
            Log.w("ChatScreen", "LiteRT-LM generate probe failed on ${method.name}(String)", throwable)
        }.isSuccess
        if (invoked) {
            Log.i("ChatScreen", "LiteRT-LM generate probe passed on ${method.name}(String)")
            return LocalLiteRtProbeResult.SUCCESS
        }
    }

    Log.e("ChatScreen", "LiteRT-LM generate probe failed for all candidate methods.")
    return LocalLiteRtProbeResult.GENERATE_FAILED
}

private fun generateLiteRtStringResponseOnceViaReflection(
    context: Context,
    inferenceInstance: Any,
    prompt: String,
    trace: LocalInferenceTrace,
    sessionAsyncPocResult: DevSessionAsyncPocResult = DevSessionAsyncPocResult(),
    onPartial: (String) -> Unit = {},
): LocalLiteRtGeneratedResponse {
    val candidateMethodNames = listOf("generateResponse", "generate", "infer")
    val candidateMethods = candidateMethodNames.flatMap { methodName ->
        inferenceInstance.javaClass.methods.filter { method ->
            method.name == methodName &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
            }
    }
    Log.i("ChatScreen", "LOCAL reflection oneshot-entry: candidateMethodCount=${candidateMethods.size}")
    appendLocalReflectionTrace(context = context, message = "oneshot-entry candidateMethodCount=${candidateMethods.size}")
    if (candidateMethods.isEmpty()) {
        Log.w("ChatScreen", "LiteRT-LM generate-like method not found for response generation.")
        appendLocalReflectionTrace(context = context, message = "early-return reason=generate-like-method-not-found")
        val inventoryTrace = trace.merge(probeLocalStatsCandidates(inferenceInstance))
        return LocalLiteRtGeneratedResponse(trace = inventoryTrace)
    }

    val partialHook = tryAttachSingleListenerPartialHook(
        inferenceInstance = inferenceInstance,
        onPartial = onPartial,
    )
    val partialHookSnapshot = partialHook.snapshot()
    appendLocalReflectionTrace(
        context = context,
        message = "real-partial-hook attempted=${partialHookSnapshot.attempted} attached=${partialHookSnapshot.attached}",
    )

    var lastFailureDiagnosticsText: String? = null
    candidateMethods.forEach { method ->
        Log.i("ChatScreen", "LOCAL reflection oneshot-try: method=${method.toGenericString()}")
        appendLocalReflectionTrace(context = context, message = "oneshot-try method=${method.toGenericString()}")
        val generateStartNs = SystemClock.elapsedRealtimeNanos()
        val result = runCatching {
            method.invoke(inferenceInstance, prompt)
        }.onFailure { throwable ->
            lastFailureDiagnosticsText = buildLocalInferenceFailureDiagnosticsText(
                context = context,
                stage = "generate-response",
                throwable = throwable,
                selectedModelName = trace.mediaPipeProbeModelPath ?: trace.localModelDisplayName,
                selectedFallbackPath = "none",
            )
            Log.w("ChatScreen", "LiteRT-LM generate invocation failed on ${method.name}(String)", throwable)
        }.getOrNull()
        val wallClockTotalInferenceDurationNs = (SystemClock.elapsedRealtimeNanos() - generateStartNs).coerceAtLeast(0L)
        when (result) {
            is String -> {
                val oneShotResponse = sanitizeOneShotShortAnswerResponse(prompt = prompt, raw = result)
                val oneShotResponseHead = sanitizeDebugTraceHead(oneShotResponse)
                val sessionAsyncPocCandidateHead = sanitizeDebugTraceHead(sessionAsyncPocResult.responseText)
                var inventoryTrace = trace.copy(
                    generateMethodSignature = method.toGenericString(),
                    wallClockTotalInferenceDurationNs = wallClockTotalInferenceDurationNs,
                    oneShotResponseHead = oneShotResponseHead,
                    sessionAsyncPocSelectedCandidateHead = sessionAsyncPocCandidateHead,
                    realPartialHookAttempted = partialHookSnapshot.attempted,
                    realPartialHookAttached = partialHookSnapshot.attached,
                    realPartialCallbackCount = partialHook.snapshot().callbackCount,
                )
                    .merge(probeLocalStatsCandidates(inferenceInstance))
                val responseSelection = selectLocalAssistantResponse(
                    prompt = prompt,
                    oneShotResponse = oneShotResponse,
                    sessionAsyncPocResult = sessionAsyncPocResult,
                )
                val responseCompletedElapsedRealtimeMs = SystemClock.elapsedRealtime()
                inventoryTrace = inventoryTrace.copy(
                    selectedAssistantResponseSource = responseSelection.source,
                    selectedAssistantResponseHead = sanitizeDebugTraceHead(responseSelection.responseText),
                    localTraceFirstResponseElapsedRealtimeMs =
                        inventoryTrace.localTraceFirstResponseElapsedRealtimeMs
                            ?: responseCompletedElapsedRealtimeMs.takeIf { responseSelection.responseText.isNotBlank() },
                    localTraceCompletedElapsedRealtimeMs = responseCompletedElapsedRealtimeMs,
                )
                val tokenProbe = tryProbeLlmSessionTokensViaReflection(
                    inferenceInstance = inferenceInstance,
                    prompt = prompt,
                    response = responseSelection.responseText,
                )
                inventoryTrace = inventoryTrace.copy(
                    sessionPromptTokens = tokenProbe.promptTokens,
                    sessionResponseTokens = tokenProbe.responseTokens,
                    sessionTotalTokens = tokenProbe.totalTokens,
                    sessionTokenProbeErrorStage = tokenProbe.errorStage,
                    sessionTokenProbeErrorClassName = tokenProbe.errorClassName,
                )
                Log.i("ChatScreen", "LOCAL reflection oneshot-success: method=${method.name}, responseLength=${responseSelection.responseText.length}")
                appendLocalReflectionTrace(
                    context = context,
                    message = "oneshot-success method=${method.name} responseLength=${responseSelection.responseText.length}",
                )
                return LocalLiteRtGeneratedResponse(response = responseSelection.responseText, trace = inventoryTrace)
            }
            is CharSequence -> {
                val oneShotResponse = sanitizeOneShotShortAnswerResponse(prompt = prompt, raw = result.toString())
                val oneShotResponseHead = sanitizeDebugTraceHead(oneShotResponse)
                val sessionAsyncPocCandidateHead = sanitizeDebugTraceHead(sessionAsyncPocResult.responseText)
                var inventoryTrace = trace.copy(
                    generateMethodSignature = method.toGenericString(),
                    wallClockTotalInferenceDurationNs = wallClockTotalInferenceDurationNs,
                    oneShotResponseHead = oneShotResponseHead,
                    sessionAsyncPocSelectedCandidateHead = sessionAsyncPocCandidateHead,
                    realPartialHookAttempted = partialHookSnapshot.attempted,
                    realPartialHookAttached = partialHookSnapshot.attached,
                    realPartialCallbackCount = partialHook.snapshot().callbackCount,
                )
                    .merge(probeLocalStatsCandidates(inferenceInstance))
                val responseSelection = selectLocalAssistantResponse(
                    prompt = prompt,
                    oneShotResponse = oneShotResponse,
                    sessionAsyncPocResult = sessionAsyncPocResult,
                )
                val responseCompletedElapsedRealtimeMs = SystemClock.elapsedRealtime()
                inventoryTrace = inventoryTrace.copy(
                    selectedAssistantResponseSource = responseSelection.source,
                    selectedAssistantResponseHead = sanitizeDebugTraceHead(responseSelection.responseText),
                    localTraceFirstResponseElapsedRealtimeMs =
                        inventoryTrace.localTraceFirstResponseElapsedRealtimeMs
                            ?: responseCompletedElapsedRealtimeMs.takeIf { responseSelection.responseText.isNotBlank() },
                    localTraceCompletedElapsedRealtimeMs = responseCompletedElapsedRealtimeMs,
                )
                val tokenProbe = tryProbeLlmSessionTokensViaReflection(
                    inferenceInstance = inferenceInstance,
                    prompt = prompt,
                    response = responseSelection.responseText,
                )
                inventoryTrace = inventoryTrace.copy(
                    sessionPromptTokens = tokenProbe.promptTokens,
                    sessionResponseTokens = tokenProbe.responseTokens,
                    sessionTotalTokens = tokenProbe.totalTokens,
                    sessionTokenProbeErrorStage = tokenProbe.errorStage,
                    sessionTokenProbeErrorClassName = tokenProbe.errorClassName,
                )
                Log.i("ChatScreen", "LOCAL reflection oneshot-success: method=${method.name}, responseLength=${responseSelection.responseText.length}")
                appendLocalReflectionTrace(
                    context = context,
                    message = "oneshot-success method=${method.name} responseLength=${responseSelection.responseText.length}",
                )
                return LocalLiteRtGeneratedResponse(response = responseSelection.responseText, trace = inventoryTrace)
            }
            else -> {
                Log.i("ChatScreen", "LOCAL reflection oneshot-null-result: method=${method.name}")
                appendLocalReflectionTrace(context = context, message = "oneshot-null-result method=${method.name}")
            }
        }
    }
    Log.e("ChatScreen", "LiteRT-LM string response generation failed for all candidate methods.")
    appendLocalReflectionTrace(context = context, message = "oneshot-all-candidate-failed")
    val inventoryTrace = trace.copy(
        realPartialHookAttempted = partialHookSnapshot.attempted,
        realPartialHookAttached = partialHookSnapshot.attached,
        realPartialCallbackCount = partialHook.snapshot().callbackCount,
        localFailureDiagnosticsText = trace.localFailureDiagnosticsText ?: lastFailureDiagnosticsText,
    ).merge(probeLocalStatsCandidates(inferenceInstance))
    return LocalLiteRtGeneratedResponse(trace = inventoryTrace)
}

private fun localReflectionTraceLine(message: String): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    return "$timestamp [LOCAL_REFLECTION] $message"
}

private fun appendLocalReflectionTrace(context: Context, message: String) {
    runCatching {
        val traceFile = File(context.filesDir, "debug/local_reflection_trace.log")
        traceFile.parentFile?.mkdirs()
        traceFile.appendText(localReflectionTraceLine(message) + "\n", Charsets.UTF_8)
    }
}

private fun LocalInferenceTrace.merge(probe: LocalInferenceTrace): LocalInferenceTrace {
    return copy(
        modelNameProbe = if (modelNameProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.modelNameProbe else modelNameProbe,
        finishReasonProbe = if (finishReasonProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.finishReasonProbe else finishReasonProbe,
        outputTokenProbe = if (outputTokenProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.outputTokenProbe else outputTokenProbe,
        loadTimeProbe = if (loadTimeProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.loadTimeProbe else loadTimeProbe,
        promptEvalTimeProbe = if (promptEvalTimeProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.promptEvalTimeProbe else promptEvalTimeProbe,
        evalTimeProbe = if (evalTimeProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.evalTimeProbe else evalTimeProbe,
        firstTokenProbe = if (firstTokenProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.firstTokenProbe else firstTokenProbe,
        estimatedTokenProbe = if (estimatedTokenProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.estimatedTokenProbe else estimatedTokenProbe,
        asyncApiProbeResult = asyncApiProbeResult ?: probe.asyncApiProbeResult,
        asyncApiSignature = asyncApiSignature ?: probe.asyncApiSignature,
        listenerApiProbeResult = listenerApiProbeResult ?: probe.listenerApiProbeResult,
        listenerApiSignature = listenerApiSignature ?: probe.listenerApiSignature,
        sessionApiProbeResult = sessionApiProbeResult ?: probe.sessionApiProbeResult,
        sessionApiSignature = sessionApiSignature ?: probe.sessionApiSignature,
        officialFlowAttempted = officialFlowAttempted || probe.officialFlowAttempted,
        officialFlowUsed = officialFlowUsed || probe.officialFlowUsed,
        officialFlowFallbackReason = officialFlowFallbackReason ?: probe.officialFlowFallbackReason,
        officialConversationApiAvailable = officialConversationApiAvailable ?: probe.officialConversationApiAvailable,
        officialFlowChunkCount = if (officialFlowChunkCount > 0) officialFlowChunkCount else probe.officialFlowChunkCount,
        officialChunkCount = if (officialChunkCount > 0) officialChunkCount else probe.officialChunkCount,
        officialChunkIntervalAvgMs = officialChunkIntervalAvgMs ?: probe.officialChunkIntervalAvgMs,
        officialChunkIntervalMaxMs = officialChunkIntervalMaxMs ?: probe.officialChunkIntervalMaxMs,
        officialChunkIntervalMinMs = officialChunkIntervalMinMs ?: probe.officialChunkIntervalMinMs,
        officialChunkFirstToLastMs = officialChunkFirstToLastMs ?: probe.officialChunkFirstToLastMs,
        officialChunkCharsAvg = officialChunkCharsAvg ?: probe.officialChunkCharsAvg,
        officialChunkCharsMax = officialChunkCharsMax ?: probe.officialChunkCharsMax,
        officialChunkCharsMin = officialChunkCharsMin ?: probe.officialChunkCharsMin,
        officialChunkEmptyCount = if (officialChunkEmptyCount > 0) officialChunkEmptyCount else probe.officialChunkEmptyCount,
        officialChunkNonEmptyCount = if (officialChunkNonEmptyCount > 0) officialChunkNonEmptyCount else probe.officialChunkNonEmptyCount,
        officialChunkEventsPerSecond = officialChunkEventsPerSecond ?: probe.officialChunkEventsPerSecond,
        officialChunkCharsPerSecond = officialChunkCharsPerSecond ?: probe.officialChunkCharsPerSecond,
        requestedPreferredBackend = requestedPreferredBackend ?: probe.requestedPreferredBackend,
        appliedPreferredBackend = appliedPreferredBackend ?: probe.appliedPreferredBackend,
        preferredBackendApplyResult = preferredBackendApplyResult ?: probe.preferredBackendApplyResult,
        preferredBackendHookReached = preferredBackendHookReached ?: probe.preferredBackendHookReached,
        preferredBackendHookSource = preferredBackendHookSource ?: probe.preferredBackendHookSource,
        preferredBackendApplyError = preferredBackendApplyError ?: probe.preferredBackendApplyError,
        preferredBackendApplyBuilderClass = preferredBackendApplyBuilderClass ?: probe.preferredBackendApplyBuilderClass,
        preferredBackendApplyMethodCandidates = if (preferredBackendApplyMethodCandidates.isNotEmpty()) preferredBackendApplyMethodCandidates else probe.preferredBackendApplyMethodCandidates,
        preferredBackendApplyBackendEnumCandidates = if (preferredBackendApplyBackendEnumCandidates.isNotEmpty()) preferredBackendApplyBackendEnumCandidates else probe.preferredBackendApplyBackendEnumCandidates,
        preferredBackendApplyNotSupportedReason = preferredBackendApplyNotSupportedReason ?: probe.preferredBackendApplyNotSupportedReason,
        heldEngineCreatePath = heldEngineCreatePath ?: probe.heldEngineCreatePath,
        llmInferenceCreateMethod = llmInferenceCreateMethod ?: probe.llmInferenceCreateMethod,
        optionsBuilderSource = optionsBuilderSource ?: probe.optionsBuilderSource,
        preferredBackendHookEligible = preferredBackendHookEligible ?: probe.preferredBackendHookEligible,
        preferredBackendHookMissingReason = preferredBackendHookMissingReason ?: probe.preferredBackendHookMissingReason,
        preferredBackendRequiresEngineRecreate = preferredBackendRequiresEngineRecreate ?: probe.preferredBackendRequiresEngineRecreate,
        preferredBackendEngineRecreateReason = preferredBackendEngineRecreateReason ?: probe.preferredBackendEngineRecreateReason,
        holderInstanceHash = holderInstanceHash ?: probe.holderInstanceHash,
        heldEngineHash = heldEngineHash ?: probe.heldEngineHash,
        holderAppInForeground = holderAppInForeground ?: probe.holderAppInForeground,
        holderLastAcquireAction = holderLastAcquireAction ?: probe.holderLastAcquireAction,
        holderLastLifecycleEventReason = holderLastLifecycleEventReason ?: probe.holderLastLifecycleEventReason,
        holderLastLifecycleDecisionAction = holderLastLifecycleDecisionAction ?: probe.holderLastLifecycleDecisionAction,
        heldEngineRecreateRequestCount = heldEngineRecreateRequestCount ?: probe.heldEngineRecreateRequestCount,
        heldEngineWasPresentAtRunStart = heldEngineWasPresentAtRunStart ?: probe.heldEngineWasPresentAtRunStart,
        heldEngineCreatedDuringRun = heldEngineCreatedDuringRun ?: probe.heldEngineCreatedDuringRun,
        holderLastRecreateResult = holderLastRecreateResult ?: probe.holderLastRecreateResult,
        holderLastRecreateReason = holderLastRecreateReason ?: probe.holderLastRecreateReason,
        holderHasHeldEngineBeforeRecreate = holderHasHeldEngineBeforeRecreate ?: probe.holderHasHeldEngineBeforeRecreate,
        holderHasHeldEngineAfterRecreate = holderHasHeldEngineAfterRecreate ?: probe.holderHasHeldEngineAfterRecreate,
        lastHeldEngineCreateReason = lastHeldEngineCreateReason ?: probe.lastHeldEngineCreateReason,
        lastHeldEngineCreateSource = lastHeldEngineCreateSource ?: probe.lastHeldEngineCreateSource,
        lastHeldEngineCreateAtElapsedMs = lastHeldEngineCreateAtElapsedMs ?: probe.lastHeldEngineCreateAtElapsedMs,
        lastHeldEngineCreateRequestedPreferredBackend = lastHeldEngineCreateRequestedPreferredBackend ?: probe.lastHeldEngineCreateRequestedPreferredBackend,
        lastHeldEngineCreateStackHint = lastHeldEngineCreateStackHint ?: probe.lastHeldEngineCreateStackHint,
        measuredTokenSnapshot = measuredTokenSnapshot ?: probe.measuredTokenSnapshot,
        localFailureDiagnosticsText = localFailureDiagnosticsText ?: probe.localFailureDiagnosticsText,
    )
}

private fun probeLocalStatsCandidates(
    inferenceInstance: Any,
): LocalInferenceTrace {
    val metadataRoot = findAndInvokeNoArgMethodByNames(
        target = inferenceInstance,
        candidateNames = listOf("getMetadata", "metadata", "getResult", "result", "getSession", "session"),
        label = "metadataRoot",
    )?.value
    val primaryTarget = metadataRoot ?: inferenceInstance
    return LocalInferenceTrace(
        modelNameProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "modelName",
            candidateNames = listOf("getModelName", "modelName", "getModel", "model"),
        ),
        finishReasonProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "finishReason",
            candidateNames = listOf("getFinishReason", "finishReason", "getDoneReason", "doneReason"),
        ),
        outputTokenProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "outputTokens",
            candidateNames = listOf("getOutputTokenCount", "outputTokenCount", "getCompletionTokens", "completionTokens", "getEvalCount", "evalCount"),
        ),
        loadTimeProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "loadTime",
            candidateNames = listOf(
                "getLoadDuration",
                "loadDuration",
                "getLoadTimeMs",
                "loadTimeMs",
                "getLoadDurationNs",
                "loadDurationNs",
                "getLoadDurationMs",
                "loadDurationMs",
                "getModelLoadDuration",
                "modelLoadDuration",
                "getModelLoadDurationNs",
                "modelLoadDurationNs",
                "getModelLoadTimeMs",
                "modelLoadTimeMs",
                "getPrefillDuration",
                "prefillDuration",
                "getPrefillDurationNs",
                "prefillDurationNs",
                "getInitializationDuration",
                "initializationDuration",
            ),
        ),
        promptEvalTimeProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "promptEvalTime",
            candidateNames = listOf(
                "getPromptEvalDuration",
                "promptEvalDuration",
                "getPromptEvalTimeMs",
                "promptEvalTimeMs",
                "getPromptEvalDurationNs",
                "promptEvalDurationNs",
                "getPromptEvalDurationMs",
                "promptEvalDurationMs",
                "getPromptProcessingDuration",
                "promptProcessingDuration",
                "getInputEvalDuration",
                "inputEvalDuration",
                "getInputEvalDurationNs",
                "inputEvalDurationNs",
                "getPrefillDuration",
                "prefillDuration",
                "getPrefillDurationNs",
                "prefillDurationNs",
            ),
        ),
        evalTimeProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "evalTime",
            candidateNames = listOf(
                "getEvalDuration",
                "evalDuration",
                "getGenerationDuration",
                "generationDuration",
                "getDecodeDuration",
                "decodeDuration",
                "getDecodeDurationNs",
                "decodeDurationNs",
                "getDecodeDurationMs",
                "decodeDurationMs",
                "getDecodeTimeNs",
                "decodeTimeNs",
                "getDecodeTimeMs",
                "decodeTimeMs",
                "getCompletionDuration",
                "completionDuration",
                "getCompletionDurationNs",
                "completionDurationNs",
                "getCompletionDurationMs",
                "completionDurationMs",
                "getCompletionTimeNs",
                "completionTimeNs",
                "getCompletionTimeMs",
                "completionTimeMs",
                "getResponseDurationNs",
                "responseDurationNs",
            ),
        ),
        firstTokenProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "firstToken",
            candidateNames = listOf("getTimeToFirstTokenMs", "timeToFirstTokenMs", "getFirstToken", "firstToken"),
        ),
        estimatedTokenProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "estimatedTokenCount",
            candidateNames = listOf("getEstimatedTokenCount", "estimatedTokenCount", "getTokenCount", "tokenCount"),
        ),
    )
}

private data class LocalInvokedMethodResult(
    val signature: String,
    val returnTypeName: String,
    val value: Any?,
)

private fun findAndInvokeNoArgMethodByNames(
    target: Any,
    candidateNames: List<String>,
    label: String,
): LocalInvokedMethodResult? {
    candidateNames.forEach { candidateName ->
        val method = target.javaClass.methods.firstOrNull { reflectedMethod ->
            reflectedMethod.name.equals(candidateName, ignoreCase = true) &&
                reflectedMethod.parameterTypes.isEmpty()
        } ?: return@forEach
        val signature = method.toGenericString()
        Log.i("ChatScreen", "LOCAL stats candidate method found: label=$label signature=$signature")
        val invoked = runCatching { method.invoke(target) }.onFailure { throwable ->
            Log.w("ChatScreen", "LOCAL stats candidate invoke failed: label=$label signature=$signature", throwable)
        }.getOrNull()
        return LocalInvokedMethodResult(
            signature = signature,
            returnTypeName = method.returnType.name,
            value = invoked,
        )
    }
    return null
}

private fun probeSingleCandidate(
    target: Any,
    label: String,
    candidateNames: List<String>,
): LocalStatsCandidateProbe {
    val invoked = findAndInvokeNoArgMethodByNames(target, candidateNames, label)
        ?: return LocalStatsCandidateProbe(availability = LocalStatsAvailability.NOT_FOUND)
    val availability = if (invoked.value != null) {
        LocalStatsAvailability.AVAILABLE_NOW
    } else {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    }
    return LocalStatsCandidateProbe(
        availability = availability,
        signature = invoked.signature,
        returnTypeName = invoked.returnTypeName,
        valueSummary = invoked.value?.toString(),
    )
}

private fun logLocalStatsInventoryClassification(runResult: LocalInferenceRunResult?) {
    val trace = runResult?.trace ?: LocalInferenceTrace()
    val responseAvailability = if (runResult?.response != null) {
        LocalStatsAvailability.DERIVABLE_NOW
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val generationAvailability = if (runResult != null) {
        LocalStatsAvailability.AVAILABLE_NOW
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val timeoutAvailability = LocalStatsAvailability.AVAILABLE_NOW
    val initializationAvailability = LocalStatsAvailability.AVAILABLE_NOW
    val streamingAvailability = when (trace.streamingCandidateDetected) {
        true -> LocalStatsAvailability.API_CANDIDATE_ONLY
        false -> LocalStatsAvailability.NOT_FOUND
        null -> LocalStatsAvailability.NOT_FOUND
    }
    val generateMethodAvailability = if (trace.generateMethodSignature != null) {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val createPathAvailability = if (trace.createMethodSignature != null) {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val optionsBuildPathAvailability = if (trace.optionsBuildPath != null) {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    Log.i(
        "ChatScreen",
        "LOCAL stats availability: responseTimeMs=$generationAvailability, responseCharCount=$responseAvailability, modelName=${trace.modelNameProbe.availability}, finishReason=${trace.finishReasonProbe.availability}, timeoutFlag=$timeoutAvailability, initState=$initializationAvailability, streamingProbe=$streamingAvailability, generateMethod=$generateMethodAvailability, createPath=$createPathAvailability, optionsBuildPath=$optionsBuildPathAvailability, firstToken=${trace.firstTokenProbe.availability}, loadTime=${trace.loadTimeProbe.availability}, promptEvalTime=${trace.promptEvalTimeProbe.availability}, outputTokenCount=${trace.outputTokenProbe.availability}, estimatedTokenCount=${trace.estimatedTokenProbe.availability}",
    )
    Log.i(
        "ChatScreen",
        "LOCAL stats signatures: modelName=${trace.modelNameProbe.signature}, finishReason=${trace.finishReasonProbe.signature}, outputTokens=${trace.outputTokenProbe.signature}, loadTime=${trace.loadTimeProbe.signature}, promptEval=${trace.promptEvalTimeProbe.signature}, evalTime=${trace.evalTimeProbe.signature}, firstToken=${trace.firstTokenProbe.signature}, estimatedTokens=${trace.estimatedTokenProbe.signature}",
    )
    Log.i(
        "ChatScreen",
        "LOCAL load/prompt details: loadAvailability=${trace.loadTimeProbe.availability}, loadSignature=${trace.loadTimeProbe.signature}, loadRaw=${trace.loadTimeProbe.valueSummary}, loadParsed=${trace.loadTimeProbe.longValueOrNull()}, promptAvailability=${trace.promptEvalTimeProbe.availability}, promptSignature=${trace.promptEvalTimeProbe.signature}, promptRaw=${trace.promptEvalTimeProbe.valueSummary}, promptParsed=${trace.promptEvalTimeProbe.longValueOrNull()}",
    )
}

@Composable
private fun AssistantStreamingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("assistantStreamingIndicator")
    ) {
        Text(
            text = "生成中…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InferenceStatRow(
    label: String,
    value: String,
    emphasizeValue: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasizeValue) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InferenceStatsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

private fun sanitizeLocalAssistantResponse(raw: String): String {
    val sanitized = raw
        .replace("<end_of_turn>", "")
        .replace("<eot>", "")
        .replace("<|eot_id|>", "")
        .replace("<|end_of_text|>", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    logLocalStreamingWhitespace(
        stage = "ChatScreen#sanitizeLocalAssistantResponse",
        raw = raw,
        normalized = sanitized,
    )
    return sanitized
}

private fun logLocalStreamingWhitespace(
    stage: String,
    raw: String?,
    normalized: String? = null,
) {
    if (!BuildConfig.DEBUG) return
    val rawSummary = summarizeWhitespaceForDebug(raw)
    val normalizedSummary = summarizeWhitespaceForDebug(normalized)
    if (normalized == null) {
        Log.d(LOCAL_STREAMING_WHITESPACE_LOG_TAG, "$stage raw=$rawSummary")
    } else {
        Log.d(
            LOCAL_STREAMING_WHITESPACE_LOG_TAG,
            "$stage raw=$rawSummary normalized=$normalizedSummary delta=${buildWhitespaceDeltaForDebug(raw, normalized)}",
        )
    }
}

private fun summarizeWhitespaceForDebug(text: String?): String {
    if (text == null) return "null"
    val spaces = text.count { it == ' ' }
    val newlines = text.count { it == '\n' }
    val tabs = text.count { it == '\t' }
    val carriageReturns = text.count { it == '\r' }
    val visualized = text
        .replace(" ", "␠")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    val head = visualized.take(60)
    val tail = if (visualized.length > 60) visualized.takeLast(60) else visualized
    return "len=${text.length},spaces=$spaces,newlines=$newlines,tabs=$tabs,cr=$carriageReturns,head=\"$head\",tail=\"$tail\""
}

private fun buildWhitespaceDeltaForDebug(raw: String?, normalized: String?): String {
    if (raw == null || normalized == null) return "n/a"
    return "len=${raw.length - normalized.length},spaces=${raw.count { it == ' ' } - normalized.count { it == ' ' }},newlines=${raw.count { it == '\n' } - normalized.count { it == '\n' }}"
}

private fun buildMeasuredTokenSnapshotSummary(trace: LocalInferenceTrace?): String? {
    if (trace == null) return null
    val measuredSnapshot = trace.measuredTokenSnapshot
    val inputTokens = measuredSnapshot?.inputTokens
    val outputTokens = measuredSnapshot?.outputTokens
    val totalTokens = measuredSnapshot?.totalTokens
    fun rawValueOrUnavailable(rawValue: String?): String = rawValue?.takeIf { it.isNotBlank() } ?: "unavailable"
    return buildString {
        append("in=$inputTokens / out=$outputTokens / total=$totalTokens")
        measuredSnapshot?.mediaPipeTokenizerSummary
            ?.takeIf { it.isNotBlank() }
            ?.let { mediaPipeSummary ->
                appendLine()
                append(mediaPipeSummary)
            }
        measuredSnapshot?.tokenizerRecountStatus?.takeIf { it.isNotBlank() }?.let { status ->
            appendLine()
            append("tokenizer-recount status: $status")
            measuredSnapshot.tokenizerSourceTraceSummary
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceTraceSummary ->
                    appendLine()
                    append(sourceTraceSummary)
                }
            if (status == "success" || measuredSnapshot.mediaPipeTokenizerStatus == "success") {
                appendLine()
                append("tokenizer-recount tokens: in=$inputTokens / out=$outputTokens / total=$totalTokens")
            }
        }
        appendLine()
        append("[BenchmarkInfo raw]")
        appendLine()
        append("prefillTokenCount: ${rawValueOrUnavailable(measuredSnapshot?.rawPrefillTokenCount)}")
        appendLine()
        append("decodeTokenCount: ${rawValueOrUnavailable(measuredSnapshot?.rawDecodeTokenCount)}")
        appendLine()
        append("prefillTokensPerSecond: ${rawValueOrUnavailable(measuredSnapshot?.rawPrefillTokensPerSecond)}")
        appendLine()
        append("decodeTokensPerSecond: ${rawValueOrUnavailable(measuredSnapshot?.rawDecodeTokensPerSecond)}")
        appendLine()
        append("timeToFirstTokenMs: ${rawValueOrUnavailable(measuredSnapshot?.rawTimeToFirstTokenMs)}")
        appendLine()
        append("modelInitMs: ${rawValueOrUnavailable(measuredSnapshot?.rawModelInitMs)}")
    }
}


private fun buildLocalGenerationOnlyMsOrNull(
    generationTimeMs: Long,
    timeToFirstTokenMs: Long?,
): Long? {
    val firstTokenMs = timeToFirstTokenMs ?: return null
    if (generationTimeMs <= 0L || firstTokenMs < 0L) return null
    return (generationTimeMs - firstTokenMs).coerceAtLeast(0L)
}

private fun buildLocalInferenceStatsFromTrace(
    trace: LocalInferenceTrace,
    generationTimeMs: Long,
    responseCharCount: Int,
    responseText: String? = null,
    fallbackTimeToFirstTokenMs: Long? = null,
): InferenceStats? {
    val resolvedStats = resolveLocalInferenceStats(trace)
    val measuredSnapshot = trace.measuredTokenSnapshot
    val existingInputTokens: Int? = null
    val existingOutputTokens = resolvedStats.outputTokens.value
    val existingTotalTokens = resolvedStats.totalTokens.value
    val existingTimeToFirstTokenMs = resolvedStats.firstTokenMs.value
    val existingGenerationDurationNs = resolvedStats.generationDurationNs.value
    val totalInferenceDurationNs = resolvedStats.evalDurationNs.value
    val wallClockLoadDurationNs = trace.wallClockLoadDurationNs?.takeIf { it >= 0L }
    val existingLoadDurationNs =
        wallClockLoadDurationNs ?: trace.loadTimeProbe.longValueOrNull()?.takeIf { it >= 0L }
    val timeToFirstTokenMs = existingTimeToFirstTokenMs ?: fallbackTimeToFirstTokenMs
    val fallbackGenerationDurationNs = buildLocalGenerationOnlyMsOrNull(
        generationTimeMs = generationTimeMs,
        timeToFirstTokenMs = timeToFirstTokenMs,
    )?.times(1_000_000L)
    val fallbackPromptEvalNs =
        if (resolvedStats.promptEvalDurationNs.value != null) {
            resolvedStats.promptEvalDurationNs.value
        } else {
            val evalNs = totalInferenceDurationNs
            val genNs = fallbackGenerationDurationNs
            if (evalNs != null && genNs != null) {
                (evalNs - genNs).takeIf { it > 0L }
            } else {
                null
            }
        }
    val inputTokens = measuredSnapshot?.inputTokens ?: existingInputTokens ?: trace.sessionPromptTokens
    val outputTokens = measuredSnapshot?.outputTokens ?: existingOutputTokens ?: trace.sessionResponseTokens
    val totalTokens = measuredSnapshot?.totalTokens ?: existingTotalTokens ?: trace.sessionTotalTokens
    val existingTokensPerSecond: Double? = null
    val tokensPerSecond = measuredSnapshot?.tokensPerSecond
        ?: existingTokensPerSecond
        ?: buildLocalTokensPerSecondOrNull(
            outputTokens = outputTokens,
            generationTimeMs = generationTimeMs,
        )
    val modelName = trace.modelNameProbe.stringValueOrNull()
        ?: trace.localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
    val finishReason = buildLocalFinishReasonOrNull(
        existingFinishReason = trace.finishReasonProbe.stringValueOrNull(),
        responseText = responseText,
    )
    val hasStats = modelName != null ||
        finishReason != null ||
        inputTokens != null ||
        outputTokens != null ||
        totalTokens != null
    if (!hasStats) return null
    return InferenceStats(
        modelName = modelName,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
        tokensPerSecond = tokensPerSecond,
        charsPerSecond = measuredSnapshot?.charsPerSecond,
        tokenCountMode = measuredSnapshot?.tokenCountMode,
        notes = measuredSnapshot?.notes,
        completionTokens = outputTokens,
        finishReason = finishReason,
        generationTimeMs = generationTimeMs,
        decodeDurationMs = measuredSnapshot?.decodeDurationMs,
        totalDurationMs = measuredSnapshot?.totalDurationMs,
        generationDurationNs = existingGenerationDurationNs ?: fallbackGenerationDurationNs,
        evalDurationNs = totalInferenceDurationNs,
        modelLoadDurationNs = existingLoadDurationNs,
        promptEvalDurationNs = fallbackPromptEvalNs,
        timeToFirstTokenMs = measuredSnapshot?.ttftMs ?: timeToFirstTokenMs,
        responseCharCount = responseCharCount,
    )
}

private fun buildLocalFinishReasonOrNull(
    existingFinishReason: String?,
    responseText: String?,
): String? {
    val normalizedExisting = existingFinishReason?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedExisting != null) return normalizedExisting
    return if (responseText.isNullOrBlank()) null else "stop"
}

internal fun createAssistantMessage(
    chatId: Int,
    response: String,
    latestInferenceStats: InferenceStats? = null,
    localSourceSummary: String? = null,
    imageInputCount: Int? = null,
    generationTimeMs: Long? = null,
): Message {
    val outputTokens = latestInferenceStats?.outputTokens ?: latestInferenceStats?.completionTokens
    val inputTokens = latestInferenceStats?.inputTokens
    val persistedTotalTokens = latestInferenceStats?.totalTokens
        ?: if (inputTokens != null && outputTokens != null) inputTokens + outputTokens else null
    return Message(
        message = response,
        chatId = chatId,
        isSendbyMe = false,
        completionTokens = outputTokens,
        generationTimeMs = generationTimeMs
            ?: latestInferenceStats?.generationTimeMs
            ?: latestInferenceStats?.inferenceTimeSec?.times(1000.0)?.toLong(),
        generationDurationNs = latestInferenceStats?.generationDurationNs,
        evalDurationNs = latestInferenceStats?.evalDurationNs,
        loadDurationNs = latestInferenceStats?.modelLoadDurationNs,
        promptEvalDurationNs = latestInferenceStats?.promptEvalDurationNs,
        modelName = latestInferenceStats?.modelName ?: latestInferenceStats?.model,
        inputTokens = inputTokens,
        totalTokens = persistedTotalTokens,
        tokensPerSecond = latestInferenceStats?.tokensPerSecond,
        charsPerSecond = latestInferenceStats?.charsPerSecond,
        tokenCountMode = latestInferenceStats?.tokenCountMode,
        inferenceNotes = latestInferenceStats?.notes,
        inferenceTimeSec = latestInferenceStats?.inferenceTimeSec,
        decodeDurationMs = latestInferenceStats?.decodeDurationMs,
        totalDurationMs = latestInferenceStats?.totalDurationMs,
        finishReason = latestInferenceStats?.finishReason,
        localSourceSummary = localSourceSummary,
        timeToFirstTokenMs = latestInferenceStats?.timeToFirstTokenMs,
        // 画像入力数は添付画像の枚数。入力トークンとは別メトリクスとして保存する。
        imageInputCount = imageInputCount ?: latestInferenceStats?.imageInputCount,
    )
}

@Composable
private fun InferenceStatsSheetContent(
    stats: InferenceStats,
    initialDisplayMode: InferenceStatsDisplayMode,
    onDisplayModeChange: (InferenceStatsDisplayMode) -> Unit,
    localTraceForDev: LocalInferenceTrace? = null,
    assistantText: String? = null,
    promptText: String? = null,
    devHeldStateText: String? = null,
    devCloseLifecycleText: String? = null,
    devDebugText: String? = null,
    preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
    markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
    showDevManualEngineRecreate: Boolean = false,
    manualEngineRecreateEnabled: Boolean = false,
    manualEngineRecreateBusy: Boolean = false,
    manualEngineRecreateResult: String = "none",
    manualEngineRecreateReason: String = "user-requested",
    onManualEngineRecreate: () -> Unit = {},
) {
    var selectedDisplayMode by rememberSaveable { mutableStateOf(initialDisplayMode) }
    LaunchedEffect(initialDisplayMode) {
        selectedDisplayMode = initialDisplayMode
    }
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val sheetContentPadding = 14.dp
    val sectionSpacing = 12.dp

    val sections = buildInferenceSummarySections(
        stats = stats,
        displayMode = selectedDisplayMode,
        localTraceForDev = localTraceForDev,
        assistantText = assistantText,
        promptText = promptText,
        enableDevLlmSessionAsyncPoc = ENABLE_DEV_LLM_SESSION_ASYNC_POC,
    )
    val acceleratorProbeSnapshot = if (BuildConfig.DEBUG && selectedDisplayMode == InferenceStatsDisplayMode.DEVELOPER) {
        remember(context) { AcceleratorProbe.captureSnapshot(context = context.applicationContext) }
    } else {
        null
    }
    val measuredTokenSnapshotSummary = if (BuildConfig.DEBUG && DEV_UI_DEBUG_MODE) {
        buildMeasuredTokenSnapshotSummary(localTraceForDev)
    } else {
        null
    }
    val detailSections = buildInferenceDetailSections(
        stats = stats,
        displayMode = selectedDisplayMode,
        localTraceForDev = localTraceForDev,
        assistantText = assistantText,
        promptText = promptText,
        devHeldStateText = devHeldStateText,
        devCloseLifecycleText = devCloseLifecycleText,
        devDebugText = devDebugText,
        measuredTokenSnapshotSummary = measuredTokenSnapshotSummary,
        enableDevLlmSessionAsyncPoc = ENABLE_DEV_LLM_SESSION_ASYNC_POC,
        acceleratorProbeSnapshot = acceleratorProbeSnapshot,
        preferredBackendDryRunSetting = preferredBackendDryRunSetting,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            // BottomSheet 内の視認性を上げるため、周囲の余白を揃える。
            .padding(sheetContentPadding),
            // 下部コンテンツが IME / ナビゲーションバーに埋もれないようにする。
            // シート内でのみ insets を吸収し、既存レイアウトへの影響を最小化する。
        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "推論統計",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                InferenceStatsModeSelector(
                    selectedMode = selectedDisplayMode,
                    onModeSelected = { mode ->
                        selectedDisplayMode = mode
                        onDisplayModeChange(mode)
                    },
                )
            }

            InferenceModelInfoRow(
                stats = stats,
                inferenceTarget = resolveInferenceTargetForStats(
                    stats = stats,
                    localTraceForDev = localTraceForDev,
                ),
                onCopyInferenceStats = {
                    clipboardManager.setText(
                        AnnotatedString(
                            buildInferenceStatsFullCopyText(
                                stats = stats,
                                displayMode = selectedDisplayMode,
                                sections = sections,
                                detailSections = detailSections,
                            ),
                        ),
                    )
                },
            )

            sections.forEach { section ->
                InferenceStatsSection(title = section.title) {
                    section.items.forEach { item ->
                        InferenceStatRow(label = item.label, value = item.value, emphasizeValue = item.emphasizeValue)
                    }
                }
            }

            if (selectedDisplayMode != InferenceStatsDisplayMode.SIMPLE) {
                InferenceTimingBreakdownSection(stats)
                InferenceContextUsageSection(stats)
            }

            if (selectedDisplayMode != InferenceStatsDisplayMode.SIMPLE && shouldShowInferenceTimingNote(stats)) {
                Text(
                    text = inferenceTimingNoteText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (selectedDisplayMode != InferenceStatsDisplayMode.SIMPLE) {
                Column(
                    modifier = Modifier.testTag("inferenceStatsDetailContent"),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                ) {
            detailSections.forEach { section ->
                        InferenceStatsSection(title = section.title) {
                            section.items.forEach { item ->
                                InferenceStatRow(
                                    label = item.label,
                                    value = item.value,
                                    emphasizeValue = item.emphasizeValue,
                                )
                            }
                        }
                    }
                }
            }
            if (selectedDisplayMode == InferenceStatsDisplayMode.DEVELOPER) {
                InferenceStatsSection(title = "DEV Markdown") {
                    InferenceStatRow(
                        label = "Markdown mode",
                        value = markdownStreamingMode.displayLabel,
                    )
                }
            }
            if (showDevManualEngineRecreate && selectedDisplayMode == InferenceStatsDisplayMode.DEVELOPER) {
                HorizontalDivider()
                Text(
                    text = "ローカルエンジンを再作成",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "現在のローカルエンジンを閉じ、次回推論で再作成します。preferredBackend変更後に使用してください。生成中は実行できません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onManualEngineRecreate,
                    enabled = manualEngineRecreateEnabled && !manualEngineRecreateBusy,
                ) {
                    Text("ローカルエンジンを再作成")
                }
                Text(
                    text = "PreferredBackend manual recreate result: $manualEngineRecreateResult",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "PreferredBackend manual recreate reason: $manualEngineRecreateReason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InferenceModelInfoRow(
    stats: InferenceStats,
    inferenceTarget: InferenceTarget,
    onCopyInferenceStats: () -> Unit,
) {
    val modelName = formatModelName(stats)
    InferenceStatsSection(title = "モデル情報") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "使用モデル",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InferenceTargetIcon(
                        target = inferenceTarget,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = modelName ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(
                onClick = onCopyInferenceStats,
                modifier = Modifier.semantics { contentDescription = "推論統計をコピー" },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "推論統計をコピー",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CopyableDebugBlock(
    text: String,
    title: String? = null,
    onCopy: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Red,
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { contentDescription = "デバッグテキストをコピー" },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "デバッグテキストをコピー",
                tint = Color.Red,
            )
        }
    }
}

private fun resolveInferenceTargetForStats(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace?,
): InferenceTarget {
    if (localTraceForDev != null) return InferenceTarget.LOCAL
    val localSourceSummary = stats.localSourceSummary?.trim().orEmpty()
    return if (localSourceSummary.isNotBlank()) InferenceTarget.LOCAL else InferenceTarget.SERVER
}

internal fun buildInferenceStatsFullCopyText(
    stats: InferenceStats,
    displayMode: InferenceStatsDisplayMode,
    sections: List<InferenceStatsSectionUi>,
    detailSections: List<InferenceStatsSectionUi>,
): String {
    return buildString {
        appendLine("推論統計")
        appendLine()
        appendLine("[モデル情報]")
        appendLine("使用モデル: ${formatModelName(stats) ?: "—"}")
        appendLine()

        sections.forEachIndexed { index, section ->
            appendSectionAsPlainText(
                sectionTitle = section.title,
                items = section.items,
            )
            if (index != sections.lastIndex) appendLine()
        }

        if (displayMode != InferenceStatsDisplayMode.SIMPLE) {
            appendLine()
            appendLine("[推論時間内訳]")
            val breakdown = buildInferenceTimeBreakdown(stats)
            if (breakdown == null) {
                appendLine("—")
            } else {
                breakdown.segments.forEach { segment ->
                    appendLine("${segment.label}: ${segment.durationText} / ${segment.percent}%")
                }
            }
            appendLine()
            appendLine("[コンテキスト使用量]")
            when (val usage = buildContextUsageUi(stats)) {
                null -> appendLine("—")
                is ContextUsageUi.WithMax -> appendLine("${usage.used} / ${usage.max} tokens (${usage.percent}%)")
                is ContextUsageUi.Loading -> {
                    appendLine("使用トークン ${usage.used}")
                    appendLine("上限取得中…")
                }

                is ContextUsageUi.WithoutMax -> {
                    appendLine("使用トークン ${usage.used}")
                    appendLine("上限未取得")
                }
            }
        }

        if (displayMode != InferenceStatsDisplayMode.SIMPLE) {
            appendLine()
            appendLine("[追加情報]")
            if (detailSections.isEmpty()) {
                appendLine("—")
            } else {
                detailSections.forEachIndexed { index, section ->
                    appendSectionAsPlainText(
                        sectionTitle = section.title,
                        items = section.items,
                    )
                    if (index != detailSections.lastIndex) appendLine()
                }
            }
        }

    }.trimEnd()
}

@Composable
private fun InferenceStatsModeSelector(
    selectedMode: InferenceStatsDisplayMode,
    onModeSelected: (InferenceStatsDisplayMode) -> Unit,
) {
    val modeButtons = listOf(
        Triple(
            InferenceStatsDisplayMode.SIMPLE,
            Icons.Outlined.ViewAgenda,
            "シンプル表示",
        ),
        Triple(
            InferenceStatsDisplayMode.DETAILED,
            Icons.AutoMirrored.Outlined.ViewList,
            "詳細表示",
        ),
        Triple(
            InferenceStatsDisplayMode.DEVELOPER,
            Icons.Outlined.Code,
            "開発者表示",
        ),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modeButtons.forEach { (mode, icon, description) ->
            InferenceStatsModeIconButton(
                icon = icon,
                contentDescription = description,
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun InferenceStatsModeIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun StringBuilder.appendSectionAsPlainText(
    sectionTitle: String,
    items: List<InferenceStatItemUi>,
) {
    appendLine("[$sectionTitle]")
    if (items.isEmpty()) {
        appendLine("—")
        return
    }
    items.forEach { item ->
        appendLine("${item.label}: ${item.value}")
    }
}

@Composable
private fun InferenceTimingBreakdownSection(stats: InferenceStats) {
    val breakdown = buildInferenceTimeBreakdown(stats) ?: return
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    InferenceStatsSection(title = "推論時間内訳") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            breakdown.segments.forEach { segment ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InferenceStatRow(
                        label = segment.label,
                        value = "${segment.durationText} / ${segment.percent}%",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                color = trackColor,
                                shape = RoundedCornerShape(999.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segment.ratio.toFloat().coerceIn(0f, 1f))
                                .height(8.dp)
                                .background(
                                    color = barColor,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InferenceContextUsageSection(stats: InferenceStats) {
    val usage = buildContextUsageUi(stats) ?: return
    InferenceStatsSection(title = "コンテキスト使用量") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (usage) {
                is ContextUsageUi.WithMax -> {
                    if (usage.ratio in 0.0..1.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(usage.ratio.toFloat().coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(999.dp),
                                    ),
                            )
                        }
                    }
                    Text(
                        text = "${usage.used} / ${usage.max} tokens (${usage.percent}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is ContextUsageUi.Loading -> {
                    Text(
                        text = "使用トークン ${usage.used}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上限取得中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ContextUsageUi.WithoutMax -> {
                    Text(
                        text = "使用トークン ${usage.used}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上限未取得",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun inferenceTimingNoteText(): String =
    "初回受信までは端末側の受信タイミング、全体完了までは推論統計の完了タイミングを示します。"

internal fun shouldShowInferenceTimingNote(stats: InferenceStats): Boolean =
    formatTimeToFirstToken(stats) != null || formatInferenceTime(stats) != null


internal data class InferenceTimeSegmentUi(
    val label: String,
    val ratio: Double,
    val percent: Int,
    val durationText: String,
)

internal data class InferenceTimeBreakdownUi(
    val segments: List<InferenceTimeSegmentUi>,
)

internal fun buildInferenceTimeBreakdown(stats: InferenceStats): InferenceTimeBreakdownUi? {
    val load = stats.modelLoadDurationNs?.takeIf { it >= 0L }
    val prompt = stats.promptEvalDurationNs?.takeIf { it >= 0L }
    val generation = stats.generationDurationNs?.takeIf { it > 0L }

    val segmentSources = buildList {
        if (load != null) add("ロード" to load)
        if (prompt != null) add("入力" to prompt)
        if (generation != null) add("生成" to generation)
    }
    val total = segmentSources.sumOf { it.second }
    if (total <= 0L) return null

    fun ratio(value: Long): Double = value.toDouble() / total.toDouble()
    return InferenceTimeBreakdownUi(
        segments = segmentSources.map { (label, duration) ->
            val valueRatio = ratio(duration)
            InferenceTimeSegmentUi(
                label = label,
                ratio = valueRatio,
                percent = (valueRatio * 100).roundToInt(),
                durationText = formatDurationNsAsSecondsForSheet(duration),
            )
        },
    )
}

private fun formatDurationNsAsSecondsForSheet(durationNs: Long): String {
    val seconds = durationNs / 1_000_000_000.0
    if (seconds > 0.0 && seconds < 0.1) return "<0.1 s"
    return String.format(Locale.US, "%.1f s", seconds)
}

internal sealed interface ContextUsageUi {
    data class WithMax(
        val used: Int,
        val max: Int,
        val ratio: Double,
        val percent: Int,
    ) : ContextUsageUi

    data class Loading(val used: Int) : ContextUsageUi

    data class WithoutMax(val used: Int) : ContextUsageUi
}

internal fun buildContextUsageUi(stats: InferenceStats): ContextUsageUi? {
    val used = stats.totalTokens?.takeIf { it >= 0 } ?: return null
    val max = stats.contextWindow?.takeIf { it > 0 }
    if (max != null) {
        val ratio = used.toDouble() / max.toDouble()
        return ContextUsageUi.WithMax(
            used = used,
            max = max,
            ratio = ratio,
            percent = (ratio * 100).roundToInt(),
        )
    }
    return when (stats.contextWindowFetchState) {
        ContextWindowFetchState.LOADING -> ContextUsageUi.Loading(used = used)
        ContextWindowFetchState.AVAILABLE,
        ContextWindowFetchState.UNAVAILABLE,
        -> ContextUsageUi.WithoutMax(used = used)
    }
}

@Composable
private fun DrawerSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val height = 40.dp
    val shape = RoundedCornerShape(height / 2)
    val drawerSearchTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default,
    )
    Box(
        modifier = modifier
            .height(height)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(start = 16.dp, top = 1.dp, end = 0.dp, bottom = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = drawerSearchTextStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "タイトル検索",
                                style = drawerSearchTextStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "検索をクリア",
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewRow(
    uris: List<Uri>,
    onOpen: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    inComposer: Boolean = false,
) {
    val attachmentPreviewSize = 72.dp
    val edgeFadeWidth = 12.dp
    val epsilonPx = 2
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val showLeftFade by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showRightFade by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1
            val lastVisibleItem = visibleItems.lastOrNull()

            totalItemsCount > 0 && (
                lastVisibleIndex < totalItemsCount - 1 ||
                    (
                        lastVisibleItem != null &&
                            (lastVisibleItem.offset + lastVisibleItem.size) >
                            (layoutInfo.viewportEndOffset + epsilonPx)
                        )
                )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 入力欄との視認分離に必要な最小限の余白
            .padding(
                horizontal = if (inComposer) 12.dp else 17.dp,
                // 入力欄内表示時の上側余白を +2dp 調整して縁との距離を確保
                vertical = if (inComposer) 3.5.dp else 8.dp,
            ),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(uris) { index, uri ->
                Box(
                    modifier = Modifier
                        .size(attachmentPreviewSize),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { imageView ->
                                imageView.setImageURI(uri)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpen(index) },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 1.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onRemoveAt(index) }
                            .testTag("attachment_remove_$index"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove attachment",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        if (showLeftFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(attachmentPreviewSize)
                    .width(edgeFadeWidth)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 1f),
                                surfaceColor.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
        }

        if (showRightFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(attachmentPreviewSize)
                    .width(edgeFadeWidth)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 0f),
                                surfaceColor.copy(alpha = 1f),
                            ),
                        ),
                    ),
            )
        }
    }
}

internal fun filterChatsByTitle(chats: List<Chat>, query: String): List<Chat> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
        return chats
    }
    return chats.filter { chat ->
        chat.title.contains(normalizedQuery, ignoreCase = true)
    }
}


internal fun formatChatPreview(message: String?): String {
    if (message.isNullOrBlank()) {
        return ""
    }
    val oneLineMessage = message
        .replace("\n", " ")
        .replace("\r", " ")
        .trim()
    if (oneLineMessage.isEmpty()) {
        return ""
    }
    val maxLength = 80
    return if (oneLineMessage.length > maxLength) {
        oneLineMessage.take(maxLength) + "…"
    } else {
        oneLineMessage
    }
}


internal fun resolveDefaultChatId(explicitChatId: Int?, chats: List<Chat>): Int? {
    return explicitChatId
}

internal suspend fun createAndNavigateToNewChat(
    createNewChat: suspend () -> Int,
    onChatResolved: (Int) -> Unit,
    navigateToChat: (Int) -> Unit,
    closeDrawer: suspend () -> Unit,
) {
    val newChatId = createNewChat()
    onChatResolved(newChatId)
    closeThenNavigate(
        closeDrawer = closeDrawer,
        navigate = {
            navigateToChat(newChatId)
        }
    )
}

internal suspend fun closeThenNavigate(
    closeDrawer: suspend () -> Unit,
    navigate: () -> Unit,
) {
    // close の失敗は吸収し、navigate は必ず実行する
    runCatching { closeDrawer() }
    navigate()
}

private suspend fun closeDrawerSafely(drawerState: DrawerState) {
    if (!drawerState.isOpen) {
        return
    }
    runCatching { drawerState.close() }
}

private suspend fun closeDrawerForNavigation(drawerState: DrawerState) {
    if (!drawerState.isOpen) return
    if (RuntimeFlags.isUiTestRuntime()) {
        runCatching { drawerState.snapTo(DrawerValue.Closed) }
    } else {
        runCatching { drawerState.close() }
    }
}

internal fun shouldAutoCreateNewChat(
    suppressAutoNewChat: Boolean,
    resolvedChatId: Int?,
    isCreatingChat: Boolean,
): Boolean {
    return !suppressAutoNewChat && resolvedChatId == null && !isCreatingChat
}

private fun computeLatestUserAnchor(messages: List<Message>): Int {
    if (messages.isEmpty()) {
        return 0
    }
    val lastUser = messages.indexOfLast { it.isSendbyMe }
    return if (lastUser >= 0) {
        lastUser
    } else {
        messages.lastIndex
    }
}

private fun isStopCancellationLikeMessage(message: String?): Boolean {
    val text = message?.lowercase().orEmpty()
    return "socket closed" in text ||
        "software caused connection abort" in text ||
        "canceled" in text ||
        "cancelled" in text ||
        "stream was reset" in text
}

private fun runDevOnlyNpuChatScreenBlockedBranchViaReflection(
    context: Context,
    prompt: String,
): String {
    return runCatching {
        val branchClass = Class.forName(
            "io.github.ninbyo02.lami.npu.DevOnlyNpuChatScreenBlockedBranch",
        )
        branchClass
            .getMethod("run", Context::class.java, String::class.java)
            .invoke(null, context, prompt) as String
    }.getOrElse { throwable ->
        "DEV NPU blocked branch unavailable: ${throwable.javaClass.simpleName}"
    }
}

private fun runDevQairt244Sm8750NpuChatScreenRouteViaReflection(
    context: Context,
    prompt: String,
): DevQairt244Sm8750NpuChatScreenResult {
    val raw = runCatching {
        val branchClass = Class.forName(
            "io.github.ninbyo02.lami.npu.DevOnlyNpuChatScreenBlockedBranch",
        )
        branchClass
            .getMethod("runForChatScreen", Context::class.java, String::class.java)
            .invoke(null, context, prompt) as String
    }.getOrElse { throwable ->
        return DevQairt244Sm8750NpuChatScreenResult(
            success = false,
            reasonCode = "reflection_unavailable:${throwable.javaClass.simpleName}",
            assistantMessage = "DEV NPU route failed: ${throwable.javaClass.simpleName}",
            failureStage = "reflection",
            stopReason = "reflection_unavailable",
        )
    }
    return DevQairt244Sm8750NpuChatScreenResult.fromKeyValueText(raw)
}

private data class DevQairt244Sm8750NpuChatScreenResult(
    val success: Boolean,
    val reasonCode: String,
    val assistantMessage: String,
    val output: String = "",
    val selectedRoute: String = "qairt244_sm8750_dev_npu",
    val resolvedModelBasename: String = "",
    val requiredSm8750ModelPath: Boolean = false,
    val npuBackend: String = "",
    val npuBackendEvidence: String = "",
    val nativeMaxOutputTokensLimit: Int? = null,
    val runDecodeReached: Boolean = false,
    val uiCleanupStatus: String = "cleanup_scheduled",
    val decodeElapsedMs: Long? = null,
    val elapsedMs: Long? = null,
    val maxOutputTokens: Int = 16,
    val fallbackUsed: Boolean = false,
    val failureStage: String = "",
    val stopReason: String = "",
    val artifactPath: String = "",
) {
    fun toInferenceStats(): InferenceStats = InferenceStats(
        modelName = selectedRoute,
        generationTimeMs = elapsedMs,
        decodeDurationMs = decodeElapsedMs,
        totalDurationMs = elapsedMs,
        tokenCountMode = "qairt244-dev-npu-lower-level",
        notes = listOf(
            "selected_route=$selectedRoute",
            "resolved_model_basename=$resolvedModelBasename",
            "required_sm8750_model_path=$requiredSm8750ModelPath",
            "npu_backend=$npuBackend",
            "npu_backend_evidence=$npuBackendEvidence",
            "native_max_output_tokens_limit=${nativeMaxOutputTokensLimit ?: "unknown"}",
            "run_decode_reached=$runDecodeReached",
            "max_output_tokens=$maxOutputTokens",
            "fallback_used=$fallbackUsed",
            "ui_cleanup_status=$uiCleanupStatus",
            "failure_stage=$failureStage",
            "stop_reason=$stopReason",
        ).joinToString(";"),
        finishReason = if (success) "success" else reasonCode,
        localSourceSummary = toLocalSourceSummary(),
        model = selectedRoute,
        modelLabel = selectedRoute,
        responseCharCount = assistantMessage.length,
    )

    fun toLocalSourceSummary(): String = listOf(
        "selected_route=$selectedRoute",
        "resolved_model_basename=$resolvedModelBasename",
        "required_sm8750_model_path=$requiredSm8750ModelPath",
        "npu_backend=$npuBackend",
        "npu_backend_evidence=$npuBackendEvidence",
        "native_max_output_tokens_limit=${nativeMaxOutputTokensLimit ?: "unknown"}",
        "run_decode_reached=$runDecodeReached",
        "decode_elapsed_ms=${decodeElapsedMs ?: "unknown"}",
        "max_output_tokens=$maxOutputTokens",
        "fallback_used=$fallbackUsed",
        "ui_cleanup_status=$uiCleanupStatus",
        "failure_stage=$failureStage",
        "stop_reason=$stopReason",
        "normal_ui_route_connected=false",
        "artifact_path=$artifactPath",
    ).joinToString("\n")

    companion object {
        fun fromKeyValueText(text: String): DevQairt244Sm8750NpuChatScreenResult {
            val values = text.lineSequence()
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) return@mapNotNull null
                    line.substring(0, index) to unescapeValue(line.substring(index + 1))
                }
                .toMap()
            val success = values["success"]?.toBooleanStrictOrNull() ?: false
            val reasonCode = values["reasonCode"].orEmpty().ifBlank { if (success) "success" else "unknown" }
            val output = values["output"].orEmpty()
            val assistantMessage = values["assistant_message"].orEmpty().ifBlank {
                if (success) output else "DEV NPU route failed: $reasonCode"
            }
            return DevQairt244Sm8750NpuChatScreenResult(
                success = success,
                reasonCode = reasonCode,
                assistantMessage = assistantMessage,
                output = output,
                selectedRoute = values["selected_route"].orEmpty().ifBlank { "qairt244_sm8750_dev_npu" },
                resolvedModelBasename = values["resolved_model_basename"].orEmpty(),
                requiredSm8750ModelPath = values["required_sm8750_model_path"]?.toBooleanStrictOrNull() ?: false,
                npuBackend = values["npu_backend"].orEmpty(),
                npuBackendEvidence = values["npu_backend_evidence"].orEmpty(),
                nativeMaxOutputTokensLimit = values["native_max_output_tokens_limit"]?.toIntOrNull(),
                runDecodeReached = values["run_decode_reached"]?.toBooleanStrictOrNull() ?: false,
                uiCleanupStatus = values["ui_cleanup_status"].orEmpty().ifBlank { "cleanup_scheduled" },
                decodeElapsedMs = values["decode_elapsed_ms"]?.toLongOrNull(),
                elapsedMs = values["elapsed_ms"]?.toLongOrNull(),
                maxOutputTokens = values["max_output_tokens"]?.toIntOrNull() ?: 16,
                fallbackUsed = values["fallback_used"]?.toBooleanStrictOrNull() ?: false,
                failureStage = values["failure_stage"].orEmpty(),
                stopReason = values["stop_reason"].orEmpty(),
                artifactPath = values["artifact_path"].orEmpty(),
            )
        }

        private fun unescapeValue(value: String): String =
            value.replace("\\n", "\n").replace("\\\\", "\\")
    }
}

private fun List<String>.toAttachmentUriStringsJson(): String =
    JSONArray().apply { forEach { uri -> put(uri) } }.toString()
