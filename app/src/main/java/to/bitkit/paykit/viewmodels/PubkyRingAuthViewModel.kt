package to.bitkit.paykit.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.bitkit.paykit.PaykitManager
import to.bitkit.paykit.services.AuthMethod
import to.bitkit.paykit.services.CrossDeviceRequest
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.services.PubkySession
import javax.inject.Inject

/**
 * ViewModel for Pubky-ring authentication screen.
 */
@HiltViewModel
class PubkyRingAuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pubkyRingBridge: PubkyRingBridge,
    private val directoryService: DirectoryService,
    private val paykitManager: PaykitManager,
) : ViewModel() {

    val isPubkyRingInstalled: Boolean = pubkyRingBridge.isPubkyRingInstalled(context)
    val recommendedMethod: AuthMethod = pubkyRingBridge.getRecommendedAuthMethod(context)

    private val _crossDeviceRequest = MutableStateFlow<CrossDeviceRequest?>(null)
    val crossDeviceRequest: StateFlow<CrossDeviceRequest?> = _crossDeviceRequest.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun requestSession(onSuccess: (PubkySession) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Use the new secure handoff flow (paykit-connect)
                val setupResult = pubkyRingBridge.requestPaykitSetup(context)
                val session = setupResult.session
                // Configure DirectoryService for authenticated writes to homeserver
                directoryService.configureWithPubkySession(session)
                // Update PaykitManager with owner pubkey for polling and other operations
                paykitManager.setOwnerPubkey(session.pubkey)
                onSuccess(session)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateCrossDeviceRequest() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val request = pubkyRingBridge.generateCrossDeviceRequest()
                _crossDeviceRequest.value = request
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startPollingForSession(onSuccess: (PubkySession) -> Unit) {
        val request = _crossDeviceRequest.value ?: return
        
        viewModelScope.launch {
            _isPolling.value = true
            _errorMessage.value = null
            try {
                val session = pubkyRingBridge.pollForCrossDeviceSession(request.requestId)
                // Configure DirectoryService for authenticated writes to homeserver
                directoryService.configureWithPubkySession(session)
                // Update PaykitManager with owner pubkey for polling and other operations
                paykitManager.setOwnerPubkey(session.pubkey)
                onSuccess(session)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isPolling.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    fun handleAuthUrl(url: String, onSuccess: (PubkySession) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val session = pubkyRingBridge.handleAuthUrl(url)
                // Configure DirectoryService for authenticated writes to homeserver
                directoryService.configureWithPubkySession(session)
                // Update PaykitManager with owner pubkey for polling and other operations
                paykitManager.setOwnerPubkey(session.pubkey)
                onSuccess(session)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    fun processManualInput(
        pubkey: String,
        sessionSecret: String,
        onSuccess: (PubkySession) -> Unit,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val session = pubkyRingBridge.importSession(
                    pubkey = pubkey.trim(),
                    sessionSecret = sessionSecret.trim(),
                )
                onSuccess(session)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelCrossDeviceRequest() {
        _crossDeviceRequest.value = null
        _isPolling.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

