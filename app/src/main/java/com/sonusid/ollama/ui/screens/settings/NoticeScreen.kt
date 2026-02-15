package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(navController: NavController) {
    val context = LocalContext.current
    var noticeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        noticeText = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.notice).bufferedReader().use { it.readText() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // 上下InsetはScaffold側で一元管理する
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("NOTICE") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.back),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 上：ScaffoldのinnerPaddingをそのまま適用
                .padding(innerPadding)
                // 四辺：長文可読性を保つ最小限の余白
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(text = noticeText)
            }
        }
    }
}
