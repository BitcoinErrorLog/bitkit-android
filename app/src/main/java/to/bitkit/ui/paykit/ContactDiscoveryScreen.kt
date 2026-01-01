package to.bitkit.ui.paykit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.services.DiscoveredContact
import to.bitkit.paykit.viewmodels.ContactsViewModel
import to.bitkit.ui.components.Title
import to.bitkit.ui.scaffold.AppTopBar

@Composable
fun ContactDiscoveryScreen(
    onNavigateBack: () -> Unit,
    onContactDiscovered: (Contact) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val discoveredContacts by viewModel.discoveredContacts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pubkeyToFollow by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.discoverContacts()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues),
        ) {
            AppTopBar(
                titleText = "Discover Contacts",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.discoverContacts() },
                        enabled = !isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Title(text = "Discover from Pubky")
                        Text(
                            text = "Find contacts from your Pubky follows directory. Contacts with published payment endpoints will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Title(text = "Add Follow by Pubkey")
                        OutlinedTextField(
                            value = pubkeyToFollow,
                            onValueChange = { pubkeyToFollow = it },
                            label = { Text("Enter pubkey (z-base-32)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("PubkeyInput"),
                        )
                        Button(
                            onClick = {
                                if (pubkeyToFollow.isNotBlank()) {
                                    viewModel.followContact(pubkeyToFollow.trim())
                                    pubkeyToFollow = ""
                                    viewModel.discoverContacts()
                                }
                            },
                            enabled = pubkeyToFollow.isNotBlank() && !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("AddFollowButton"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text("Add Follow")
                        }
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (discoveredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No contacts discovered",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(discoveredContacts) { discovered ->
                            DiscoveredContactRow(
                                discovered = discovered,
                                onAdd = {
                                    onContactDiscovered(
                                        Contact.create(
                                            publicKeyZ32 = discovered.pubkey,
                                            name = discovered.name ?: "",
                                        )
                                    )
                                },
                                onFollow = { viewModel.followContact(discovered.pubkey) },
                                onUnfollow = { viewModel.unfollowContact(discovered.pubkey) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredContactRow(
    discovered: DiscoveredContact,
    onAdd: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = discovered.name ?: (discovered.pubkey.take(16) + "..."),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = discovered.pubkey,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (discovered.hasPaymentMethods) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = "Has payment methods",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = discovered.supportedMethods.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row {
                IconButton(onClick = onFollow) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Follow",
                    )
                }
                IconButton(onClick = onUnfollow) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = "Unfollow",
                    )
                }
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Contact",
                    )
                }
            }
        }
    }
}
