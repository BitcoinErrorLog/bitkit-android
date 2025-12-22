# Android Production Readiness Audit

You are a team of expert auditors reviewing this Android Bitcoin/Lightning wallet app for production deployment. You must perform a comprehensive, hands-on audit - not a documentation review.

## MANDATORY FIRST STEPS (Do these before anything else)

### 1. Build & Test Verification

```bash
# Compile all variants
./gradlew compileDevDebugKotlin
./gradlew compileTnetDebugKotlin

# Build for all configurations
./gradlew assembleDevDebug
./gradlew assembleDevRelease
./gradlew assembleTnetDebug

# Build for E2E tests
E2E=true ./gradlew assembleDevRelease

# Build with geoblocking disabled
GEO=false E2E=true ./gradlew assembleDevRelease

# Run unit tests
./gradlew testDevDebugUnitTest 2>&1

# Run specific test class
./gradlew testDevDebugUnitTest --tests LightningRepoTest

# Run instrumented tests (requires emulator/device)
./gradlew connectedDevDebugAndroidTest 2>&1

# Lint using detekt
./gradlew detekt 2>&1

# Auto-format using detekt
./gradlew detekt --auto-correct 2>&1

# Check for dependency vulnerabilities
./gradlew dependencyCheckAnalyze 2>&1

# Generate ProGuard mapping (for release)
./gradlew assembleDevRelease --stacktrace
```

### 2. Dependency & Configuration Verification

```bash
# Check for Rust/FFI dependencies (BitkitCore, LDKNode, Paykit, PubkyNoise)
find app/src/main -name "*.so" -o -name "*.a" | grep -v build/

# Verify JNI bindings exist
find app/src/main/java -name "*Bindings.kt" -o -name "*FFI.kt"

# Check AndroidManifest.xml for required permissions
cat app/src/main/AndroidManifest.xml | grep -i "uses-permission\|uses-feature"

# Check for hardcoded secrets in build files
grep -rn "apiKey\|secretKey\|password" --include="*.gradle*" --include="*.properties" . | grep -v "\.properties\.template\|example"

# Check ProGuard rules
cat app/proguard-rules.pro 2>&1

# Verify build.gradle.kts configuration
cat app/build.gradle.kts | grep -i "minSdk\|targetSdk\|compileSdk\|buildTypes"
```

### 3. Code Quality Searches

```bash
# Find all TODOs/FIXMEs in source code
grep -rn "TODO\|FIXME\|XXX\|HACK" --include="*.kt" app/src/main | grep -v build/ | grep -v /archive/

# Find non-null assertions (dangerous)
grep -rn "!!" --include="*.kt" app/src/main | grep -v test | grep -v build/ | grep -v "//.*safe"

# Find potential memory leaks (GlobalScope usage)
grep -rn "GlobalScope\|runBlocking" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find potential secret logging
grep -rn "Log\.\|Logger\.\|println\|timber" --include="*.kt" app/src/main | grep -vi test | grep -i "key\|secret\|mnemonic\|seed\|private"

# Find missing error handling (empty catch blocks)
grep -rn "catch.*{$" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find unsafe coroutine launches
grep -rn "launch\s*{" --include="*.kt" app/src/main | grep -v "viewModelScope\|lifecycleScope" | grep -v test | grep -v build/

# Find deprecated APIs
grep -rn "@Deprecated\|@deprecated" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

## DO NOT

- ❌ Read archive/ directories as current state
- ❌ Trust README claims without code verification
- ❌ Skim files - read the actual implementations
- ❌ Assume tests pass without running them
- ❌ Report issues from docs instead of code inspection
- ❌ Conflate demo/example code with production app code
- ❌ Ignore Compose-specific patterns and lifecycle issues
- ❌ Skip ProGuard/R8 configuration audit for release builds

## REQUIRED AUDIT CATEGORIES

For each category, read actual source files and grep for patterns:

---

### 1. Compilation & Build

- Does the project build for all variants (dev, tnet)?
- Do all build types compile (debug, release)?
- Are there missing dependencies or unresolved symbols?
- Do Rust/JNI bindings compile correctly?
- Are all native libraries (.so files) properly included for all ABIs?
- Does the E2E build configuration work?
- Are there any deprecated APIs or warnings?
- Is ProGuard/R8 configuration correct for release builds?
- Are build variants properly configured in build.gradle.kts?
- Is minSdkVersion appropriate (minimum API 26 for security)?

---

### 2. Jetpack Compose Architecture & Patterns

```bash
# Find ViewModel usage (should follow MVVM with Hilt)
grep -rn "class.*ViewModel\|HiltViewModel\|@HiltViewModel" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find StateFlow/SharedFlow usage (preferred for state)
grep -rn "StateFlow\|SharedFlow\|MutableStateFlow\|MutableSharedFlow" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find improper state management (mutable state in composables)
grep -rn "@Composable.*var\s" --include="*.kt" app/src/main | grep -v "remember\|mutableStateOf" | grep -v test | grep -v build/

# Find LaunchedEffect usage (check for proper keys)
grep -rn "LaunchedEffect" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find DisposableEffect usage (check for cleanup)
grep -rn "DisposableEffect" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find remember usage (check for proper keys)
grep -rn "remember\s*{" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find derivedStateOf usage
grep -rn "derivedStateOf" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are ViewModels properly annotated with `@HiltViewModel`?
- Is state exposed via `StateFlow`/`SharedFlow` (not `LiveData`)?
- Are composables free from business logic (delegated to ViewModels)?
- Is `LaunchedEffect` used with proper keys to avoid re-execution?
- Is `DisposableEffect` properly cleaning up resources?
- Are `remember` blocks used for expensive computations?
- Is state hoisting properly implemented?
- Are side effects properly handled with effect handlers?
- Is recomposition minimized with proper key usage?

---

### 3. Kotlin Coroutines & Concurrency

```bash
# Find coroutine scope usage
grep -rn "viewModelScope\|lifecycleScope\|CoroutineScope" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Dispatchers usage
grep -rn "Dispatchers\.\|withContext" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find blocking operations (dangerous in coroutines)
grep -rn "runBlocking\|Thread\.sleep" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find GlobalScope usage (anti-pattern)
grep -rn "GlobalScope" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find flow collection
grep -rn "\.collect\|collectLatest\|collectAsState" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find mutex/lock usage
grep -rn "Mutex\|synchronized\|ReentrantLock" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are coroutines launched with appropriate scopes (`viewModelScope`, `lifecycleScope`)?
- Is `Dispatchers.IO` used for I/O operations?
- Is `Dispatchers.Default` used for CPU-intensive work?
- Is `Dispatchers.Main` used for UI updates?
- Is `runBlocking` avoided (especially in production code)?
- Is `GlobalScope` never used?
- Are flows properly collected in lifecycle-aware manner?
- Is structured concurrency followed?
- Are cancellation properly handled?

---

### 4. Error Handling

```bash
# Find non-null assertions
grep -rn "!!" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find empty catch blocks
grep -rn "catch.*{\s*}" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Result type usage
grep -rn "Result<\|\.getOrNull\|\.getOrElse\|runCatching" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find try-catch with Result (anti-pattern)
grep -rn "try.*Result\|catch.*Result" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find elvis operator usage
grep -rn "\?\:" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are `!!` (non-null assertions) used only where absolutely safe?
- Are errors properly propagated with `Result<T>` or exceptions?
- Are user-facing errors displayed via UI state?
- Are errors logged appropriately without exposing secrets?
- Are retryable errors distinguished from permanent failures?
- Is error handling consistent in coroutines?
- Are `Result` methods never wrapped in try-catch?
- Are null safety operators (`?.`, `?:`) used properly?

---

### 5. Security (act as security engineer)

#### 5.1 Android Keystore & Secure Storage

```bash
# Find Keystore usage
grep -rn "KeyStore\|KeyGenParameterSpec\|SecretKey\|AndroidKeyStore" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find EncryptedSharedPreferences usage
grep -rn "EncryptedSharedPreferences\|MasterKey" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find plaintext secret storage
grep -rn "SharedPreferences\|DataStore\|File.*write" --include="*.kt" app/src/main | grep -i "key\|secret\|mnemonic\|seed" | grep -v test | grep -v build/

# Find secret logging
grep -rn "Log\.\|Logger\.\|println" --include="*.kt" app/src/main | grep -i "key\|secret\|mnemonic\|seed\|private" | grep -v test | grep -v build/

# Find BiometricPrompt usage
grep -rn "BiometricPrompt\|BiometricManager" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are mnemonics and seeds stored ONLY in Android Keystore?
- Are secrets never logged or printed?
- Is `EncryptedSharedPreferences` used for sensitive preferences?
- Is `MasterKey` properly configured with `KeyGenParameterSpec`?
- Are Keystore keys protected with `setUserAuthenticationRequired(true)`?
- Are secrets zeroized from memory when no longer needed?
- Is there proper separation between demo/test code (plaintext OK) and production code?
- Is biometric authentication properly implemented for sensitive operations?
- Are Keystore operations wrapped in try-catch for device compatibility?

#### 5.2 Data Privacy & Backup Exclusion

```bash
# Find backup exclusion configuration
cat app/src/main/AndroidManifest.xml | grep -i "allowBackup\|fullBackupContent\|dataExtractionRules"

# Find File encryption
grep -rn "MODE_PRIVATE\|Context\.MODE_PRIVATE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find external storage usage (dangerous)
grep -rn "getExternalStorageDirectory\|EXTERNAL_STORAGE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find data directory usage
grep -rn "filesDir\|dataDir\|cacheDir" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is `android:allowBackup="false"` set in AndroidManifest.xml?
- Are backup rules properly configured to exclude sensitive data?
- Is the LDK storage directory excluded from backup?
- Are files created with `MODE_PRIVATE`?
- Is external storage avoided for sensitive data?
- Are database files encrypted?
- Is the `no_backup` directory used for sensitive temporary files?

#### 5.3 Cryptographic Operations

```bash
# Find cryptographic operations
grep -rn "Cipher\|MessageDigest\|Mac\|Signature\|SecureRandom" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find nonce/IV handling
grep -rn "IvParameterSpec\|nonce\|Nonce" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Ed25519/X25519 usage
grep -rn "Ed25519\|X25519\|Curve25519" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find weak algorithms (MD5, SHA1)
grep -rn "MD5\|SHA1\|SHA-1" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are cryptographic operations using modern algorithms (AES-GCM, ChaCha20-Poly1305)?
- Are nonces generated with `SecureRandom`?
- Are nonces never reused?
- Is key derivation using proper PBKDF2/HKDF?
- Are Ed25519 keys used ONLY for signatures?
- Are X25519 keys used ONLY for key exchange?
- Is there proper domain separation for different signature types?
- Are weak algorithms (MD5, SHA1) avoided?

#### 5.4 Input Validation & Deep Linking

```bash
# Find Intent handling
grep -rn "onNewIntent\|Intent\.get\|getIntent" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find deep link configuration
cat app/src/main/AndroidManifest.xml | grep -i "intent-filter\|data android:scheme"

# Find URL parsing
grep -rn "Uri\.parse\|URL(\|URLDecoder" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find JSON parsing
grep -rn "Json\|Gson\|Moshi\|kotlinx\.serialization" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find WebView usage (potential security risk)
grep -rn "WebView\|WebViewClient" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are all external inputs (Intents, URLs, JSON) validated before use?
- Are Bitcoin addresses validated before use?
- Are BOLT11 invoices validated before parsing?
- Are deep links properly validated in `onNewIntent`?
- Are `bitcoin:`, `lightning:`, `paykit:`, `pubky:` schemes registered and handled securely?
- Is URL scheme validation preventing injection attacks?
- Are WebViews disabled or properly secured if used?
- Is JavaScript disabled in WebViews?

---

### 6. Bitcoin & Lightning Network Operations

#### 6.1 Lightning Network (LDK Node)

```bash
# Find LDK Node operations
grep -rn "LdkNode\|LightningService\|LightningRepo" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Lightning payment operations
grep -rn "sendPayment\|payInvoice\|bolt11" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find channel management
grep -rn "openChannel\|closeChannel\|forceClose" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find node lifecycle management
grep -rn "startNode\|stopNode\|syncWallet" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find event handling
grep -rn "onEvent\|EventHandler\|LdkEvent" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are Lightning operations properly synchronized (no concurrent node access)?
- Is the node lifecycle properly managed (start/stop/restart)?
- Are Lightning events properly handled and propagated to UI?
- Are payment states properly tracked (pending/successful/failed)?
- Is channel state properly synchronized?
- Are network configuration changes (regtest/testnet/mainnet) properly handled?
- Is force-close properly gated with user confirmation?
- Are payment preimages properly stored for proof of payment?
- Is the node properly stopped before app termination?

#### 6.2 Bitcoin Operations (BitkitCore)

```bash
# Find BitkitCore operations
grep -rn "BitkitCore\|WalletService\|WalletRepo" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find onchain payment operations
grep -rn "sendTransaction\|broadcastTx\|createTx" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find UTXO management
grep -rn "Utxo\|selectCoins\|spendable" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find RBF handling
grep -rn "rbf\|RBF\|bumpFee\|replaceable" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find address generation
grep -rn "getAddress\|newAddress\|receiveAddress" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are Bitcoin operations properly queued/synchronized?
- Is RBF (Replace-By-Fee) properly handled?
- Are transaction fees properly calculated and validated?
- Is the wallet properly synchronized with blockchain state?
- Are balance calculations accurate (confirmed vs unconfirmed)?
- Are transaction confirmations properly tracked?
- Is address reuse prevented?
- Are change outputs handled correctly?
- Are UTXO selections optimal?

#### 6.3 Financial/Arithmetic Safety

```bash
# Find amount/satoshi handling
grep -rn "ULong.*sat\|sats.*ULong\|USat" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find dangerous floating-point usage for amounts
grep -rn "Double.*sat\|Float.*sat" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find currency conversion
grep -rn "CurrencyService\|exchangeRate\|convert\|fiat" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find BigDecimal usage (preferred for currency)
grep -rn "BigDecimal\|toBigDecimal" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find overflow-prone operations
grep -rn "\.plus\|\.minus\|\.times\|\+\s*sats" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is floating-point NEVER used for satoshi amounts?
- Are all amounts stored as `ULong` (satoshi)?
- Is checked arithmetic used via `USat` wrapper to prevent overflow?
- Are currency conversions using `BigDecimal` (not `Double`)?
- Are spending limits enforced atomically (no TOCTOU races)?
- Are fee calculations accurate and validated?
- Is dust limit properly enforced?
- Are amounts displayed with proper formatting (no precision loss)?

---

### 7. Paykit Integration

```bash
# Find Paykit usage
grep -rn "Paykit\|PaykitManager\|PaykitPayment" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find executor usage
grep -rn "BitkitExecutor\|LightningExecutor\|BitcoinExecutor" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find payment request handling
grep -rn "PaymentRequest\|paykit://" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find spending limits
grep -rn "SpendingLimit\|maxAmount\|dailyLimit" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find receipt handling
grep -rn "Receipt\|PaymentReceipt\|proof" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find directory service
grep -rn "DirectoryService\|PubkyDirectory" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is Paykit properly initialized in Application.onCreate()?
- Are executors properly registered with Hilt?
- Are payment requests properly parsed and validated?
- Is spending limit enforcement atomic (using synchronized blocks)?
- Are payment receipts properly generated and stored?
- Is the directory service integration working correctly?
- Are spending limits properly enforced via Paykit?
- Is autopay properly gated with user consent?
- Are payment method preferences persisted securely?

---

### 8. Pubky & Noise Protocol Integration

```bash
# Find Pubky/Noise usage
grep -rn "Pubky\|Noise\|PubkyNoise\|PubkyRing" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Noise handshake operations
grep -rn "handshake\|Handshake\|initiator\|responder" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find session/rekey operations
grep -rn "rekey\|Rekey\|session\|Session" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Pubky storage operations
grep -rn "pubky://\|/pub/\|homeserver" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Pkarr operations
grep -rn "Pkarr\|pkarr" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is the Noise protocol handshake properly implemented?
- Are session keys properly rotated (rekeying)?
- Is the channel state machine correct (no invalid transitions)?
- Are Pubky storage paths using consistent prefixes?
- Is 404 handling correct (missing data returns `null`, not error)?
- Are public vs authenticated operations properly separated?
- Is homeserver integration working correctly?
- Is Pkarr resolution working for identity lookups?

---

### 9. Dependency Injection (Hilt)

```bash
# Find Hilt annotations
grep -rn "@HiltAndroidApp\|@AndroidEntryPoint\|@HiltViewModel\|@Inject" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Module definitions
grep -rn "@Module\|@InstallIn\|@Provides\|@Binds" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Singleton usage
grep -rn "@Singleton\|@ActivityScoped\|@ViewModelScoped" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find ViewModel injection (anti-pattern in services)
grep -rn "class.*Service.*ViewModel\|class.*Repository.*ViewModel" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is the Application class annotated with `@HiltAndroidApp`?
- Are all activities annotated with `@AndroidEntryPoint`?
- Are all ViewModels annotated with `@HiltViewModel`?
- Are services and repositories NOT injecting ViewModels?
- Are `@Singleton` annotations used appropriately?
- Are Hilt modules properly installed in the correct components?
- Are all dependencies properly provided?
- Is circular dependency avoided?

---

### 10. Background Work & WorkManager

```bash
# Find WorkManager usage
grep -rn "Worker\|WorkManager\|WorkRequest\|PeriodicWorkRequest" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find foreground service usage
grep -rn "ForegroundService\|startForeground\|NotificationChannel" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find JobScheduler usage (legacy, prefer WorkManager)
grep -rn "JobScheduler\|JobService" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find AlarmManager usage
grep -rn "AlarmManager\|setExact\|setRepeating" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find WakeLock usage (potential battery drain)
grep -rn "WakeLock\|acquire\|PowerManager" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are background tasks properly implemented with WorkManager?
- Is `PeriodicWorkRequest` used for recurring tasks (subscriptions, polling)?
- Are `OneTimeWorkRequest` used for one-off tasks?
- Are WorkManager constraints properly set (network, battery, charging)?
- Is foreground service used for long-running operations (node sync)?
- Are foreground service notifications properly displayed?
- Is `JobScheduler` avoided (deprecated)?
- Are `WakeLock` used sparingly and released properly?
- Are background tasks properly cancelled when no longer needed?

---

### 11. Push Notifications & Firebase

```bash
# Find FCM usage
grep -rn "FirebaseMessaging\|onMessageReceived\|RemoteMessage" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find notification handling
grep -rn "NotificationCompat\|NotificationManager\|NotificationChannel" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find notification permissions (Android 13+)
grep -rn "POST_NOTIFICATIONS\|requestPermission" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check google-services.json exists
ls app/google-services.json 2>/dev/null || echo "Missing google-services.json"
```

- Is `FirebaseMessagingService` properly handling incoming messages?
- Are notifications properly displayed with required channels (Android O+)?
- Is notification permission requested on Android 13+?
- Are notification intents properly secured (no exported receivers)?
- Are push tokens properly registered with backend?
- Is token refresh properly handled?
- Are notification payloads validated before processing?
- Is the Lightning node properly woken up for payment notifications?

---

### 12. JNI/FFI & Rust Integration

```bash
# Find JNI/FFI usage
grep -rn "external\s*fun\|System\.loadLibrary\|@JvmName" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find native library loading
grep -rn "loadLibrary\|System\.load" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find UniFFI-generated code
find app/src/main/java -name "*FFI.kt" -o -name "*Lib.kt"

# Check for native libraries
find app/src/main/jniLibs -name "*.so" 2>/dev/null

# Find callback patterns (potential memory leaks)
grep -rn "callback\|Callback\|listener\|Listener" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are all native libraries (.so) properly loaded?
- Are FFI calls properly wrapped in coroutines?
- Are callbacks from Rust properly dispatched to correct thread?
- Are Rust types properly bridged to Kotlin types?
- Are errors from Rust properly converted to Kotlin exceptions?
- Is memory management correct across FFI boundary?
- Are JNI references properly cleaned up?
- Are there native crashes logged in logcat?

---

### 13. Network & Transport Layer

```bash
# Find network operations
grep -rn "HttpClient\|OkHttp\|Retrofit\|Ktor" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Electrum/Esplora integration
grep -rn "Electrum\|Esplora\|electrumUrl" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find timeout configurations
grep -rn "timeout\|connectTimeout\|readTimeout" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find certificate pinning
grep -rn "CertificatePinner\|pinning" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find network security config
cat app/src/main/res/xml/network_security_config.xml 2>/dev/null

# Find network connectivity monitoring
grep -rn "ConnectivityManager\|NetworkCallback" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are network requests using proper timeouts?
- Is TLS/HTTPS required for all network operations?
- Are network errors properly handled and retried where appropriate?
- Is the Electrum/Esplora backend properly configured?
- Are network configuration changes (regtest/testnet/mainnet) properly handled?
- Is certificate pinning implemented for production?
- Is `network_security_config.xml` properly configured (cleartext traffic blocked)?
- Is network connectivity properly monitored?
- Are requests properly cancelled when view is destroyed?

---

### 14. Permissions & Runtime Security

```bash
# Check AndroidManifest permissions
cat app/src/main/AndroidManifest.xml | grep "uses-permission"

# Find runtime permission requests
grep -rn "requestPermissions\|checkSelfPermission\|shouldShowRequestPermissionRationale" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find dangerous permissions (CAMERA, LOCATION, etc.)
grep -rn "CAMERA\|ACCESS_FINE_LOCATION\|READ_EXTERNAL_STORAGE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find exported components (potential security risk)
cat app/src/main/AndroidManifest.xml | grep "android:exported"
```

- Are all required permissions declared in AndroidManifest.xml?
- Are runtime permissions properly requested before use?
- Is permission rationale shown to users when needed?
- Are dangerous permissions minimized?
- Are all exported components properly secured (intent filters validated)?
- Is `android:exported` explicitly set on all components (required Android 12+)?
- Are permissions requested only when needed (runtime, not at startup)?
- Is CAMERA permission only requested for QR scanning?

---

### 15. ProGuard/R8 Configuration

```bash
# Check ProGuard rules
cat app/proguard-rules.pro

# Find ProGuard keep rules
grep -n "\-keep\|\-keepclassmembers\|\-dontwarn" app/proguard-rules.pro

# Find missing ProGuard rules (check for reflection usage)
grep -rn "Class\.forName\|getDeclaredField\|getMethod" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check if ProGuard is enabled for release
cat app/build.gradle.kts | grep -i "minifyEnabled\|proguardFiles"
```

- Is ProGuard/R8 enabled for release builds (`minifyEnabled true`)?
- Are Rust/FFI classes properly kept in ProGuard rules?
- Are data classes used for JSON properly kept?
- Are reflection-based libraries properly configured?
- Are `-dontwarn` rules justified?
- Is mapping file generated for crash deobfuscation?
- Are ProGuard rules tested with release builds?

---

### 16. Testing Quality

```bash
# Find test files
find app/src/test -name "*.kt" 2>/dev/null
find app/src/androidTest -name "*.kt" 2>/dev/null

# Find test annotations
grep -rn "@Test\|@Before\|@After" --include="*.kt" app/src/test app/src/androidTest | grep -v build/

# Find mock usage
grep -rn "mock\|Mock\|mockk\|MockK" --include="*.kt" app/src/test | grep -v build/

# Find Compose testing
grep -rn "createComposeRule\|onNodeWithText\|performClick" --include="*.kt" app/src/androidTest | grep -v build/

# Find coroutine testing
grep -rn "runTest\|TestDispatcher\|StandardTestDispatcher" --include="*.kt" app/src/test | grep -v build/
```

- Is there adequate test coverage for critical paths?
- Are Lightning operations properly tested?
- Are Bitcoin operations properly tested?
- Are security-critical operations (Keystore, crypto) properly tested?
- Are edge cases tested (network failures, invalid inputs, etc.)?
- Are ViewModels properly tested with coroutine test rules?
- Are Compose UI components tested?
- Are repository tests using proper test doubles?
- Are async operations tested with `runTest`?

---

### 17. Performance & Memory

```bash
# Find potential memory leaks (Activity/Context references in static)
grep -rn "companion object.*Context\|companion object.*Activity" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find potential performance issues (nested loops)
grep -rn "for.*in.*for\|forEach.*forEach" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find unnecessary allocations
grep -rn "Array(.*)\|ArrayList(.*)\|HashMap(.*)" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find large bitmap operations (potential OOM)
grep -rn "Bitmap\|BitmapFactory\|decodeStream" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find database operations on main thread
grep -rn "\.query\|\.insert\|\.update\|\.delete" --include="*.kt" app/src/main | grep -v "Dispatchers\|withContext" | grep -v test | grep -v build/
```

- Are expensive operations moved off the main thread?
- Are images properly loaded and cached (Coil/Glide)?
- Are there unnecessary allocations in hot paths?
- Is memory usage reasonable (no leaks, proper cleanup)?
- Are LazyColumn/LazyRow used for lists (not Column/Row)?
- Are database operations performed on IO dispatcher?
- Are large data structures properly managed?
- Is `LeakCanary` used in debug builds to detect leaks?

---

### 18. Accessibility & Localization

```bash
# Find accessibility properties
grep -rn "contentDescription\|semantics" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find localization usage
grep -rn "stringResource\|R\.string\|getString" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find hardcoded strings in composables
grep -rn "Text(\"" --include="*.kt" app/src/main | grep -v test | grep -v build/ | grep -v "Preview"

# Check for string resources
cat app/src/main/res/values/strings.xml | wc -l

# Find plurals usage
grep -rn "pluralStringResource\|R\.plurals" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are all UI elements properly accessible?
- Are all user-facing strings in `strings.xml`?
- Are there hardcoded strings that should be localized?
- Are `contentDescription` set for images and icons?
- Are plurals properly handled with `R.plurals`?
- Is TalkBack support properly tested?
- Are font scaling and display settings respected?

---

### 19. Build Configuration & Environment

```bash
# Find BuildConfig usage
grep -rn "BuildConfig\." --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find hardcoded configuration
grep -rn "https://.*\.\|http://.*\." --include="*.kt" app/src/main | grep -v test | grep -v build/ | grep -v "BuildConfig\|Env"

# Check build.gradle.kts for build types
cat app/build.gradle.kts | grep -A 10 "buildTypes\|buildFeatures"

# Find feature flags
grep -rn "FeatureFlag\|isEnabled\|feature.*enabled" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check for signing config
cat app/build.gradle.kts | grep -i "signingConfig"
```

- Are all URLs/endpoints coming from BuildConfig (not hardcoded)?
- Are feature flags properly managed?
- Are debug vs release configurations properly separated?
- Is `BuildConfig` properly used for environment-specific values?
- Are API keys in `gradle.properties` (not committed to git)?
- Is signing configuration properly set for release builds?
- Are `debuggable` and `minifyEnabled` correct for each build type?

---

### 20. Android-Specific Security

```bash
# Find content provider usage
grep -rn "ContentProvider\|FileProvider" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find broadcast receiver usage
grep -rn "BroadcastReceiver\|sendBroadcast" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find pending intent usage
grep -rn "PendingIntent\|FLAG_IMMUTABLE\|FLAG_MUTABLE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find SQL injection potential
grep -rn "rawQuery\|execSQL" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check for tapjacking protection
grep -rn "filterTouchesWhenObscured\|FLAG_SECURE" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are ContentProviders properly secured with permissions?
- Are FileProviders properly configured with limited paths?
- Are BroadcastReceivers not exported unless necessary?
- Are PendingIntents created with `FLAG_IMMUTABLE` (Android 12+)?
- Is Room/DAO used instead of raw SQL (prevent injection)?
- Is `FLAG_SECURE` set on sensitive screens (prevent screenshots)?
- Is tapjacking protection implemented for sensitive screens?

---

## OUTPUT FORMAT

```markdown
# Android Audit Report: Bitkit Android

## Build Status
- [ ] Dev debug build succeeds: YES/NO
- [ ] Dev release build succeeds: YES/NO
- [ ] Tnet build succeeds: YES/NO
- [ ] E2E build succeeds: YES/NO
- [ ] Unit tests pass: YES/NO
- [ ] Instrumented tests pass: YES/NO
- [ ] Detekt clean: YES/NO
- [ ] ProGuard configured correctly: YES/NO

## Architecture Assessment
- [ ] MVVM with Hilt used correctly: YES/NO
- [ ] StateFlow used for state: YES/NO
- [ ] Compose best practices followed: YES/NO
- [ ] Coroutines properly scoped: YES/NO
- [ ] No memory leaks (GlobalScope, runBlocking): YES/NO
- [ ] Repository pattern properly implemented: YES/NO

## Security Assessment
- [ ] Secrets stored only in Keystore: YES/NO
- [ ] No secrets in logs: YES/NO
- [ ] EncryptedSharedPreferences used: YES/NO
- [ ] Backup properly excluded: YES/NO
- [ ] Cryptographic operations secure: YES/NO
- [ ] Input validation comprehensive: YES/NO
- [ ] Deep links properly validated: YES/NO
- [ ] Biometric auth implemented: YES/NO

## Bitcoin/Lightning Assessment
- [ ] Lightning operations properly synchronized: YES/NO
- [ ] Node lifecycle properly managed: YES/NO
- [ ] Payment states properly tracked: YES/NO
- [ ] Amount arithmetic safe (ULong, USat): YES/NO
- [ ] RBF properly handled: YES/NO
- [ ] Network configuration correct: YES/NO

## Paykit Integration
- [ ] Paykit properly initialized: YES/NO
- [ ] Executors properly registered: YES/NO
- [ ] Payment requests validated: YES/NO
- [ ] Receipts properly generated: YES/NO
- [ ] Spending limits enforced atomically: YES/NO

## Pubky/Noise Integration
- [ ] Noise handshake correct: YES/NO
- [ ] Session key rotation working: YES/NO
- [ ] Pubky storage paths consistent: YES/NO
- [ ] 404 handling correct: YES/NO

## Background Work & Notifications
- [ ] WorkManager properly used: YES/NO
- [ ] Foreground service for long operations: YES/NO
- [ ] FCM notifications handled: YES/NO
- [ ] Notification permissions requested: YES/NO
- [ ] Work constraints properly set: YES/NO

## JNI/FFI Integration
- [ ] Native libraries properly loaded: YES/NO
- [ ] FFI calls async-wrapped: YES/NO
- [ ] Callbacks properly dispatched: YES/NO
- [ ] Errors properly bridged: YES/NO
- [ ] Memory management correct: YES/NO

## Critical Issues (blocks release)
1. [Issue]: [Location] - [Description]

## High Priority (fix before release)
1. [Issue]: [Location] - [Description]

## Medium Priority (fix soon)
1. [Issue]: [Location] - [Description]

## Low Priority (technical debt)
1. [Issue]: [Location] - [Description]

## What's Actually Good
- [Positive finding with specific evidence]

## Recommended Fix Order
1. [First fix]
2. [Second fix]
```

---

## EXPERT PERSPECTIVES

Review as ALL of these experts simultaneously:

- **Android Security Engineer**: Keystore usage, EncryptedSharedPreferences, backup exclusion, permissions, exported components
- **Bitcoin/Lightning Engineer**: LDK Node integration, payment flows, channel management, RBF handling, fee calculation
- **Jetpack Compose Expert**: State management, recomposition, side effects, lifecycle, performance optimization
- **Kotlin Coroutines Specialist**: Coroutine scopes, dispatchers, structured concurrency, flow collection, cancellation
- **Hilt/DI Architect**: Dependency injection, scoping, module organization, avoiding circular dependencies
- **Mobile Security Expert**: ProGuard/R8, permissions, deep linking, WebView security, PendingIntent security
- **Protocol Engineer**: Noise handshake, Pubky storage, session management, key rotation, Pkarr resolution
- **Paykit Specialist**: Executor registration, payment request handling, receipt generation, directory service, spending limits
- **WorkManager Expert**: Background work, constraints, foreground services, periodic tasks, battery optimization
- **QA Engineer**: Test coverage, edge cases, error paths, Compose UI tests, instrumented tests
- **Performance Engineer**: Memory leaks, allocations, database optimization, network efficiency, UI jank
- **JNI/FFI Expert**: Native library loading, callback safety, memory management, crash handling

---

## PROTOCOL-SPECIFIC CONSIDERATIONS

### Lightning Network (LDK Node)
- Verify node lifecycle is properly managed (start/stop/restart)
- Check that concurrent node access is prevented
- Verify payment states are properly tracked and persisted
- Ensure channel state is properly synchronized
- Verify network configuration (regtest/testnet/mainnet) is correct
- Check that events are properly propagated to UI
- Verify force-close is properly gated
- Check that payment preimages are stored for proof

### Bitcoin Operations (BitkitCore)
- Verify operations are properly synchronized
- Check that RBF is properly handled
- Ensure fee calculations are accurate
- Verify wallet synchronization is working
- Check balance calculations (confirmed vs unconfirmed)
- Verify transaction confirmation tracking
- Check address reuse prevention
- Verify UTXO selection is optimal

### Paykit Protocol
- Verify executors are properly registered with Hilt
- Check payment request parsing and validation
- Ensure receipts are properly generated and stored
- Verify directory service integration
- Check spending limit enforcement is atomic
- Verify payment method selection logic
- Check autopay consent flow

### Noise Protocol
- Verify handshake pattern matches specification
- Check session key rotation (rekeying) is implemented
- Ensure channel state machine has no invalid transitions
- Verify rekeying is triggered at appropriate times

### Pubky Storage
- Verify path prefixes are consistent (`/pub/paykit.app/v0/`, `/pub/pubky.app/follows/`)
- Check 404 handling (missing data returns `null`, not error)
- Verify public vs authenticated operations are separated
- Check homeserver integration patterns
- Verify storage operations use proper error handling

### Ed25519/X25519 Key Usage
- Ed25519 for signatures ONLY
- X25519 for key exchange ONLY
- Never use X25519 keys for signing
- Verify keypair derivation is correct
- Check that keys are properly stored in Keystore

---

## MANUAL VERIFICATION CHECKLIST

Some things cannot be grepped. Manually verify:

### UI/UX Verification
- [ ] Does the UI handle "No Network" states gracefully?
- [ ] Do state updates cause unnecessary recompositions?
- [ ] Does the app handle background → foreground transitions correctly?
- [ ] Does the node restart properly after app restart?
- [ ] Are loading states shown during async operations?
- [ ] Are error states recoverable (retry buttons)?
- [ ] Is haptic feedback used appropriately?

### Security Manual Checks
- [ ] Run the app with LeakCanary to check for memory leaks
- [ ] Test with airplane mode to verify offline behavior
- [ ] Test deep links with malformed Intents
- [ ] Verify biometric prompt appears for sensitive operations
- [ ] Check that secrets don't appear in logcat
- [ ] Verify app works correctly after process death (don't keep activities)
- [ ] Test with TalkBack enabled for accessibility

### Performance Manual Checks
- [ ] Does the app launch in < 3 seconds cold start?
- [ ] Does scrolling remain smooth (no jank)?
- [ ] Does the UI freeze during node sync?
- [ ] Is memory usage reasonable (< 300MB typical)?
- [ ] Are there any ANRs (Application Not Responding)?

---

## FINAL CHECKLIST

Before concluding the audit:

1. [ ] Ran all build/test commands and recorded output
2. [ ] Searched for all security-critical patterns (Keystore, EncryptedSharedPreferences, secrets)
3. [ ] Read actual implementation of critical services (LightningService, WalletService, PaykitManager)
4. [ ] Verified Bitcoin/Lightning operations follow proper patterns
5. [ ] Checked Compose architecture follows best practices
6. [ ] Verified all coroutines are properly scoped
7. [ ] Checked Hilt dependency injection is correct
8. [ ] Verified WorkManager is used for background tasks
9. [ ] Checked FCM notification handling
10. [ ] Reviewed error handling for information leakage
11. [ ] Checked for proper resource cleanup (lifecycle awareness)
12. [ ] Verified accessibility and localization coverage
13. [ ] Checked test coverage for critical paths
14. [ ] Verified ProGuard/R8 configuration for release
15. [ ] Checked all permissions are necessary and properly requested
16. [ ] Verified deep link handling is secure
17. [ ] Checked JNI/FFI integration is correct
18. [ ] Completed manual verification checklist

---

Now audit the Android codebase.

