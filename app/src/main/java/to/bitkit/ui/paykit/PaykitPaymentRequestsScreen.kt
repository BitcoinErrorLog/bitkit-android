package to.bitkit.ui.paykit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.storage.SentPaymentRequest
import to.bitkit.paykit.viewmodels.PaymentRequestsViewModel
import to.bitkit.paykit.viewmodels.RequestTab
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.Colors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PaykitPaymentRequestsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaymentRequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<PaymentRequest?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadRequests()
        viewModel.loadSentRequests()
    }

    ScreenColumn {
        AppTopBar(
            titleText = "Payment Requests",
            onBackClick = onNavigateBack,
            actions = {
                if (uiState.isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = Colors.Brand,
                    )
                } else {
                    IconButton(onClick = { viewModel.discoverRequests() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Discover Requests",
                            tint = Colors.Brand,
                        )
                    }
                }
                IconButton(onClick = { showCreateSheet = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create Request",
                        tint = Colors.Brand,
                    )
                }
            },
        )

        // Show discovery result
        uiState.discoveryResult?.let { result ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (result.startsWith("⚠️")) Colors.Yellow.copy(alpha = 0.2f) else Colors.Gray6
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        result,
                        color = if (result.startsWith("⚠️")) Colors.Yellow else Colors.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.clearDiscoveryResult() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Colors.White64)
                    }
                }
            }
        }

        TabSection(
            selectedTab = uiState.selectedTab,
            onSelectTab = { viewModel.selectTab(it) },
        )

        Box(modifier = Modifier.weight(1f)) {
            when (uiState.selectedTab) {
                RequestTab.INCOMING -> IncomingRequestsList(
                    requests = uiState.incomingRequests,
                    isLoading = uiState.isLoading,
                    onRequestClick = { selectedRequest = it },
                    onAccept = { viewModel.acceptRequest(it) },
                    onDecline = { viewModel.declineRequest(it) },
                )
                RequestTab.SENT -> SentRequestsList(
                    requests = uiState.sentRequests,
                    outgoingRequests = uiState.outgoingRequests,
                    isLoading = uiState.isLoading,
                    onRequestClick = { selectedRequest = it },
                    onCancelSent = { viewModel.cancelSentRequest(it) },
                    onCleanup = { viewModel.cleanupOrphanedRequests() },
                    isCleaningUp = uiState.isCleaningUp,
                    cleanupResult = uiState.cleanupResult,
                    onClearCleanupResult = { viewModel.clearCleanupResult() },
                )
            }
        }
    }

    if (showCreateSheet) {
        CreatePaymentRequestSheet(
            viewModel = viewModel,
            onDismiss = { showCreateSheet = false },
        )
    }

    selectedRequest?.let { request ->
        PaymentRequestDetailSheet(
            request = request,
            viewModel = viewModel,
            onDismiss = { selectedRequest = null },
        )
    }
}

@Composable
private fun TabSection(
    selectedTab: RequestTab,
    onSelectTab: (RequestTab) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = Colors.Gray5,
        contentColor = Colors.White,
    ) {
        Tab(
            selected = selectedTab == RequestTab.INCOMING,
            onClick = { onSelectTab(RequestTab.INCOMING) },
            text = { Text("Incoming") },
        )
        Tab(
            selected = selectedTab == RequestTab.SENT,
            onClick = { onSelectTab(RequestTab.SENT) },
            text = { Text("Sent") },
        )
    }
}

@Composable
private fun IncomingRequestsList(
    requests: List<PaymentRequest>,
    isLoading: Boolean,
    onRequestClick: (PaymentRequest) -> Unit,
    onAccept: (PaymentRequest) -> Unit,
    onDecline: (PaymentRequest) -> Unit,
) {
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        requests.isEmpty() -> {
            EmptyState(
                icon = Icons.Default.CallReceived,
                title = "No incoming requests",
                subtitle = "Requests from others will appear here",
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(requests, key = { it.id }) { request ->
                    PaymentRequestRow(
                        request = request,
                        onClick = { onRequestClick(request) },
                        onAccept = { onAccept(request) },
                        onDecline = { onDecline(request) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SentRequestsList(
    requests: List<SentPaymentRequest>,
    outgoingRequests: List<PaymentRequest>,
    isLoading: Boolean,
    onRequestClick: (PaymentRequest) -> Unit,
    onCancelSent: (SentPaymentRequest) -> Unit,
    onCleanup: () -> Unit,
    isCleaningUp: Boolean,
    cleanupResult: String?,
    onClearCleanupResult: () -> Unit,
) {
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        outgoingRequests.isEmpty() -> {
            EmptyState(
                icon = Icons.Default.Send,
                title = "No sent requests",
                subtitle = "Requests you send will appear here",
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedButton(
                        onClick = onCleanup,
                        enabled = !isCleaningUp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isCleaningUp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isCleaningUp) "Cleaning up..." else "Cleanup Orphaned Requests")
                    }
                }

                cleanupResult?.let { result ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(result, color = Colors.White, modifier = Modifier.weight(1f))
                                IconButton(onClick = onClearCleanupResult) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Colors.White64)
                                }
                            }
                        }
                    }
                }

                items(outgoingRequests, key = { it.id }) { request ->
                    val sentRequest = requests.find { it.id == request.id }
                    SentPaymentRequestRow(
                        request = request,
                        sentRequest = sentRequest,
                        onClick = { onRequestClick(request) },
                        onCancel = { sentRequest?.let { onCancelSent(it) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Colors.White32,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Colors.White)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Colors.White64)
    }
}

@Composable
fun PaymentRequestRow(
    request: PaymentRequest,
    onClick: () -> Unit = {},
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Colors.Green.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.CallReceived,
                            contentDescription = null,
                            tint = Colors.Green,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = request.counterpartyName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Colors.White,
                        )
                        Text(
                            text = formatSats(request.amountSats),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Colors.White,
                        )
                    }
                }
                StatusBadge(status = request.status)
            }

            if (request.description.isNotEmpty()) {
                Text(
                    text = request.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.White64,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (request.status == PaymentRequestStatus.PENDING && request.direction == RequestDirection.INCOMING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Colors.Green),
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}

@Composable
private fun SentPaymentRequestRow(
    request: PaymentRequest,
    sentRequest: SentPaymentRequest?,
    onClick: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Colors.Gray6),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Colors.Brand.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            tint = Colors.Brand,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = request.counterpartyName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Colors.White,
                        )
                        Text(
                            text = formatSats(request.amountSats),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Colors.White,
                        )
                    }
                }
                StatusBadge(status = request.status)
            }

            if (request.description.isNotEmpty()) {
                Text(
                    text = request.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.White64,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (request.status == PaymentRequestStatus.PENDING) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Colors.Red),
                ) {
                    Text("Cancel Request")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: PaymentRequestStatus) {
    val color = when (status) {
        PaymentRequestStatus.PENDING -> Colors.Yellow
        PaymentRequestStatus.ACCEPTED -> Colors.Green
        PaymentRequestStatus.DECLINED -> Colors.Red
        PaymentRequestStatus.EXPIRED -> Colors.White32
        PaymentRequestStatus.PAID -> Colors.Green
    }
    val text = when (status) {
        PaymentRequestStatus.PENDING -> "Pending"
        PaymentRequestStatus.ACCEPTED -> "Accepted"
        PaymentRequestStatus.DECLINED -> "Declined"
        PaymentRequestStatus.EXPIRED -> "Expired"
        PaymentRequestStatus.PAID -> "Paid"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.2f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePaymentRequestSheet(
    viewModel: PaymentRequestsViewModel,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var recipientPubkey by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("1000") }
    var methodId by remember { mutableStateOf("lightning") }
    var description by remember { mutableStateOf("") }
    var expiresInDays by remember { mutableStateOf(7) }

    LaunchedEffect(uiState.sendSuccess) {
        if (uiState.sendSuccess) {
            viewModel.clearSendSuccess()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Colors.Gray5,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Create Payment Request",
                style = MaterialTheme.typography.titleLarge,
                color = Colors.White,
            )

            OutlinedTextField(
                value = recipientPubkey,
                onValueChange = { recipientPubkey = it },
                label = { Text("Recipient Pubkey") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                label = { Text("Amount (sats)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = methodId == "lightning",
                    onClick = { methodId = "lightning" },
                    label = { Text("Lightning") },
                )
                FilterChip(
                    selected = methodId == "onchain",
                    onClick = { methodId = "onchain" },
                    label = { Text("On-Chain") },
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Expires in", color = Colors.White64)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 7, 30, 90).forEach { days ->
                        FilterChip(
                            selected = expiresInDays == days,
                            onClick = { expiresInDays = days },
                            label = { Text("${days}d") },
                        )
                    }
                }
            }

            uiState.error?.let { error ->
                Text(error, color = Colors.Red, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (recipientPubkey.isNotBlank() && amount > 0) {
                        viewModel.sendPaymentRequest(
                            recipientPubkey = recipientPubkey.trim(),
                            amountSats = amount,
                            methodId = methodId,
                            description = description,
                            expiresInDays = expiresInDays,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSending && recipientPubkey.isNotBlank() && (amountText.toLongOrNull() ?: 0L) > 0,
            ) {
                if (uiState.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Colors.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.isSending) "Sending..." else "Send Request")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentRequestDetailSheet(
    request: PaymentRequest,
    viewModel: PaymentRequestsViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Colors.Gray5,
    ) {
        Column(
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (request.direction == RequestDirection.INCOMING) Colors.Green.copy(alpha = 0.2f)
                        else Colors.Brand.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (request.direction == RequestDirection.INCOMING) Icons.Default.CallReceived else Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = if (request.direction == RequestDirection.INCOMING) Colors.Green else Colors.Brand,
                )
            }

            Text(
                text = formatSats(request.amountSats),
                style = MaterialTheme.typography.headlineMedium,
                color = Colors.White,
            )

            StatusBadge(status = request.status)

            if (request.description.isNotEmpty()) {
                Text(
                    text = request.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colors.White64,
                )
            }

            HorizontalDivider(color = Colors.White16)

            DetailRow(
                label = if (request.direction == RequestDirection.INCOMING) "From" else "To",
                value = request.counterpartyName,
            )
            DetailRow(label = "Method", value = request.methodId.replaceFirstChar { it.uppercase() })
            DetailRow(label = "Created", value = formatDate(request.createdAt))
            request.expiresAt?.let { DetailRow(label = "Expires", value = formatDate(it)) }

            if (request.status == PaymentRequestStatus.PENDING) {
                Spacer(Modifier.height(8.dp))

                if (request.direction == RequestDirection.INCOMING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.acceptRequest(request)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Colors.Green),
                        ) {
                            Text("Accept")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.declineRequest(request)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Decline")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.deleteRequest(request)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Colors.Red),
                    ) {
                        Text("Cancel Request")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Colors.White64)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Colors.White)
    }
}

private fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
