package to.bitkit.paykit.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import to.bitkit.paykit.models.PrivateEndpointOffer
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of private payment endpoints using Keychain.
 */
@Singleton
class PrivateEndpointStorage @Inject constructor(
    private val keychain: PaykitKeychainStorage
) {
    companion object {
        private const val TAG = "PrivateEndpointStorage"
    }
    
    @Serializable
    private data class StoredEndpoint(
        val methodId: String,
        val endpoint: String
    )
    
    private var endpointsCache: Map<String, List<PrivateEndpointOffer>>? = null
    private val identityName: String = "default"
    
    private val endpointsKey: String
        get() = "private_endpoints.$identityName"
    
    /**
     * Get all private endpoints for a peer
     */
    fun listForPeer(peerPubkey: String): List<PrivateEndpointOffer> {
        val all = loadAllEndpoints()
        return all[peerPubkey] ?: emptyList()
    }
    
    /**
     * Get a specific endpoint for a peer and method
     */
    fun get(peerPubkey: String, methodId: String): PrivateEndpointOffer? {
        val endpoints = listForPeer(peerPubkey)
        return endpoints.firstOrNull { it.methodId == methodId }
    }
    
    /**
     * Save a private endpoint
     */
    suspend fun save(endpoint: PrivateEndpointOffer, forPeer peerPubkey: String) {
        val all = loadAllEndpoints().toMutableMap()
        
        // Get or create list for this peer
        val peerEndpoints = all[peerPubkey]?.toMutableList() ?: mutableListOf()
        
        // Remove existing endpoint for this method if it exists
        peerEndpoints.removeAll { it.methodId == endpoint.methodId }
        
        // Add the new endpoint
        peerEndpoints.add(endpoint)
        
        // Update the map
        all[peerPubkey] = peerEndpoints
        
        persistAllEndpoints(all)
    }
    
    /**
     * Remove a specific endpoint
     */
    suspend fun remove(peerPubkey: String, methodId: String) {
        val all = loadAllEndpoints().toMutableMap()
        
        val peerEndpoints = all[peerPubkey]?.toMutableList() ?: return
        
        peerEndpoints.removeAll { it.methodId == methodId }
        
        if (peerEndpoints.isEmpty()) {
            all.remove(peerPubkey)
        } else {
            all[peerPubkey] = peerEndpoints
        }
        
        persistAllEndpoints(all)
    }
    
    /**
     * Clear all endpoints for a peer
     */
    suspend fun clearForPeer(peerPubkey: String) {
        val all = loadAllEndpoints().toMutableMap()
        all.remove(peerPubkey)
        persistAllEndpoints(all)
    }
    
    /**
     * Clear all endpoints
     */
    suspend fun clearAll() {
        persistAllEndpoints(emptyMap())
    }
    
    /**
     * List all peers with stored endpoints
     */
    fun listPeers(): List<String> {
        return loadAllEndpoints().keys.toList()
    }
    
    // MARK: - Private
    
    private fun loadAllEndpoints(): Map<String, List<PrivateEndpointOffer>> {
        if (endpointsCache != null) {
            return endpointsCache!!
        }
        
        return try {
            val data = keychain.retrieve(endpointsKey) ?: return emptyMap()
            val json = String(data)
            
            // Deserialize as Map<String, List<StoredEndpoint>>
            val stored: Map<String, List<StoredEndpoint>> = Json.decodeFromString(json)
            
            // Convert to Map<String, List<PrivateEndpointOffer>>
            val endpoints = stored.mapValues { (_, storedList) ->
                storedList.map { stored ->
                    PrivateEndpointOffer(
                        methodId = stored.methodId,
                        endpoint = stored.endpoint
                    )
                }
            }
            
            endpointsCache = endpoints
            endpoints
        } catch (e: Exception) {
            Logger.error("PrivateEndpointStorage: Failed to load endpoints", e, context = TAG)
            emptyMap()
        }
    }
    
    private suspend fun persistAllEndpoints(endpoints: Map<String, List<PrivateEndpointOffer>>) {
        try {
            // Convert PrivateEndpointOffer to StoredEndpoint for serialization
            val stored = endpoints.mapValues { (_, endpointList) ->
                endpointList.map { endpoint ->
                    StoredEndpoint(
                        methodId = endpoint.methodId,
                        endpoint = endpoint.endpoint
                    )
                }
            }
            
            val json = Json.encodeToString(stored)
            keychain.store(endpointsKey, json.toByteArray())
            endpointsCache = endpoints
        } catch (e: Exception) {
            Logger.error("PrivateEndpointStorage: Failed to persist endpoints", e, context = TAG)
            throw PaykitStorageException.SaveFailed(endpointsKey)
        }
    }
}

