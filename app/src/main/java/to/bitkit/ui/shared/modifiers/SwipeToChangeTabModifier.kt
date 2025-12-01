package to.bitkit.ui.shared.modifiers

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * Enables tab navigation via horizontal swipe gestures.
 *
 * Detects horizontal swipe velocity and navigates to adjacent tabs when the velocity
 * exceeds the specified threshold. Provides boundary protection to prevent navigation
 * beyond the first and last tabs.
 *
 * @param currentTabIndex The currently selected tab index (0-based)
 * @param tabCount Total number of tabs available for navigation
 * @param onTabChange Callback invoked when user swipes to change tabs, receives the new tab index
 * @param threshold Velocity threshold in pixels per second (default: 1500f)
 *                  Swipe velocity must exceed this value to trigger navigation
 */
fun Modifier.swipeToChangeTab(
    currentTabIndex: Int,
    tabCount: Int,
    onTabChange: (Int) -> Unit,
    threshold: Float = DEFAULT_SWIPE_THRESHOLD,
): Modifier = composed {
    val velocityTracker = remember { VelocityTracker() }

    pointerInput(currentTabIndex) {
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, _ ->
                velocityTracker.addPosition(change.uptimeMillis, change.position)
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity().x
                when {
                    velocity >= threshold && currentTabIndex > 0 ->
                        onTabChange(currentTabIndex - 1)

                    velocity <= -threshold && currentTabIndex < tabCount - 1 ->
                        onTabChange(currentTabIndex + 1)
                }
                velocityTracker.resetTracking()
            },
            onDragCancel = {
                velocityTracker.resetTracking()
            },
        )
    }
}

private const val DEFAULT_SWIPE_THRESHOLD = 1500f
