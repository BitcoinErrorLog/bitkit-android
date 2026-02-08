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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import to.bitkit.R
import to.bitkit.paykit.models.Contact
import to.bitkit.paykit.viewmodels.ContactsViewModel
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyMSB
import to.bitkit.ui.components.BodyS
import to.bitkit.ui.components.HorizontalSpacer
import to.bitkit.ui.components.SearchInput
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.shared.modifiers.clickableAlpha
import to.bitkit.ui.theme.Colors

@Composable
fun PaykitContactsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContactDiscovery: () -> Unit = {},
    onNavigateToContactDetail: (String) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val contactAvatars by viewModel.contactAvatars.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }

    ScreenColumn {
        AppTopBar(
            titleText = stringResource(R.string.paykit__contacts),
            onBackClick = onNavigateBack,
            actions = {
                IconButton(onClick = { viewModel.loadContacts() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Colors.White,
                    )
                }
                IconButton(onClick = onNavigateToContactDiscovery) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Contact",
                        tint = MaterialTheme.colorScheme.primary,
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
            SearchInput(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = stringResource(R.string.paykit__search_contacts),
            )

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
                        BodyM(text = stringResource(R.string.paykit__no_contacts_yet), color = Colors.White64)
                        VerticalSpacer(8.dp)
                        BodyS(
                            text = stringResource(R.string.paykit__add_people_you_follow_on_pubky),
                            color = Colors.White32,
                        )
                        VerticalSpacer(24.dp)
                        SecondaryButton(
                            text = stringResource(R.string.paykit__add_follow),
                            onClick = onNavigateToContactDiscovery,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(contacts) { contact ->
                        ContactRow(
                            contact = contact,
                            avatar = contactAvatars[contact.publicKeyZ32]?.bitmap,
                            onLoadAvatar = { viewModel.loadAvatar(contact.publicKeyZ32, contact.avatarUrl) },
                            onClick = { onNavigateToContactDetail(contact.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    avatar: android.graphics.Bitmap?,
    onLoadAvatar: () -> Unit,
    onClick: () -> Unit,
) {
    LaunchedEffect(contact.publicKeyZ32, contact.avatarUrl) {
        onLoadAvatar()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Gray6, RoundedCornerShape(12.dp))
            .clickableAlpha { onClick() }
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
                    text = contact.name.take(1).uppercase().ifEmpty { "?" },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalSpacer(12.dp)

        Column(modifier = Modifier.weight(1f)) {
            BodyMSB(
                text = contact.name.ifEmpty { stringResource(R.string.paykit__unknown) },
                color = Colors.White,
            )
            BodyS(
                text = contact.abbreviatedKey,
                color = Colors.White64,
            )
            if (contact.paymentCount > 0) {
                BodyS(
                    text = "${contact.paymentCount} payments",
                    color = Colors.Green,
                )
            }
        }

        if (contact.paymentCount > 0) {
            Icon(
                painter = painterResource(R.drawable.ic_checkmark),
                contentDescription = null,
                tint = Colors.Green,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
