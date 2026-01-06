package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.services.NoisePaymentRequest
import to.bitkit.paykit.viewmodels.NoisePaymentViewModel
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@Composable
fun NoisePaymentScreen(
    onNavigateBack: () -> Unit,
    prefillRecipient: String? = null,
    viewModel: NoisePaymentViewModel = hiltViewModel(),
) {
    var mode by remember { mutableStateOf(PaymentMode.SEND) }
    var recipientPubkey by remember { mutableStateOf(prefillRecipient ?: "") }
    var amount by remember { mutableStateOf("") }
    var methodId by remember { mutableStateOf("lightning") }
    var description by remember { mutableStateOf("") }

    val myPubkey by viewModel.myPubkey.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val paymentRequest by viewModel.paymentRequest.collectAsStateWithLifecycle()
    val paymentResponse by viewModel.paymentResponse.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    ScreenColumn {
        AppTopBar(
            titleText = "Noise Payment", // TODO: Localize via Transifex
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == PaymentMode.SEND,
                    onClick = { mode = PaymentMode.SEND },
                    label = { Text("Send") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = mode == PaymentMode.RECEIVE,
                    onClick = { mode = PaymentMode.RECEIVE },
                    label = { Text("Receive") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (mode == PaymentMode.SEND) {
                // Send Payment Form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = recipientPubkey,
                            onValueChange = { recipientPubkey = it },
                            label = { Text("Recipient Public Key") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Amount (sats)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = methodId,
                            onValueChange = { methodId = it },
                            label = { Text("Payment Method") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val request = NoisePaymentRequest(
                                    payerPubkey = myPubkey,
                                    payeePubkey = recipientPubkey,
                                    methodId = methodId,
                                    amount = amount.takeIf { it.isNotEmpty() },
                                    description = description.takeIf { it.isNotEmpty() }
                                )
                                viewModel.sendPayment(request)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isConnecting && myPubkey.isNotEmpty() && recipientPubkey.isNotEmpty() && amount.isNotEmpty()
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text("Send Payment")
                            }
                        }
                    }
                }
            } else {
                // Receive Payment Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Show incoming payment request if we have one
                        if (paymentRequest != null) {
                            val request = paymentRequest!!
                            Text(
                                text = "Incoming Payment Request",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider()
                            Text("From: ${request.payerPubkey.take(16)}...")
                            Text("Amount: ${request.amount ?: "N/A"} sats")
                            request.description?.let {
                                Text("Description: $it")
                            }

                            if (isAuthenticating) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Authenticating...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.declineIncomingRequest() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Decline")
                                    }
                                    Button(
                                        onClick = { viewModel.acceptIncomingRequest() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Accept & Pay")
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Waiting for payment request...",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Button(
                                onClick = { viewModel.receivePayment() },
                                enabled = !isConnecting
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                } else {
                                    Text("Start Listening")
                                }
                            }
                        }
                    }
                }
            }

            // Status
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            paymentResponse?.let { response ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (response.success) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (response.success) "Payment successful" else "Payment failed",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        response.errorMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class PaymentMode {
    SEND, RECEIVE
}
