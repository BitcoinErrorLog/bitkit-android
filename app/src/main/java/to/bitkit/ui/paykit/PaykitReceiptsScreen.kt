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
import androidx.hilt.navigation.compose.hiltViewModel
import to.bitkit.paykit.models.PaymentDirection
import to.bitkit.paykit.models.PaymentStatus
import to.bitkit.paykit.models.Receipt
import to.bitkit.paykit.viewmodels.ReceiptsViewModel
import to.bitkit.ui.components.SearchInput
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@Composable
fun PaykitReceiptsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReceiptDetail: (String) -> Unit = {},
    viewModel: ReceiptsViewModel = hiltViewModel()
) {
    val receipts by viewModel.receipts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val selectedDirection by viewModel.selectedDirection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadReceipts()
    }
    
    ScreenColumn {
        AppTopBar(
            titleText = "Receipts",
            onBackClick = onNavigateBack
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SearchInput(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = "Search receipts..."
            )
            
            // Filter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedDirection == PaymentDirection.SENT,
                    onClick = {
                        viewModel.setSelectedDirection(
                            if (selectedDirection == PaymentDirection.SENT) null else PaymentDirection.SENT
                        )
                    },
                    label = { Text("Sent") }
                )
                FilterChip(
                    selected = selectedDirection == PaymentDirection.RECEIVED,
                    onClick = {
                        viewModel.setSelectedDirection(
                            if (selectedDirection == PaymentDirection.RECEIVED) null else PaymentDirection.RECEIVED
                        )
                    },
                    label = { Text("Received") }
                )
                FilterChip(
                    selected = selectedStatus == PaymentStatus.PENDING,
                    onClick = {
                        viewModel.setSelectedStatus(
                            if (selectedStatus == PaymentStatus.PENDING) null else PaymentStatus.PENDING
                        )
                    },
                    label = { Text("Pending") }
                )
                FilterChip(
                    selected = selectedStatus == PaymentStatus.COMPLETED,
                    onClick = {
                        viewModel.setSelectedStatus(
                            if (selectedStatus == PaymentStatus.COMPLETED) null else PaymentStatus.COMPLETED
                        )
                    },
                    label = { Text("Completed") }
                )
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (receipts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No receipts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(receipts) { receipt ->
                        ReceiptRow(
                            receipt = receipt,
                            onClick = { onNavigateToReceiptDetail(receipt.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptRow(
    receipt: Receipt,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (receipt.direction == PaymentDirection.SENT) {
                        Icons.Default.ArrowUpward
                    } else {
                        Icons.Default.ArrowDownward
                    },
                    contentDescription = null,
                    tint = if (receipt.direction == PaymentDirection.SENT) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Column {
                    Text(
                        text = receipt.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = receipt.paymentMethod,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${receipt.amountSats} sats",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (receipt.direction == PaymentDirection.SENT) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = receipt.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

