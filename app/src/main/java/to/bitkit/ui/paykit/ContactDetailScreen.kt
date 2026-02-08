package to.bitkit.ui.paykit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import to.bitkit.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.viewmodels.ContactsViewModel
import to.bitkit.ui.appViewModel
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyS
import to.bitkit.ui.components.Caption13Up
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.Title
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppAlertDialog
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.Colors

@Composable
fun ContactDetailScreen(
    contactId: String,
    onNavigateBack: () -> Unit,
    onNavigateToNoisePayment: (String) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var contact by remember { mutableStateOf<Contact?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val app = appViewModel
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(contactId, contacts) {
        contact = contacts.find { it.id == contactId }
        if (contact == null && !isLoading) {
            viewModel.loadContacts()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            app?.toast(
                type = to.bitkit.models.Toast.ToastType.ERROR,
                title = context.getString(R.string.paykit__error),
                description = msg,
            )
            viewModel.clearError()
        }
    }

    if (showDeleteConfirm) {
        AppAlertDialog(
            title = stringResource(R.string.paykit__remove_contact),
            text = "This will unfollow ${contact?.name?.ifEmpty { "this contact" } ?: "this contact"} on your homeserver. Are you sure?",
            confirmText = stringResource(R.string.paykit__remove),
            onConfirm = {
                showDeleteConfirm = false
                isDeleting = true
                contact?.let { c ->
                    viewModel.deleteContact(c)
                    onNavigateBack()
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.paykit__contact),
            onBackClick = onNavigateBack,
        )

        if (isLoading || contact == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Colors.Brand)
            }
        } else {
            ContactDetailContent(
                contact = requireNotNull(contact),
                isDeleting = isDeleting,
                onCopyPubkey = {
                    clipboardManager.setText(AnnotatedString(contact!!.publicKeyZ32))
                    app?.toast(
                        type = to.bitkit.models.Toast.ToastType.SUCCESS,
                        title = context.getString(R.string.paykit__copied),
                        description = context.getString(R.string.paykit__public_key_copied_to_clipboard),
                    )
                },
                onSendPayment = { onNavigateToNoisePayment(contact!!.publicKeyZ32) },
                onRemoveContact = { showDeleteConfirm = true },
            )
        }
    }
}

@Composable
private fun ContactDetailContent(
    contact: Contact,
    isDeleting: Boolean,
    onCopyPubkey: () -> Unit,
    onSendPayment: () -> Unit,
    onRemoveContact: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Large Avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Colors.White16, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Title(
                text = contact.name.take(1).uppercase().ifEmpty { "?" },
                color = MaterialTheme.colorScheme.primary,
            )
        }

        VerticalSpacer(24.dp)

        // Name
        Title(
            text = contact.name.ifEmpty { stringResource(R.string.paykit__unknown_contact) },
            color = Colors.White,
        )

        VerticalSpacer(8.dp)

        // Public Key with copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Colors.Gray6, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Caption13Up(text = stringResource(R.string.paykit__public_key), color = Colors.White64)
                VerticalSpacer(4.dp)
                BodyS(
                    text = contact.publicKeyZ32,
                    color = Colors.White,
                )
            }
            IconButton(onClick = onCopyPubkey) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        VerticalSpacer(24.dp)

        // Stats
        if (contact.paymentCount > 0 || contact.lastPaymentAt != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Colors.Gray6, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Caption13Up(text = stringResource(R.string.paykit__payment_history), color = Colors.White64)

                if (contact.paymentCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BodyM(text = stringResource(R.string.paykit__total_payments), color = Colors.White64)
                        BodyM(text = "${contact.paymentCount}", color = Colors.Green)
                    }
                }

                contact.lastPaymentAt?.let { lastPayment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BodyM(text = stringResource(R.string.paykit__last_payment), color = Colors.White64)
                        BodyM(
                            text = formatTimestamp(lastPayment),
                            color = Colors.White,
                        )
                    }
                }
            }

            VerticalSpacer(24.dp)
        }

        // Notes if any
        contact.notes?.let { notes ->
            if (notes.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Colors.Gray6, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Caption13Up(text = stringResource(R.string.paykit__notes), color = Colors.White64)
                    VerticalSpacer(8.dp)
                    BodyM(text = notes, color = Colors.White)
                }
                VerticalSpacer(24.dp)
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.paykit__send_payment),
                onClick = onSendPayment,
                modifier = Modifier.fillMaxWidth(),
            )

            SecondaryButton(
                text = if (isDeleting) stringResource(R.string.paykit__removing) else stringResource(R.string.paykit__remove_contact),
                onClick = onRemoveContact,
                enabled = !isDeleting,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        VerticalSpacer(16.dp)

        BodyS(
            text = stringResource(R.string.paykit__removing_this_contact_will_unfollow_them_on_your_p),
            color = Colors.White32,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
