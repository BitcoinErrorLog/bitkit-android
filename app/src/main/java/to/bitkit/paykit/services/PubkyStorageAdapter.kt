package to.bitkit.paykit.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import to.bitkit.paykit.di.PaykitOkHttp
import to.bitkit.paykit.types.HomeserverURL
import to.bitkit.utils.Logger
import uniffi.paykit_mobile.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter for Pubky SDK storage operations
 * Implements Pubky storage callback interfaces for Paykit directory operations
 */
@Singleton
class PubkyStorageAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    @PaykitOkHttp private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "PubkyStorageAdapter"

        /**
         * Default OkHttpClient for non-DI contexts.
         * Prefer using Hilt injection where possible.
         */
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    /**
     * Secondary constructor for manual instantiation without DI.
     * Prefer using Hilt injection where possible.
     */
    constructor(context: Context) : this(context, defaultClient)

    /**
     * Create unauthenticated storage adapter for public reads
     */
    fun createUnauthenticatedAdapter(homeserverURL: HomeserverURL? = null): PubkyUnauthenticatedStorageAdapter {
        return PubkyUnauthenticatedStorageAdapter(httpClient, homeserverURL)
    }

    /**
     * Create authenticated storage adapter for writes.
     * Uses Pubky transport URL format: https://_pubky.{ownerPubkey}/{path}
     */
    fun createAuthenticatedAdapter(
        sessionSecret: String,
        ownerPubkey: String,
        homeserverURL: HomeserverURL? = null,
    ): PubkyAuthenticatedStorageAdapter {
        return PubkyAuthenticatedStorageAdapter(httpClient, sessionSecret, ownerPubkey, homeserverURL)
    }

    /**
     * Store data in Pubky storage (requires authenticated adapter)
     */
    suspend fun store(path: String, data: ByteArray, adapter: PubkyAuthenticatedStorageAdapter) {
        val content = String(data)
        val result = adapter.put(path, content)
        if (!result.success) {
            throw PubkyStorageException("Failed to store: ${result.error}")
        }
        Logger.debug("Stored data to Pubky: $path", context = TAG)
    }

    /**
     * Retrieve data from Pubky storage
     */
    suspend fun retrieve(path: String, adapter: PubkyUnauthenticatedStorageAdapter, ownerPubkey: String): ByteArray? {
        val result = adapter.get(ownerPubkey, path)
        if (!result.success) {
            throw PubkyStorageException("Failed to retrieve: ${result.error}")
        }
        return result.content?.toByteArray()
    }

    /**
     * List items in a directory
     */
    suspend fun listDirectory(
        prefix: String,
        adapter: PubkyUnauthenticatedStorageAdapter,
        ownerPubkey: String
    ): List<String> {
        val result = adapter.list(ownerPubkey, prefix)
        if (!result.success) {
            throw PubkyStorageException("Failed to list: ${result.error}")
        }
        return result.entries
    }
}

/**
 * Adapter for unauthenticated (read-only) Pubky storage operations
 */
class PubkyUnauthenticatedStorageAdapter(
    private val client: OkHttpClient,
    private val homeserverURL: HomeserverURL? = null,
) : PubkyUnauthenticatedStorageCallback {

    override fun get(ownerPubkey: String, path: String): StorageGetResult {
        val url = homeserverURL?.value
        // When using central homeserver, path is just /path, owner identified via pubky-host header
        val urlString = if (url != null) {
            "$url$path"
        } else {
            "https://_pubky.$ownerPubkey$path"
        }

        var requestBuilder = Request.Builder()
            .url(urlString)
            .get()

        // Add pubky-host header when using central homeserver
        if (url != null) {
            requestBuilder = requestBuilder.header("pubky-host", ownerPubkey)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()

            when {
                response.code == 404 -> StorageGetResult(success = true, content = null, error = null)
                response.code in 200..299 -> {
                    val body = response.body?.string()
                    StorageGetResult(success = true, content = body, error = null)
                }
                else -> StorageGetResult(success = false, content = null, error = "HTTP ${response.code}")
            }
        } catch (e: IOException) {
            StorageGetResult(success = false, content = null, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            StorageGetResult(success = false, content = null, error = "Error: ${e.message}")
        }
    }

    override fun list(ownerPubkey: String, prefix: String): StorageListResult {
        // When using central homeserver, path is just /prefix, owner identified via pubky-host header
        // When using Pubky DNS format, pubkey is in the hostname
        val urlString = if (homeserverURL?.value != null) {
            "${homeserverURL.value}$prefix?shallow=true"
        } else {
            "https://_pubky.$ownerPubkey$prefix?shallow=true"
        }
        android.util.Log.d("PubkyStorageAdapter", "LIST URL: $urlString, owner: ${ownerPubkey.take(12)}...")

        var requestBuilder = Request.Builder()
            .url(urlString)
            .get()

        // Add pubky-host header when using central homeserver
        if (homeserverURL?.value != null) {
            requestBuilder = requestBuilder.header("pubky-host", ownerPubkey)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()
            android.util.Log.d("PubkyStorageAdapter", "LIST response code: ${response.code}")

            when {
                response.code == 404 -> StorageListResult(success = true, entries = emptyList(), error = null)
                response.code in 200..299 -> {
                    val body = response.body?.string()
                    android.util.Log.d("PubkyStorageAdapter", "LIST body (first 200): ${body?.take(200)}")
                    if (body.isNullOrEmpty()) {
                        StorageListResult(success = true, entries = emptyList(), error = null)
                    } else {
                        // Response can be:
                        // 1. JSON array of objects with "path" field
                        // 2. JSON array of strings (paths)
                        // 3. Line-separated pubky:// URLs (from homeserver)
                        try {
                            val jsonArray = JSONArray(body)
                            val entries = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                try {
                                    val item = jsonArray.getJSONObject(i)
                                    entries.add(item.getString("path"))
                                } catch (e: Exception) {
                                    entries.add(jsonArray.getString(i))
                                }
                            }
                            StorageListResult(success = true, entries = entries, error = null)
                        } catch (e: Exception) {
                            // Try parsing as line-separated pubky:// URLs
                            val entries = body.lines()
                                .filter { it.startsWith("pubky://") }
                                .mapNotNull { line ->
                                    // Extract the last path segment (follow pubkey)
                                    // Format: pubky://{owner}/pub/pubky.app/follows/{followPubkey}
                                    line.substringAfterLast("/").takeIf { it.isNotEmpty() }
                                }
                            if (entries.isNotEmpty()) {
                                StorageListResult(success = true, entries = entries, error = null)
                            } else {
                                StorageListResult(
                                    success = false,
                                    entries = emptyList(),
                                    error = "Failed to parse response: ${e.message}"
                                )
                            }
                        }
                    }
                }
                else -> StorageListResult(success = false, entries = emptyList(), error = "HTTP ${response.code}")
            }
        } catch (e: IOException) {
            android.util.Log.e("PubkyStorageAdapter", "LIST IOException: ${e.message}", e)
            StorageListResult(success = false, entries = emptyList(), error = "Network error: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("PubkyStorageAdapter", "LIST Exception: ${e.javaClass.name}: ${e.message}", e)
            StorageListResult(success = false, entries = emptyList(), error = "Error: ${e.message}")
        }
    }
}

/**
 * Adapter for authenticated Pubky storage operations.
 * Uses the Pubky transport URL format: https://_pubky.{ownerPubkey}/{path}
 * Cookie format: {ownerPubkey}={sessionSecret}
 */
class PubkyAuthenticatedStorageAdapter(
    private val baseClient: OkHttpClient,
    private val sessionSecret: String,
    val ownerPubkey: String,
    private val homeserverURL: HomeserverURL? = null,
) : PubkyAuthenticatedStorageCallback {
    companion object {
        private const val TAG = "PubkyAuthStorage"
    }

    // Create a client with cookie jar for session handling, sharing connection pool with base client
    private val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(object : CookieJar {
            private val cookies = mutableListOf<Cookie>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this.cookies.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return this.cookies
            }
        })
        .build()

    /**
     * Build the transport URL for the owner's storage.
     * When using homeserverURL, the pubky-host header identifies the owner (see addPubkyHostHeader).
     * Otherwise falls back to Pubky DNS format: https://_pubky.{ownerPubkey}/{path}
     */
    private fun buildTransportUrl(path: String): String {
        val relativePath = path.trimStart('/')
        // Use concrete homeserver URL if available (recommended for mobile/Android)
        // The owner is identified via pubky-host header, not the URL path
        homeserverURL?.value?.let { baseUrl ->
            return "$baseUrl/$relativePath"
        }
        // Fallback to Pubky DNS format (requires special DNS resolution)
        return "https://_pubky.$ownerPubkey/$relativePath"
    }

    /**
     * Check if we need to add the pubky-host header (when using central homeserver URL)
     */
    private fun needsPubkyHostHeader(): Boolean = homeserverURL != null

    /**
     * Build the session cookie in Pubky format: {ownerPubkey}={actualSecret}
     * The sessionSecret may come as "{pubkey}:{actualSecret}" format from Pubky Ring,
     * so we extract just the actualSecret portion after the colon.
     */
    private fun buildSessionCookie(): String {
        val actualSecret = if (sessionSecret.contains(":")) {
            sessionSecret.substringAfter(":")
        } else {
            sessionSecret
        }
        return "$ownerPubkey=$actualSecret"
    }

    override fun put(path: String, content: String): StorageOperationResult {
        val urlString = buildTransportUrl(path)
        val cookieValue = buildSessionCookie()

        // Log request details (SECURITY: Never log secrets or full content)
        Logger.debug("PUT request to: $urlString", context = TAG)
        Logger.debug(
            "Request headers: pubky-host=${if (needsPubkyHostHeader()) ownerPubkey.take(12) + "..." else "N/A"}",
            context = TAG
        )
        Logger.debug("Content-Length: ${content.length} bytes", context = TAG)

        val mediaType = "application/json".toMediaType()
        val requestBody = content.toRequestBody(mediaType)

        var requestBuilder = Request.Builder()
            .url(urlString)
            .put(requestBody)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Cookie", cookieValue)

        // Add pubky-host header when using central homeserver URL
        if (needsPubkyHostHeader()) {
            requestBuilder = requestBuilder.header("pubky-host", ownerPubkey)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val responseCode = response.code

            // Enhanced diagnostic logging for debugging auth issues
            Logger.debug("Response code: $responseCode", context = TAG)
            if (responseCode !in 200..299) {
                // Log response headers for auth debugging (skip sensitive headers)
                val relevantHeaders = listOfNotNull(
                    response.header("WWW-Authenticate")?.let { "WWW-Authenticate: $it" },
                    response.header("X-Error")?.let { "X-Error: $it" },
                    response.header("X-Request-Id")?.let { "X-Request-Id: $it" },
                )
                if (relevantHeaders.isNotEmpty()) {
                    Logger.debug("Response headers: ${relevantHeaders.joinToString(", ")}", context = TAG)
                }
                Logger.debug("Response body: ${responseBody?.take(500)}", context = TAG)
            }

            when (responseCode) {
                in 200..299 -> {
                    Logger.debug("PUT succeeded for path: $path", context = TAG)
                    StorageOperationResult(success = true, error = null)
                }
                401 -> {
                    Logger.error(
                        "PUT failed: Unauthorized (401) - session cookie may be invalid or expired",
                        context = TAG
                    )
                    StorageOperationResult(
                        success = false,
                        error = "Unauthorized: Session expired or invalid. Please reconnect to Pubky Ring."
                    )
                }
                403 -> {
                    Logger.error("PUT failed: Forbidden (403) - no write permission for path: $path", context = TAG)
                    StorageOperationResult(
                        success = false,
                        error = "Forbidden: No write permission. Check pubky-host header and session capabilities."
                    )
                }
                404 -> {
                    Logger.error("PUT failed: Not Found (404) - homeserver endpoint not found", context = TAG)
                    StorageOperationResult(success = false, error = "Not Found: Homeserver endpoint unavailable.")
                }
                else -> {
                    Logger.error("PUT failed: HTTP $responseCode - $responseBody", context = TAG)
                    StorageOperationResult(success = false, error = "HTTP $responseCode: $responseBody")
                }
            }
        } catch (e: IOException) {
            Logger.error("PUT network error: ${e.message}", e, context = TAG)
            StorageOperationResult(success = false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            Logger.error("PUT unexpected error: ${e.message}", e, context = TAG)
            StorageOperationResult(success = false, error = "Error: ${e.message}")
        }
    }

    /**
     * PUT binary data (for blob uploads like images)
     */
    fun putData(path: String, data: ByteArray, contentType: String): StorageOperationResult {
        val urlString = buildTransportUrl(path)
        val cookieValue = buildSessionCookie()

        Logger.debug("PUT binary data to: $urlString (${data.size} bytes)", context = TAG)

        val mediaType = contentType.toMediaType()
        val requestBody = data.toRequestBody(mediaType)

        var requestBuilder = Request.Builder()
            .url(urlString)
            .put(requestBody)
            .header("Content-Type", contentType)
            .header("Cookie", cookieValue)

        if (needsPubkyHostHeader()) {
            requestBuilder = requestBuilder.header("pubky-host", ownerPubkey)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()
            val responseCode = response.code

            when (responseCode) {
                in 200..299 -> {
                    Logger.debug("PUT binary succeeded for path: $path", context = TAG)
                    StorageOperationResult(success = true, error = null)
                }
                401 -> {
                    Logger.error("PUT binary failed: Unauthorized (401)", context = TAG)
                    StorageOperationResult(success = false, error = "Unauthorized: Session expired.")
                }
                403 -> {
                    Logger.error("PUT binary failed: Forbidden (403)", context = TAG)
                    StorageOperationResult(success = false, error = "Forbidden: No write permission.")
                }
                else -> {
                    val responseBody = response.body?.string()
                    Logger.error("PUT binary failed: HTTP $responseCode - $responseBody", context = TAG)
                    StorageOperationResult(success = false, error = "HTTP $responseCode: $responseBody")
                }
            }
        } catch (e: IOException) {
            Logger.error("PUT binary network error: ${e.message}", e, context = TAG)
            StorageOperationResult(success = false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            Logger.error("PUT binary unexpected error: ${e.message}", e, context = TAG)
            StorageOperationResult(success = false, error = "Error: ${e.message}")
        }
    }

    override fun get(path: String): StorageGetResult {
        val urlString = buildTransportUrl(path)

        Logger.debug("GET request to: $urlString", context = TAG)

        var requestBuilder = Request.Builder()
            .url(urlString)
            .get()
            .header("Accept", "application/json")
            .header("Cookie", buildSessionCookie())

        if (needsPubkyHostHeader()) {
            requestBuilder = requestBuilder.header("pubky-host", ownerPubkey)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()
            val responseCode = response.code

            when (responseCode) {
                404 -> {
                    Logger.debug("GET returned 404 (not found) for path: $path", context = TAG)
                    StorageGetResult(success = true, content = null, error = null)
                }
                in 200..299 -> {
                    val body = response.body?.string()
                    Logger.debug("GET succeeded for path: $path (${body?.length ?: 0} bytes)", context = TAG)
                    StorageGetResult(success = true, content = body, error = null)
                }
                401 -> {
                    Logger.error("GET failed: Unauthorized (401) for path: $path", context = TAG)
                    StorageGetResult(
                        success = false,
                        content = null,
                        error = "Unauthorized: Session expired or invalid."
                    )
                }
                403 -> {
                    Logger.error("GET failed: Forbidden (403) for path: $path", context = TAG)
                    StorageGetResult(success = false, content = null, error = "Forbidden: No read permission.")
                }
                else -> {
                    val body = response.body?.string()
                    Logger.error("GET failed: HTTP $responseCode for path: $path - $body", context = TAG)
                    StorageGetResult(success = false, content = null, error = "HTTP $responseCode")
                }
            }
        } catch (e: IOException) {
            Logger.error("GET network error: ${e.message}", e, context = TAG)
            StorageGetResult(success = false, content = null, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            Logger.error("GET unexpected error: ${e.message}", e, context = TAG)
            StorageGetResult(success = false, content = null, error = "Error: ${e.message}")
        }
    }

    override fun delete(path: String): StorageOperationResult {
        val urlString = buildTransportUrl(path)

        var requestBuilder = Request.Builder()
            .url(urlString)
            .delete()
            .header("Cookie", buildSessionCookie())

        if (needsPubkyHostHeader()) {
            requestBuilder = requestBuilder.header("pubky-host", ownerPubkey)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()

            if (response.code in 200..299 || response.code == 404) {
                StorageOperationResult(success = true, error = null)
            } else {
                StorageOperationResult(success = false, error = "HTTP ${response.code}")
            }
        } catch (e: IOException) {
            StorageOperationResult(success = false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            StorageOperationResult(success = false, error = "Error: ${e.message}")
        }
    }

    override fun list(prefix: String): StorageListResult {
        val urlString = "${buildTransportUrl(prefix)}?shallow=true"

        var requestBuilder = Request.Builder()
            .url(urlString)
            .get()
            .header("Accept", "application/json")
            .header("Cookie", buildSessionCookie())

        if (needsPubkyHostHeader()) {
            requestBuilder = requestBuilder.header("pubky-host", ownerPubkey)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()

            when {
                response.code == 404 -> StorageListResult(success = true, entries = emptyList(), error = null)
                response.code in 200..299 -> {
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        StorageListResult(success = true, entries = emptyList(), error = null)
                    } else {
                        try {
                            val jsonArray = JSONArray(body)
                            val entries = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONObject(i)
                                entries.add(item.getString("path"))
                            }
                            StorageListResult(success = true, entries = entries, error = null)
                        } catch (e: Exception) {
                            try {
                                val jsonArray = JSONArray(body)
                                val entries = mutableListOf<String>()
                                for (i in 0 until jsonArray.length()) {
                                    entries.add(jsonArray.getString(i))
                                }
                                StorageListResult(success = true, entries = entries, error = null)
                            } catch (e2: Exception) {
                                StorageListResult(
                                    success = false,
                                    entries = emptyList(),
                                    error = "Failed to parse response: ${e2.message}"
                                )
                            }
                        }
                    }
                }
                else -> StorageListResult(success = false, entries = emptyList(), error = "HTTP ${response.code}")
            }
        } catch (e: IOException) {
            StorageListResult(success = false, entries = emptyList(), error = "Network error: ${e.message}")
        } catch (e: Exception) {
            StorageListResult(success = false, entries = emptyList(), error = "Error: ${e.message}")
        }
    }
}

class PubkyStorageException(message: String) : Exception(message)
