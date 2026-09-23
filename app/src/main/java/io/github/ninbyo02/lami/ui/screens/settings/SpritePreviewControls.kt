package io.github.ninbyo02.lami.ui.screens.settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.ui.components.rememberNightSpriteColorFilterForDarkTheme
import kotlin.math.min
@Composable
internal fun SpritePreviewBlock(
    imageBitmap: ImageBitmap?, backgroundColor: Color, modifier: Modifier = Modifier,
    onContainerSizeChanged: ((IntSize) -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val minHeight = 180.dp
        val configuration = LocalConfiguration.current
        val aspectRatio = min(
            1f,
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
        ).coerceAtLeast(0.7f)
        val previewShape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .heightIn(min = minHeight)
                .background(backgroundColor, previewShape)
                .clip(previewShape),
            contentAlignment = Alignment.TopCenter
        ) {
            val spriteColorFilter = rememberNightSpriteColorFilterForDarkTheme()
            if (imageBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { newSize -> onContainerSizeChanged?.invoke(newSize) }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Loading...", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Sprite Preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { newSize -> onContainerSizeChanged?.invoke(newSize) },
                    contentScale = ContentScale.Fit,
                    colorFilter = spriteColorFilter
                )
                overlayContent()
            }
        }
    }
}
@Composable
internal fun SpriteSettingsControls(
    buttonHeight: Dp, buttonContentPadding: PaddingValues, buttonShape: Shape,
    onPrev: () -> Unit, onNext: () -> Unit,
    onMoveXNegative: () -> Unit, onMoveXPositive: () -> Unit,
    onMoveYNegative: () -> Unit, onMoveYPositive: () -> Unit,
    onSizeDecrease: () -> Unit, onSizeIncrease: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val buttonModifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
        val navigatorButtonColors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        val defaultControlButtonColors = ButtonDefaults.filledTonalButtonColors()
        val cellModifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onPrev,
                        modifier = buttonModifier.semantics { contentDescription = "Previous" },
                        colors = navigatorButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onNext,
                        modifier = buttonModifier.semantics { contentDescription = "Next" },
                        colors = navigatorButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onMoveXNegative,
                        modifier = buttonModifier,
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("X-")
                    }
                }
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onMoveXPositive,
                        modifier = buttonModifier.testTag("spriteAdjustMoveRight"),
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("X+")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onSizeDecrease,
                        modifier = buttonModifier.testTag("spriteAdjustSizeDecrease"),
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("-")
                    }
                }
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onSizeIncrease,
                        modifier = buttonModifier.testTag("spriteAdjustSizeIncrease"),
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("+")
                    }
                }
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onMoveYNegative,
                        modifier = buttonModifier,
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("Y-")
                    }
                }
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    FilledTonalButton(
                        onClick = onMoveYPositive,
                        modifier = buttonModifier,
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("Y+")
                    }
                }
            }
        }
    }
}
