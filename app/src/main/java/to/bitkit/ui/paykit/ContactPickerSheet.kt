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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.R
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.viewmodels.ContactsViewModel
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyMSB
import to.bitkit.ui.components.BodyS
import to.bitkit.ui.components.HorizontalSpacer
import to.bitkit.ui.components.SearchInput
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.components.Title
import to.bitkit.ui.shared.modifiers.clickableAlpha
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.theme.Colors

@Composable
fun ContactPickerSheet(
    onBack: () -> Unit,
    onContactSelected: (Contact) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheetHeight()
            .gradientBackground(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_x),
                    contentDescription = "Back",
                    tint = Colors.White,
                )
            }
            Title(text = "Select Contact")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            SearchInput(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = "Search contacts...",
            )

            VerticalSpacer(16.dp)

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Colors.Brand)
                }
            } else if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_users),
                            contentDescription = null,
                            tint = Colors.White32,
                            modifier = Modifier.size(48.dp),
                        )
                        VerticalSpacer(16.dp)
                        BodyM(text = "No contacts yet", color = Colors.White64)
                        VerticalSpacer(8.dp)
                        BodyS(
                            text = "Add people you follow on Pubky",
                            color = Colors.White32,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(contacts) { contact ->
                        ContactPickerRow(
                            contact = contact,
                            onClick = { onContactSelected(contact) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPickerRow(
    contact: Contact,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Gray6, RoundedCornerShape(12.dp))
            .clickableAlpha { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Colors.Brand24, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BodyMSB(
                text = contact.name.take(1).uppercase().ifEmpty { "?" },
                color = Colors.Brand,
            )
        }

        HorizontalSpacer(12.dp)

        Column(modifier = Modifier.weight(1f)) {
            BodyMSB(
                text = contact.name.ifEmpty { "Unknown" },
                color = Colors.White,
            )
            BodyS(
                text = contact.abbreviatedKey,
                color = Colors.White64,
            )
        }

        if (contact.paymentCount > 0) {
            Icon(
                imageVector = Icons.Default.Payment,
                contentDescription = null,
                tint = Colors.Green,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

