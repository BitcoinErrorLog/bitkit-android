package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import to.bitkit.ui.components.Caption
import to.bitkit.ui.components.BodyM
import to.bitkit.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.models.PaymentDirection
import to.bitkit.paykit.models.PaymentStatus
import to.bitkit.paykit.models.Receipt
import to.bitkit.paykit.viewmodels.ReceiptsViewModel
import to.bitkit.ui.components.SearchInput
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.Colors

@Composable
fun PaykitReceiptsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReceiptDetail: (String) -> Unit = {},
    viewModel: ReceiptsViewModel = hiltViewModel()
) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val selectedDirection by viewModel.selectedDirection.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.paykit__receipts), // TODO: Localize via Transifex
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
                placeholder = stringResource(R.string.paykit__search_receipts)
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
                    label = { Text(stringResource(R.string.paykit__sent)) }
                )
                FilterChip(
                    selected = selectedDirection == PaymentDirection.RECEIVED,
                    onClick = {
                        viewModel.setSelectedDirection(
                            if (selectedDirection == PaymentDirection.RECEIVED) null else PaymentDirection.RECEIVED
                        )
                    },
                    label = { Text(stringResource(R.string.paykit__received)) }
                )
                FilterChip(
                    selected = selectedStatus == PaymentStatus.PENDING,
                    onClick = {
                        viewModel.setSelectedStatus(
                            if (selectedStatus == PaymentStatus.PENDING) null else PaymentStatus.PENDING
                        )
                    },
                    label = { Text(stringResource(R.string.paykit__pending)) }
                )
                FilterChip(
                    selected = selectedStatus == PaymentStatus.COMPLETED,
                    onClick = {
                        viewModel.setSelectedStatus(
                            if (selectedStatus == PaymentStatus.COMPLETED) null else PaymentStatus.COMPLETED
                        )
                    },
                    label = { Text(stringResource(R.string.paykit__completed)) }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Colors.Brand)
                }
            } else if (receipts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    BodyM(
                        text = stringResource(R.string.paykit__no_receipts),
                            color = Colors.White64,
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
                        Colors.Red
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Column {
                    BodyM(
                        text = receipt.displayName,
                    )
                    Caption(
                        text = receipt.paymentMethod,
                            color = Colors.White64,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                BodyM(
                    text = "${receipt.amountSats} sats",
                        color = if (receipt.direction == PaymentDirection.SENT) {
                        Colors.Red
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Caption(
                    text = receipt.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = Colors.White64,
                )
            }
        }
    }
}
