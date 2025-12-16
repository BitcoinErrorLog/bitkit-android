package to.bitkit.paykit.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.bitkit.utils.Logger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for endpoint rotation configuration and history.
 */

/**
 * Rotation policy types
 */
@Serializable
enum class RotationPolicy(val displayName: String, val description: String) {
    ON_USE("on-use", "Rotate on every use"),
    AFTER_USES("after-uses", "Rotate after N uses"),
    MANUAL("manual", "Manual only");

    companion object {
        fun fromString(value: String): RotationPolicy {
            return values().firstOrNull { it.name == value || it.displayName == value } ?: ON_USE
        }
    }
}

/**
 * Rotation settings for a specific method
 */
@Serializable
data class MethodRotationSettings(
    var policy: String = RotationPolicy.ON_USE.displayName,
    var threshold: Int = 5, // For afterUses policy
    var useCount: Int = 0,
    var lastRotated: Long? = null,
    var rotationCount: Int = 0
) {
    fun getPolicy(): RotationPolicy {
        return RotationPolicy.fromString(policy)
    }
}

/**
 * Global rotation settings
 */
@Serializable
data class RotationSettings(
    var autoRotateEnabled: Boolean = true,
    var defaultPolicy: String = RotationPolicy.ON_USE.displayName,
    var defaultThreshold: Int = 5,
    var methodSettings: Map<String, MethodRotationSettings> = emptyMap()
)

/**
 * Rotation event for history tracking
 */
@Serializable
data class RotationEvent(
    val id: String = UUID.randomUUID().toString(),
    val methodId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String
)

/**
 * Manages rotation settings and history persistence
 */
@Singleton
class RotationSettingsStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage
) {
    companion object {
        private const val TAG = "RotationSettingsStorage"
        private const val MAX_HISTORY_EVENTS = 100
    }

    private val identityName: String = "default"

    private val settingsKey: String
        get() = "rotation_settings.$identityName"

    private val historyKey: String
        get() = "rotation_history.$identityName"

    // MARK: - Settings

    fun loadSettings(): RotationSettings {
        return try {
            val data = keychain.retrieve(settingsKey) ?: return RotationSettings()
            val json = String(data)
            Json.decodeFromString<RotationSettings>(json)
        } catch (e: Exception) {
            Logger.error("RotationSettingsStorage: Failed to load settings", e, context = TAG)
            RotationSettings()
        }
    }

    suspend fun saveSettings(settings: RotationSettings) {
        try {
            val json = Json.encodeToString(settings)
            keychain.store(settingsKey, json.toByteArray())
        } catch (e: Exception) {
            Logger.error("RotationSettingsStorage: Failed to save settings", e, context = TAG)
            throw PaykitStorageException.SaveFailed(settingsKey)
        }
    }

    fun getMethodSettings(methodId: String): MethodRotationSettings {
        val settings = loadSettings()
        return settings.methodSettings[methodId] ?: MethodRotationSettings(
            policy = settings.defaultPolicy,
            threshold = settings.defaultThreshold
        )
    }

    suspend fun updateMethodSettings(methodId: String, methodSettings: MethodRotationSettings) {
        val settings = loadSettings()
        val updatedMethodSettings = settings.methodSettings.toMutableMap()
        updatedMethodSettings[methodId] = methodSettings
        val updated = settings.copy(methodSettings = updatedMethodSettings)
        saveSettings(updated)
    }

    // MARK: - Use Tracking

    /**
     * Record a payment use for a method
     * Returns true if rotation should occur
     */
    suspend fun recordUse(methodId: String): Boolean {
        val settings = loadSettings()

        if (!settings.autoRotateEnabled) {
            return false
        }

        val current = settings.methodSettings[methodId] ?: MethodRotationSettings(
            policy = settings.defaultPolicy,
            threshold = settings.defaultThreshold
        )

        val updated = current.copy(useCount = current.useCount + 1)
        val updatedMethodSettings = settings.methodSettings.toMutableMap()
        updatedMethodSettings[methodId] = updated

        val updatedSettings = settings.copy(
            methodSettings = updatedMethodSettings
        )
        saveSettings(updatedSettings)

        return when (updated.getPolicy()) {
            RotationPolicy.ON_USE -> true
            RotationPolicy.AFTER_USES -> updated.useCount >= updated.threshold
            RotationPolicy.MANUAL -> false
        }
    }

    /**
     * Record that a rotation occurred
     */
    suspend fun recordRotation(methodId: String, reason: String) {
        val settings = loadSettings()
        val methodSettings = settings.methodSettings.toMutableMap()
        val current = methodSettings[methodId] ?: MethodRotationSettings(
            policy = settings.defaultPolicy,
            threshold = settings.defaultThreshold
        )

        val updated = current.copy(
            useCount = 0,
            lastRotated = System.currentTimeMillis(),
            rotationCount = current.rotationCount + 1
        )
        methodSettings[methodId] = updated

        val updatedSettings = settings.copy(methodSettings = methodSettings)
        saveSettings(updatedSettings)

        // Add to history
        addHistoryEvent(RotationEvent(methodId = methodId, reason = reason))
    }

    // MARK: - History

    fun loadHistory(): List<RotationEvent> {
        return try {
            val data = keychain.retrieve(historyKey) ?: return emptyList()
            val json = String(data)
            val events = Json.decodeFromString<List<RotationEvent>>(json)
            events.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Logger.error("RotationSettingsStorage: Failed to load history", e, context = TAG)
            emptyList()
        }
    }

    private suspend fun addHistoryEvent(event: RotationEvent) {
        val history = loadHistory().toMutableList()
        history.add(0, event)

        // Keep only last MAX_HISTORY_EVENTS events
        val trimmed = if (history.size > MAX_HISTORY_EVENTS) {
            history.take(MAX_HISTORY_EVENTS)
        } else {
            history
        }

        try {
            val json = Json.encodeToString(trimmed)
            keychain.store(historyKey, json.toByteArray())
        } catch (e: Exception) {
            Logger.error("RotationSettingsStorage: Failed to save history", e, context = TAG)
        }
    }

    suspend fun clearHistory() {
        try {
            keychain.delete(historyKey)
        } catch (e: Exception) {
            Logger.error("RotationSettingsStorage: Failed to clear history", e, context = TAG)
        }
    }

    // MARK: - Statistics

    fun totalRotations(): Int {
        val settings = loadSettings()
        return settings.methodSettings.values.sumOf { it.rotationCount }
    }

    fun methodsWithRotations(): List<String> {
        val settings = loadSettings()
        return settings.methodSettings
            .filter { it.value.rotationCount > 0 }
            .map { it.key }
    }
}
