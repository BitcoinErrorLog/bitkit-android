# Paykit Integration Guide for Android

This document outlines the integration steps for Paykit Phase 4 features in Bitkit Android.

## Overview

Phase 4 adds smart checkout flow and payment profiles to Bitkit. The backend infrastructure (bitkit-core) has been implemented, and this guide covers the Android UI integration.

## Changes Made

### 1. Payment Profile UI

**Location**: Create new file `app/src/main/java/to/bitkit/ui/screens/settings/PaymentProfileScreen.kt`

**Features**:
- QR code display for Pubky URI
- Toggle switches for enabling/disabling payment methods (onchain, lightning)
- Real-time updates to published endpoints

**Implementation**:

```kotlin
@Composable
fun PaymentProfileScreen(
    viewModel: PaymentProfileViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Payment Profile",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Description
        Text(
            text = "Share your public payment profile. Let others find and pay you using your Pubky ID.",
            style = MaterialTheme.typography.body2,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // QR Code
        if (state.pubkyUri.isNotEmpty()) {
            QRCodeView(
                data = state.pubkyUri,
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            )
            
            // URI display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.pubkyUri,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = {
                        // Copy to clipboard
                        viewModel.copyToClipboard(state.pubkyUri)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Payment Methods Section
        Text(
            text = "Public Payment Methods",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // On-chain toggle
        PaymentMethodToggle(
            title = "On-chain Bitcoin",
            description = "Accept Bitcoin payments to your savings wallet",
            icon = Icons.Default.CurrencyBitcoin,
            checked = state.enableOnchain,
            onCheckedChange = { enabled ->
                viewModel.updatePaymentMethod("onchain", enabled)
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Lightning toggle
        PaymentMethodToggle(
            title = "Lightning Network",
            description = "Accept instant Lightning payments",
            icon = Icons.Default.Bolt,
            checked = state.enableLightning,
            onCheckedChange = { enabled ->
                viewModel.updatePaymentMethod("lightning", enabled)
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.Gray.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Text(
                    text = "When you enable a payment method, it will be published to your Pubky homeserver. Anyone can scan your QR code or lookup your Pubky ID to see which payment methods you accept.",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun PaymentMethodToggle(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.DarkGray
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 12.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body1,
                    color = Color.White
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
```

### 2. Payment Profile ViewModel

**Location**: `app/src/main/java/to/bitkit/ui/screens/settings/PaymentProfileViewModel.kt`

```kotlin
@HiltViewModel
class PaymentProfileViewModel @Inject constructor(
    private val bitkitCore: BitkitCoreProvider
) : ViewModel() {
    
    private val _state = MutableStateFlow(PaymentProfileState())
    val state: StateFlow<PaymentProfileState> = _state.asStateFlow()
    
    init {
        loadPaymentProfile()
    }
    
    private fun loadPaymentProfile() {
        viewModelScope.launch {
            try {
                // Get user's Pubky ID
                // TODO: Get this from wallet's key management
                // val userPublicKey = wallet.pubkyId
                
                // _state.value = _state.value.copy(
                //     pubkyUri = "pubky://$userPublicKey"
                // )
                
                // Check which methods are currently enabled
                // val methods = bitkitCore.paykitGetSupportedMethodsForKey(userPublicKey)
                // 
                // _state.value = _state.value.copy(
                //     enableOnchain = methods.methods.any { it.methodId == "onchain" },
                //     enableLightning = methods.methods.any { it.methodId == "lightning" }
                // )
                
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updatePaymentMethod(method: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) {
                    // Get the appropriate endpoint based on the method
                    val endpoint = when (method) {
                        "onchain" -> /* wallet.onchainAddress */ ""
                        "lightning" -> /* wallet.bolt11 */ ""
                        else -> return@launch
                    }
                    
                    bitkitCore.paykitSetEndpoint(method, endpoint)
                    
                    // Show success message
                    
                } else {
                    bitkitCore.paykitRemoveEndpoint(method)
                    
                    // Show success message
                }
                
                // Update state
                when (method) {
                    "onchain" -> _state.value = _state.value.copy(enableOnchain = enabled)
                    "lightning" -> _state.value = _state.value.copy(enableLightning = enabled)
                }
                
            } catch (e: Exception) {
                // Revert state on error
                when (method) {
                    "onchain" -> _state.value = _state.value.copy(enableOnchain = !enabled)
                    "lightning" -> _state.value = _state.value.copy(enableLightning = !enabled)
                }
                // Show error message
            }
        }
    }
    
    fun copyToClipboard(text: String) {
        // Copy to clipboard implementation
    }
}

data class PaymentProfileState(
    val pubkyUri: String = "",
    val enableOnchain: Boolean = false,
    val enableLightning: Boolean = false,
    val isLoading: Boolean = false
)
```

### 3. Smart Checkout Flow

**Scanner Integration**: Update scanner to handle Pubky URIs

**Location**: `app/src/main/java/to/bitkit/scanner/ScannerHandler.kt`

```kotlin
fun handleScannedData(uri: String) {
    viewModelScope.launch {
        try {
            val result = bitkitCore.decode(uri)
            
            when (result) {
                is Scanner.OnChain -> handleOnchainInvoice(result.invoice)
                is Scanner.Lightning -> handleLightningInvoice(result.invoice)
                is Scanner.PubkyPayment -> handlePubkyPayment(result.data)
                // ... other cases
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
}

private suspend fun handlePubkyPayment(data: PubkyPayment) {
    try {
        // Call smart checkout to get best available payment method
        val result = bitkitCore.paykitSmartCheckout(
            pubkey = data.pubkey,
            preferredMethod = null  // or "lightning"/"onchain" based on user preference
        )
        
        // Check if it's a private channel (requires interactive protocol)
        if (result.requiresInteractive) {
            // TODO: Implement interactive payment flow
            showMessage("Private payment flow not yet implemented")
            return
        }
        
        // Public directory payment - treat as regular invoice
        when (result.methodId) {
            "lightning" -> {
                val invoice = bitkitCore.decode(result.endpoint)
                if (invoice is Scanner.Lightning) {
                    handleLightningInvoice(invoice.invoice)
                }
            }
            "onchain" -> {
                val invoice = bitkitCore.decode("bitcoin:${result.endpoint}")
                if (invoice is Scanner.OnChain) {
                    handleOnchainInvoice(invoice.invoice)
                }
            }
        }
        
    } catch (e: Exception) {
        showError("Could not fetch payment methods for this contact")
    }
}
```

### 4. Navigation

**Add route**: In your navigation graph, add the payment profile screen:

```kotlin
composable(Route.PaymentProfile) {
    PaymentProfileScreen(
        onBack = { navController.popBackStack() }
    )
}
```

**Add menu item**: In Settings screen, add navigation link:

```kotlin
SettingsMenuItem(
    title = "Payment Profile",
    icon = Icons.Default.Person,
    onClick = { navController.navigate(Route.PaymentProfile) }
)
```

### 5. Initialize Paykit

In your app initialization (e.g., `MainActivity` or Application class):

```kotlin
lifecycleScope.launch {
    try {
        val secretKeyHex = // Get from wallet's key management
        val homeserverPubkey = // Get from user's homeserver config
        
        bitkitCore.paykitInitialize(
            secretKeyHex = secretKeyHex,
            homeserverPubkey = homeserverPubkey
        )
    } catch (e: Exception) {
        Log.e("Paykit", "Failed to initialize Paykit", e)
    }
}
```

## Testing

1. **Payment Profile**:
   - Open Settings → Payment Profile
   - Toggle on "On-chain Bitcoin"
   - Verify QR code displays your Pubky URI
   - Have another user scan the QR code and verify they see your payment endpoint

2. **Smart Checkout**:
   - Generate a Pubky URI for a test contact with published endpoints
   - Scan the QR code
   - Verify it navigates to the send flow with the correct payment method pre-filled

3. **Privacy**:
   - Verify that private channels are preferred over public directory
   - Verify that endpoints rotate after receiving payments

## Future Enhancements

1. **Interactive Payments**: Full integration of PaykitInteractive for private receipt-based payments
2. **Receipts History**: UI to display payment receipts with metadata
3. **Contact Management**: Store frequently used Pubky contacts for quick payments
4. **Rotation Automation**: Automatically rotate endpoints after use

## References

- Paykit Roadmap: `paykit-rs/PAYKIT_ROADMAP.md`
- iOS Integration: `bitkit-ios/PAYKIT_INTEGRATION.md`
- Phase 3 Report: `paykit-rs/FINAL_DELIVERY_REPORT.md`

