package to.bitkit.ui.shared.animations

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * Animation specifications for tab transitions with iOS-like smooth feel.
 *
 * - Tween animation: 450ms duration with FastOutSlowInEasing
 * - Direction-aware horizontal sliding
 * - Smooth, natural feel matching iOS PageTabViewStyle
 */
object TabTransitionAnimations {

    /**
     * Tween animation for smooth tab content transitions.
     * - Duration: 300ms (matches iOS native tab transitions)
     * - Easing: FastOutSlowInEasing (iOS-like deceleration curve)
     */
    private val tabTweenSpec = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    /**
     * Tween animation for IntOffset (horizontal sliding).
     */
    private val tabTweenSpecIntOffset = tween<IntOffset>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    /**
     * Direction-aware tab content transition.
     *
     * @param isForward true if moving to next tab (swipe left), false if previous (swipe right)
     */
    fun tabContentTransition(isForward: Boolean): ContentTransform {
        val slideInOffset = if (isForward) {
            { fullWidth: Int -> fullWidth } // Slide in from right
        } else {
            { fullWidth: Int -> -fullWidth } // Slide in from left
        }

        val slideOutOffset = if (isForward) {
            { fullWidth: Int -> -fullWidth / 5 } // Slide out to left (20% parallax)
        } else {
            { fullWidth: Int -> fullWidth / 5 } // Slide out to right (20% parallax)
        }

        return slideInHorizontally(
            initialOffsetX = slideInOffset,
            animationSpec = tabTweenSpecIntOffset
        ) + fadeIn(
            animationSpec = tabTweenSpec
        ) togetherWith slideOutHorizontally(
            targetOffsetX = slideOutOffset,
            animationSpec = tabTweenSpecIntOffset
        ) + fadeOut(
            animationSpec = tabTweenSpec
        )
    }
}
