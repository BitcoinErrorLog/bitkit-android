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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import kotlin.reflect.KType

object Transitions {
    val slideInHorizontally = slideInHorizontally(animationSpec = tween(), initialOffsetX = { it })
    val slideOutHorizontally = slideOutHorizontally(animationSpec = tween(), targetOffsetX = { it })
    val scaleIn = scaleIn(animationSpec = tween(), initialScale = 0.95f) + fadeIn()
    val scaleOut = scaleOut(animationSpec = tween(), targetScale = 0.95f) + fadeOut()
    val slideInVertically = slideInVertically(animationSpec = tween(), initialOffsetY = { it })
    val slideOutVertically = slideOutVertically(animationSpec = tween(), targetOffsetY = { it })
}

/**
 * Adds the [Composable] to the [NavGraphBuilder] with the default screen transitions.
 */
@Suppress("LongParameterList")
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

/**
 * Construct a nested [NavGraph] with the default screen transitions.
 */
@Suppress("LongParameterList")
inline fun <reified T : Any> NavGraphBuilder.navigationWithDefaultTransitions(
    startDestination: Any,
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
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
    noinline builder: NavGraphBuilder.() -> Unit,
) {
    navigation<T>(
        startDestination = startDestination,
        typeMap = typeMap,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        builder = builder,
    )
}
