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
     * Cache of resolved URLs with expiry
     */
    private val cache = mutableMapOf<HomeserverPubkey, Pair<HomeserverURL, Long>>()
    
    /**
     * Known homeserver mappings (pubkey → URL)
     */
    private val knownHomeservers = mutableMapOf<String, String>()
    
    init {
        loadDefaultMappings()
    }
    
    /**
     * Load default homeserver mappings
     */
    private fun loadDefaultMappings() {
        // Production homeserver (Synonym mainnet)
        knownHomeservers["8um71us3fyw6h8wbcxb5ar3rwusy1a6u49956ikzojg3gcwd1dty"] = "https://homeserver.pubky.app"
        
        // Staging homeserver (Synonym staging)
        knownHomeservers["ufibwbmed6jeq9k4p583go95wofakh9fwpp4k734trq79pd9u1uy"] = "https://staging.homeserver.pubky.app"
    }
    
    /**
     * Add a custom homeserver mapping
     */
    fun addMapping(pubkey: HomeserverPubkey, url: HomeserverURL) {
        knownHomeservers[pubkey.value] = url.value
        cache.remove(pubkey)
    }
    
    /**
     * Resolve a homeserver pubkey to its URL.
     *
     * Resolution order:
     * 1. Check override (for testing)
     * 2. Check cache
     * 3. Check known mappings
     * 4. Fall back to default
     *
     * @param pubkey The homeserver's pubkey
     * @return The resolved URL
     */
    fun resolve(pubkey: HomeserverPubkey): HomeserverURL {
        // 1. Check for override (testing/development)
        overrideURL?.let { return it }
        
        // 2. Check cache
        val now = System.currentTimeMillis()
        cache[pubkey]?.let { (url, expires) ->
            if (now < expires) return url
        }
        
        // 3. Check known mappings
        knownHomeservers[pubkey.value]?.let { urlString ->
            val url = HomeserverURL(urlString)
            cache[pubkey] = url to (now + 3600 * 1000)
            return url
        }
        
        // 4. Fall back to default
        // Note: DNS resolution is available via resolveWithDNS() for async contexts
        val defaultURL = HomeserverDefaults.defaultHomeserverURL
        cache[pubkey] = defaultURL to (now + 3600 * 1000)
        return defaultURL
    }
    
    /**
     * Resolve a homeserver pubkey with DNS lookup fallback.
     * 
     * This async version tries DNS TXT record lookup at _pubky.{pubkey}
     * before falling back to the default homeserver.
     *
     * @param pubkey The homeserver's pubkey
     * @return The resolved URL
     */
    suspend fun resolveWithDNS(pubkey: HomeserverPubkey): HomeserverURL {
        // 1. Check for override (testing/development)
        overrideURL?.let { return it }
        
        // 2. Check cache
        val now = System.currentTimeMillis()
        cache[pubkey]?.let { (url, expires) ->
            if (now < expires) return url
        }
        
        // 3. Check known mappings
        knownHomeservers[pubkey.value]?.let { urlString ->
            val url = HomeserverURL(urlString)
            cache[pubkey] = url to (now + 3600 * 1000)
            return url
        }
        
        // 4. Try DNS-based resolution
        val dnsResolved = resolveViaDNS(pubkey.value)
        if (dnsResolved != null) {
            cache[pubkey] = dnsResolved to (now + 3600 * 1000)
            return dnsResolved
        }
        
        // 5. Fall back to default
        val defaultURL = HomeserverDefaults.defaultHomeserverURL
        cache[pubkey] = defaultURL to (now + 3600 * 1000)
        return defaultURL
    }
    
    /**
     * Resolve homeserver via DNS TXT record at _pubky.{pubkey}
     * 
     * Requires API 29+. Returns null if DNS lookup fails or API not available.
     */
    @android.annotation.SuppressLint("NewApi")
    private suspend fun resolveViaDNS(pubkey: String): HomeserverURL? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return null
        }
        
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dnsName = "_pubky.$pubkey"
            
            try {
                val resolver = android.net.DnsResolver.getInstance()
                val latch = java.util.concurrent.CountDownLatch(1)
                var result: HomeserverURL? = null
                
                resolver.rawQuery(
                    null,
                    dnsName,
                    android.net.DnsResolver.TYPE_TXT,
                    android.net.DnsResolver.FLAG_EMPTY,
                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                    null,
                    object : android.net.DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            result = parseTxtRecord(answer)
                            latch.countDown()
                        }
                        override fun onError(error: android.net.DnsResolver.DnsException) {
                            latch.countDown()
                        }
                    }
                )
                
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                result
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Parse DNS TXT record for homeserver URL.
     * Expected format: "hs=https://homeserver.example.com"
     */
    private fun parseTxtRecord(data: ByteArray): HomeserverURL? {
        return try {
            val txt = String(data, Charsets.UTF_8)
            val urlMatch = Regex("hs=([^\\s]+)").find(txt)
            urlMatch?.groupValues?.get(1)?.let { HomeserverURL(it) }
        } catch (e: Exception) {
            null
        }
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
    
    /**
     * Clear the resolution cache
     */
    fun clearCache() {
        cache.clear()
    }
}

