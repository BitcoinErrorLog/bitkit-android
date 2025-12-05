package to.bitkit.ui.shared.util

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.bitkit.ui.theme.Colors

/**
 * Adjusts the alpha of a composable when it is pressed and makes it clickable.
 * When pressed, the alpha is reduced to provide visual feedback.
 * If `onClick` is null, the clickable behavior is disabled.
 *
 * Analogue of `TouchableOpacity` in React Native.
 */
fun Modifier.clickableAlpha(
    pressedAlpha: Float = 0.7f,
    onClick: (() -> Unit)?,
): Modifier = if (onClick != null) {
    this.then(ClickableAlphaElement(pressedAlpha, onClick))
} else {
    this
}

private data class ClickableAlphaElement(
    val pressedAlpha: Float,
    val onClick: () -> Unit,
) : ModifierNodeElement<ClickableAlphaNode>() {
    override fun create(): ClickableAlphaNode = ClickableAlphaNode(pressedAlpha, onClick)

    override fun update(node: ClickableAlphaNode) {
        node.pressedAlpha = pressedAlpha
        node.onClick = onClick
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "clickableAlpha"
        properties["pressedAlpha"] = pressedAlpha
        properties["onClick"] = onClick
    }
}

private class ClickableAlphaNode(
    var pressedAlpha: Float,
    var onClick: () -> Unit,
) : DelegatingNode(), LayoutModifierNode, SemanticsModifierNode {

    private val animatable = Animatable(1f)

    init {
        delegate(SuspendingPointerInputModifierNode {
            detectTapGestures(
                onPress = {
                    coroutineScope.launch { animatable.animateTo(pressedAlpha) }
                    val released = tryAwaitRelease()
                    if (!released) {
                        coroutineScope.launch { animatable.animateTo(1f) }
                    }
                },
                onTap = {
                    onClick()
                    coroutineScope.launch {
                        animatable.animateTo(pressedAlpha)
                        animatable.animateTo(1f)
                    }
                }
            )
        })
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                this.alpha = animatable.value
            }
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        role = Role.Button
        onClick(action = { onClick(); true })
    }
}

fun Modifier.gradientBackground(
    startColor: Color = Colors.White08,
    endColor: Color = Color.White.copy(alpha = 0.012f),
): Modifier {
    return this
        .background(Color.Black)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(startColor, endColor)
            )
        )
}

fun Modifier.blockPointerInputPassthrough(): Modifier {
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent()
            }
        }
    }
}

@Composable
fun Modifier.screen(
    noBackground: Boolean = false,
    insets: WindowInsets? = WindowInsets.systemBars,
): Modifier = this
    .fillMaxSize()
    .then(if (noBackground) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
    .then(if (insets == null) Modifier else Modifier.windowInsetsPadding(insets))

fun Modifier.primaryButtonStyle(
    isEnabled: Boolean,
    shape: Shape,
    primaryColor: Color? = null,
): Modifier {
    return this
        // Step 1: Add shadow (only when enabled)
        .then(
            if (isEnabled) {
                Modifier.shadow(
                    elevation = 16.dp,
                    shape = shape,
                    clip = false // Don't clip content, just add shadow
                )
            } else {
                Modifier
            }
        )
        // Step 2: Clip to shape first
        .clip(shape)
        // Step 3: Apply gradient background with border overlay
        .then(
            if (isEnabled) {
                Modifier.drawWithContent {
                    // Draw the main gradient background filling entire button
                    val mainBrush = Brush.verticalGradient(
                        colors = listOf(primaryColor ?: Colors.Gray5, Colors.Gray6),
                        startY = 0f,
                        endY = size.height
                    )
                    drawRect(
                        brush = mainBrush,
                        topLeft = Offset.Zero,
                        size = size
                    )

                    // Draw top border highlight (2dp gradient fade)
                    val borderHeight = 2.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Colors.White16,
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = borderHeight
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, borderHeight)
                    )

                    // Draw the actual button content on top
                    drawContent()
                }
            } else {
                Modifier.background(Colors.White06)
            }
        )
}
