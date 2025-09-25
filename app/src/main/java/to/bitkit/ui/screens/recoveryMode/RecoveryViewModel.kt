package to.bitkit.ui.screens.recoveryMode

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.env.Env
import to.bitkit.repositories.LightningRepo
import to.bitkit.repositories.LogsRepo
import to.bitkit.utils.Logger
import javax.inject.Inject

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logsRepo: LogsRepo,
    private val lightningRepo: LightningRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun onExportLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingLogs = true) }

            logsRepo.zipLogsForSharing().fold(
                onSuccess = { uri ->
                    shareLogsFile(uri)
                    _uiState.update { it.copy(isExportingLogs = false) }
                },
                onFailure = { error ->
                    Logger.error("Failed to export logs", error, context = TAG)
                    _uiState.update {
                        it.copy(
                            isExportingLogs = false,
                        )
                        // TODO TOAST ERROR
                    }
                }
            )
        }
    }

    fun onContactSupport() {
        val supportIntent = createSupportIntent()
        try {
            context.startActivity(supportIntent)
        } catch (e: Exception) {
            Logger.warn("Failed to open email client, trying web fallback", e, context = TAG)
            // Fallback to web contact page
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Env.SYNONYM_CONTACT.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (fallbackError: Exception) {
                Logger.error("Failed to open support links", fallbackError, context = TAG)
                // TODO TOAST ERROR
            }
        }
    }

    fun showWipeConfirmation() {
        _uiState.update { it.copy(showWipeConfirmation = true) }
    }

    fun hideWipeConfirmation() {
        _uiState.update { it.copy(showWipeConfirmation = false) }
    }

    fun onWipeConfirmed() {
        _uiState.update { it.copy(showWipeConfirmation = false, wipeConfirmed = true) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun shareLogsFile(uri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, SUBJECT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(
            shareIntent,
            SUBJECT
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
    }

    private fun createSupportIntent(): Intent {
        val subject = SUBJECT
        val body = buildSupportEmailBody()

        return Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(Env.SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildSupportEmailBody(): String {
        return buildString {
            appendLine("Platform: ${Env.platform}")
            appendLine("Version: ${Env.version}")

            val nodeId = lightningRepo.lightningState.value.nodeId
            appendLine("LDK node ID: $nodeId")

            appendLine()
        }
    }

    private companion object {
        const val TAG = "RecoveryViewModel"
        private const val SUBJECT = "Bitkit Support"
    }
}

data class RecoveryUiState(
    val isExportingLogs: Boolean = false,
    val showWipeConfirmation: Boolean = false,
    val wipeConfirmed: Boolean = false,
    val errorMessage: String? = null,
)
