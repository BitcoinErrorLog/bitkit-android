package to.bitkit.paykit.types

/**
 * Type-safe wrappers for homeserver-related identifiers.
 * Prevents accidental confusion between pubkeys, URLs, and session secrets.
 */

/**
 * A z32-encoded Ed25519 public key identifying a homeserver.
 *
 * This is the pubkey of the homeserver operator, NOT a URL.
 * Used for:
 * - Identifying which homeserver a user is registered with
 * - Constructing storage paths
 * - Authenticating homeserver responses
 *
 * Example: `pk:8pinxxgqs41n4aididenw5apqp1urfmzdztr8jt4abrkdn435ewo`
 */
@JvmInline
value class HomeserverPubkey(private val rawValue: String) {
    
    /**
     * The normalized z32-encoded pubkey string (without pk: prefix)
     */
    val value: String
        get() = if (rawValue.startsWith("pk:")) rawValue.drop(3) else rawValue
    
    /**
     * Validate the pubkey format
     */
    val isValid: Boolean
        get() {
            val v = value
            // z32 pubkeys are 52 characters (256 bits / 5 bits per char)
            return v.length == 52 && v.all { c ->
                c in "ybndrfg8ejkmcpqxot1uwisza345h769"
            }
        }
    
    /**
     * Returns the pubkey with pk: prefix
     */
    val withPrefix: String
        get() = "pk:$value"
    
    override fun toString(): String = "HomeserverPubkey(${value.take(12)}...)"
}

/**
 * A resolved HTTPS URL for a homeserver's API endpoint.
 *
 * This is the actual URL to make HTTP requests to, NOT a pubkey.
 * Resolved from a HomeserverPubkey via DNS or configuration.
 *
 * Example: `https://homeserver.pubky.app`
 */
@JvmInline
value class HomeserverURL(private val rawValue: String) {
    
    /**
     * The normalized HTTPS URL string (with https://, no trailing slash)
     */
    val value: String
        get() {
            var normalized = rawValue
            if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
                normalized = "https://$normalized"
            }
            if (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }
            return normalized
        }
    
    /**
     * Validate the URL format
     */
    val isValid: Boolean
        get() = try {
            java.net.URL(value)
            value.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    
    /**
     * Construct a full URL for a pubky path
     *
     * @param ownerPubkey The owner's pubkey
     * @param path The path within the owner's storage
     * @return Full URL string for the resource
     */
    fun urlForPath(ownerPubkey: String, path: String): String {
        return "$value/$ownerPubkey$path"
    }
    
    override fun toString(): String = "HomeserverURL($value)"
}

/**
 * A session secret token for authenticated homeserver operations.
 *
 * This is a sensitive credential - handle with care.
 * Never log or expose in URLs.
 */
@JvmInline
value class SessionSecret(val hexValue: String) {
    
    /**
     * Validate the secret format
     */
    val isValid: Boolean
        get() {
            // Session secrets are typically 32 bytes = 64 hex chars
            return hexValue.length >= 32 && hexValue.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }
    
    /**
     * Get the raw bytes
     */
    val bytes: ByteArray?
        get() = try {
            if (hexValue.length % 2 != 0) null
            else hexValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            null
        }
    
    /**
     * Redacted description for logging
     */
    override fun toString(): String = "SessionSecret(***)"
}

/**
 * A z32-encoded Ed25519 public key identifying a user/owner.
 *
 * This is the user's public identity key.
 * Used for:
 * - Identifying the owner of storage paths
 * - Payment addressing
 * - Directory discovery
 */
@JvmInline
value class OwnerPubkey(private val rawValue: String) {
    
    /**
     * The normalized z32-encoded pubkey string (without pk: prefix)
     */
    val value: String
        get() = if (rawValue.startsWith("pk:")) rawValue.drop(3) else rawValue
    
    /**
     * Validate the pubkey format
     */
    val isValid: Boolean
        get() {
            val v = value
            return v.length == 52 && v.all { c ->
                c in "ybndrfg8ejkmcpqxot1uwisza345h769"
            }
        }
    
    /**
     * Returns the pubkey with pk: prefix
     */
    val withPrefix: String
        get() = "pk:$value"
    
    override fun toString(): String = "OwnerPubkey(${value.take(12)}...)"
}

/**
 * PubkyConfig extension with typed defaults
 */
object HomeserverDefaults {
    
    /**
     * The default homeserver URL (resolved from pubkey)
     */
    val defaultHomeserverURL: HomeserverURL
        get() = HomeserverURL("https://homeserver.pubky.app")
    
    /**
     * The default homeserver pubkey
     */
    val defaultHomeserverPubkey: HomeserverPubkey
        get() = HomeserverPubkey(PubkyConfig.DEFAULT_HOMESERVER)
}

/**
 * Configuration constants for Pubky
 */
object PubkyConfig {
    const val DEFAULT_HOMESERVER = "8pinxxgqs41n4aididenw5apqp1urfmzdztr8jt4abrkdn435ewo"
}

/**
 * Centralized homeserver URL resolution.
 *
 * Converts pubkeys to URLs and handles configuration.
 * This prevents hardcoded URLs scattered throughout the codebase.
 */
object HomeserverResolver {
    
    /**
     * Override for testing/development
     */
    var overrideURL: HomeserverURL? = null
    
    /**
     * Resolve a homeserver pubkey to its URL.
     *
     * In production, this would perform DNS-based discovery.
     * For now, it uses the default homeserver URL.
     *
     * @param pubkey The homeserver's pubkey
     * @return The resolved URL
     */
    fun resolve(pubkey: HomeserverPubkey): HomeserverURL {
        // Check for override (testing/development)
        overrideURL?.let { return it }
        
        // TODO: Implement DNS-based resolution
        // For now, all pubkeys resolve to the default homeserver
        return HomeserverDefaults.defaultHomeserverURL
    }
    
    /**
     * Construct a full URL for accessing a user's data on a homeserver.
     *
     * @param owner The owner's pubkey
     * @param path The path within their storage
     * @param homeserver Optional specific homeserver (defaults to owner's homeserver)
     * @return Full URL string for the resource
     */
    fun urlFor(owner: OwnerPubkey, path: String, homeserver: HomeserverPubkey? = null): String {
        val resolvedURL = resolve(homeserver ?: HomeserverDefaults.defaultHomeserverPubkey)
        return resolvedURL.urlForPath(owner.value, path)
    }
    
    /**
     * The base URL for authenticated operations.
     *
     * @param sessionPubkey The session owner's pubkey
     * @return Base URL for the session's homeserver
     */
    fun baseURLForSession(sessionPubkey: String): HomeserverURL {
        // For now, all sessions use the default homeserver
        // In production, this would be stored with the session
        return HomeserverDefaults.defaultHomeserverURL
    }
}

