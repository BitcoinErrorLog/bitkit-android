package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import to.bitkit.paykit.models.PaymentRequest
import to.bitkit.paykit.models.PaymentRequestStatus
import to.bitkit.paykit.models.RequestDirection
import to.bitkit.paykit.storage.PaymentRequestStorage
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import javax.inject.Inject

@Composable
fun PaykitPaymentRequestsScreen(
    onNavigateBack: () -> Unit,
    paymentRequestStorage: PaymentRequestStorage? = null
) {
    // TODO: Create ViewModel for PaymentRequests
    val requests = remember { mutableStateOf<List<PaymentRequest>>(emptyList()) }
    val isLoading = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isLoading.value = true
        // TODO: Load requests from storage
        isLoading.value = false
    }
    
    ScreenColumn {
        AppTopBar(
            titleText = "Payment Requests",
            onBackClick = onNavigateBack
        )
        
        if (isLoading.value) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (requests.value.isEmpty()) {
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
                items(requests.value) { request ->
                    PaymentRequestRow(request = request)
                }
            }
        }
    }
}

@Composable
fun PaymentRequestRow(request: PaymentRequest) {
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
                        onClick = { /* TODO: Accept request */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = { /* TODO: Decline request */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}

