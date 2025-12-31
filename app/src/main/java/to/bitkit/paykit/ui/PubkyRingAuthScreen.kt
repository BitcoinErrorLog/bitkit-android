package to.bitkit.paykit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import to.bitkit.paykit.services.CrossDeviceRequest
import to.bitkit.paykit.services.PubkySession
import to.bitkit.paykit.viewmodels.PubkyRingAuthViewModel
import to.bitkit.ui.theme.Colors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkyRingAuthScreen(
    onNavigateBack: () -> Unit,
    onSessionReceived: (PubkySession) -> Unit,
    onNavigateToScanner: (() -> Unit)? = null,
    scannedQrCode: String? = null,
    modifier: Modifier = Modifier,
    viewModel: PubkyRingAuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val crossDeviceRequest by viewModel.crossDeviceRequest.collectAsStateWithLifecycle()
    val isPolling by viewModel.isPolling.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var manualPubkey by remember { mutableStateOf("") }
    var manualSessionSecret by remember { mutableStateOf("") }
    var timeRemaining by remember { mutableLongStateOf(0L) }
    var pastedUrl by remember { mutableStateOf("") }

    val isPubkyRingInstalled = viewModel.isPubkyRingInstalled
    val recommendedMethod = viewModel.recommendedMethod

    // Handle scanned QR code
    LaunchedEffect(scannedQrCode) {
        scannedQrCode?.let { qrCode ->
            if (qrCode.contains("pubky://") || qrCode.contains("pubkyring://")) {
                viewModel.handleAuthUrl(qrCode, onSessionReceived)
            } else {
                pastedUrl = qrCode
            }
        }
    }

    // Set initial tab based on availability
    LaunchedEffect(Unit) {
        if (!isPubkyRingInstalled) {
            selectedTab = 1 // QR code tab
        }
    }

    // Timer for countdown
    LaunchedEffect(crossDeviceRequest) {
        val request = crossDeviceRequest ?: return@LaunchedEffect
        while (!request.isExpired) {
            timeRemaining = request.timeRemainingMs / 1000
            delay(1000)
        }
        viewModel.cancelCrossDeviceRequest()
    }

    // Show error snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val tabs = if (isPubkyRingInstalled) {
        listOf("Same Device", "Show QR", "Scan/Paste")
    } else {
        listOf("Show QR", "Scan/Paste")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Connect Pubky") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            val pageIndex = if (isPubkyRingInstalled) selectedTab else selectedTab + 1

            when (pageIndex) {
                0 -> SameDeviceTabContent(
                    onAuthenticate = {
                        viewModel.requestSession(onSessionReceived)
                    },
                )
                1 -> CrossDeviceTabContent(
                    crossDeviceRequest = crossDeviceRequest,
                    timeRemaining = timeRemaining,
                    isPolling = isPolling,
                    onGenerateRequest = {
                        viewModel.generateCrossDeviceRequest()
                        viewModel.startPollingForSession(onSessionReceived)
                    },
                    onCopyLink = {
                        crossDeviceRequest?.url?.let { url ->
                            clipboardManager.setText(AnnotatedString(url))
                            scope.launch {
                                snackbarHostState.showSnackbar("Link copied to clipboard")
                            }
                        }
                    },
                )
                2 -> ScanPasteTabContent(
                    pastedUrl = pastedUrl,
                    onPastedUrlChange = { pastedUrl = it },
                    onScanClick = onNavigateToScanner,
                    onPasteFromClipboard = {
                        clipboardManager.getText()?.text?.let { text ->
                            pastedUrl = text
                        }
                    },
                    onConnect = {
                        viewModel.handleAuthUrl(pastedUrl.trim(), onSessionReceived)
                    },
                )
            }
        }
    }
}

@Composable
private fun SameDeviceTabContent(
    onAuthenticate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connect with Pubky-ring",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pubky-ring is installed on this device. Tap the button below to connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = Colors.White64,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onAuthenticate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Pubky-ring")
        }
    }
}

@Composable
private fun CrossDeviceTabContent(
    crossDeviceRequest: CrossDeviceRequest?,
    timeRemaining: Long,
    isPolling: Boolean,
    onGenerateRequest: () -> Unit,
    onCopyLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (crossDeviceRequest != null && !crossDeviceRequest.isExpired) {
            // Show QR code and link
            Text(
                text = "Scan this QR code",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            crossDeviceRequest.qrCodeBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (timeRemaining < 60) MaterialTheme.colorScheme.error else Colors.White64,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Expires in ${timeRemaining}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (timeRemaining < 60) MaterialTheme.colorScheme.error else Colors.White64,
                )
            }

            if (isPolling) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Waiting for authentication...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.White64,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Or share this link:",
                style = MaterialTheme.typography.bodySmall,
                color = Colors.White64,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Colors.White10, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = crossDeviceRequest.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopyLink) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy link",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGenerateRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.White10,
                ),
            ) {
                Text("Generate New Code")
            }
        } else {
            // Setup view
            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Scan from Another Device",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Generate a QR code to scan with a device that has Pubky-ring installed.",
                style = MaterialTheme.typography.bodyMedium,
                color = Colors.White64,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onGenerateRequest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate QR Code")
            }
        }
    }
}

@Composable
private fun ScanPasteTabContent(
    pastedUrl: String,
    onPastedUrlChange: (String) -> Unit,
    onScanClick: (() -> Unit)?,
    onPasteFromClipboard: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = Colors.Brand,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Scan or Paste",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan a Pubky Ring QR code or paste the connection URL.",
            style = MaterialTheme.typography.bodyMedium,
            color = Colors.White64,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (onScanClick != null) {
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "or paste a URL",
                style = MaterialTheme.typography.bodySmall,
                color = Colors.White64,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = pastedUrl,
            onValueChange = onPastedUrlChange,
            label = { Text("Pubky Ring URL") },
            placeholder = { Text("pubky://... or pubkyring://...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onPasteFromClipboard) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Paste from clipboard")
                }
            },
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = pastedUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Colors.Brand,
            ),
        ) {
            Text("Connect")
        }
    }
}
