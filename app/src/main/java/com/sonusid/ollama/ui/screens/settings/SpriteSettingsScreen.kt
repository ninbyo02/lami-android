package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R

data class BoxRect(val x: Int, val y: Int, val w: Int, val h: Int)

private fun boxRectsSaver() = listSaver<List<BoxRect>, Int>(
    save = { list -> list.flatMap { rect -> listOf(rect.x, rect.y, rect.w, rect.h) } },
    restore = { flat ->
        flat.chunked(4).map { (x, y, w, h) ->
            BoxRect(x = x, y = y, w = w, h = h)
        }
    }
)

private fun defaultBoxRects(): List<BoxRect> =
    List(9) { index ->
        val column = index % 3
        val row = index / 3
        BoxRect(
            x = column * 96,
            y = row * 96,
            w = 96,
            h = 96
        )
    }

@Composable
fun SpriteSettingsScreen(navController: NavController) {
    val imageBitmap: ImageBitmap =
        ImageBitmap.imageResource(LocalContext.current.resources, R.drawable.lami_sprite_3x3_288)

    var selectedNumber by rememberSaveable { mutableStateOf(1) }
    var boxRects by rememberSaveable(stateSaver = boxRectsSaver()) { mutableStateOf(defaultBoxRects()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var displayScale by remember { mutableStateOf(1f) }

    val selectedRect = boxRects.getOrNull(selectedNumber - 1)

    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("Sprite Settings")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("元画像解像度: ${imageBitmap.width} x ${imageBitmap.height} px")
                    Text("表示倍率: ${"%.2f".format(displayScale)}x")
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.lami_sprite_3x3_288),
                            contentDescription = "Sprite Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { size ->
                                    containerSize = size
                                    if (imageBitmap.width != 0) {
                                        displayScale = size.width / imageBitmap.width.toFloat()
                                    }
                                },
                            contentScale = ContentScale.Fit
                        )
                        if (selectedRect != null && containerSize.width > 0 && containerSize.height > 0) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val scaleX = size.width / imageBitmap.width
                                val scaleY = size.height / imageBitmap.height
                                drawRect(
                                    color = Color.Red,
                                    topLeft = Offset(
                                        x = selectedRect.x * scaleX,
                                        y = selectedRect.y * scaleY
                                    ),
                                    size = Size(
                                        width = selectedRect.w * scaleX,
                                        height = selectedRect.h * scaleY
                                    ),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

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
                        text = "選択中: $selectedNumber",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
