package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.viewmodels.ContactsViewModel
import to.bitkit.ui.components.SearchInput
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@Composable
fun PaykitContactsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContactDiscovery: () -> Unit = {},
    onNavigateToContactDetail: (String) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()


    ScreenColumn {
        AppTopBar(
            titleText = "Contacts",
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = onNavigateToContactDiscovery) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Discover Contacts"
                    )
                }
            }
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
                placeholder = "Search contacts..."
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No contacts",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onNavigateToContactDiscovery) {
                            Text("Discover Contacts")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts) { contact ->
                        ContactRow(
                            contact = contact,
                            onClick = { onNavigateToContactDetail(contact.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactRow(
    contact: Contact,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = contact.abbreviatedKey,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (contact.paymentCount > 0) {
                    Text(
                        text = "${contact.paymentCount} payments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (contact.paymentCount > 0) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
