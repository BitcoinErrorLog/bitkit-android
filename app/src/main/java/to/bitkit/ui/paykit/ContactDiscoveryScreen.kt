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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.R
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.services.DiscoveredContact
import to.bitkit.paykit.viewmodels.ContactsViewModel
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyMSB
import to.bitkit.ui.components.BodyS
import to.bitkit.ui.components.HorizontalSpacer
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.Title
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.Colors

@Composable
fun ContactDiscoveryScreen(
    onNavigateBack: () -> Unit,
    onContactDiscovered: (Contact) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val discoveredContacts by viewModel.discoveredContacts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val contactAvatars by viewModel.contactAvatars.collectAsStateWithLifecycle()
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

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenColumn {
            AppTopBar(
                titleText = stringResource(R.string.paykit__add_follow),
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.discoverContacts() },
                        enabled = !isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isLoading) Colors.White32 else Colors.White,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Colors.Gray6, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Title(text = stringResource(R.string.paykit__enter_pubkey))
                        BodyS(
                            text = stringResource(R.string.paykit__paste_a_pubky_id_to_follow_someone_directly),
                            color = Colors.White64,
                        )
                        OutlinedTextField(
                            value = pubkeyToFollow,
                            onValueChange = { pubkeyToFollow = it },
                            placeholder = { Text(stringResource(R.string.paykit__pubkey_z_base_32)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Colors.Gray5,
                                focusedContainerColor = Colors.Gray5,
                                unfocusedBorderColor = Colors.Gray5,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("PubkeyInput"),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.paykit__add_follow),
                            enabled = pubkeyToFollow.isNotBlank() && !isLoading,
                            onClick = {
                                if (pubkeyToFollow.isNotBlank()) {
                                    viewModel.followContact(pubkeyToFollow.trim())
                                    pubkeyToFollow = ""
                                }
                            },
                            modifier = Modifier.testTag("AddFollowButton"),
                        )
                    }
                }

                if (discoveredContacts.isNotEmpty()) {
                    Title(text = stringResource(R.string.paykit__your_follows))
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Colors.Brand)
                    }
                } else if (discoveredContacts.isEmpty()) {
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
                            BodyM(text = stringResource(R.string.paykit__no_follows_yet), color = Colors.White64)
                            VerticalSpacer(8.dp)
                            BodyS(
                                text = stringResource(R.string.paykit__enter_a_pubkey_above_to_add_your_first_follow),
                                color = Colors.White32,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(discoveredContacts) { discovered ->
                            DiscoveredContactRow(
                                discovered = discovered,
                                avatar = contactAvatars[discovered.pubkey]?.bitmap,
                                onLoadAvatar = { viewModel.loadAvatar(discovered.pubkey, discovered.avatarUrl) },
                                onAdd = {
                                    onContactDiscovered(
                                        Contact.create(
                                            publicKeyZ32 = discovered.pubkey,
                                            name = discovered.name ?: "",
                                            avatarUrl = discovered.avatarUrl,
                                        ),
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DiscoveredContactRow(
    discovered: DiscoveredContact,
    avatar: android.graphics.Bitmap?,
    onLoadAvatar: () -> Unit,
    onAdd: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
) {
    LaunchedEffect(discovered.pubkey, discovered.avatarUrl) {
        onLoadAvatar()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Gray6, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(Colors.White16, CircleShape)
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Colors.White16, CircleShape)
                        .clip(CircleShape)
                )
            } else {
                BodyMSB(
                    text = (discovered.name?.take(1) ?: discovered.pubkey.take(1)).uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalSpacer(12.dp)

        Column(modifier = Modifier.weight(1f)) {
            BodyMSB(
                text = discovered.name ?: discovered.pubkey.take(16) + "...",
                color = Colors.White,
            )
            BodyS(
                text = discovered.pubkey.take(8) + "..." + discovered.pubkey.takeLast(8),
                color = Colors.White64,
            )
            if (discovered.hasPaymentMethods) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lightning),
                        contentDescription = null,
                        tint = Colors.Yellow,
                        modifier = Modifier.size(14.dp),
                    )
                    BodyS(
                        text = discovered.supportedMethods.joinToString(", "),
                        color = Colors.Yellow,
                    )
                }
            }
        }

        IconButton(onClick = onUnfollow) {
            Icon(
                imageVector = Icons.Default.PersonRemove,
                contentDescription = "Unfollow",
                tint = Colors.Red,
            )
        }
    }
}
