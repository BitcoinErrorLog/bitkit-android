package to.bitkit.ui.utils

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import kotlin.reflect.KType

fun NavOptionsBuilder.clearBackStack() = popUpTo(id = 0)

object Transitions {
    val slideInHorizontally = slideInHorizontally(animationSpec = tween(), initialOffsetX = { it })
    val slideOutHorizontally = slideOutHorizontally(animationSpec = tween(), targetOffsetX = { it })
    val scaleIn = scaleIn(animationSpec = tween(), initialScale = 0.95f) + fadeIn()
    val scaleOut = scaleOut(animationSpec = tween(), targetScale = 0.95f) + fadeOut()
}

/**
 * Adds the [Composable] to the [NavGraphBuilder] with the default screen transitions.
 */
inline fun <reified T : Any> NavGraphBuilder.composableWithDefaultTransitions(
    typeMap: Map<KType, NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = {
        Transitions.slideInHorizontally
    },
    noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = {
        Transitions.scaleOut
    },
    noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = {
        Transitions.scaleIn
    },
    noinline popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = {
        Transitions.slideOutHorizontally
    },
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(
        typeMap = typeMap,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        content = content,
    )
}
