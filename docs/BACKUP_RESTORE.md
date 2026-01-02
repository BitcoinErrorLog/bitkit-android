# Backup and Restore Guide (Android)

This guide covers Paykit session and key backup/restore procedures for Android.

## Table of Contents

1. [Overview](#overview)
2. [What Gets Backed Up](#what-gets-backed-up)
3. [Backup Process](#backup-process)
4. [Restore Process](#restore-process)
5. [Security Considerations](#security-considerations)
6. [Testing Procedures](#testing-procedures)

---

## Overview

Paykit sessions and noise keys are stored locally and can be exported for backup or device migration. The backup contains:

- Device ID (for key derivation consistency)
- Active sessions (pubky, session secret, expiry)
- Derived noise keypairs (device ID, epoch, encrypted secret)

**Important**: Backup data is sensitive. Always encrypt backups at rest.

---

## What Gets Backed Up

### Session Data

```kotlin
data class PubkySession(
    val pubky: String,           // z32-encoded public key
    val sessionSecret: ByteArray, // 32-byte session secret
    val expiresAt: Long,         // Unix timestamp
    val capabilities: List<String>,
    val homeserverUrl: String
)
```

### Noise Keys

```kotlin
data class BackupNoiseKey(
    val deviceId: String,
    val epoch: Int,
    val secretKey: ByteArray  // 32-byte X25519 secret key
)
```

### Full Backup Structure

```kotlin
data class BackupData(
    val deviceId: String,
    val sessions: List<PubkySession>,
    val noiseKeys: List<BackupNoiseKey>,
    val exportedAt: Date,
    val version: Int
)
```

---

## Backup Process

### Export to JSON

```kotlin
val bridge = PubkyRingBridge.shared

// Export as BackupData object
val backup = bridge.exportBackup()

// Export as JSON string
val jsonString = bridge.exportBackupAsJSON()

// Save to secure storage
saveToSecureStorage(jsonString)
```

### Encrypt Before Storage

Always encrypt backups before writing to disk:

```kotlin
suspend fun createEncryptedBackup(password: String): ByteArray {
    val bridge = PubkyRingBridge.shared
    val backupJson = bridge.exportBackupAsJSON()
    
    // Derive encryption key from password
    val salt = generateRandomBytes(16)
    val key = deriveKeyFromPassword(password, salt)
    
    // Encrypt using AES-GCM
    val encrypted = encryptAesGcm(backupJson.toByteArray(), key)
    
    // Combine salt + encrypted data
    return salt + encrypted
}
```

### Export to File

```kotlin
suspend fun exportBackupToFile(context: Context, password: String) {
    val encryptedBackup = createEncryptedBackup(password)
    
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_TITLE, "bitkit_paykit_backup_${Date().time}.bin")
    }
    
    // Handle result in activity/fragment
}
```

---

## Restore Process

### Decrypt and Import

```kotlin
suspend fun restoreFromEncryptedBackup(
    encryptedData: ByteArray,
    password: String,
    overwriteDeviceId: Boolean = false
) {
    // Extract salt (first 16 bytes)
    val salt = encryptedData.take(16).toByteArray()
    val encrypted = encryptedData.drop(16).toByteArray()
    
    // Derive key and decrypt
    val key = deriveKeyFromPassword(password, salt)
    val decrypted = decryptAesGcm(encrypted, key)
    
    // Import backup
    val jsonString = String(decrypted)
    PubkyRingBridge.shared.importBackup(jsonString, overwriteDeviceId)
}
```

### Import from JSON

```kotlin
val bridge = PubkyRingBridge.shared

// Import from JSON string
bridge.importBackup(jsonString, overwriteDeviceId = false)

// Import from BackupData object
bridge.importBackup(backupData, overwriteDeviceId = false)
```

### Device ID Handling

When restoring:

| Scenario | `overwriteDeviceId` | Result |
|----------|---------------------|--------|
| Same device | `false` | Keep existing device ID |
| New device, same keys | `true` | Restore original device ID |
| New device, fresh start | `false` | Keep new device ID (keys may differ) |

**Warning**: If device ID differs and `overwriteDeviceId = false`, derived noise keys will not match the backup. Only session data will be usable.

---

## Security Considerations

### Key Derivation

```kotlin
private fun deriveKeyFromPassword(password: String, salt: ByteArray): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(
        password.toCharArray(),
        salt,
        100_000,  // iterations
        256       // key length in bits
    )
    return factory.generateSecret(spec).encoded
}
```

### Secure Deletion

After restoring or if backup is no longer needed:

```kotlin
fun secureDeleteBackup(filePath: String) {
    val file = File(filePath)
    if (file.exists()) {
        // Overwrite with random data
        val randomBytes = generateRandomBytes(file.length().toInt())
        FileOutputStream(file).use { it.write(randomBytes) }
        // Then delete
        file.delete()
    }
}
```

### Keystore Integration

For device-local backups, consider using Android Keystore:

```kotlin
fun encryptWithKeystore(data: ByteArray): ByteArray {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    
    val key = keyStore.getKey("paykit_backup_key", null) as SecretKey
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    
    return cipher.iv + cipher.doFinal(data)
}
```

---

## Testing Procedures

### Manual Testing Checklist

1. **Export Backup**
   - [ ] Export creates valid JSON
   - [ ] All sessions included
   - [ ] All noise keys included
   - [ ] Device ID captured

2. **Encryption**
   - [ ] Encrypted backup differs from plaintext
   - [ ] Wrong password fails decryption
   - [ ] Correct password succeeds

3. **Import Backup**
   - [ ] Sessions restored correctly
   - [ ] Noise keys restored correctly
   - [ ] Existing sessions preserved (no overwrite)

4. **Device ID Handling**
   - [ ] Same device: ID unchanged
   - [ ] New device + overwrite: ID restored
   - [ ] New device + no overwrite: ID preserved

5. **Edge Cases**
   - [ ] Empty backup (no sessions)
   - [ ] Expired sessions handled
   - [ ] Corrupted backup rejected
   - [ ] Version mismatch handled

### Automated Tests

```kotlin
@Test
fun testBackupRestoreRoundtrip() = runTest {
    val bridge = PubkyRingBridge.shared
    
    // Setup: Create some sessions
    val session = createTestSession()
    bridge.cacheSession(session)
    
    // Export
    val backup = bridge.exportBackup()
    
    // Clear state
    bridge.clearAllCaches()
    
    // Import
    bridge.importBackup(backup)
    
    // Verify
    val restored = bridge.getCachedSession(session.pubky)
    assertEquals(session.pubky, restored?.pubky)
    assertArrayEquals(session.sessionSecret, restored?.sessionSecret)
}

@Test
fun testEncryptedBackupRoundtrip() = runTest {
    val password = "test_password_123"
    
    val encrypted = createEncryptedBackup(password)
    
    // Verify decryption with correct password
    restoreFromEncryptedBackup(encrypted, password)
    
    // Verify wrong password fails
    assertThrows<Exception> {
        restoreFromEncryptedBackup(encrypted, "wrong_password")
    }
}
```

---

## Recovery Scenarios

### Scenario 1: Lost Device

1. Install Bitkit on new device
2. Import encrypted backup from cloud storage
3. Set `overwriteDeviceId = true` to restore original device ID
4. Re-authenticate with Pubky-ring if sessions expired

### Scenario 2: App Reinstall (Same Device)

1. Import backup (device ID usually preserved via Android backup)
2. Set `overwriteDeviceId = false`
3. Sessions should work immediately

### Scenario 3: Corrupted Local Storage

1. Clear app data
2. Import backup
3. Set `overwriteDeviceId = true`
4. Validate all sessions active

---

## Related Documentation

- [SECURITY_AUDIT_REPORT.md](SECURITY_AUDIT_REPORT.md) - Key storage security
- [PAYKIT_SETUP.md](PAYKIT_SETUP.md) - Initial configuration
- [CROSS_APP_TESTING.md](CROSS_APP_TESTING.md) - Testing with Pubky-ring

