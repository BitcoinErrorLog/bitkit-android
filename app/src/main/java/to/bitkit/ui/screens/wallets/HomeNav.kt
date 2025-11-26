package to.bitkit.ui.screens.wallets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.Serializable
import to.bitkit.ui.components.DrawerMenu
import to.bitkit.ui.components.Sheet
import to.bitkit.ui.components.TabBar
import to.bitkit.ui.navigateToActivityItem
import to.bitkit.ui.navigateToScanner
import to.bitkit.ui.navigateToTransferSavingsAvailability
import to.bitkit.ui.navigateToTransferSavingsIntro
import to.bitkit.ui.navigateToTransferSpendingAmount
import to.bitkit.ui.navigateToTransferSpendingIntro
import to.bitkit.ui.screens.wallets.activity.AllActivityScreen
import to.bitkit.ui.utils.RequestNotificationPermissions
import to.bitkit.ui.utils.Transitions
import to.bitkit.viewmodels.ActivityListViewModel
import to.bitkit.viewmodels.AppViewModel
import to.bitkit.viewmodels.MainUiState
import to.bitkit.viewmodels.SettingsViewModel
import to.bitkit.viewmodels.WalletViewModel

@Composable
fun HomeNav(
    walletViewModel: WalletViewModel,
    appViewModel: AppViewModel,
    activityListViewModel: ActivityListViewModel,
    settingsViewModel: SettingsViewModel,
    rootNavController: NavController,
    drawerState: DrawerState,
) {
    val uiState: MainUiState by walletViewModel.uiState.collectAsStateWithLifecycle()
    val hasSeenWidgetsIntro: Boolean by settingsViewModel.hasSeenWidgetsIntro.collectAsStateWithLifecycle()
    val hasSeenShopIntro: Boolean by settingsViewModel.hasSeenShopIntro.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()

    RequestNotificationPermissions(
        onPermissionChange = { granted ->
            settingsViewModel.setNotificationPreference(granted)
        }
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val walletNavController = rememberNavController()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            NavContent(
                walletNavController = walletNavController,
                rootNavController = rootNavController,
                mainUiState = uiState,
                drawerState = drawerState,
                settingsViewModel = settingsViewModel,
                appViewModel = appViewModel,
                walletViewModel = walletViewModel,
                activityListViewModel = activityListViewModel,
            )
        }

        TabBar(
            hazeState = hazeState,
            onSendClick = { appViewModel.showSheet(Sheet.Send()) },
            onReceiveClick = { appViewModel.showSheet(Sheet.Receive) },
            onScanClick = { rootNavController.navigateToScanner() },
        )

        DrawerMenu(
            drawerState = drawerState,
            walletNavController = walletNavController,
            rootNavController = rootNavController,
            hasSeenWidgetsIntro = hasSeenWidgetsIntro,
            hasSeenShopIntro = hasSeenShopIntro,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun NavContent(
    walletNavController: NavHostController,
    rootNavController: NavController,
    mainUiState: MainUiState,
    drawerState: DrawerState,
    settingsViewModel: SettingsViewModel,
    appViewModel: AppViewModel,
    walletViewModel: WalletViewModel,
    activityListViewModel: ActivityListViewModel,
) {
    NavHost(
        navController = walletNavController,
        startDestination = HomeRoutes.Home,
    ) {
        composable<HomeRoutes.Home> {
            HomeScreen(
                mainUiState = mainUiState,
                drawerState = drawerState,
                rootNavController = rootNavController,
                walletNavController = walletNavController,
                settingsViewModel = settingsViewModel,
                walletViewModel = walletViewModel,
                appViewModel = appViewModel,
                activityListViewModel = activityListViewModel,
            )
        }
        composable<HomeRoutes.Savings>(
            enterTransition = { Transitions.slideInHorizontally },
            exitTransition = { Transitions.slideOutHorizontally },
        ) {
            val hasSeenSpendingIntro by settingsViewModel.hasSeenSpendingIntro.collectAsStateWithLifecycle()
            val isGeoBlocked by appViewModel.isGeoBlocked.collectAsStateWithLifecycle()
            val onchainActivities by activityListViewModel.onchainActivities.collectAsStateWithLifecycle()

            SavingsWalletScreen(
                isGeoBlocked = isGeoBlocked,
                onchainActivities = onchainActivities.orEmpty(),
                onAllActivityButtonClick = { walletNavController.navigate(HomeRoutes.AllActivity) },
                onActivityItemClick = { rootNavController.navigateToActivityItem(it) },
                onEmptyActivityRowClick = { appViewModel.showSheet(Sheet.Receive) },
                onTransferToSpendingClick = {
                    if (!hasSeenSpendingIntro) {
                        rootNavController.navigateToTransferSpendingIntro()
                    } else {
                        rootNavController.navigateToTransferSpendingAmount()
                    }
                },
                onBackClick = { walletNavController.popBackStack() },
            )
        }
        composable<HomeRoutes.Spending>(
            enterTransition = { Transitions.slideInHorizontally },
            exitTransition = { Transitions.slideOutHorizontally },
        ) {
            val hasSeenSavingsIntro by settingsViewModel.hasSeenSavingsIntro.collectAsStateWithLifecycle()
            val lightningActivities by activityListViewModel.lightningActivities.collectAsStateWithLifecycle()
            SpendingWalletScreen(
                uiState = mainUiState,
                lightningActivities = lightningActivities.orEmpty(),
                onAllActivityButtonClick = { walletNavController.navigate(HomeRoutes.AllActivity) },
                onActivityItemClick = { rootNavController.navigateToActivityItem(it) },
                onEmptyActivityRowClick = { appViewModel.showSheet(Sheet.Receive) },
                onTransferToSavingsClick = {
                    if (!hasSeenSavingsIntro) {
                        rootNavController.navigateToTransferSavingsIntro()
                    } else {
                        rootNavController.navigateToTransferSavingsAvailability()
                    }
                },
                onBackClick = { walletNavController.popBackStack() },
            )
        }
        composable<HomeRoutes.AllActivity>(
            enterTransition = { Transitions.slideInHorizontally },
            exitTransition = { Transitions.slideOutHorizontally },
        ) {
            AllActivityScreen(
                viewModel = activityListViewModel,
                onBack = {
                    activityListViewModel.clearFilters()
                    walletNavController.popBackStack()
                },
                onActivityItemClick = { rootNavController.navigateToActivityItem(it) },
            )
        }
    }
}

object HomeRoutes {
    @Serializable
    data object Home

    @Serializable
    data object Savings

    @Serializable
    data object Spending

    @Serializable
    data object AllActivity
}
