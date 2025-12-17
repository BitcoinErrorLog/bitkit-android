package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.viewmodels.PaymentRequestsViewModel
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@Composable
fun PaykitPaymentRequestsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaymentRequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    ScreenColumn {
        AppTopBar(
            titleText = "Payment Requests",
            onBackClick = onNavigateBack
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.requests.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No payment requests",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.requests,
                    key = { it.id }
                ) { request ->
                    PaymentRequestRow(
                        request = request,
                        onAccept = { viewModel.acceptRequest(request) },
                        onDecline = { viewModel.declineRequest(request) }
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentRequestRow(
    request: PaymentRequest,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.counterpartyName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${request.amountSats} ${request.currency}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = request.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = request.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (request.status) {
                            PaymentRequestStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                            PaymentRequestStatus.ACCEPTED -> MaterialTheme.colorScheme.primary
                            PaymentRequestStatus.DECLINED -> MaterialTheme.colorScheme.error
                            PaymentRequestStatus.EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant
                            PaymentRequestStatus.PAID -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            if (request.status == PaymentRequestStatus.PENDING && request.direction == RequestDirection.INCOMING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}
