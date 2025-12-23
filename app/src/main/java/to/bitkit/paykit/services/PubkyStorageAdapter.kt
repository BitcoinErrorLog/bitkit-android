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
     * Create authenticated storage adapter for writes
     */
    fun createAuthenticatedAdapter(
        sessionId: String,
        homeserverURL: HomeserverURL? = null,
    ): PubkyAuthenticatedStorageAdapter {
        return PubkyAuthenticatedStorageAdapter(httpClient, sessionId, homeserverURL)
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

    /**
     * Store data in Pubky storage using authenticated transport
     */
    suspend fun store(path: String, data: ByteArray, transport: AuthenticatedTransportFfi) {
        // For now, use shared OkHttpClient since transport is an FFI wrapper
        val content = String(data)
        val urlString = "https://homeserver.pubky.app$path"

        val mediaType = "application/json".toMediaType()
        val requestBody = content.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(urlString)
            .put(requestBody)
            .header("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.code !in 200..299) {
            throw PubkyStorageException("Failed to store: HTTP ${response.code}")
        }
        Logger.debug("Stored data to Pubky: $path", context = TAG)
    }

    /**
     * Delete from Pubky storage using authenticated transport
     */
    suspend fun delete(path: String, transport: AuthenticatedTransportFfi) {
        val urlString = "https://homeserver.pubky.app$path"

        val request = Request.Builder()
            .url(urlString)
            .delete()
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.code !in 200..299 && response.code != 404) {
            throw PubkyStorageException("Failed to delete: HTTP ${response.code}")
        }
        Logger.debug("Deleted from Pubky: $path", context = TAG)
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
        val urlString = if (url != null) {
            "$url/pubky$ownerPubkey$path"
        } else {
            "https://_pubky.$ownerPubkey$path"
        }

        val request = Request.Builder()
            .url(urlString)
            .get()
            .build()

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
        val urlString = if (homeserverURL?.value != null) {
            "${homeserverURL.value}/pubky$ownerPubkey$prefix?shallow=true"
        } else {
            "https://_pubky.$ownerPubkey$prefix?shallow=true"
        }

        val request = Request.Builder()
            .url(urlString)
            .get()
            .build()

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

/**
 * Adapter for authenticated Pubky storage operations
 */
class PubkyAuthenticatedStorageAdapter(
    private val baseClient: OkHttpClient,
    private val sessionId: String,
    private val homeserverURL: HomeserverURL? = null,
) : PubkyAuthenticatedStorageCallback {

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

    override fun put(path: String, content: String): StorageOperationResult {
        val urlString = if (homeserverURL?.value != null) {
            "${homeserverURL.value}$path"
        } else {
            "https://homeserver.pubky.app$path"
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = content.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(urlString)
            .put(requestBody)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Cookie", "session=$sessionId")
            .build()

        return try {
            val response = client.newCall(request).execute()

            if (response.code in 200..299) {
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

    override fun get(path: String): StorageGetResult {
        val urlString = if (homeserverURL?.value != null) {
            "${homeserverURL.value}$path"
        } else {
            "https://homeserver.pubky.app$path"
        }

        val request = Request.Builder()
            .url(urlString)
            .get()
            .header("Accept", "application/json")
            .header("Cookie", "session=$sessionId")
            .build()

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

    override fun delete(path: String): StorageOperationResult {
        val urlString = if (homeserverURL?.value != null) {
            "${homeserverURL.value}$path"
        } else {
            "https://homeserver.pubky.app$path"
        }

        val request = Request.Builder()
            .url(urlString)
            .delete()
            .header("Cookie", "session=$sessionId")
            .build()

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
        val urlString = if (homeserverURL?.value != null) {
            "${homeserverURL.value}$prefix?shallow=true"
        } else {
            "https://homeserver.pubky.app$prefix?shallow=true"
        }

        val request = Request.Builder()
            .url(urlString)
            .get()
            .header("Accept", "application/json")
            .header("Cookie", "session=$sessionId")
            .build()

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
