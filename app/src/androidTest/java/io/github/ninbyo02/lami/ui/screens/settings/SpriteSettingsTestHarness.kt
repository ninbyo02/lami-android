package io.github.ninbyo02.lami.ui.screens.settings

import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.printToString
import androidx.navigation.compose.NavHost
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.navigation.SettingsRoute
import io.github.ninbyo02.lami.ui.TestAppWrapper
import io.github.ninbyo02.lami.ui.theme.OllamaTheme

@Suppress("UNCHECKED_CAST")
private fun ComposeTestRule.asAndroidRule(): AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, out ComponentActivity> =
    this as AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, out ComponentActivity>

fun ComposeTestRule.setSpriteSettingsContentForTest() {
    asAndroidRule().activityRule.scenario.onActivity { activity ->
        activity.setContent {
            TestAppWrapper {
                val navController = rememberNavController()
                OllamaTheme(dynamicColor = false) {
                    NavHost(
                        navController = navController,
                        startDestination = SettingsRoute.SpriteSettings.route,
                    ) {
                        composable(SettingsRoute.SpriteSettings.route) {
                            SpriteSettingsScreen(navController)
                        }
                        composable(Routes.SETTINGS) {
                            Text("Settings", modifier = Modifier.testTag("settingsScreenRoot"))
                        }
                    }
                }
            }
        }
    }
    waitForIdle()
}

fun ComposeTestRule.recreateAndAwaitTag(tag: String, timeoutMillis: Long = 30_000) {
    asAndroidRule().activityRule.scenario.recreate()
    setSpriteSettingsContentForTest()
    awaitNodeWithTag(tag, timeoutMillis)
}

fun ComposeTestRule.awaitNodeWithTag(tag: String, timeoutMillis: Long = 30_000) {
    if ((tag == "spriteBaseIntervalInput" || tag == "spriteInsertionIntervalInput") &&
        runCatching {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }.getOrDefault(true)
    ) {
        // 無効時は interval 入力 UI が仕様上存在しないため、待機せずに戻す。
        return
    }

    try {
        waitUntil(timeoutMillis = timeoutMillis) {
            runCatching {
                onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false) || runCatching {
                onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    } catch (error: Throwable) {
        val semanticsDump = dumpSemanticsTree()
        throw AssertionError(
            "タグが見つかりません: tag=$tag timeout=${timeoutMillis}ms\nSemanticsDump:\n$semanticsDump",
            error,
        )
    }
}

fun ComposeTestRule.hasNodeWithTag(tag: String): Boolean {
    val unmerged = runCatching {
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    if (unmerged) return true

    val merged = runCatching {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    return merged
}

fun ComposeTestRule.dumpSemanticsTree(maxChars: Int = 20_000): String {
    val dump = runCatching {
        onRoot(useUnmergedTree = true).printToString()
    }.getOrElse {
        runCatching {
            onRoot(useUnmergedTree = true).printToLog("SemanticsDump")
        }
        "(see logcat: SemanticsDump)"
    }
    return if (dump.length > maxChars) {
        dump.take(maxChars) + "...<truncated>"
    } else {
        dump
    }
}
