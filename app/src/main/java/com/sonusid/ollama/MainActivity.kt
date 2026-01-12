package com.sonusid.ollama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.AppDatabase
import com.sonusid.ollama.db.ChatDatabase
import com.sonusid.ollama.db.repository.BaseUrlRepository
import com.sonusid.ollama.db.repository.ChatRepository
import com.sonusid.ollama.db.repository.ModelPreferenceRepository
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.ui.screens.chats.Chats
import com.sonusid.ollama.ui.screens.home.Home
import com.sonusid.ollama.ui.screens.settings.About
import com.sonusid.ollama.ui.screens.settings.SettingsData
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.ui.screens.settings.Settings
import com.sonusid.ollama.ui.screens.settings.SpriteSettingsScreen
import com.sonusid.ollama.ui.theme.OllamaTheme
import com.sonusid.ollama.viewmodels.OllamaViewModel
import com.sonusid.ollama.viewmodels.OllamaViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: OllamaViewModel

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
            modelPreferenceRepository.getSelectedModel(resolvedBaseUrl)
        }

        // Initialize ViewModel with Factory
        val factory = OllamaViewModelFactory(
            repository,
            modelPreferenceRepository,
            initialSelectedModel,
            baseUrlRepository.activeBaseUrl
        )
        viewModel = ViewModelProvider(this, factory)[OllamaViewModel::class.java]

        val settingsPreferences = SettingsPreferences(applicationContext)

        setContent {
            val settingsData by settingsPreferences.settingsData.collectAsState(initial = SettingsData())
            var initialRoute by rememberSaveable { mutableStateOf<String?>(null) }
            // Initialise navigation
            val navController = rememberNavController()
            LaunchedEffect(Unit) {
                // 起動時1回だけ復元して初期ルートを固定し、NavHost再生成防止を徹底する
                val restored = settingsPreferences.lastRoute.first()
                val allowed = setOf(
                    Routes.HOME,
                    Routes.CHATS,
                    Routes.SETTINGS,
                    Routes.ABOUT,
                    SettingsRoute.SpriteSettings.route
                )
                initialRoute = restored?.takeIf { it in allowed } ?: Routes.CHATS
            }
            OllamaTheme(dynamicColor = settingsData.useDynamicColor) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            // 全体：ScaffoldのinnerPaddingを適用しコンテンツ被りを防止
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        if (initialRoute == null) {
                            // 起動時1回だけ復元が終わるまでNavHostを描かない（NavHost再生成防止）
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            NavHost(
                                navController = navController,
                                startDestination = initialRoute!!
                            ) {
                                composable(Routes.HOME) {
                                    Home(navController, viewModel)
                                }
                                composable(Routes.CHATS) {
                                    Chats(navController, viewModel)
                                }
                                composable(
                                    route = Routes.CHAT_WITH_ID,
                                    arguments = listOf(navArgument(Routes.CHAT_ID_ARG) { type = NavType.IntType })
                                ) { backStackEntry ->
                                    val chatId = backStackEntry.arguments?.getInt(Routes.CHAT_ID_ARG)?.takeIf { it != 0 }
                                    Home(navController, viewModel, chatId)
                                }
                                composable(Routes.SETTINGS) {
                                    Settings(navController)
                                }
                                composable(Routes.ABOUT) {
                                    About(navController)
                                }
                                composable(SettingsRoute.SpriteSettings.route) {
                                    SpriteSettingsScreen(navController)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
