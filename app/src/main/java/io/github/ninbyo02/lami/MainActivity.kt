package io.github.ninbyo02.lami

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ninbyo02.lami.api.RetrofitClient
import io.github.ninbyo02.lami.db.AppDatabase
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.repository.BaseUrlRepository
import io.github.ninbyo02.lami.db.repository.ChatRepository
import io.github.ninbyo02.lami.db.repository.ModelPreferenceRepository
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.navigation.SettingsRoute
import io.github.ninbyo02.lami.ui.screens.chats.Chats
import io.github.ninbyo02.lami.ui.screens.home.Home
import io.github.ninbyo02.lami.ui.screens.home.LocalInferenceEngineHolder
import io.github.ninbyo02.lami.ui.screens.settings.About
import io.github.ninbyo02.lami.ui.screens.settings.SettingsData
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.ui.screens.settings.Settings
import io.github.ninbyo02.lami.ui.screens.settings.NoticeScreen
import io.github.ninbyo02.lami.ui.screens.settings.LocalBaseModelScreen
import io.github.ninbyo02.lami.ui.screens.settings.SpriteSettingsScreen
import io.github.ninbyo02.lami.ui.screens.spriteeditor.SpriteEditorScreen
import io.github.ninbyo02.lami.ui.common.LocalAppSnackbarHostState
import io.github.ninbyo02.lami.ui.common.ProjectSnackbar
import io.github.ninbyo02.lami.ui.common.TopAppBarHeight
import io.github.ninbyo02.lami.ui.theme.OllamaTheme
import io.github.ninbyo02.lami.util.RuntimeFlags
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import io.github.ninbyo02.lami.viewmodels.OllamaViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: OllamaViewModel
    private val heldEngineLifecycleBridge by lazy {
        HeldEngineLifecycleBridge(holder = LocalInferenceEngineHolder.getInstance(applicationContext))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val settingsData by settingsPreferences.settingsData.collectAsState(initial = SettingsData())
            // Initialise navigation
            val navController = rememberNavController()
            LaunchedEffect(Unit) {
                // UIテスト時は復元ナビゲーションを無効化して常にCHAT_ROOTから開始する
                if (RuntimeFlags.isUiTestRuntime()) return@LaunchedEffect
                // 回転などでActivity再生成時にlastRouteがSettingsだと意図せずSettingsへ遷移するため、
                // savedInstanceStateがある場合は復元ナビゲーションをスキップして現在画面の復元を優先する
                if (!shouldRestoreLastRoute) return@LaunchedEffect

                val restored = settingsPreferences.lastRoute.first()
                val allowedRoutes = setOf(
                    Routes.HOME,
                    Routes.CHATS,
                    Routes.CHAT_ROOT,
                    Routes.SETTINGS,
                    Routes.ABOUT,
                    Routes.NOTICE,
                    SettingsRoute.LocalBaseModel.route,
                    SettingsRoute.SpriteSettings.route,
                    SettingsRoute.SpriteEditor.route
                )
                val targetRoute = resolveStartRoute(restored = restored, allowed = allowedRoutes)
                // NavHost生成後に必要な場合のみ遷移して復元する
                if (targetRoute != Routes.CHAT_ROOT) {
                    navController.navigate(targetRoute) {
                        launchSingleTop = true
                        // ベースを固定して復元時のBackStack重複を防ぐ
                        popUpTo(Routes.CHAT_ROOT) { inclusive = false }
                    }
                }
            }
            OllamaTheme(dynamicColor = settingsData.useDynamicColor) {
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
                                            Home(navController, viewModel)
                                        }
                                        composable(Routes.CHATS) {
                                            Chats(navController, viewModel)
                                        }
                                        composable(route = Routes.CHAT_ROOT) {
                                            Home(navController, viewModel, chatId = null)
                                        }
                                        composable(
                                            route = Routes.CHAT_WITH_ID_ROUTE,
                                            arguments = listOf(navArgument(Routes.CHAT_ID_ARG_ROUTE) { type = NavType.IntType })
                                        ) { backStackEntry ->
                                            val chatId = backStackEntry.arguments?.getInt(Routes.CHAT_ID_ARG_ROUTE)
                                            Home(navController, viewModel, chatId)
                                        }
                                        composable(Routes.SETTINGS) { settingsBackStackEntry ->
                                            Settings(
                                                navgationController = navController,
                                                settingsBackStackEntry = settingsBackStackEntry,
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

    override fun onStart() {
        super.onStart()
        heldEngineLifecycleBridge.onStart(scope = lifecycleScope)
    }

    override fun onStop() {
        heldEngineLifecycleBridge.onStop(scope = lifecycleScope)
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        heldEngineLifecycleBridge.onTrimMemory(scope = lifecycleScope, level = level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        heldEngineLifecycleBridge.onLowMemory(scope = lifecycleScope)
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
    return if (isAllowedRoute) restored else Routes.CHAT_ROOT
}
