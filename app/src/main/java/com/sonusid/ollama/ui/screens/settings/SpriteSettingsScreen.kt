package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
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
        }
    }
}
