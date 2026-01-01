package to.bitkit.ui.screens.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.R
import to.bitkit.paykit.services.PubkyRingBridge
import to.bitkit.paykit.services.PubkySDKService
import to.bitkit.paykit.services.SDKPubkyProfile
import to.bitkit.paykit.services.SDKProfileLink
import to.bitkit.paykit.storage.PaykitKeychainStorage
import to.bitkit.data.SettingsStore
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.scaffold.DrawerNavIcon
import to.bitkit.ui.scaffold.ScreenColumn
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.utils.Logger
import javax.inject.Inject

@Composable
fun CreateProfileScreen(
    onBack: () -> Unit,
    viewModel: CreateProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    CreateProfileContent(
        uiState = uiState,
        onBack = onBack,
        onConnectPubkyRing = { viewModel.connectPubkyRing(context) },
        onCreateNewIdentity = viewModel::createNewIdentity,
        onRefreshSession = viewModel::refreshSession,
        onNameChange = viewModel::updateName,
        onBioChange = viewModel::updateBio,
        onAddLink = viewModel::addLink,
        onRemoveLink = viewModel::removeLink,
        onUpdateLinkTitle = viewModel::updateLinkTitle,
        onUpdateLinkUrl = viewModel::updateLinkUrl,
        onSave = viewModel::saveProfile,
        onDisconnect = viewModel::disconnectIdentity,
    )
}

@Composable
private fun CreateProfileContent(
    uiState: CreateProfileUiState,
    onBack: () -> Unit,
    onConnectPubkyRing: () -> Unit,
    onCreateNewIdentity: () -> Unit,
    onRefreshSession: () -> Unit,
    onNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onAddLink: () -> Unit,
    onRemoveLink: (Int) -> Unit,
    onUpdateLinkTitle: (Int, String) -> Unit,
    onUpdateLinkUrl: (Int, String) -> Unit,
    onSave: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ScreenColumn {
        AppTopBar(
            titleText = if (uiState.hasIdentity) {
                "Edit Profile"
            } else {
                stringResource(R.string.slashtags__profile_create)
            },
            onBackClick = onBack,
            actions = {
                if (uiState.hasIdentity && uiState.hasChanges && !uiState.isSaving) {
                    TextButton(onClick = onSave) {
                        Text("Save", color = Colors.Brand)
                    }
                }
                DrawerNavIcon()
            },
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Colors.Brand)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading...",
                            color = Colors.White64
                        )
                    }
                }
            }

            !uiState.hasIdentity -> {
                NoIdentityContent(
                    isConnecting = uiState.isConnecting,
                    errorMessage = uiState.errorMessage,
                    onConnectPubkyRing = onConnectPubkyRing,
                    onCreateNewIdentity = onCreateNewIdentity,
                )
            }

            else -> {
                ProfileEditorContent(
                    uiState = uiState,
                    onRefreshSession = onRefreshSession,
                    onNameChange = onNameChange,
                    onBioChange = onBioChange,
                    onAddLink = onAddLink,
                    onRemoveLink = onRemoveLink,
                    onUpdateLinkTitle = onUpdateLinkTitle,
                    onUpdateLinkUrl = onUpdateLinkUrl,
                    onSave = onSave,
                    onDisconnect = onDisconnect,
                )
            }
        }
    }
}

@Composable
private fun NoIdentityContent(
    isConnecting: Boolean,
    errorMessage: String?,
    onConnectPubkyRing: () -> Unit,
    onCreateNewIdentity: () -> Unit,
) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Colors.Brand.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Colors.Brand,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Set Up Your Profile",
            style = MaterialTheme.typography.headlineSmall,
            color = Colors.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create a public profile so others can find and pay you. Your profile is published to your Pubky homeserver.",
            style = MaterialTheme.typography.bodyMedium,
            color = Colors.White64,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onConnectPubkyRing,
            enabled = !isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Colors.Brand,
                disabledContainerColor = Colors.Gray6
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Colors.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Connecting...")
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Connect with Pubky Ring")
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Colors.Red.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Colors.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        color = Colors.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorContent(
    uiState: CreateProfileUiState,
    onRefreshSession: () -> Unit,
    onNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onAddLink: () -> Unit,
    onRemoveLink: (Int) -> Unit,
    onUpdateLinkTitle: (Int, String) -> Unit,
    onUpdateLinkUrl: (Int, String) -> Unit,
    onSave: () -> Unit,
    onDisconnect: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Colors.Gray7),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Pubky ID",
                            style = MaterialTheme.typography.labelMedium,
                            color = Colors.White64
                        )
                        IconButton(
                            onClick = onRefreshSession,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Colors.White64,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.pubkyId.take(20) + "..." + uiState.pubkyId.takeLast(8),
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.White
                    )
                }
            }
        }

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
                }
            }
        }

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

        if (uiState.errorMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Colors.Red.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
        }

        if (uiState.successMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Colors.Green.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
        }

        item {
            Button(
                onClick = onSave,
                enabled = uiState.hasChanges && !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Brand,
                    disabledContainerColor = Colors.Gray6
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Colors.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Publishing...")
                } else {
                    Text("Publish Profile")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Colors.Red
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnect Identity")
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@HiltViewModel
class CreateProfileViewModel @Inject constructor(
    private val pubkySDKService: PubkySDKService,
    private val pubkyRingBridge: PubkyRingBridge,
    private val keychainStorage: PaykitKeychainStorage,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    companion object {
        private const val TAG = "CreateProfileViewModel"
        private const val KEY_SECRET = "pubky.identity.secret"
        private const val KEY_PUBLIC = "pubky.identity.public"
        private const val KEY_SESSION_SECRET = "pubky.session.secret"
        private const val KEY_DEVICE_ID = "pubky.paykit.deviceId"  // Device ID for noise key derivation
        private const val KEY_PROFILE_NAME = "profile.name"
        private const val KEY_PROFILE_BIO = "profile.bio"
        private const val KEY_PROFILE_AVATAR_URL = "profile.avatar_url"
    }

    private val _uiState = MutableStateFlow(CreateProfileUiState())
    val uiState: StateFlow<CreateProfileUiState> = _uiState.asStateFlow()

    private var originalProfile: SDKPubkyProfile? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        checkIdentityAndLoadProfile()
    }

    private fun checkIdentityAndLoadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Check if we have a stored pubky identity
            val storedPubkey = keychainStorage.getString(KEY_PUBLIC)

            if (storedPubkey == null) {
                _uiState.update {
                    it.copy(isLoading = false, hasIdentity = false)
                }
                return@launch
            }

            _uiState.update {
                it.copy(hasIdentity = true, pubkyId = storedPubkey)
            }

            // Restore the session if we have the session secret stored
            val sessionSecret = keychainStorage.getString(KEY_SESSION_SECRET)
            if (sessionSecret != null) {
                try {
                    pubkySDKService.importSession(storedPubkey, sessionSecret)
                    Logger.debug("Session restored for ${storedPubkey.take(12)}...", context = TAG)
                } catch (e: Exception) {
                    Logger.error("Failed to restore session, may need to reconnect", e, context = TAG)
                    _uiState.update {
                        it.copy(errorMessage = "Session expired. Please reconnect with Pubky Ring.")
                    }
                }
            }

            // Load profile from SettingsStore first (fast local cache)
            val settings = settingsStore.data.first()
            if (settings.profileName.isNotEmpty() || settings.profileBio.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        name = settings.profileName,
                        bio = settings.profileBio,
                    )
                }
            }

            // Then try to load from homeserver to get full profile including links
            loadProfile(storedPubkey)
        }
    }

    private suspend fun loadProfile(pubkey: String) {
        try {
            val profile = pubkySDKService.fetchProfile(pubkey)
            originalProfile = profile
            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = profile.name ?: "",
                    bio = profile.bio ?: "",
                    links = profile.links?.map { link ->
                        EditableLink(title = link.title, url = link.url)
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Logger.debug("No existing profile found: ${e.message}", context = TAG)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun connectPubkyRing(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }

            try {
                if (!pubkyRingBridge.isPubkyRingInstalled(context)) {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            errorMessage = "Pubky Ring app is not installed. Please install it first."
                        )
                    }
                    return@launch
                }

                // Request complete Paykit setup from Pubky Ring (session + noise keys in one request)
                // This ensures we have everything we need even if Ring becomes unavailable later
                val setupResult = pubkyRingBridge.requestPaykitSetup(context)
                
                // Import the session into PubkySDK using the session token
                pubkySDKService.importSession(setupResult.session.pubkey, setupResult.session.sessionSecret)
                
                // Store pubkey, session secret, and device ID so we can restore everything on app restart
                keychainStorage.setString(KEY_PUBLIC, setupResult.session.pubkey)
                keychainStorage.setString(KEY_SESSION_SECRET, setupResult.session.sessionSecret)
                keychainStorage.setString(KEY_DEVICE_ID, setupResult.deviceId)
                
                // Log noise key status
                if (setupResult.hasNoiseKeys) {
                    Logger.info("Received noise keypairs for Paykit (epoch 0 & 1)", context = TAG)
                } else {
                    Logger.warn("No noise keypairs received - Paykit P2P features may be limited", context = TAG)
                }
                
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        hasIdentity = true,
                        pubkyId = setupResult.session.pubkey
                    )
                }
                
                // Load the profile
                loadProfile(setupResult.session.pubkey)
                
                Logger.info("Successfully connected to Pubky Ring: ${setupResult.session.pubkey.take(16)}...", context = TAG)

            } catch (e: Exception) {
                Logger.error("Failed to connect to Pubky Ring", e, context = TAG)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = "Failed to connect: ${e.message}"
                    )
                }
            }
        }
    }

    fun createNewIdentity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }

            try {
                // Generate new keypair using pubky-core SDK
                val (secretKey, publicKey, _) = pubkySDKService.generateNewSecretKey()

                // Store the keys securely
                keychainStorage.setString(KEY_SECRET, secretKey)
                keychainStorage.setString(KEY_PUBLIC, publicKey)

                // Sign up to the default homeserver
                try {
                    pubkySDKService.signup(secretKey)
                    Logger.info("Signed up to homeserver with new identity", context = TAG)
                } catch (e: Exception) {
                    Logger.debug("Signup failed (may already exist): ${e.message}", context = TAG)
                    // Try signin instead
                    try {
                        pubkySDKService.signin(secretKey)
                        Logger.info("Signed in to homeserver with new identity", context = TAG)
                    } catch (e2: Exception) {
                        Logger.error("Both signup and signin failed", e2, context = TAG)
                    }
                }

                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        hasIdentity = true,
                        pubkyId = publicKey
                    )
                }

                Logger.info("Created new Pubky identity: ${publicKey.take(16)}...", context = TAG)

            } catch (e: Exception) {
                Logger.error("Failed to create identity", e, context = TAG)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = "Failed to create identity: ${e.message}"
                    )
                }
            }
        }
    }

    fun refreshSession() {
        checkIdentityAndLoadProfile()
    }

    fun updateName(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                hasChanges = checkHasChanges(name, it.bio, it.links),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun updateBio(bio: String) {
        _uiState.update {
            it.copy(
                bio = bio,
                hasChanges = checkHasChanges(it.name, bio, it.links),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun addLink() {
        _uiState.update {
            val newLinks = it.links + EditableLink("", "")
            it.copy(
                links = newLinks,
                hasChanges = checkHasChanges(it.name, it.bio, newLinks),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun removeLink(index: Int) {
        _uiState.update {
            val newLinks = it.links.toMutableList().apply { removeAt(index) }
            it.copy(
                links = newLinks,
                hasChanges = checkHasChanges(it.name, it.bio, newLinks),
                errorMessage = null,
                successMessage = null
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
                hasChanges = checkHasChanges(it.name, it.bio, newLinks),
                errorMessage = null,
                successMessage = null
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
                hasChanges = checkHasChanges(it.name, it.bio, newLinks),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

            val state = _uiState.value
            
            // Check if we have an identity (pubkey)
            if (state.pubkyId.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "No identity found. Please connect with Pubky Ring first."
                    )
                }
                return@launch
            }

            // Ensure we have an active session - restore it if needed
            if (!pubkySDKService.hasActiveSession()) {
                val sessionSecret = keychainStorage.getString(KEY_SESSION_SECRET)
                if (sessionSecret != null) {
                    try {
                        pubkySDKService.importSession(state.pubkyId, sessionSecret)
                        Logger.debug("Session restored before save", context = TAG)
                    } catch (e: Exception) {
                        Logger.error("Failed to restore session", e, context = TAG)
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = "Session expired. Please reconnect with Pubky Ring."
                            )
                        }
                        return@launch
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "No session found. Please connect with Pubky Ring."
                        )
                    }
                    return@launch
                }
            }

            val profile = SDKPubkyProfile(
                name = state.name.trim().takeIf { it.isNotEmpty() },
                bio = state.bio.trim().takeIf { it.isNotEmpty() },
                image = null,
                links = state.links
                    .filter { it.title.trim().isNotEmpty() && it.url.trim().isNotEmpty() }
                    .map { SDKProfileLink(it.title.trim(), it.url.trim()) }
                    .takeIf { it.isNotEmpty() }
            )

            try {
                // Publish profile to homeserver using PubkySDKService's active session
                val profileJson = json.encodeToString(profile)
                val profileUrl = "pubky://${state.pubkyId}/pub/pubky.app/profile.json"
                
                // Get the active session's secret key
                val session = pubkySDKService.activeSession()
                    ?: throw Exception("No active session")
                
                pubkySDKService.putData(profileUrl, profileJson, session.sessionSecret)

                originalProfile = profile
                
                // Save to SettingsStore for home screen display
                settingsStore.update { settings ->
                    settings.copy(
                        profileName = profile.name ?: "",
                        profileBio = profile.bio ?: "",
                        profileAvatarUrl = profile.image ?: "",
                        profilePubkyId = state.pubkyId
                    )
                }
                
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasChanges = false,
                        successMessage = "Profile published successfully!"
                    )
                }
                Logger.info("Profile published to homeserver", context = TAG)
            } catch (e: Exception) {
                Logger.error("Failed to publish profile", e, context = TAG)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Failed to publish: ${e.message}"
                    )
                }
            }
        }
    }

    fun disconnectIdentity() {
        viewModelScope.launch {
            // Clear stored keys and session
            runCatching { keychainStorage.delete(KEY_PUBLIC) }
            runCatching { keychainStorage.delete(KEY_SECRET) }
            runCatching { keychainStorage.delete(KEY_SESSION_SECRET) }
            runCatching { keychainStorage.delete(KEY_DEVICE_ID) }

            // Clear cached session and noise keys from PubkyRingBridge
            val currentPubkey = _uiState.value.pubkyId
            if (currentPubkey.isNotEmpty()) {
                pubkyRingBridge.clearSession(currentPubkey)
            }
            pubkyRingBridge.clearCache()  // Clear noise keypair cache as well

            // Clear profile from SettingsStore
            settingsStore.update { settings ->
                settings.copy(
                    profileName = "",
                    profileBio = "",
                    profileAvatarUrl = "",
                    profilePubkyId = ""
                )
            }

            // Reset all state
            originalProfile = null
            _uiState.update {
                CreateProfileUiState(
                    successMessage = "Identity disconnected"
                )
            }

            Logger.info("Disconnected identity and cleared profile state", context = TAG)
        }
    }

    private fun checkHasChanges(
        name: String,
        bio: String,
        links: List<EditableLink>,
    ): Boolean {
        val original = originalProfile
            ?: return name.isNotEmpty() || bio.isNotEmpty() || links.any { it.title.isNotEmpty() || it.url.isNotEmpty() }

        val validLinks = links.filter { it.title.isNotEmpty() && it.url.isNotEmpty() }
        val originalLinks = original.links ?: emptyList()

        return name != (original.name ?: "") ||
            bio != (original.bio ?: "") ||
            validLinks.size != originalLinks.size
    }
}

data class CreateProfileUiState(
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val isSaving: Boolean = false,
    val hasIdentity: Boolean = false,
    val pubkyId: String = "",
    val name: String = "",
    val bio: String = "",
    val links: List<EditableLink> = emptyList(),
    val hasChanges: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

data class EditableLink(
    val title: String,
    val url: String,
)

@Preview(showBackground = true)
@Composable
private fun PreviewNoIdentity() {
    AppThemeSurface {
        CreateProfileContent(
            uiState = CreateProfileUiState(hasIdentity = false),
            onBack = {},
            onConnectPubkyRing = {},
            onCreateNewIdentity = {},
            onRefreshSession = {},
            onNameChange = {},
            onBioChange = {},
            onAddLink = {},
            onRemoveLink = {},
            onUpdateLinkTitle = { _, _ -> },
            onUpdateLinkUrl = { _, _ -> },
            onSave = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewWithIdentity() {
    AppThemeSurface {
        CreateProfileContent(
            uiState = CreateProfileUiState(
                hasIdentity = true,
                pubkyId = "79jjdiuuzn7q758kjdjm35sooicb8sfo6pcpqkdgzyctpe6ix1fy",
                name = "John",
                bio = "Bitcoin enthusiast",
                links = listOf(
                    EditableLink("Twitter", "https://twitter.com/john"),
                    EditableLink("Website", "https://john.dev"),
                ),
                hasChanges = true,
            ),
            onBack = {},
            onConnectPubkyRing = {},
            onCreateNewIdentity = {},
            onRefreshSession = {},
            onNameChange = {},
            onBioChange = {},
            onAddLink = {},
            onRemoveLink = {},
            onUpdateLinkTitle = { _, _ -> },
            onUpdateLinkUrl = { _, _ -> },
            onSave = {},
            onDisconnect = {},
        )
    }
}
