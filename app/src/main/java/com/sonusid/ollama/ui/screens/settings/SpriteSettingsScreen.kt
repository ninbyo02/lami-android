package com.sonusid.ollama.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R

@Composable
fun SpriteSettingsScreen(navController: NavController) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "戻る"
                )
            }
            val imageBitmap: ImageBitmap =
                ImageBitmap.imageResource(LocalContext.current.resources, R.drawable.lami_sprite_3x3_288)
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val screenWidthPx = with(LocalDensity.current) { screenWidthDp.dp.toPx() }
            val displayScale = screenWidthPx / imageBitmap.width
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("Sprite Settings")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("元画像解像度: ${imageBitmap.width} x ${imageBitmap.height} px")
                    Text("表示倍率: ${"%.1f".format(displayScale)}x")
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.lami_sprite_3x3_288),
                        contentDescription = "Sprite Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.FillWidth
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    LaunchedEffect(Unit) {
                        Log.d("SpriteSettingsCHK", "BOTTOM COMPOSED")
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color.Red)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    var selectedNumber by rememberSaveable { mutableStateOf<Int?>(null) }
                    val numbers = (1..9).toList().chunked(3)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        numbers.forEach { rowNumbers ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowNumbers.forEach { number ->
                                    Button(
                                        onClick = { selectedNumber = number }
                                    ) {
                                        Text(
                                            text = number.toString()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = selectedNumber?.let { "選択中: $it" } ?: "未選択",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
