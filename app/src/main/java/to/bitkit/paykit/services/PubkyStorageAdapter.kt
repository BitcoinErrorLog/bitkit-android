package to.bitkit.paykit.services

import android.content.Context
import com.paykit.mobile.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import to.bitkit.utils.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter for Pubky SDK storage operations
 * Implements Pubky storage callback interfaces for Paykit directory operations
 */
@Singleton
class PubkyStorageAdapter @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "PubkyStorageAdapter"
    }

    /**
     * Create unauthenticated storage adapter for public reads
     */
    fun createUnauthenticatedAdapter(homeserverBaseURL: String? = null): PubkyUnauthenticatedStorageAdapter {
        return PubkyUnauthenticatedStorageAdapter(homeserverBaseURL)
    }

    /**
     * Create authenticated storage adapter for writes
     */
    fun createAuthenticatedAdapter(sessionId: String, homeserverBaseURL: String? = null): PubkyAuthenticatedStorageAdapter {
        return PubkyAuthenticatedStorageAdapter(sessionId, homeserverBaseURL)
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
    suspend fun listDirectory(prefix: String, adapter: PubkyUnauthenticatedStorageAdapter, ownerPubkey: String): List<String> {
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
    private val homeserverBaseURL: String? = null
) : PubkyUnauthenticatedStorageCallback {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun get(ownerPubkey: String, path: String): StorageGetResult {
        val urlString = if (homeserverBaseURL != null) {
            "$homeserverBaseURL/pubky$ownerPubkey$path"
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
        val urlString = if (homeserverBaseURL != null) {
            "$homeserverBaseURL/pubky$ownerPubkey$prefix?shallow=true"
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
    private val sessionId: String,
    private val homeserverBaseURL: String? = null
) : PubkyAuthenticatedStorageCallback {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
        val urlString = if (homeserverBaseURL != null) {
            "$homeserverBaseURL$path"
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
        val urlString = if (homeserverBaseURL != null) {
            "$homeserverBaseURL$path"
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
        val urlString = if (homeserverBaseURL != null) {
            "$homeserverBaseURL$path"
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
        val urlString = if (homeserverBaseURL != null) {
            "$homeserverBaseURL$prefix?shallow=true"
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
