package to.bitkit.ui.screens.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import to.bitkit.BuildConfig
import to.bitkit.data.SettingsStore
import to.bitkit.data.dto.TransferType
import to.bitkit.di.BgDispatcher
import to.bitkit.models.Suggestion
import to.bitkit.models.WidgetType
import to.bitkit.models.toSuggestionOrNull
import to.bitkit.models.widget.ArticleModel
import to.bitkit.models.widget.toArticleModel
import to.bitkit.models.widget.toBlockModel
import to.bitkit.repositories.ActivityRepo
import to.bitkit.repositories.CurrencyRepo
import to.bitkit.repositories.WalletRepo
import to.bitkit.repositories.WidgetsRepo
import to.bitkit.services.AppUpdaterService
import to.bitkit.ui.screens.widgets.blocks.toWeatherModel
import to.bitkit.utils.Logger
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val walletRepo: WalletRepo,
    private val widgetsRepo: WidgetsRepo,
    private val settingsStore: SettingsStore,
    private val currencyRepo: CurrencyRepo,
    private val activityRepo: ActivityRepo,
    private val appUpdaterService: AppUpdaterService,
    @BgDispatcher private val bgDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private var timedSheetsScope: CoroutineScope? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _currentArticle = MutableStateFlow<ArticleModel?>(null)
    private val _currentFact = MutableStateFlow<String?>(null)

    private val _homeEffect = MutableSharedFlow<HomeEffects>(extraBufferCapacity = 1)
    val homeEffect = _homeEffect.asSharedFlow()
    private fun setHomeEffect(effect: HomeEffects) = viewModelScope.launch { _homeEffect.emit(effect) }

    init {
        setupStateObservation()
        setupArticleRotation()
        setupFactRotation()
    }

    private fun setupStateObservation() {
        viewModelScope.launch {
            combine(
                createSuggestionsFlow(),
                settingsStore.data,
                widgetsRepo.widgetsDataFlow,
                _currentArticle,
                _currentFact,
            ) { suggestions, settings, widgetsData, currentArticle, currentFact ->
                _uiState.value.copy(
                    suggestions = suggestions,
                    showWidgets = settings.showWidgets,
                    showWidgetTitles = settings.showWidgetTitles,
                    widgetsWithPosition = widgetsData.widgets,
                    headlinePreferences = widgetsData.headlinePreferences,
                    factsPreferences = widgetsData.factsPreferences,
                    blocksPreferences = widgetsData.blocksPreferences,
                    weatherPreferences = widgetsData.weatherPreferences,
                    pricePreferences = widgetsData.pricePreferences,
                    currentArticle = currentArticle,
                    currentFact = currentFact,
                    currentBlock = widgetsData.block?.toBlockModel(),
                    currentWeather = widgetsData.weather?.toWeatherModel(),
                    currentPrice = widgetsData.price,
                )
            }.collect { newState ->
                _uiState.update { newState }
            }
        }
    }

    private fun setupArticleRotation() {
        viewModelScope.launch {
            combine(
                widgetsRepo.articlesFlow.map { articles -> articles.map { it.toArticleModel() } },
                settingsStore.data.map { it.showWidgets }
            ) { articlesList, showWidgets ->
                Pair(articlesList, showWidgets)
            }.collect { (articlesList, showWidgets) ->
                if (showWidgets && articlesList.isNotEmpty()) {
                    startArticleRotation(articlesList)
                } else {
                    _currentArticle.value = null
                }
            }
        }
    }

    private fun setupFactRotation() {
        viewModelScope.launch {
            combine(
                widgetsRepo.factsFlow,
                settingsStore.data.map { it.showWidgets }
            ) { factList, showWidgets ->
                Pair(factList, showWidgets)
            }.collect { (factList, showWidgets) ->
                if (showWidgets && factList.isNotEmpty()) {
                    startFactsRotation(factList = factList)
                } else {
                    _currentFact.value = null
                }
            }
        }
    }

    private suspend fun startArticleRotation(articlesList: List<ArticleModel>) {
        while (_uiState.value.showWidgets && articlesList.isNotEmpty()) {
            _currentArticle.value = articlesList.randomOrNull()
            delay(30.seconds)
        }
        _currentArticle.value = null
    }

    private suspend fun startFactsRotation(factList: List<String>) {
        while (_uiState.value.showWidgets && factList.isNotEmpty()) {
            _currentFact.value = factList.randomOrNull()
            delay(20.seconds)
        }
        _currentFact.value = null
    }

    fun checkTimedSheets() {
        timedSheetsScope = CoroutineScope(bgDispatcher + SupervisorJob())
        timedSheetsScope?.launch {
            if (_uiState.value.timedSheet != null) return@launch

            delay(CHECK_DELAY_MILLIS)

            val sheetToDisplay = TimedSheets.entries
                .sortedByDescending { it.priority }
                .firstOrNull { shouldDisplaySheet(it) }

            sheetToDisplay?.let { sheet ->
                Logger.debug("Displaying timedSheet $sheet")
                _uiState.update { it.copy(timedSheet = sheet) }
            }
        }
    }

    private suspend fun shouldDisplaySheet(sheet: TimedSheets): Boolean = when (sheet) {
        TimedSheets.APP_UPDATE -> checkAppUpdate()
        TimedSheets.BACKUP -> checkBackupSheet()
        TimedSheets.NOTIFICATIONS -> checkNotificationSheet()
        TimedSheets.QUICK_PAY -> checkQuickPaySheet()
        TimedSheets.HIGH_BALANCE -> checkHighBalance()
    }

    fun onLeftHome() {
        timedSheetsScope?.cancel()
        timedSheetsScope = null
    }

    fun dismissTimedSheet() {
        _uiState.update { it.copy(timedSheet = null) }
    }

    private suspend fun checkQuickPaySheet(): Boolean {
        val settings = settingsStore.data.first()
        if (settings.quickPayIntroSeen) return false
        return walletRepo.balanceState.value.totalLightningSats > 0U
    }

    fun checkQuickPayAsSeen() {
        viewModelScope.launch { settingsStore.update { it.copy(quickPayIntroSeen = true) } }
    }

    private suspend fun checkNotificationSheet(): Boolean {
        val settings = settingsStore.data.first()
        if (settings.notificationsGranted) return false

        return checkTimeoutAndUpdate(
            lastIgnoredMillis = settings.notificationsIgnoredMillis,
            intervalMillis = ONE_WEEK_ASK_INTERVAL_MILLIS
        ) { currentTime ->
            settingsStore.update { it.copy(notificationsIgnoredMillis = currentTime) }
        }
    }

    private suspend fun checkBackupSheet(): Boolean {
        val settings = settingsStore.data.first()
        if (settings.backupVerified) return false

        val hasBalance = walletRepo.balanceState.value.totalSats > 0U
        if (!hasBalance) return false

        return checkTimeoutAndUpdate(
            lastIgnoredMillis = settings.backupWarningIgnoredMillis,
            intervalMillis = ONE_DAY_ASK_INTERVAL_MILLIS
        ) { currentTime ->
            settingsStore.update { it.copy(backupWarningIgnoredMillis = currentTime) }
        }
    }

    private suspend fun checkAppUpdate(): Boolean = withContext(bgDispatcher) {
        try {
            val androidReleaseInfo = appUpdaterService.getReleaseInfo().platforms.android
            val currentBuildNumber = BuildConfig.VERSION_CODE

            if (androidReleaseInfo.buildNumber <= currentBuildNumber) return@withContext false

            if (androidReleaseInfo.isCritical) {
                setHomeEffect(HomeEffects.NavigateCriticalUpdate)
                return@withContext false
            }

            return@withContext true
        } catch (e: Exception) {
            Logger.warn("Failure fetching new releases", e = e)
            return@withContext false
        }
    }

    private suspend fun checkHighBalance(): Boolean {
        val settings = settingsStore.data.first()

        val totalOnChainSats = walletRepo.balanceState.value.totalSats
        val balanceUsd = satsToUsd(totalOnChainSats) ?: return false
        val thresholdReached = balanceUsd > BigDecimal(BALANCE_THRESHOLD_USD)

        if (!thresholdReached) {
            settingsStore.update { it.copy(balanceWarningTimes = 0) }
            return false
        }

        val belowMaxWarnings = settings.balanceWarningTimes < MAX_WARNINGS

        return checkTimeoutAndUpdate(
            lastIgnoredMillis = settings.balanceWarningIgnoredMillis,
            intervalMillis = ONE_DAY_ASK_INTERVAL_MILLIS,
            additionalCondition = belowMaxWarnings
        ) { currentTime ->
            settingsStore.update {
                it.copy(
                    balanceWarningTimes = it.balanceWarningTimes + 1,
                    balanceWarningIgnoredMillis = currentTime
                )
            }
        }
    }

    private suspend inline fun checkTimeoutAndUpdate(
        lastIgnoredMillis: Long,
        intervalMillis: Long,
        additionalCondition: Boolean = true,
        crossinline updateSettings: suspend (Long) -> Unit,
    ): Boolean {
        if (!additionalCondition) return false

        val currentTime = Clock.System.now().toEpochMilliseconds()
        val isTimeOutOver = lastIgnoredMillis == 0L ||
            (currentTime - lastIgnoredMillis > intervalMillis)

        if (isTimeOutOver) {
            updateSettings(currentTime)
        }

        return isTimeOutOver
    }

    private fun satsToUsd(sats: ULong): BigDecimal? {
        val converted = currencyRepo.convertSatsToFiat(sats = sats.toLong(), currency = "USD").getOrNull()
        return converted?.value
    }

    fun dismissEmptyState() {
        viewModelScope.launch {
            settingsStore.update { it.copy(showEmptyBalanceView = false) }
        }
    }

    fun removeSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            settingsStore.addDismissedSuggestion(suggestion)
        }
    }

    fun refreshWidgets() {
        viewModelScope.launch {
            widgetsRepo.refreshEnabledWidgets()
        }
    }

    fun moveWidget(fromIndex: Int, toIndex: Int) {
        val currentWidgets = _uiState.value.widgetsWithPosition.toMutableList()
        if (fromIndex in currentWidgets.indices && toIndex in currentWidgets.indices) {
            val item = currentWidgets.removeAt(fromIndex)
            currentWidgets.add(toIndex, item)

            val updatedWidgets = currentWidgets.mapIndexed { index, widget ->
                widget.copy(position = index)
            }

            _uiState.update { it.copy(widgetsWithPosition = updatedWidgets) }
        }
    }

    fun onClickEditWidgetList() {
        if (_uiState.value.isEditingWidgets) {
            viewModelScope.launch {
                val widgets = _uiState.value.widgetsWithPosition
                widgetsRepo.updateWidgets(widgets)
                disableEditMode()
            }
        } else {
            enableEditMode()
        }
    }

    fun deleteWidget(widgetType: WidgetType) {
        viewModelScope.launch {
            widgetsRepo.deleteWidget(widgetType)
            dismissAlertDeleteWidget()
        }
    }

    fun displayAlertDeleteWidget(widgetType: WidgetType) {
        viewModelScope.launch {
            _uiState.update { it.copy(deleteWidgetAlert = widgetType) }
        }
    }

    fun dismissAlertDeleteWidget() {
        viewModelScope.launch {
            _uiState.update { it.copy(deleteWidgetAlert = null) }
        }
    }

    private fun enableEditMode() {
        _uiState.update { it.copy(isEditingWidgets = true) }
    }

    private fun disableEditMode() {
        _uiState.update { it.copy(isEditingWidgets = false) }
    }

    private fun createSuggestionsFlow() = combine(
        walletRepo.balanceState,
        settingsStore.data,
        activityRepo.inProgressTransfers
    ) { balanceState, settings, transfers ->
        val baseSuggestions = when {
            balanceState.totalLightningSats > 0uL -> {
                listOfNotNull(
                    Suggestion.BACK_UP.takeIf { !settings.backupVerified },
                    Suggestion.LIGHTNING_READY.takeIf {
                        Suggestion.LIGHTNING_SETTING_UP in _uiState.value.suggestions &&
                            transfers.all { it.type != TransferType.TO_SPENDING }
                    },
                    Suggestion.LIGHTNING_SETTING_UP.takeIf { transfers.any { it.type == TransferType.TO_SPENDING } },
                    Suggestion.TRANSFER_CLOSING_CHANNEL.takeIf { transfers.any { it.type == TransferType.COOP_CLOSE } },
                    Suggestion.TRANSFER_PENDING.takeIf { transfers.any { it.type == TransferType.FORCE_CLOSE } },
                    Suggestion.SECURE.takeIf { !settings.isPinEnabled },
                    Suggestion.BUY,
                    Suggestion.SUPPORT,
                    Suggestion.INVITE,
                    Suggestion.QUICK_PAY,
                    Suggestion.SHOP,
                    Suggestion.PROFILE,
                )
            }

            balanceState.totalOnchainSats > 0uL -> {
                listOfNotNull(
                    Suggestion.BACK_UP.takeIf { !settings.backupVerified },
                    Suggestion.LIGHTNING.takeIf {
                        transfers.all { it.type != TransferType.TO_SPENDING }
                    } ?: Suggestion.LIGHTNING_SETTING_UP,
                    Suggestion.TRANSFER_CLOSING_CHANNEL.takeIf { transfers.any { it.type == TransferType.COOP_CLOSE } },
                    Suggestion.TRANSFER_PENDING.takeIf { transfers.any { it.type == TransferType.FORCE_CLOSE } },
                    Suggestion.SECURE.takeIf { !settings.isPinEnabled },
                    Suggestion.BUY,
                    Suggestion.SUPPORT,
                    Suggestion.INVITE,
                    Suggestion.SHOP,
                    Suggestion.PROFILE,
                )
            }

            else -> {
                listOfNotNull(
                    Suggestion.BUY,
                    Suggestion.LIGHTNING.takeIf {
                        transfers.all { it.type != TransferType.TO_SPENDING }
                    } ?: Suggestion.LIGHTNING_SETTING_UP,
                    Suggestion.BACK_UP.takeIf { !settings.backupVerified },
                    Suggestion.SECURE.takeIf { !settings.isPinEnabled },
                    Suggestion.SUPPORT,
                    Suggestion.INVITE,
                    Suggestion.PROFILE,
                )
            }
        }
        val dismissedList = settings.dismissedSuggestions.mapNotNull { it.toSuggestionOrNull() }
        baseSuggestions.filterNot { it in dismissedList }
    }

    companion object {
        /**How high the balance must be to show this warning to the user (in USD)*/
        private const val BALANCE_THRESHOLD_USD = 500L
        private const val MAX_WARNINGS = 3

        /** how long this prompt will be hidden if user taps Later*/
        private const val ONE_DAY_ASK_INTERVAL_MILLIS = 1000 * 60 * 60 * 24L

        /** how long this prompt will be hidden if user taps Later*/
        private const val ONE_WEEK_ASK_INTERVAL_MILLIS = ONE_DAY_ASK_INTERVAL_MILLIS * 7L

        /**How long user needs to stay on the home screen before he see this prompt*/
        private const val CHECK_DELAY_MILLIS = 2000L
    }
}

sealed interface HomeEffects {
    data object NavigateCriticalUpdate : HomeEffects
}
