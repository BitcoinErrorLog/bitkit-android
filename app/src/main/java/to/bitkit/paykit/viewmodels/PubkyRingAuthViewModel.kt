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
import to.bitkit.paykit.services.AuthMethod
import to.bitkit.paykit.services.CrossDeviceRequest
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
                val session = pubkyRingBridge.requestSession(context)
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
                onSuccess(session)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isPolling.value = false
            }
        }
    }

    fun handleAuthUrl(url: String, onSuccess: (PubkySession) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val session = pubkyRingBridge.handleAuthUrl(url)
                onSuccess(session)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

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

