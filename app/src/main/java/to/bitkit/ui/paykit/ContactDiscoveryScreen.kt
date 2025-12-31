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
import to.bitkit.paykit.viewmodels.ContactsViewModel
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn

@Composable
fun ContactDiscoveryScreen(
    onNavigateBack: () -> Unit,
    onContactDiscovered: (to.bitkit.paykit.models.Contact) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val discoveredContacts by viewModel.discoveredContacts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    ScreenColumn {
        AppTopBar(
            titleText = "Discover Contacts",
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Title(text = "Discover from Pubky")
                    Text(
                        text = "Find contacts from your Pubky follows directory. Contacts with published payment endpoints will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (discoveredContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No contacts discovered",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredContacts) { discovered ->
                        DiscoveredContactRow(
                            discovered = discovered,
                            onAdd = { onContactDiscovered(discovered) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredContactRow(
    discovered: to.bitkit.paykit.models.Contact,
    onAdd: () -> Unit
) {
    Card(
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
                    text = discovered.name.ifEmpty { discovered.publicKeyZ32.take(16) + "..." },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = discovered.publicKeyZ32,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Contact"
                )
            }
        }
    }
}
