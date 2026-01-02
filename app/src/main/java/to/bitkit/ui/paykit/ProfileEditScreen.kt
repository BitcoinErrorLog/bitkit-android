package to.bitkit.ui.paykit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.bitkit.paykit.KeyManager
import to.bitkit.paykit.services.DirectoryService
import to.bitkit.paykit.services.PubkyProfile
import to.bitkit.paykit.services.PubkyProfileLink
import to.bitkit.paykit.services.DirectoryError
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.utils.Logger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun ProfileEditScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileEditContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onNameChange = viewModel::updateName,
        onBioChange = viewModel::updateBio,
        onAddLink = viewModel::addLink,
        onRemoveLink = viewModel::removeLink,
        onUpdateLinkTitle = viewModel::updateLinkTitle,
        onUpdateLinkUrl = viewModel::updateLinkUrl,
        onSave = viewModel::saveProfile,
    )
}

@Composable
private fun ProfileEditContent(
    uiState: ProfileEditUiState,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onAddLink: () -> Unit,
    onRemoveLink: (Int) -> Unit,
    onUpdateLinkTitle: (Int, String) -> Unit,
    onUpdateLinkUrl: (Int, String) -> Unit,
    onSave: () -> Unit,
) {
    ScreenColumn {
        AppTopBar(
            titleText = "Edit Profile", // TODO: Localize via Transifex
            onBackClick = onBackClick,
            actions = {
                if (uiState.hasChanges && !uiState.isSaving) {
                    TextButton(onClick = onSave) {
                        Text("Save", color = Colors.Brand)
                    }
                }
            },
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Colors.Brand)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading profile...",
                        color = Colors.White64
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Avatar section
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Colors.Brand.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.name.isNotEmpty()) {
                                    Text(
                                        text = uiState.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Colors.Brand
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Colors.Brand,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to change avatar",
                                style = MaterialTheme.typography.bodySmall,
                                color = Colors.White64
                            )
                        }
                    }
                }

                // Name field
                item {
                    Column {
                        Text(
                            text = "Display Name",
                            style = MaterialTheme.typography.bodySmall,
                            color = Colors.White64
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = onNameChange,
                            placeholder = { Text("Enter your name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Colors.Gray6,
                                focusedContainerColor = Colors.Gray6,
                                unfocusedBorderColor = Colors.Gray6,
                                focusedBorderColor = Colors.Brand,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                        )
                    }
                }

                // Bio field
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bio",
                                style = MaterialTheme.typography.bodySmall,
                                color = Colors.White64
                            )
                            Text(
                                text = "${uiState.bio.length}/160",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.bio.length > 160) Colors.Red else Colors.White64
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.bio,
                            onValueChange = onBioChange,
                            placeholder = { Text("Tell people about yourself") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Colors.Gray6,
                                focusedContainerColor = Colors.Gray6,
                                unfocusedBorderColor = Colors.Gray6,
                                focusedBorderColor = Colors.Brand,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }

                // Links section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Links",
                            style = MaterialTheme.typography.bodySmall,
                            color = Colors.White64
                        )
                        TextButton(onClick = onAddLink) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Colors.Brand,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Link", color = Colors.Brand)
                        }
                    }
                }

                // Link items
                itemsIndexed(uiState.links) { index, link ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Colors.Gray7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = link.title,
                                    onValueChange = { onUpdateLinkTitle(index, it) },
                                    placeholder = { Text("Title") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Colors.Gray6,
                                        focusedContainerColor = Colors.Gray6,
                                        unfocusedBorderColor = Colors.Gray6,
                                        focusedBorderColor = Colors.Brand,
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    singleLine = true,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { onRemoveLink(index) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove link",
                                        tint = Colors.Red
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = link.url,
                                onValueChange = { onUpdateLinkUrl(index, it) },
                                placeholder = { Text("URL") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = Colors.Gray6,
                                    focusedContainerColor = Colors.Gray6,
                                    unfocusedBorderColor = Colors.Gray6,
                                    focusedBorderColor = Colors.Brand,
                                ),
                                shape = RoundedCornerShape(6.dp),
                                singleLine = true,
                            )
                        }
                    }
                }

                // Error message
                if (uiState.errorMessage != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Colors.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage,
                                color = Colors.Red,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Success message
                if (uiState.successMessage != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Colors.Green,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.successMessage,
                                color = Colors.Green,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Save button
                item {
                    Button(
                        onClick = onSave,
                        enabled = uiState.hasChanges && !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Colors.Brand,
                            disabledContainerColor = Colors.Gray6
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Colors.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Publish to Pubky")
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val directoryService: DirectoryService,
    private val keyManager: KeyManager,
) : ViewModel() {
    companion object {
        private const val TAG = "ProfileEditViewModel"
    }

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    private var originalProfile: PubkyProfile? = null

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val pubkey = keyManager.getCurrentPublicKeyZ32()
            if (pubkey == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            try {
                val profile = directoryService.fetchProfile(pubkey)
                if (profile != null) {
                    originalProfile = profile
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = profile.name ?: "",
                            bio = profile.bio ?: "",
                            links = profile.links?.map { link ->
                                EditableLinkState(title = link.title, url = link.url)
                            } ?: emptyList()
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DirectoryError.NotConfigured) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Not connected to Pubky")
                }
            } catch (e: DirectoryError.NetworkError) {
                Logger.error("Network error loading profile", e, context = TAG)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Network error: ${e.message}")
                }
            } catch (e: DirectoryError.NotFound) {
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: DirectoryError) {
                Logger.error("Directory error loading profile", e, context = TAG)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to load profile: ${e.message}")
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                hasChanges = checkHasChanges(name, it.bio, it.links)
            )
        }
    }

    fun updateBio(bio: String) {
        _uiState.update {
            it.copy(
                bio = bio,
                hasChanges = checkHasChanges(it.name, bio, it.links)
            )
        }
    }

    fun addLink() {
        _uiState.update {
            val newLinks = it.links + EditableLinkState("", "")
            it.copy(
                links = newLinks,
                hasChanges = checkHasChanges(it.name, it.bio, newLinks)
            )
        }
    }

    fun removeLink(index: Int) {
        _uiState.update {
            val newLinks = it.links.toMutableList().apply { removeAt(index) }
            it.copy(
                links = newLinks,
                hasChanges = checkHasChanges(it.name, it.bio, newLinks)
            )
        }
    }

    fun updateLinkTitle(index: Int, title: String) {
        _uiState.update {
            val newLinks = it.links.toMutableList()
            if (index < newLinks.size) {
                newLinks[index] = newLinks[index].copy(title = title)
            }
            it.copy(
                links = newLinks,
                hasChanges = checkHasChanges(it.name, it.bio, newLinks)
            )
        }
    }

    fun updateLinkUrl(index: Int, url: String) {
        _uiState.update {
            val newLinks = it.links.toMutableList()
            if (index < newLinks.size) {
                newLinks[index] = newLinks[index].copy(url = url)
            }
            it.copy(
                links = newLinks,
                hasChanges = checkHasChanges(it.name, it.bio, newLinks)
            )
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

            val state = _uiState.value
            val profile = PubkyProfile(
                name = state.name.takeIf { it.isNotEmpty() },
                bio = state.bio.takeIf { it.isNotEmpty() },
                image = null,
                links = state.links
                    .filter { it.title.isNotEmpty() && it.url.isNotEmpty() }
                    .map { PubkyProfileLink(it.title, it.url) }
                    .takeIf { it.isNotEmpty() },
            )

            try {
                directoryService.publishProfile(profile)
                originalProfile = profile
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasChanges = false,
                        successMessage = "Profile published successfully!"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DirectoryError.NotConfigured) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Not connected to Pubky")
                }
            } catch (e: DirectoryError.NetworkError) {
                Logger.error("Network error publishing profile", e, context = TAG)
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Network error: ${e.message}")
                }
            } catch (e: DirectoryError.PublishFailed) {
                Logger.error("Publish failed", e, context = TAG)
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Failed to publish: ${e.message}")
                }
            } catch (e: DirectoryError) {
                Logger.error("Directory error publishing profile", e, context = TAG)
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Failed to publish: ${e.message}")
                }
            }
        }
    }

    private fun checkHasChanges(
        name: String,
        bio: String,
        links: List<EditableLinkState>,
    ): Boolean {
        val original = originalProfile ?: return name.isNotEmpty() || bio.isNotEmpty() || links.isNotEmpty()

        val validLinks = links.filter { it.title.isNotEmpty() && it.url.isNotEmpty() }
        val originalLinks = original.links ?: emptyList()

        return name != (original.name ?: "") ||
            bio != (original.bio ?: "") ||
            validLinks.size != originalLinks.size
    }
}

data class ProfileEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val bio: String = "",
    val links: List<EditableLinkState> = emptyList(),
    val hasChanges: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

data class EditableLinkState(
    val title: String,
    val url: String,
)

@Preview
@Composable
private fun Preview() {
    AppThemeSurface {
        ProfileEditContent(
            uiState = ProfileEditUiState(
                name = "John",
                bio = "Bitcoin enthusiast",
                links = listOf(
                    EditableLinkState("Twitter", "https://twitter.com/john"),
                    EditableLinkState("Website", "https://john.dev"),
                ),
                hasChanges = true,
            ),
            onBackClick = {},
            onNameChange = {},
            onBioChange = {},
            onAddLink = {},
            onRemoveLink = {},
            onUpdateLinkTitle = { _, _ -> },
            onUpdateLinkUrl = { _, _ -> },
            onSave = {},
        )
    }
}
