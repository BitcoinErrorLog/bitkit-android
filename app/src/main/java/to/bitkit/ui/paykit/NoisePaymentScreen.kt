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
import androidx.compose.ui.res.stringResource
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.Caption
import to.bitkit.ui.components.Subtitle
import to.bitkit.ui.theme.Colors

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
            titleText = stringResource(R.string.paykit__noise_payment), // TODO: Localize via Transifex
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
                    label = { Text(stringResource(R.string.paykit__send)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = mode == PaymentMode.RECEIVE,
                    onClick = { mode = PaymentMode.RECEIVE },
                    label = { Text(stringResource(R.string.paykit__receive)) },
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
                            label = { Text(stringResource(R.string.paykit__recipient_public_key)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text(stringResource(R.string.paykit__amount_sats)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = methodId,
                            onValueChange = { methodId = it },
                            label = { Text(stringResource(R.string.paykit__payment_method)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(stringResource(R.string.paykit__description_optional)) },
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
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(stringResource(R.string.paykit__send_payment))
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
                            Subtitle(
                                text = stringResource(R.string.paykit__incoming_payment_request),
                            )
                            HorizontalDivider()
                            Text("From: ${request.payerPubkey.take(16)}...")
                            Text("Amount: ${request.amount ?: "N/A"} sats")
                            request.description?.let {
                                Text("Description: $it")
                            }

                            if (isAuthenticating) {
                                CircularProgressIndicator(color = Colors.Brand)
                                Caption(
                                    text = stringResource(R.string.paykit__authenticating),
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
                                        Text(stringResource(R.string.paykit__decline))
                                    }
                                    Button(
                                        onClick = { viewModel.acceptIncomingRequest() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.paykit__accept_pay))
                                    }
                                }
                            }
                        } else {
                            BodyM(
                                text = stringResource(R.string.paykit__waiting_for_payment_request),
                            )

                            Button(
                                onClick = { viewModel.receivePayment() },
                                enabled = !isConnecting
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text(stringResource(R.string.paykit__start_listening))
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
                        containerColor = Colors.Red16
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
                            Colors.Red16
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BodyM(
                            text = if (response.success) stringResource(R.string.paykit__payment_successful) else stringResource(R.string.paykit__payment_failed),
                        )
                        response.errorMessage?.let {
                            Caption(
                                text = it,
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
