package to.bitkit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import to.bitkit.R
import to.bitkit.models.Suggestion
import to.bitkit.ui.shared.util.clickableAlpha
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.Colors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun SuggestionCard(
    modifier: Modifier = Modifier,
    gradientColor: Color,
    title: String,
    description: String,
    @DrawableRes icon: Int,
    onClose: (() -> Unit)? = null,
    duration: Duration? = null,
    size: Int = 152,
    captionColor: Color = Colors.White64,
    dismissable: Boolean = true,
    onClick: () -> Unit,
) {
    LaunchedEffect(Unit) {
        duration?.let {
            delay(it)
            onClose?.invoke()
        }
    }

    Box(modifier = modifier) {
        if (!dismissable) {
            GlowEffect(
                size = size,
                color = gradientColor,
                modifier = Modifier.size(size.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (dismissable) {
                        Modifier.gradientBackground(gradientColor)
                    } else {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = getBorderColorForGradient(gradientColor),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .gradientBackground(gradientColor)
                    }
                )
                .clickableAlpha { onClick() }
        ) {
            // Shade effect for dismissable cards (similar to the Shade component in RN)
            if (dismissable) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                ),
                                startY = size * 0.4f,
                                endY = size.toFloat()
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier.weight(1f)
                    )

                    if (duration == null && onClose != null && dismissable) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("SuggestionDismiss")
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_x),
                                contentDescription = null,
                                tint = Colors.White,
                            )
                        }
                    }
                }

                Headline20(
                    text = AnnotatedString(title),
                    color = Colors.White,
                )

                CaptionB(
                    text = description,
                    color = captionColor,
                )
            }
        }
    }
}

@Composable
private fun GlowEffect(
    size: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glowTransition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val (shadowColor, _, radialGradientColor) = getGlowColors(color)

    Box(modifier = modifier) {
        // Outer glow with animated opacity
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(glowAlpha)
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            this.color = shadowColor.toArgb()
                            setShadowLayer(15f, 0f, 0f, shadowColor.toArgb())
                            isAntiAlias = true
                        }

                        val rect = android.graphics.RectF(
                            5f,
                            5f,
                            size.toFloat() - 5f,
                            size.toFloat() - 5f
                        )

                        canvas.nativeCanvas.drawRoundRect(
                            rect,
                            16f,
                            16f,
                            paint
                        )
                    }
                }
        )

        // Static radial gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.4f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(radialGradientColor, color),
                        center = androidx.compose.ui.geometry.Offset(
                            size / 2f,
                            size / 2f
                        ),
                        radius = size / 2f
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        )
    }
}

private fun getGlowColors(color: Color): Triple<Color, Color, Color> {
    return when (color) {
        Colors.Brand24 -> Triple(
            Color(200, 48, 0), // shadowColor
            Color(255, 68, 0), // borderColor
            Color(100, 24, 0)  // radialGradientColor
        )

        else -> Triple(
            Color(130, 65, 175), // shadowColor (default purple)
            Color(185, 92, 232), // borderColor
            Color(65, 32, 80)    // radialGradientColor
        )
    }
}

private fun getBorderColorForGradient(color: Color): Color {
    return when (color) {
        Colors.Brand24 -> Color(255, 68, 0)
        Colors.Purple24 -> Color(185, 92, 232)
        Colors.Blue24 -> Color(92, 185, 232)
        Colors.Green24 -> Color(92, 232, 185)
        Colors.Yellow24 -> Color(232, 185, 92)
        Colors.Red24 -> Color(232, 92, 92)
        else -> Color(185, 92, 232)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    LazyVerticalGrid(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(Suggestion.entries) { item ->
            SuggestionCard(
                gradientColor = item.color,
                title = stringResource(item.title),
                description = stringResource(item.description),
                icon = item.icon,
                onClose = {},
                onClick = {},
                dismissable = item != Suggestion.LIGHTNING_READY,
                duration = 5.seconds.takeIf { item == Suggestion.LIGHTNING_READY }
            )
        }
    }
}
