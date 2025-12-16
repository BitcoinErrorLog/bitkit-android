package to.bitkit.ui.paykit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.services.PubkySession
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionManagementScreen(
    viewModel: SessionManagementViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showExportSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<PubkySession?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSessions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Management") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Backup") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showImportSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Sessions") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearAllSessions()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Colors.Gray4,
                ),
            )
        },
        containerColor = Colors.Gray4,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Device Info Section
            DeviceInfoSection(
                deviceId = uiState.deviceId,
                currentEpoch = uiState.currentEpoch,
                cachedKeyCount = uiState.cachedKeyCount,
            )

            // Active Sessions Section
            SessionsSection(
                sessions = uiState.sessions,
                onRemoveSession = { session ->
                    sessionToDelete = session
                    showDeleteConfirmation = true
                },
            )

            // Backup Section
            BackupSection(
                onExport = { showExportSheet = true },
                onImport = { showImportSheet = true },
            )
        }
    }

    // Export Sheet
    if (showExportSheet) {
        ExportBackupSheet(
            backupJSON = viewModel.exportBackupJSON(),
            onCopy = { json ->
                clipboardManager.setText(AnnotatedString(json))
            },
            onDismiss = { showExportSheet = false },
        )
    }

    // Import Sheet
    if (showImportSheet) {
        ImportBackupSheet(
            onImport = { json, overwriteDeviceId ->
                scope.launch {
                    viewModel.importBackup(json, overwriteDeviceId)
                    showImportSheet = false
                }
            },
            onDismiss = { showImportSheet = false },
        )
    }

    // Delete Confirmation
    if (showDeleteConfirmation && sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                sessionToDelete = null
            },
            title = { Text("Remove Session") },
            text = {
                Text("Are you sure you want to remove this session for ${sessionToDelete?.pubkey?.take(12)}...?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let { viewModel.removeSession(it.pubkey) }
                        showDeleteConfirmation = false
                        sessionToDelete = null
                    },
                ) {
                    Text("Remove", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    sessionToDelete = null
                }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DeviceInfoSection(
    deviceId: String,
    currentEpoch: Long,
    cachedKeyCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Device Info",
            style = MaterialTheme.typography.titleMedium,
            color = Colors.White64,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoRow(label = "Device ID", value = "${deviceId.take(8)}...")
                InfoRow(label = "Current Epoch", value = "$currentEpoch")
                InfoRow(label = "Cached Keys", value = "$cachedKeyCount")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Colors.White64,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun SessionsSection(
    sessions: List<PubkySession>,
    onRemoveSession: (PubkySession) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Active Sessions",
                style = MaterialTheme.typography.titleMedium,
                color = Colors.White64,
            )
            Text(
                text = "${sessions.size} session(s)",
                style = MaterialTheme.typography.bodySmall,
                color = Colors.White64,
            )
        }

        if (sessions.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Colors.White64,
                    )
                    Text(
                        text = "No active sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Colors.White64,
                    )
                    Text(
                        text = "Connect to Pubky-ring to create a session",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.White64,
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column {
                    sessions.forEachIndexed { index, session ->
                        SessionRow(
                            session = session,
                            onRemove = { onRemoveSession(session) },
                        )
                        if (index < sessions.size - 1) {
                            HorizontalDivider(color = Colors.White16)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: PubkySession,
    onRemove: () -> Unit,
) {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pubkey",
                    style = MaterialTheme.typography.labelSmall,
                    color = Colors.White64,
                )
                Text(
                    text = "${session.pubkey.take(20)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.Red.copy(alpha = 0.7f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Created",
                    style = MaterialTheme.typography.labelSmall,
                    color = Colors.White64,
                )
                Text(
                    text = dateFormat.format(session.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Capabilities",
                    style = MaterialTheme.typography.labelSmall,
                    color = Colors.White64,
                )
                Text(
                    text = if (session.capabilities.isEmpty()) "None" else session.capabilities.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BackupSection(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Backup & Restore",
            style = MaterialTheme.typography.titleMedium,
            color = Colors.White64,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
            shape = RoundedCornerShape(8.dp),
            onClick = onExport,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = Colors.Brand,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Backup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "Save sessions and keys to restore later",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.White64,
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
            shape = RoundedCornerShape(8.dp),
            onClick = onImport,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.Blue,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import Backup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "Restore sessions and keys from backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.White64,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportBackupSheet(
    backupJSON: String,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Colors.Gray4,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Export Backup",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Colors.White64)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = backupJSON,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White,
                )
            }

            Button(
                onClick = {
                    onCopy(backupJSON)
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (copied) "Copied!" else "Copy to Clipboard")
            }

            Text(
                text = "Save this backup in a secure location. It contains your session secrets and keys.",
                style = MaterialTheme.typography.bodySmall,
                color = Colors.White64,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportBackupSheet(
    onImport: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var importText by remember { mutableStateOf("") }
    var overwriteDeviceId by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Colors.Gray4,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Import Backup",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Colors.White64)
                }
            }

            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("Paste backup JSON here...") },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Restore Device ID",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "Use the device ID from the backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.White64,
                    )
                }
                Switch(
                    checked = overwriteDeviceId,
                    onCheckedChange = { overwriteDeviceId = it },
                )
            }

            Button(
                onClick = { onImport(importText, overwriteDeviceId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = importText.isNotEmpty(),
            ) {
                Text("Import Backup")
            }

            Text(
                text = "Paste your backup JSON to restore sessions and keys.",
                style = MaterialTheme.typography.bodySmall,
                color = Colors.White64,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// MARK: - ViewModel

@HiltViewModel
class SessionManagementViewModel @Inject constructor(
    private val pubkyRingBridge: PubkyRingBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionManagementUiState())
    val uiState: StateFlow<SessionManagementUiState> = _uiState.asStateFlow()

    fun loadSessions() {
        val sessions = pubkyRingBridge.getCachedSessions()
        val deviceId = pubkyRingBridge.deviceId
        val currentEpoch = System.currentTimeMillis() / (24 * 60 * 60 * 1000) // Daily epochs
        val cachedKeyCount = pubkyRingBridge.getCachedKeypairCount()

        _uiState.update {
            it.copy(
                sessions = sessions,
                deviceId = deviceId,
                currentEpoch = currentEpoch,
                cachedKeyCount = cachedKeyCount,
            )
        }
    }

    fun removeSession(pubkey: String) {
        viewModelScope.launch {
            pubkyRingBridge.clearSession(pubkey)
            loadSessions()
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            pubkyRingBridge.clearAllSessions()
            loadSessions()
        }
    }

    fun exportBackupJSON(): String = pubkyRingBridge.exportBackupAsJSON()

    suspend fun importBackup(jsonString: String, overwriteDeviceId: Boolean) {
        pubkyRingBridge.importBackup(jsonString, overwriteDeviceId)
        loadSessions()
    }
}

data class SessionManagementUiState(
    val sessions: List<PubkySession> = emptyList(),
    val deviceId: String = "",
    val currentEpoch: Long = 0,
    val cachedKeyCount: Int = 0,
)

@Preview
@Composable
private fun Preview() {
    AppThemeSurface {
        SessionManagementScreen(onBack = {})
    }
}

