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

# Run Android Lint
./gradlew lintDevDebug 2>&1
./gradlew lintDevRelease 2>&1

# Check dependencies
./gradlew :app:dependencies | grep -i "FAILED\|CONFLICT" 2>&1

# Generate ProGuard mapping (for release)
./gradlew assembleDevRelease --stacktrace

# Verify release build actually runs (critical!)
./gradlew assembleDevRelease && adb install app/build/outputs/apk/dev/release/app-dev-release.apk
```

### 2. Dependency & Configuration Verification

```bash
# Check for Rust/FFI dependencies (BitkitCore, LDKNode, Paykit, PubkyNoise)
find app/src/main/jniLibs -name "*.so" 2>/dev/null | sort

# Verify all ABIs are present
for abi in arm64-v8a armeabi-v7a x86_64; do
  echo "=== $abi ==="
  find app/src/main/jniLibs/$abi -name "*.so" 2>/dev/null || echo "Missing $abi"
done

# Verify JNI bindings exist
find app/src/main/java -name "*Bindings.kt" -o -name "*FFI.kt" -o -name "*Lib.kt"

# Check AndroidManifest.xml for required permissions
cat app/src/main/AndroidManifest.xml | grep -i "uses-permission\|uses-feature"

# Check for exported components (security risk)
cat app/src/main/AndroidManifest.xml | grep -i "android:exported"

# Verify android:exported is set on ALL components with intent-filters (Android 12+)
cat app/src/main/AndroidManifest.xml | grep -B 5 "intent-filter" | grep -c "android:exported" || echo "WARNING: Missing android:exported on components with intent-filters"

# Check for hardcoded secrets in build files
grep -rn "apiKey\|secretKey\|password\|token" --include="*.gradle*" --include="*.properties" . | grep -v "\.properties\.template\|example\|BuildConfig"

# Check ProGuard rules
cat app/proguard-rules.pro 2>&1

# Verify build.gradle.kts configuration
cat app/build.gradle.kts | grep -i "minSdk\|targetSdk\|compileSdk\|buildTypes\|minifyEnabled\|shrinkResources"

# Check network security config
cat app/src/main/res/xml/network_security_config.xml 2>/dev/null || echo "Missing network_security_config.xml"

# Verify backup exclusion
cat app/src/main/AndroidManifest.xml | grep -i "allowBackup\|fullBackupContent\|dataExtractionRules"
```

### 3. Code Quality Searches

```bash
# Find all TODOs/FIXMEs in source code
grep -rn "TODO\|FIXME\|XXX\|HACK" --include="*.kt" app/src/main | grep -v build/ | grep -v /archive/

# Find non-null assertions (dangerous)
grep -rn "!!" --include="*.kt" app/src/main | grep -v test | grep -v build/ | grep -v "//.*safe"

# Find potential memory leaks (GlobalScope usage)
grep -rn "GlobalScope\.launch\|runBlocking" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find potential secret logging
grep -rn "Log\.\|Logger\.\|println\|timber\.log" --include="*.kt" app/src/main | grep -vi test | grep -i "key\|secret\|mnemonic\|seed\|private\|passphrase"

# Find empty catch blocks (actual pattern)
grep -rn "catch.*{[\s]*}" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find catch blocks that only log (potential silent failures)
grep -rn "catch.*{[\s]*Log\|catch.*{[\s]*Logger" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find unscoped coroutine launches
grep -rn "CoroutineScope(" --include="*.kt" app/src/main | grep -v "viewModelScope\|lifecycleScope" | grep -v test | grep -v build/

# Find deprecated APIs
grep -rn "@Deprecated\|@deprecated" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find hardcoded dispatchers (prefer injection)
grep -rn "Dispatchers\.Main\|Dispatchers\.IO\|Dispatchers\.Default" --include="*.kt" app/src/main | grep -v test | grep -v build/ | wc -l
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
- ❌ Skip testing release builds (minified + obfuscated)
- ❌ Ignore Android platform version differences (12+, 13+, 14+)

## REQUIRED AUDIT CATEGORIES

For each category, read actual source files and grep for patterns:

---

### 1. Compilation & Build

- Does the project build for all variants (dev, tnet)?
- Do all build types compile (debug, release)?
- Does the **release build run** (not just compile)?
- Are there missing dependencies or unresolved symbols?
- Do Rust/JNI bindings compile correctly?
- Are all native libraries (.so files) properly included for all ABIs (arm64-v8a, armeabi-v7a, x86_64)?
- Does the E2E build configuration work?
- Are there any deprecated APIs or warnings?
- Is ProGuard/R8 configuration correct for release builds?
- Are build variants properly configured in build.gradle.kts?
- Is minSdkVersion appropriate (minimum API 26 for security)?
- Are ABI splits or universal APK properly configured?
- Are packagingOptions conflicts resolved correctly?

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

# Find LaunchedEffect(Unit) - often a red flag
grep -rn "LaunchedEffect(Unit)" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find DisposableEffect usage (check for cleanup)
grep -rn "DisposableEffect" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find remember usage (check for proper keys)
grep -rn "remember\s*{" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find derivedStateOf usage
grep -rn "derivedStateOf" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find rememberUpdatedState (for captured values in LaunchedEffect)
grep -rn "rememberUpdatedState" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find LazyColumn without keys (performance issue)
grep -rn "LazyColumn\|LazyRow" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find unstable lambdas in composables (recomposition triggers)
grep -rn "@Composable.*=\s*{" --include="*.kt" app/src/main | grep -v "remember" | grep -v test | grep -v build/
```

- Are ViewModels properly annotated with `@HiltViewModel`?
- Is state exposed via `StateFlow`/`SharedFlow` (not `LiveData`)?
- Are composables free from business logic (delegated to ViewModels)?
- Is `LaunchedEffect` used with proper keys to avoid re-execution?
- Is `LaunchedEffect(Unit)` avoided (prefer specific keys or `DisposableEffect`)?
- Is `DisposableEffect` properly cleaning up resources?
- Are `remember` blocks used for expensive computations?
- Is `rememberUpdatedState` used for values captured in `LaunchedEffect`?
- Is state hoisting properly implemented?
- Are side effects properly handled with effect handlers?
- Is recomposition minimized with proper key usage?
- Are `LazyColumn` items using stable keys (`items(key = { it.id })`)?
- Are lambdas in composables wrapped in `remember` to avoid recomposition?

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

# Find flow collection WITHOUT lifecycle awareness
grep -rn "\.collect\s*{" --include="*.kt" app/src/main | grep -v "repeatOnLifecycle\|flowWithLifecycle\|collectAsStateWithLifecycle" | grep -v test | grep -v build/

# Find repeatOnLifecycle usage (correct pattern)
grep -rn "repeatOnLifecycle\|flowWithLifecycle" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find collectAsState usage in Compose
grep -rn "collectAsState\|collectAsStateWithLifecycle" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find mutex/lock usage
grep -rn "Mutex\|synchronized\|ReentrantLock" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find structured concurrency violations
grep -rn "async\s*{" --include="*.kt" app/src/main | grep -v "coroutineScope\|supervisorScope" | grep -v test | grep -v build/
```

- Are coroutines launched with appropriate scopes (`viewModelScope`, `lifecycleScope`)?
- Is `Dispatchers.IO` used for I/O operations?
- Is `Dispatchers.Default` used for CPU-intensive work?
- Is `Dispatchers.Main` used for UI updates?
- Is `runBlocking` avoided (especially in production code)?
- Is `GlobalScope` never used?
- Are flows collected with `repeatOnLifecycle` or `collectAsStateWithLifecycle`?
- Is structured concurrency followed (avoid bare `async`)?
- Are cancellations properly handled?
- Are `async` blocks wrapped in `coroutineScope` or `supervisorScope`?

---

### 4. Error Handling

```bash
# Find non-null assertions
grep -rn "!!" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find empty catch blocks (improved pattern)
grep -rn "catch\s*([^)]*)\s*{\s*}" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Result type usage
grep -rn "Result<\|\.getOrNull\|\.getOrElse\|runCatching" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find try-catch with Result (anti-pattern per AGENTS.md)
grep -rn "try.*Result<" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find runCatching without proper handling
grep -rn "runCatching\s*{" --include="*.kt" app/src/main | grep -v "\.onFailure\|\.getOr\|\.fold" | grep -v test | grep -v build/

# Find elvis operator usage
grep -rn "\?\:" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are `!!` (non-null assertions) used only where absolutely safe?
- Are errors properly propagated with `Result<T>` or exceptions?
- Are user-facing errors displayed via UI state?
- Are errors logged appropriately without exposing secrets?
- Are retryable errors distinguished from permanent failures?
- Is error handling consistent in coroutines?
- Are `Result` methods never wrapped in try-catch (per AGENTS.md rule)?
- Is `runCatching` always followed by `.onFailure` or `.getOr*`?
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

# Find setUserAuthenticationRequired (critical for key protection)
grep -rn "setUserAuthenticationRequired\|setUserAuthenticationParameters" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find KeyPermanentlyInvalidatedException handling
grep -rn "KeyPermanentlyInvalidatedException\|InvalidKeyException" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find clipboard usage (copy address/invoice - security concern)
grep -rn "ClipboardManager\|setPrimaryClip" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are mnemonics and seeds stored ONLY in Android Keystore?
- Are secrets never logged or printed?
- Is `EncryptedSharedPreferences` used for sensitive preferences?
- Is `MasterKey` properly configured with `KeyGenParameterSpec`?
- Are Keystore keys protected with `setUserAuthenticationRequired(true)`?
- Are authentication timeouts (`setUserAuthenticationParameters`) properly set?
- Is `KeyPermanentlyInvalidatedException` handled (key invalidated on new biometric)?
- Are secrets zeroized from memory when no longer needed?
- Is there proper separation between demo/test code (plaintext OK) and production code?
- Is biometric authentication properly implemented for sensitive operations?
- Are Keystore operations wrapped in try-catch for device compatibility?
- Is clipboard cleared after timeout when copying addresses/invoices?
- Is `FLAG_SECURE` set on screens displaying sensitive information?

#### 5.2 Data Privacy & Backup Exclusion

```bash
# Find backup exclusion configuration
cat app/src/main/AndroidManifest.xml | grep -i "allowBackup\|fullBackupContent\|dataExtractionRules"

# Find File encryption
grep -rn "MODE_PRIVATE\|Context\.MODE_PRIVATE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find external storage usage (dangerous)
grep -rn "getExternalStorageDirectory\|EXTERNAL_STORAGE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find data directory usage
grep -rn "filesDir\|dataDir\|cacheDir\|noBackupFilesDir" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check for no_backup directory usage (proper pattern)
grep -rn "noBackupFilesDir" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is `android:allowBackup="false"` set in AndroidManifest.xml?
- Are backup rules properly configured to exclude sensitive data (`fullBackupContent`, `dataExtractionRules`)?
- Is the LDK storage directory excluded from backup?
- Are files created with `MODE_PRIVATE`?
- Is external storage avoided for sensitive data?
- Are database files encrypted (Room with SQLCipher if needed)?
- Is the `noBackupFilesDir` used for sensitive temporary files?
- Is state properly restored if backup is enabled (or properly handled if not)?

#### 5.3 Cryptographic Operations

```bash
# Find cryptographic operations
grep -rn "Cipher\|MessageDigest\|Mac\|Signature\|SecureRandom" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find nonce/IV handling
grep -rn "IvParameterSpec\|nonce\|Nonce" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Ed25519/X25519 usage
grep -rn "Ed25519\|X25519\|Curve25519" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find weak algorithms (MD5, SHA1)
grep -rn "\"MD5\"\|\"SHA1\"\|\"SHA-1\"" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find hardcoded IVs or keys (critical security flaw)
grep -rn "ByteArray.*=.*byteArrayOf\|val.*IV\|val.*KEY" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are cryptographic operations using modern algorithms (AES-GCM, ChaCha20-Poly1305)?
- Are nonces generated with `SecureRandom`?
- Are nonces never reused?
- Is key derivation using proper PBKDF2/HKDF?
- Are Ed25519 keys used ONLY for signatures?
- Are X25519 keys used ONLY for key exchange?
- Is there proper domain separation for different signature types?
- Are weak algorithms (MD5, SHA1) avoided?
- Are there NO hardcoded IVs or keys in source code?

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

# Find file:// and content:// URI handling
grep -rn "file://\|content://" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find intent extra handling (type confusion attacks)
grep -rn "intent\.get.*Extra\|intent\.extras" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are all external inputs (Intents, URLs, JSON) validated before use?
- Are Bitcoin addresses validated before use?
- Are BOLT11 invoices validated before parsing?
- Are deep links properly validated in `onNewIntent`?
- Is `intent.data` host/path/query validated against allowlists?
- Are `bitcoin:`, `lightning:`, `paykit:`, `pubky:` schemes registered and handled securely?
- Is URL scheme validation preventing injection attacks?
- Are `file://` and `content://` URIs rejected where not expected?
- Are intent extras type-checked (not blindly cast)?
- Are WebViews disabled or properly secured if used (JavaScript disabled, content restrictions)?
- Is App Links verification (`assetlinks.json`) properly configured for https:// links?

---

### 6. Bitcoin & Lightning Network Operations

#### 6.1 Lightning Network (LDK Node)

```bash
# Find LDK Node operations
grep -rn "LdkNode\|LightningService\|LightningRepo\|LightningNodeService" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find Lightning payment operations
grep -rn "sendPayment\|payInvoice\|bolt11" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find channel management
grep -rn "openChannel\|closeChannel\|forceClose" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find node lifecycle management
grep -rn "startNode\|stopNode\|syncWallet" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find event handling
grep -rn "onEvent\|EventHandler\|LdkEvent" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find node locking/synchronization (critical for concurrent access)
grep -rn "synchronized.*node\|Mutex.*lightning\|Lock.*node" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are Lightning operations properly synchronized (no concurrent node access)?
- Is there a locking mechanism analogous to iOS StateLocker?
- Is the node lifecycle properly managed (start/stop/restart)?
- Are Lightning events properly handled and propagated to UI?
- Are payment states properly tracked (pending/successful/failed)?
- Is channel state properly synchronized?
- Are network configuration changes (regtest/testnet/mainnet) properly handled?
- Is force-close properly gated with user confirmation?
- Are payment preimages properly stored for proof of payment?
- Is the node properly stopped before app termination?
- Are node operations idempotent (handle retries/process death)?

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
grep -rn "Double.*sat\|Float.*sat\|toDouble.*sat" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find currency conversion
grep -rn "CurrencyService\|exchangeRate\|convert\|fiat" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find BigDecimal usage (preferred for currency)
grep -rn "BigDecimal\|toBigDecimal" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find overflow-prone operations (should use USat wrapper per AGENTS.md)
grep -rn "sats\s*\+\s*sats\|sats\s*\*\|sats\s*-" --include="*.kt" app/src/main | grep -v "USat" | grep -v test | grep -v build/
```

- Is floating-point NEVER used for satoshi amounts?
- Are all amounts stored as `ULong` (satoshi)?
- Is checked arithmetic used via `USat` wrapper to prevent overflow (per AGENTS.md)?
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

# Find spending limits (atomic enforcement critical)
grep -rn "SpendingLimit\|maxAmount\|dailyLimit\|synchronized.*spending" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find receipt handling
grep -rn "Receipt\|PaymentReceipt\|proof" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find directory service
grep -rn "DirectoryService\|PubkyDirectory" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is Paykit properly initialized in Application.onCreate()?
- Are executors properly registered with Hilt?
- Are payment requests properly parsed and validated?
- Is spending limit enforcement atomic (using `synchronized` blocks or `Mutex`)?
- Are spending limit checks TOCTOU-safe (check-then-reserve atomically)?
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

# Find ViewModel injection (anti-pattern in services per AGENTS.md)
grep -rn "class.*Service.*@Inject.*ViewModel\|class.*Repository.*@Inject.*ViewModel" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find circular dependency potential
grep -rn "@Inject.*constructor" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Is the Application class annotated with `@HiltAndroidApp`?
- Are all activities annotated with `@AndroidEntryPoint`?
- Are all ViewModels annotated with `@HiltViewModel`?
- Are services and repositories NOT injecting ViewModels (per AGENTS.md)?
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

# Find foreground service type (Android 14+)
grep -rn "FOREGROUND_SERVICE_TYPE_\|foregroundServiceType" --include="*.kt" app/src/main AndroidManifest.xml | grep -v test | grep -v build/

# Find JobScheduler usage (legacy, prefer WorkManager)
grep -rn "JobScheduler\|JobService" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find AlarmManager usage
grep -rn "AlarmManager\|setExact\|setRepeating\|SCHEDULE_EXACT_ALARM" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find WakeLock usage (potential battery drain)
grep -rn "WakeLock\|acquire\|PowerManager\.PARTIAL_WAKE_LOCK" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find node operations in workers (must handle idempotency)
grep -rn "class.*Worker" --include="*.kt" app/src/main -A 20 | grep "startNode\|sendPayment"
```

- Are background tasks properly implemented with WorkManager?
- Is `PeriodicWorkRequest` used for recurring tasks (subscriptions, polling)?
- Are `OneTimeWorkRequest` used for one-off tasks?
- Are WorkManager constraints properly set (network, battery, charging)?
- Is foreground service used for long-running operations (node sync)?
- Are foreground service types declared (Android 14+: `dataSync`, `mediaPlayback`, etc.)?
- Are foreground service notifications properly displayed?
- Is `JobScheduler` avoided (deprecated)?
- Are exact alarms permission requested if using `AlarmManager` (Android 12+)?
- Are `WakeLock` used sparingly and released properly?
- Are background tasks properly cancelled when no longer needed?
- Do workers handle process death and retry idempotently?
- Do workers respect node locks/single-flight semantics?
- Are heavy FFI operations avoided on main thread in workers?

---

### 11. Push Notifications & Firebase

```bash
# Find FCM usage
grep -rn "FirebaseMessaging\|onMessageReceived\|RemoteMessage" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find notification handling
grep -rn "NotificationCompat\|NotificationManager\|NotificationChannel" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find notification permissions (Android 13+)
grep -rn "POST_NOTIFICATIONS\|Manifest\.permission\.POST_NOTIFICATIONS" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find notification channel importance
grep -rn "NotificationManager\.IMPORTANCE_\|setImportance" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check google-services.json exists
ls app/google-services.json 2>/dev/null || echo "Missing google-services.json"
```

- Is `FirebaseMessagingService` properly handling incoming messages?
- Are notifications properly displayed with required channels (Android O+)?
- Are notification channels created with appropriate importance levels?
- Is notification permission requested on Android 13+?
- Are notification intents properly secured (no exported receivers)?
- Are push tokens properly registered with backend?
- Is token refresh properly handled?
- Are notification payloads validated before processing?
- Is the Lightning node properly woken up for payment notifications?
- Is `WakeNodeWorker` properly triggered by push notifications?

---

### 12. JNI/FFI & Rust Integration

```bash
# Find JNI/FFI usage
grep -rn "external\s*fun\|System\.loadLibrary\|@JvmName" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find native library loading
grep -rn "loadLibrary\|System\.load" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find UniFFI-generated code
find app/src/main/java -name "*FFI.kt" -o -name "*Lib.kt"

# Check for native libraries for all ABIs
for abi in arm64-v8a armeabi-v7a x86_64; do
  echo "=== $abi ==="
  find app/src/main/jniLibs/$abi -name "*.so" 2>/dev/null | wc -l
done

# Find callback patterns (potential memory leaks)
grep -rn "callback\|Callback\|listener\|Listener" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find packagingOptions (conflict resolution)
cat app/build.gradle.kts | grep -A 10 "packagingOptions\|packaging {"
```

- Are all native libraries (.so) properly loaded?
- Are native libraries present for all required ABIs (arm64-v8a, armeabi-v7a, x86_64)?
- Are FFI calls properly wrapped in coroutines?
- Are callbacks from Rust properly dispatched to correct thread (`Dispatchers.Main` for UI)?
- Are Rust types properly bridged to Kotlin types?
- Are errors from Rust properly converted to Kotlin exceptions?
- Is memory management correct across FFI boundary?
- Are JNI references properly cleaned up?
- Are packagingOptions conflicts properly resolved (pickFirst/exclude)?
- Are native libraries not stripped incorrectly in release builds?
- Are there native crashes logged in logcat?
- Are crash symbols (mapping.txt + native symbols) kept for deobfuscation?

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
cat app/src/main/res/xml/network_security_config.xml 2>/dev/null || echo "Missing network_security_config.xml"

# Find cleartext traffic config
cat app/src/main/AndroidManifest.xml | grep "usesCleartextTraffic"
cat app/src/main/res/xml/network_security_config.xml 2>/dev/null | grep "cleartextTrafficPermitted"

# Find network connectivity monitoring
grep -rn "ConnectivityManager\|NetworkCallback\|NetworkRequest" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find TLS version configuration
grep -rn "TLS\|SSLContext\|connectionSpecs" --include="*.kt" app/src/main | grep -v test | grep -v build/
```

- Are network requests using proper timeouts?
- Is TLS/HTTPS required for all network operations?
- Is `android:usesCleartextTraffic="false"` set (or controlled by network_security_config.xml)?
- Is `cleartextTrafficPermitted="false"` in network_security_config.xml for production?
- Are network errors properly handled and retried where appropriate?
- Is the Electrum/Esplora backend properly configured?
- Are network configuration changes (regtest/testnet/mainnet) properly handled?
- Is certificate pinning implemented for production?
- Is TLS 1.2+ enforced (TLS 1.0/1.1 disabled)?
- Is hostname verification enabled?
- Is network connectivity properly monitored?
- Are requests properly cancelled when view/lifecycle is destroyed?
- Is proxy/VPN behavior considered for Lightning backends?

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

# Verify android:exported is set on ALL components (Android 12+ requirement)
cat app/src/main/AndroidManifest.xml | grep -E "<(activity|service|receiver|provider)" | grep -c "android:exported" || echo "Check if all components have android:exported"

# Find permission groups
cat app/src/main/AndroidManifest.xml | grep "uses-permission-group"
```

- Are all required permissions declared in AndroidManifest.xml?
- Are runtime permissions properly requested before use?
- Is permission rationale shown to users when needed?
- Are dangerous permissions minimized?
- Are all exported components properly secured (intent filters validated)?
- Is `android:exported` explicitly set on all components (required Android 12+)?
- Are permissions requested only when needed (just-in-time, not at startup)?
- Is CAMERA permission only requested for QR scanning?
- Are location permissions avoided (not needed for Bitcoin wallet)?

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
cat app/build.gradle.kts | grep -i "minifyEnabled\|shrinkResources\|proguardFiles"

# Check if mapping file is generated
ls app/build/outputs/mapping/*/mapping.txt 2>/dev/null || echo "No mapping.txt found (build release first)"

# Find -dontwarn rules (should be justified)
grep "\-dontwarn" app/proguard-rules.pro
```

- Is ProGuard/R8 enabled for release builds (`minifyEnabled true`)?
- Is `shrinkResources true` enabled (if desired)?
- Are Rust/FFI classes properly kept in ProGuard rules?
- Are UniFFI-generated classes kept?
- Are data classes used for JSON (kotlinx.serialization, Gson, Moshi) properly kept?
- Are Hilt/Dagger generated classes kept?
- Are Room entities/DAOs kept?
- Are reflection-based libraries properly configured?
- Are `-dontwarn` rules justified (documented why)?
- Is mapping file generated for crash deobfuscation?
- Are ProGuard rules tested with **actual release builds that run**?
- Is the release build tested on a real device (not just compiled)?

---

### 16. Testing Quality

```bash
# Find test files
find app/src/test -name "*.kt" 2>/dev/null | wc -l
find app/src/androidTest -name "*.kt" 2>/dev/null | wc -l

# Find test annotations
grep -rn "@Test\|@Before\|@After" --include="*.kt" app/src/test app/src/androidTest | grep -v build/

# Find mock usage
grep -rn "mock\|Mock\|mockk\|MockK" --include="*.kt" app/src/test | grep -v build/

# Find Compose testing
grep -rn "createComposeRule\|onNodeWithText\|performClick" --include="*.kt" app/src/androidTest | grep -v build/

# Find coroutine testing
grep -rn "runTest\|TestDispatcher\|StandardTestDispatcher\|UnconfinedTestDispatcher" --include="*.kt" app/src/test | grep -v build/

# Find repository tests (per AGENTS.md pattern)
find app/src/test -name "*RepoTest.kt" -o -name "*RepositoryTest.kt" 2>/dev/null
```

- Is there adequate test coverage for critical paths?
- Are Lightning operations properly tested (LightningRepoTest)?
- Are Bitcoin operations properly tested (WalletRepoTest)?
- Are security-critical operations (Keystore, crypto) properly tested?
- Are edge cases tested (network failures, invalid inputs, etc.)?
- Are ViewModels properly tested with coroutine test rules?
- Are Compose UI components tested?
- Are repository tests using proper test doubles (following patterns from existing *RepoTest.kt)?
- Are async operations tested with `runTest`?
- Are test dispatchers properly injected (not hardcoded `Dispatchers.*`)?

---

### 17. Performance & Memory

```bash
# Find potential memory leaks (Activity/Context references in static)
grep -rn "companion object.*Context\|companion object.*Activity" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find potential performance issues (nested loops)
grep -rn "for.*in.*for\|forEach.*forEach" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find unnecessary allocations
grep -rn "Array\(.*size\)\|ArrayList\(\)\|HashMap\(\)" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find large bitmap operations (potential OOM)
grep -rn "Bitmap\|BitmapFactory\|decodeStream\|decodeResource" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find database operations on main thread
grep -rn "@Dao" --include="*.kt" app/src/main -A 10 | grep "suspend\|Flow<" | grep -v "suspend\|Flow" || echo "Check for blocking DAO methods"

# Find Column/Row with many children (should use Lazy*)
grep -rn "Column\s*{" --include="*.kt" app/src/main -A 20 | grep -c "\.forEach\|\.map" | head -1
```

- Are expensive operations moved off the main thread?
- Are images properly loaded and cached (Coil/Glide)?
- Are there unnecessary allocations in hot paths?
- Is memory usage reasonable (no leaks, proper cleanup)?
- Are LazyColumn/LazyRow used for lists (not Column/Row with forEach)?
- Are database operations performed on IO dispatcher (Room with suspend/Flow)?
- Are large data structures properly managed?
- Is `LeakCanary` used in debug builds to detect leaks?
- Are bitmaps properly sampled/resized before loading?

---

### 18. Accessibility & Localization

```bash
# Find accessibility properties
grep -rn "contentDescription\|semantics" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find localization usage
grep -rn "stringResource\|R\.string\|getString" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find hardcoded strings in composables
grep -rn 'Text("' --include="*.kt" app/src/main | grep -v test | grep -v build/ | grep -v "Preview"

# Check for string resources
cat app/src/main/res/values/strings.xml | wc -l

# Find plurals usage
grep -rn "pluralStringResource\|R\.plurals" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Verify strings.xml is alphabetically ordered (per AGENTS.md)
cat app/src/main/res/values/strings.xml | grep '<string name=' | sort -c 2>&1 || echo "strings.xml may not be alphabetically ordered"
```

- Are all UI elements properly accessible?
- Are all user-facing strings in `strings.xml`?
- Are strings.xml entries alphabetically ordered (per AGENTS.md)?
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
grep -rn "https://.*\.\|http://.*\." --include="*.kt" app/src/main | grep -v test | grep -v build/ | grep -v "BuildConfig"

# Check build.gradle.kts for build types
cat app/build.gradle.kts | grep -A 20 "buildTypes\|buildFeatures"

# Find feature flags
grep -rn "FeatureFlag\|isEnabled\|feature.*enabled" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check for signing config
cat app/build.gradle.kts | grep -i "signingConfig"

# Verify keystore.properties is NOT committed
ls keystore.properties 2>/dev/null && echo "WARNING: keystore.properties should not be committed" || echo "OK: keystore.properties not in repo"
```

- Are all URLs/endpoints coming from BuildConfig (not hardcoded)?
- Are feature flags properly managed?
- Are debug vs release configurations properly separated?
- Is `BuildConfig` properly used for environment-specific values?
- Are API keys in `gradle.properties` or `keystore.properties` (NOT committed to git)?
- Is signing configuration properly set for release builds?
- Are `debuggable`, `minifyEnabled`, `shrinkResources` correct for each build type?
- Is `keystore.properties` in `.gitignore`?

---

### 20. Android-Specific Security

```bash
# Find content provider usage
grep -rn "ContentProvider\|FileProvider" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find broadcast receiver usage
grep -rn "BroadcastReceiver\|sendBroadcast\|LocalBroadcastManager" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find pending intent usage (FLAG_IMMUTABLE critical for Android 12+)
grep -rn "PendingIntent\|FLAG_IMMUTABLE\|FLAG_MUTABLE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find SQL injection potential
grep -rn "rawQuery\|execSQL" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check for tapjacking protection
grep -rn "filterTouchesWhenObscured\|setFilterTouchesWhenObscured\|FLAG_SECURE" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Find task affinity (potential security issue)
cat app/src/main/AndroidManifest.xml | grep "taskAffinity"
```

- Are ContentProviders properly secured with permissions?
- Are FileProviders properly configured with limited paths (file_paths.xml)?
- Are BroadcastReceivers not exported unless necessary?
- Are PendingIntents created with `FLAG_IMMUTABLE` (Android 12+ requirement)?
- Is Room/DAO used instead of raw SQL (prevent injection)?
- Is `FLAG_SECURE` set on screens displaying sensitive information (mnemonics, keys)?
- Is tapjacking protection implemented for sensitive screens (`filterTouchesWhenObscured`)?
- Are task affinities properly set (or default) to prevent task hijacking?

---

### 21. Android Platform Version Compliance

```bash
# Find version checks
grep -rn "Build\.VERSION\.SDK_INT\|VERSION_CODES\|@RequiresApi" --include="*.kt" app/src/main | grep -v test | grep -v build/

# Check targetSdk and compileSdk
cat app/build.gradle.kts | grep "targetSdk\|compileSdk"

# Find deprecated APIs for current targetSdk
./gradlew lintDevRelease 2>&1 | grep -i "deprecated"
```

- Is `targetSdk` set to latest stable (33+ for Android 13, 34 for Android 14)?
- Are all Android 12+ requirements met (exported components, PendingIntent flags)?
- Are all Android 13+ requirements met (notification permissions)?
- Are all Android 14+ requirements met (foreground service types, exact alarm permissions)?
- Are version checks properly used for platform-specific features?
- Are `@RequiresApi` annotations used correctly?
- Are deprecated APIs avoided or properly handled?

---

## OUTPUT FORMAT

```markdown
# Android Audit Report: Bitkit Android

## Build Status
- [ ] Dev debug build succeeds: YES/NO
- [ ] Dev release build succeeds: YES/NO
- [ ] Dev release build **runs on device**: YES/NO
- [ ] Tnet build succeeds: YES/NO
- [ ] E2E build succeeds: YES/NO
- [ ] Unit tests pass: YES/NO
- [ ] Instrumented tests pass: YES/NO
- [ ] Detekt clean: YES/NO
- [ ] Android Lint clean: YES/NO
- [ ] ProGuard configured correctly: YES/NO
- [ ] All ABIs present (arm64-v8a, armeabi-v7a, x86_64): YES/NO

## Architecture Assessment
- [ ] MVVM with Hilt used correctly: YES/NO
- [ ] StateFlow used for state: YES/NO
- [ ] Compose best practices followed: YES/NO
- [ ] Lifecycle-aware flow collection: YES/NO
- [ ] Coroutines properly scoped: YES/NO
- [ ] No memory leaks (GlobalScope, runBlocking): YES/NO
- [ ] Repository pattern properly implemented: YES/NO
- [ ] LazyColumn uses stable keys: YES/NO

## Security Assessment
- [ ] Secrets stored only in Keystore: YES/NO
- [ ] No secrets in logs: YES/NO
- [ ] EncryptedSharedPreferences used: YES/NO
- [ ] Backup properly excluded: YES/NO
- [ ] Cryptographic operations secure: YES/NO
- [ ] Input validation comprehensive: YES/NO
- [ ] Deep links properly validated: YES/NO
- [ ] Intent extras type-checked: YES/NO
- [ ] Biometric auth implemented: YES/NO
- [ ] KeyPermanentlyInvalidatedException handled: YES/NO
- [ ] FLAG_SECURE on sensitive screens: YES/NO

## Bitcoin/Lightning Assessment
- [ ] Lightning operations properly synchronized: YES/NO
- [ ] Node locking mechanism in place: YES/NO
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
- [ ] TOCTOU-safe spending limit checks: YES/NO

## Pubky/Noise Integration
- [ ] Noise handshake correct: YES/NO
- [ ] Session key rotation working: YES/NO
- [ ] Pubky storage paths consistent: YES/NO
- [ ] 404 handling correct: YES/NO

## Background Work & Notifications
- [ ] WorkManager properly used: YES/NO
- [ ] Work constraints properly set: YES/NO
- [ ] Foreground service for long operations: YES/NO
- [ ] Foreground service types declared (Android 14+): YES/NO
- [ ] FCM notifications handled: YES/NO
- [ ] Notification permissions requested (Android 13+): YES/NO
- [ ] Workers handle process death/retries: YES/NO

## JNI/FFI Integration
- [ ] Native libraries properly loaded: YES/NO
- [ ] All ABIs have .so files: YES/NO
- [ ] FFI calls async-wrapped: YES/NO
- [ ] Callbacks properly dispatched: YES/NO
- [ ] Errors properly bridged: YES/NO
- [ ] Memory management correct: YES/NO
- [ ] packagingOptions conflicts resolved: YES/NO

## Network Security
- [ ] TLS/HTTPS enforced: YES/NO
- [ ] Cleartext traffic blocked: YES/NO
- [ ] Certificate pinning implemented: YES/NO
- [ ] TLS 1.2+ enforced: YES/NO
- [ ] Network security config present: YES/NO

## Platform Compliance
- [ ] Android 12+ requirements met: YES/NO
- [ ] Android 13+ requirements met: YES/NO
- [ ] Android 14+ requirements met: YES/NO
- [ ] All components have android:exported: YES/NO
- [ ] PendingIntents use FLAG_IMMUTABLE: YES/NO

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

- **Android Security Engineer**: Keystore usage, EncryptedSharedPreferences, backup exclusion, permissions, exported components, PendingIntent security
- **Bitcoin/Lightning Engineer**: LDK Node integration, payment flows, channel management, RBF handling, fee calculation, node synchronization
- **Jetpack Compose Expert**: State management, recomposition, side effects, lifecycle, performance optimization, stable keys
- **Kotlin Coroutines Specialist**: Coroutine scopes, dispatchers, structured concurrency, flow collection, lifecycle awareness, cancellation
- **Hilt/DI Architect**: Dependency injection, scoping, module organization, avoiding circular dependencies
- **Mobile Security Expert**: ProGuard/R8, deep linking, WebView security, PendingIntent security, tapjacking, task hijacking
- **Protocol Engineer**: Noise handshake, Pubky storage, session management, key rotation, Pkarr resolution
- **Paykit Specialist**: Executor registration, payment request handling, receipt generation, directory service, atomic spending limits
- **WorkManager Expert**: Background work, constraints, foreground services, periodic tasks, battery optimization, idempotency
- **QA Engineer**: Test coverage, edge cases, error paths, Compose UI tests, instrumented tests, release build testing
- **Performance Engineer**: Memory leaks, allocations, database optimization, network efficiency, UI jank, LazyColumn performance
- **JNI/FFI Expert**: Native library loading, ABI coverage, callback safety, memory management, crash handling, symbolication

---

## PROTOCOL-SPECIFIC CONSIDERATIONS

### Lightning Network (LDK Node)
- Verify node lifecycle is properly managed (start/stop/restart)
- Check that concurrent node access is prevented (locking mechanism)
- Verify payment states are properly tracked and persisted
- Ensure channel state is properly synchronized
- Verify network configuration (regtest/testnet/mainnet) is correct
- Check that events are properly propagated to UI
- Verify force-close is properly gated
- Check that payment preimages are stored for proof
- Verify operations are idempotent (handle process death/retries)

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
- Check spending limit enforcement is atomic and TOCTOU-safe
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
- [ ] Test deep links with malformed Intents (inject unexpected extras)
- [ ] Verify biometric prompt appears for sensitive operations
- [ ] Check that secrets don't appear in logcat
- [ ] Verify app works correctly after process death ("Don't keep activities" in Developer Options)
- [ ] Test with TalkBack enabled for accessibility
- [ ] Test background restrictions / battery optimization exclusions flow
- [ ] Verify clipboard auto-clear when copying addresses/invoices
- [ ] Test that FLAG_SECURE prevents screenshots on sensitive screens

### Performance Manual Checks
- [ ] Does the app launch in < 3 seconds cold start?
- [ ] Does scrolling remain smooth (no jank)?
- [ ] Does the UI freeze during node sync?
- [ ] Is memory usage reasonable (< 300MB typical)?
- [ ] Are there any ANRs (Application Not Responding)?
- [ ] Enable StrictMode in debug - are there violations?
- [ ] Test offline send/receive edge cases
- [ ] Test push notification delivery delays (background restrictions)

### Release Build Testing
- [ ] Does the **minified release build run** on a real device?
- [ ] Are all features working after ProGuard/R8 obfuscation?
- [ ] Can you successfully make payments in release build?
- [ ] Are crash reports deobfuscated correctly (test with mapping.txt)?

---

## FINAL CHECKLIST

Before concluding the audit:

1. [ ] Ran all build/test commands and recorded output
2. [ ] Searched for all security-critical patterns (Keystore, EncryptedSharedPreferences, secrets)
3. [ ] Read actual implementation of critical services (LightningService, WalletService, PaykitManager)
4. [ ] Verified Bitcoin/Lightning operations follow proper patterns
5. [ ] Checked Compose architecture follows best practices
6. [ ] Verified all coroutines are properly scoped
7. [ ] Verified flows are collected with lifecycle awareness
8. [ ] Checked Hilt dependency injection is correct
9. [ ] Verified WorkManager is used for background tasks
10. [ ] Checked FCM notification handling
11. [ ] Reviewed error handling for information leakage
12. [ ] Checked for proper resource cleanup (lifecycle awareness)
13. [ ] Verified accessibility and localization coverage
14. [ ] Checked test coverage for critical paths
15. [ ] Verified ProGuard/R8 configuration for release
16. [ ] **Tested actual release build on device**
17. [ ] Checked all permissions are necessary and properly requested
18. [ ] Verified deep link handling is secure (Intent validation)
19. [ ] Checked JNI/FFI integration is correct (all ABIs present)
20. [ ] Verified Android platform version compliance (12+, 13+, 14+)
21. [ ] Completed manual verification checklist
22. [ ] Verified spending limit enforcement is TOCTOU-safe
23. [ ] Checked node locking/synchronization mechanism

---

Now audit the Android codebase.
