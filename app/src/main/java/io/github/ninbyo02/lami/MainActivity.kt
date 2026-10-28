package io.github.ninbyo02.lami

import android.Manifest
import android.content.Intent
import io.github.ninbyo02.lami.tts.LamiNeuralVoiceDiagnostics
import kotlinx.coroutines.Dispatchers
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ninbyo02.lami.assistant.AssistantMediaButtonProbe
import io.github.ninbyo02.lami.assistant.AssistantVoiceEvent
import io.github.ninbyo02.lami.assistant.AssistantVoiceEvents
import io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_AUTO_SUBMIT
import io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_SHOW_WHEN_LOCKED
import io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_START_LISTENING
import io.github.ninbyo02.lami.assistant.HeadsetAssistantVolumeController
import io.github.ninbyo02.lami.asr.AssistantRecognitionResult
import io.github.ninbyo02.lami.asr.LamiAssistantRecognizer
import io.github.ninbyo02.lami.api.RetrofitClient
import io.github.ninbyo02.lami.db.AppDatabase
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.repository.BaseUrlRepository
import io.github.ninbyo02.lami.db.repository.ChatRepository
import io.github.ninbyo02.lami.db.repository.ModelPreferenceRepository
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.navigation.SettingsRoute
import io.github.ninbyo02.lami.assistant.AssistantPackagePolicy
import io.github.ninbyo02.lami.ui.screens.home.Home
import io.github.ninbyo02.lami.ui.screens.home.LocalInferenceEngineHolder
import io.github.ninbyo02.lami.ui.screens.settings.About
import io.github.ninbyo02.lami.ui.screens.settings.ScreenOrientationMode
import io.github.ninbyo02.lami.ui.screens.settings.SettingsData
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.ui.screens.settings.Settings
import io.github.ninbyo02.lami.ui.screens.settings.NoticeScreen
import io.github.ninbyo02.lami.ui.screens.settings.LocalBaseModelScreen
import io.github.ninbyo02.lami.ui.screens.settings.LocalGenericFallbackModelScreen
import io.github.ninbyo02.lami.ui.screens.settings.SpriteSettingsScreen
import io.github.ninbyo02.lami.ui.screens.spriteeditor.SpriteEditorScreen
import io.github.ninbyo02.lami.ui.common.LocalAppSnackbarHostState
import io.github.ninbyo02.lami.ui.common.ProjectSnackbar
import io.github.ninbyo02.lami.ui.common.TopAppBarHeight
import io.github.ninbyo02.lami.ui.startup.StartupBackendSplash
import io.github.ninbyo02.lami.ui.theme.OllamaTheme
import io.github.ninbyo02.lami.util.RuntimeFlags
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import io.github.ninbyo02.lami.viewmodels.OllamaViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
    private val assistantButtonProbe by lazy {
        if (AssistantPackagePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) AssistantMediaButtonProbe(this) else null
    }
    private val headsetAssistantVolumeController by lazy {
        if (AssistantPackagePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) {
            HeadsetAssistantVolumeController(this)
        } else {
            null
        }
    }
    private lateinit var viewModel: OllamaViewModel
    private var assistantCueVoice: io.github.ninbyo02.lami.tts.AndroidTtsController? = null
    private val assistantRecognizerDelegate = lazy {
        LamiAssistantRecognizer(applicationContext, beforeCapture = {
            val voice = assistantCueVoice ?: io.github.ninbyo02.lami.tts.AndroidTtsController(applicationContext)
                .also { assistantCueVoice = it }
            val preferences = SettingsPreferences(applicationContext)
            voice.setSpeechConfig(preferences.ttsSpeechRateFlow.first(), preferences.ttsPitchFlow.first())
            if (voice.canPlayInCurrentMode()) {
                try {
                    io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext, "[LAMI_ASR] cue_started")
                    io.github.ninbyo02.lami.assistant.AssistantListeningCue(voice,
                        completedNormally = { voice.lastPlaybackCompletedNormally }).play()
                    io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext, "[LAMI_ASR] cue_finished")
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                    io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext, "[LAMI_ASR] optional_cue_timeout")
                } catch (failure: Exception) {
                    io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext,
                        "[LAMI_ASR] optional_cue_failed type=${failure.javaClass.simpleName}")
                }
            } else {
                io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext, "[LAMI_ASR] cue_suppressed_private_route_unavailable")
            }
        })
    }
    private val assistantRecognizer by assistantRecognizerDelegate
    private var assistantRecognitionJob: Job? = null
    private val assistantCaptureJobs = io.github.ninbyo02.lami.assistant.AssistantCaptureSerialJobs()
    private val assistantCaptureRequests = io.github.ninbyo02.lami.assistant.AssistantCaptureRequestGate()
    private var assistantCaptureRequestId = 0L
    private var chatLaunchVersion by mutableStateOf(0)
    private var chatEntryReadyVersion by mutableStateOf(0)
    private var assistantListeningPendingPermission = false
    private var assistantAutoSubmitPendingPermission = false
    private var assistantWakeWordPendingPermission: String? = null
    private var assistantWakeWordSessionActive = false
    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && assistantListeningPendingPermission) {
            assistantListeningPendingPermission = false
            val autoSubmit = assistantAutoSubmitPendingPermission
            assistantAutoSubmitPendingPermission = false
            val wakeWord = assistantWakeWordPendingPermission
            assistantWakeWordPendingPermission = null
            if (assistantCaptureRequests.isCurrent(assistantCaptureRequestId)) {
                startAssistantRecognition(autoSubmit, wakeWord, assistantCaptureRequestId)
            }
        } else if (!granted) {
            assistantListeningPendingPermission = false
            assistantAutoSubmitPendingPermission = false
            assistantWakeWordPendingPermission = null
            assistantCaptureRequests.finish(assistantCaptureRequestId)
            AssistantVoiceEvents.publish(
                AssistantVoiceEvent.Failure("音声認識にはマイクの許可が必要です。"),
            )
        }
    }
    private val heldEngineLifecycleBridge by lazy {
        HeldEngineLifecycleBridge(holder = LocalInferenceEngineHolder.getInstance(applicationContext))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        traceAssistantEntry("create", intent, savedInstanceState != null)
        configureAssistantWindow(intent)
        LamiNeuralVoiceDiagnostics.start(this, savedInstanceState)
        // Initialize Database & Repository
        val database = ChatDatabase.Companion.getDatabase(applicationContext)
        val repository =
            ChatRepository(chatDao = database.chatDao(), messageDao = database.messageDao())
        val baseUrlDataBase = AppDatabase.getDatabase(this) // 'this' is the Application context
        val modelPreferenceRepository = ModelPreferenceRepository(baseUrlDataBase.modelPreferenceDao())
        val baseUrlRepository = BaseUrlRepository(baseUrlDataBase.baseUrlDao())

        val initializationState = runBlocking {
            RetrofitClient.initialize(baseUrlRepository, modelPreferenceRepository)
        }
        val resolvedBaseUrl = initializationState.baseUrl.trimEnd('/')
        baseUrlRepository.updateActiveBaseUrl(resolvedBaseUrl)
        val initialSelectedModel = runBlocking {
            resolvedBaseUrl.takeIf { it.isNotBlank() }?.let { baseUrl ->
                modelPreferenceRepository.getSelectedModel(baseUrl)
            }
        }

        val settingsPreferences = SettingsPreferences(applicationContext)
        // Initialize ViewModel with Factory
        val factory = OllamaViewModelFactory(
            repository,
            modelPreferenceRepository,
            settingsPreferences,
            initialSelectedModel,
            baseUrlRepository.activeBaseUrl
        )
        viewModel = ViewModelProvider(this, factory)[OllamaViewModel::class.java]

        lifecycleScope.launch {
            // アプリ初回起動時に per-state JSON を必ず初期化する
            settingsPreferences.ensurePerStateAnimationJsonsInitialized()
        }

        val shouldRestoreLastRoute = savedInstanceState == null

        setContent {
            var showStartupSplash by remember { mutableStateOf(savedInstanceState == null) }
            val settingsData by settingsPreferences.settingsData.collectAsState(initial = SettingsData())
            LaunchedEffect(settingsData.screenOrientationMode) {
                requestedOrientation = when (settingsData.screenOrientationMode) {
                    ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    ScreenOrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            // Initialise navigation
            val navController = rememberNavController()
            LaunchedEffect(showStartupSplash, chatLaunchVersion) {
                // The splash branch does not compose NavHost yet. Navigating before the
                // graph is installed crashes NavController during cold start.
                if (showStartupSplash) return@LaunchedEffect
                if (chatLaunchVersion > 0) {
                    val entry = navController.currentBackStackEntryFlow.first()
                    if (!isAssistantChatRoute(entry.destination.route)) {
                        navController.navigate(Routes.CHAT_ROOT) {
                            launchSingleTop = true
                            popUpTo(Routes.CHAT_ROOT) { inclusive = false }
                        }
                    }
                    navController.currentBackStackEntryFlow.first { isAssistantChatRoute(it.destination.route) }
                    chatEntryReadyVersion = chatLaunchVersion
                    return@LaunchedEffect
                }
                // UIテスト時は復元ナビゲーションを無効化して常にCHAT_ROOTから開始する
                if (RuntimeFlags.isUiTestRuntime()) return@LaunchedEffect
                // 回転などでActivity再生成時にlastRouteがSettingsだと意図せずSettingsへ遷移するため、
                // savedInstanceStateがある場合は復元ナビゲーションをスキップして現在画面の復元を優先する
                if (!shouldRestoreLastRoute) return@LaunchedEffect

                val restored = settingsPreferences.lastRoute.first()
                val allowedRoutes = setOf(
                    Routes.HOME,
                    Routes.CHAT_ROOT,
                    Routes.SETTINGS,
                    Routes.ABOUT,
                    Routes.NOTICE,
                    SettingsRoute.LocalBaseModel.route,
                    SettingsRoute.LocalGenericFallbackModel.route,
                    SettingsRoute.SpriteSettings.route,
                    SettingsRoute.SpriteEditor.route
                )
                val targetRoute = resolveStartRoute(restored = restored, allowed = allowedRoutes)
                // NavHost生成後に必要な場合のみ遷移して復元する。
                // showStartupSplash=false と NavHost の graph install は同一 frame 内で競合し得るため、
                // 初期 back stack が生成されるまで待ってから navigate する。
                if (targetRoute != Routes.CHAT_ROOT) {
                    navController.currentBackStackEntryFlow.first()
                    navController.navigate(targetRoute) {
                        launchSingleTop = true
                        // ベースを固定して復元時のBackStack重複を防ぐ
                        popUpTo(Routes.CHAT_ROOT) { inclusive = false }
                    }
                }
            }
            Crossfade(targetState = showStartupSplash, label = "startup-to-app") { showSplash ->
                if (showSplash) {
                    OllamaTheme(dynamicColor = false) {
                        StartupBackendSplash(
                            context = applicationContext,
                            settingsPreferences = settingsPreferences,
                            onFinished = { showStartupSplash = false },
                        )
                    }
                } else OllamaTheme(dynamicColor = settingsData.useDynamicColor) {
                val view = LocalView.current
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    window.statusBarColor = colorScheme.background.toArgb()
                    WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !colorScheme.background.isDark()
                }
                val appSnackbarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(
                    LocalAppSnackbarHostState provides appSnackbarHostState
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    // 全体：ScaffoldのinnerPaddingを適用しコンテンツ被りを防止
                                    .padding(innerPadding)
                                    .fillMaxSize()
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = Routes.CHAT_ROOT
                                ) {
                                        composable(Routes.HOME) {
                                            Home(navController, viewModel, chatLaunchVersion = chatLaunchVersion)
                                        }
                                        composable(route = Routes.CHAT_ROOT) {
                                            Home(navController, viewModel, chatId = null, chatLaunchVersion = chatLaunchVersion)
                                        }
                                        composable(
                                            route = Routes.CHAT_WITH_ID_ROUTE,
                                            arguments = listOf(navArgument(Routes.CHAT_ID_ARG_ROUTE) { type = NavType.IntType })
                                        ) { backStackEntry ->
                                            val chatId = backStackEntry.arguments?.getInt(Routes.CHAT_ID_ARG_ROUTE)
                                            Home(navController, viewModel, chatId, chatLaunchVersion = chatLaunchVersion)
                                        }
                                        composable(
                                            route = "${Routes.SETTINGS}?localModelFocus={localModelFocus}",
                                            arguments = listOf(navArgument("localModelFocus") {
                                                type = NavType.StringType
                                                nullable = true
                                                defaultValue = null
                                            }),
                                        ) { settingsBackStackEntry ->
                                            Settings(
                                                navgationController = navController,
                                                settingsBackStackEntry = settingsBackStackEntry,
                                                onBluetoothStandby = if (AssistantPackagePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) {
                                                    {
                                                        startActivity(Intent().setClassName(packageName,
                                                            "io.github.ninbyo02.lami.assistant.BluetoothAssistSetupActivity"))
                                                    }
                                                } else null,
                                                onUsbHeadset = if (AssistantPackagePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) {
                                                    {
                                                        startActivity(Intent().setClassName(packageName,
                                                            "io.github.ninbyo02.lami.assistant.JabraUsbSetupActivity"))
                                                    }
                                                } else null,
                                            )
                                        }
                                        composable(Routes.ABOUT) {
                                            About(navController, viewModel)
                                        }
                                        composable(Routes.NOTICE) {
                                            NoticeScreen(navController)
                                        }
                                        composable(SettingsRoute.LocalBaseModel.route) {
                                            LocalBaseModelScreen(navController)
                                        }
                                        composable(SettingsRoute.LocalGenericFallbackModel.route) {
                                            LocalGenericFallbackModelScreen(navController)
                                        }
                                        composable(SettingsRoute.SpriteSettings.route) {
                                            SpriteSettingsScreen(navController)
                                        }
                                        composable(SettingsRoute.SpriteEditor.route) {
                                            SpriteEditorScreen(navController)
                                        }
                                }
                            }
                            SnackbarHost(
                                hostState = appSnackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    // 上：ステータスバー回避のため最小限の top padding
                                    .statusBarsPadding()
                                    // 上：TopAppBar回避のため最小限の top padding、左右：スナックバーの余白
                                    .padding(top = TopAppBarHeight + 8.dp, start = 16.dp, end = 16.dp)
                                    .widthIn(max = 560.dp),
                                snackbar = { data ->
                                    val isError = data.visuals.actionLabel == "ERROR"
                                    val textMaxLines = if (isError) 4 else 2
                                    val containerColor = MaterialTheme.colorScheme.inverseSurface
                                    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
                                    ProjectSnackbar(
                                        message = data.visuals.message,
                                        containerColor = containerColor,
                                        contentColor = contentColor,
                                        maxLines = textMaxLines,
                                    )
                                }
                            )
                        }
                    }
                }
                }
            }
        }
        maybeStartAssistantRecognition(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        traceAssistantEntry("new_intent", intent, false)
        setIntent(intent)
        configureAssistantWindow(intent)
        if (isLauncherChatEntry(intent.action, intent.categories.orEmpty())) chatLaunchVersion++
        maybeStartAssistantRecognition(intent)
    }

    private fun traceAssistantEntry(stage: String, request: Intent, restored: Boolean) {
        io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext,
            "[LAMI_ASR] main_entry stage=$stage assist=${request.action == Intent.ACTION_ASSIST} restored=$restored listening=${request.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false)} flags=${request.flags} elapsed=${android.os.SystemClock.elapsedRealtime()}")
    }

    private var assistantSubmissionId = 0L

    private fun maybeStartAssistantRecognition(launchIntent: Intent) {
        io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext,
            "[LAMI_ASR] assist_received listening=${launchIntent.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false)}")
        if (!launchIntent.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false)) return
        chatLaunchVersion++
        launchIntent.removeExtra(EXTRA_ASSISTANT_START_LISTENING)
        val autoSubmit = launchIntent.getBooleanExtra(EXTRA_ASSISTANT_AUTO_SUBMIT, false)
        launchIntent.removeExtra(EXTRA_ASSISTANT_AUTO_SUBMIT)
        val wakeWord = launchIntent.getStringExtra(io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_WAKE_WORD)
            ?.takeIf { BuildConfig.DEBUG }
        launchIntent.removeExtra(io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_WAKE_WORD)
        assistantCaptureRequestId = assistantCaptureRequests.begin()
        assistantSubmissionId = AssistantVoiceEvents.beginInvocation()
        traceAssistantStage("invocation", assistantCaptureRequestId)
        assistantRecognitionJob?.cancel()
        assistantCueVoice?.stop()
        AssistantVoiceEvents.prepare()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startAssistantRecognition(autoSubmit, wakeWord, assistantCaptureRequestId)
        } else {
            val alreadyRequesting = assistantListeningPendingPermission
            assistantListeningPendingPermission = true
            assistantAutoSubmitPendingPermission = autoSubmit
            assistantWakeWordPendingPermission = wakeWord
            if (!alreadyRequesting) microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAssistantRecognition(autoSubmit: Boolean, wakeWord: String? = null, requestId: Long,
                                          submissionId: Long = assistantSubmissionId) {
        if (!assistantCaptureRequests.isCurrent(requestId)) return
        io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext,
            "[LAMI_ASR] recognition_requested autoSubmit=$autoSubmit")
        AssistantVoiceEvents.prepare()
        val requestedChatVersion = chatLaunchVersion
        assistantWakeWordSessionActive = wakeWord != null
        // Only model verification/native initialization overlaps the unchanged startup splash.
        // It owns no recorder, route or focus and survives cancellation of an individual waiter.
        val preparation = assistantRecognizer.prepareModel()
        val firstInputActive = java.util.concurrent.atomic.AtomicBoolean(false)
        val firstFrame = java.util.concurrent.atomic.AtomicBoolean(false)
        assistantRecognitionJob = assistantCaptureJobs.replace(lifecycleScope) {
            if (!assistantCaptureRequests.isCurrent(requestId)) return@replace
            traceAssistantStage("capture_owner_acquired", requestId)
            val ownedJob = kotlin.coroutines.coroutineContext[Job]
            var manuallyStopped = false
            val stopWatcher = launch {
                AssistantVoiceEvents.stopRecognitionRequests.collect {
                    if (!assistantCaptureRequests.isCurrent(requestId)) return@collect
                    manuallyStopped = true
                    assistantCaptureRequests.finish(requestId)
                    assistantCueVoice?.stop()
                    if (AssistantVoiceEvents.phase.value == io.github.ninbyo02.lami.assistant.AssistantVoicePhase.WAITING_FOR_WAKE_WORD) {
                        ownedJob?.cancel()
                    } else assistantRecognizer.stopAndKeep()
                }
            }
            try {
                try {
                    withTimeout(10_000) {
                        lifecycle.withResumed { }
                        snapshotFlow { chatEntryReadyVersion >= requestedChatVersion }.first { it }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                    AssistantVoiceEvents.publish(AssistantVoiceEvent.Failure("会話画面を準備できませんでした。もう一度呼び出してください。"))
                    return@replace
                }
                io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext, "[LAMI_ASR] chat_ready")
                traceAssistantStage("chat_and_splash_ready", requestId)
                if (manuallyStopped) { AssistantVoiceEvents.reset(); return@replace }
                val automaticSending = io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences(applicationContext)
                    .voiceInputAutoSendFlow.first()
                if (manuallyStopped || !assistantCaptureRequests.isCurrent(requestId)) {
                    if (assistantCaptureRequestId == requestId) AssistantVoiceEvents.reset()
                    return@replace
                }
                // Stop chat output before selecting an input route as well as before capture.
                withTimeout(10_000) { AssistantVoiceEvents.prepareCapture() }
                traceAssistantStage("previous_audio_cleanup_complete", requestId)
                ensureActive()
                if (!assistantCaptureRequests.isCurrent(requestId)) return@replace
                val result = assistantRecognizer.listenOnce(wakeWord,
                    isCurrentSession = { assistantCaptureRequests.isCurrent(requestId) },
                    onCaptureEvent = { event ->
                        assistantCaptureRequests.observe(requestId, event)
                        when (event) {
                            io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureEvent.Started -> traceAssistantStage("capture_started", requestId)
                            io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureEvent.InputResumed ->
                                if (firstInputActive.compareAndSet(false, true)) traceAssistantStage("input_active", requestId)
                            is io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureEvent.Level ->
                                if (firstFrame.compareAndSet(false, true)) traceAssistantStage("first_frame", requestId)
                            else -> Unit
                        }
                        runOnUiThread {
                            pulseAssistantReady(requestId)
                        }
                    },
                    publishEvent = { event ->
                        runOnUiThread {
                            if (assistantCaptureRequests.isCurrent(requestId) && AssistantVoiceEvents.isCurrentInvocation(submissionId) &&
                                (event != AssistantVoiceEvent.Listening || assistantCaptureRequests.isCaptureLive(requestId))) {
                                AssistantVoiceEvents.publish(event)
                            }
                        }
                    }, onStage = { stage -> traceAssistantStage(stage, requestId) }, preparation = preparation)
                ensureActive()
                if (!assistantCaptureRequests.isCurrent(requestId) && !manuallyStopped) return@replace
                if (!AssistantVoiceEvents.isCurrentInvocation(submissionId)) return@replace
                when (result) {
                    is AssistantRecognitionResult.Success -> AssistantVoiceEvents.publishTranscript(
                        submissionId, result.transcript,
                        automaticSending && !manuallyStopped && result.automaticEligible,
                    )
                    is AssistantRecognitionResult.ModelRequired -> AssistantVoiceEvents.publish(
                        AssistantVoiceEvent.Failure(
                            "音声認識モデルをLAMIへ配置してください: ${result.stagingPath}",
                        ),
                    )
                    is AssistantRecognitionResult.Failure -> AssistantVoiceEvents.publish(
                        AssistantVoiceEvent.Failure(result.detail),
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (assistantCaptureRequests.isCurrent(requestId)) AssistantVoiceEvents.reset()
                throw cancelled
            } catch (_: Exception) {
                if (assistantCaptureRequests.isCurrent(requestId)) AssistantVoiceEvents.publish(AssistantVoiceEvent.Failure("音声入力を開始できませんでした。"))
            } finally {
                stopWatcher.cancel()
                if (assistantCaptureRequestId == requestId) assistantWakeWordSessionActive = false
                assistantCaptureRequests.finish(requestId)
            }
        }
        assistantRecognitionJob?.start()
    }

    private fun pulseAssistantReady(requestId: Long) {
        val foreground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            hasWindowFocus() && assistantRecognitionJob?.isActive == true &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (assistantCaptureRequests.claimPulse(requestId, foreground)) {
            traceAssistantStage("ready_haptic_requested", requestId)
            window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun traceAssistantStage(stage: String, requestId: Long) {
        io.github.ninbyo02.lami.util.DebugTraceFile.append(applicationContext,
            "[LAMI_ASR_TIMING] request=$requestId stage=$stage elapsed_ms=${android.os.SystemClock.elapsedRealtime()}")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pulseAssistantReady(assistantCaptureRequestId)
    }

    private fun configureAssistantWindow(launchIntent: Intent) {
        val showOverLock = AssistantPackagePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG) &&
            launchIntent.getBooleanExtra(EXTRA_ASSISTANT_SHOW_WHEN_LOCKED, false)
        setShowWhenLocked(showOverLock)
        setTurnScreenOn(showOverLock)
    }

    override fun onStart() {
        super.onStart()
        assistantButtonProbe?.start()
        heldEngineLifecycleBridge.onStart(scope = lifecycleScope)
    }

    override fun onStop() {
        AssistantVoiceEvents.cancelInvocation(assistantSubmissionId)
        if (assistantRecognitionJob?.isActive == true) {
            assistantCaptureRequests.finish(assistantCaptureRequestId)
            assistantRecognitionJob?.cancel()
            AssistantVoiceEvents.reset()
        }
        // Restrict lock-screen visibility to the current explicit assist launch.
        if (!isChangingConfigurations) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
            intent.removeExtra(EXTRA_ASSISTANT_SHOW_WHEN_LOCKED)
        }
        assistantButtonProbe?.stop()
        heldEngineLifecycleBridge.onStop(scope = lifecycleScope)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        headsetAssistantVolumeController?.activate()
    }

    override fun onPause() {
        headsetAssistantVolumeController?.deactivate()
        super.onPause()
    }

    // Public Activity callback; AndroidX's inherited bridge has a library-only annotation.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (AssistantPackagePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) {
            assistantButtonProbe?.observeActivityKey(event)
            if (headsetAssistantVolumeController?.observeKey(event) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (assistantRecognizerDelegate.isInitialized() &&
            (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)) {
            assistantRecognizer.releasePreparedModel()
        }
        heldEngineLifecycleBridge.onTrimMemory(scope = lifecycleScope, level = level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (assistantRecognizerDelegate.isInitialized()) assistantRecognizer.releasePreparedModel()
        heldEngineLifecycleBridge.onLowMemory(scope = lifecycleScope)
    }

    override fun onDestroy() {
        assistantCaptureRequests.finish(assistantCaptureRequestId)
        assistantRecognitionJob?.cancel()
        if (assistantRecognizerDelegate.isInitialized()) assistantRecognizer.close()
        assistantCueVoice?.shutdown()
        assistantCueVoice = null
        super.onDestroy()
    }
}



private fun androidx.compose.ui.graphics.Color.isDark(): Boolean {
    return ((red * 0.299f) + (green * 0.587f) + (blue * 0.114f)) < 0.5f
}

internal fun resolveStartRoute(
    restored: String?,
    allowed: Set<String>
): String {
    val isAllowedRoute = restored != null && (
        restored in allowed || restored.startsWith("${Routes.CHAT}/")
    )
    return if (isAllowedRoute && isAssistantChatRoute(restored)) restored else Routes.CHAT_ROOT
}

internal fun isLauncherChatEntry(action: String?, categories: Set<String>): Boolean =
    action == Intent.ACTION_MAIN && Intent.CATEGORY_LAUNCHER in categories

internal fun isAssistantChatRoute(route: String?): Boolean =
    route == Routes.CHAT_ROOT || route == Routes.HOME ||
        route?.startsWith("${Routes.CHAT}/") == true
