package io.github.mrroguekknight.drishti.network

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlinx.coroutines.launch

object NetworkDataSync {
    private val client = OkHttpClient()
    
    // Use extension function (dependency is now fixed)
    private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    
    // Config
    var serverIp: String = "192.168.1.100" // Default
    var serverPort: String = "5000"
    var isSyncing: Boolean = false
    var syncStatus: String = "Ready" // Debug Status
    
    private var deviceId: String = "Unknown"
    
    fun initialize(context: android.content.Context) {
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val model = android.os.Build.MODEL.replace(" ", "_")
            deviceId = "${model}_${androidId.takeLast(4)}" 
        } catch (e: Exception) {
            deviceId = android.os.Build.MODEL + "_" + (1000..9999).random()
        }
    }
    
    fun sendData(
        timestamp: Long,
        lat: Double,
        lon: Double,
        speed: Float,
        bearing: Float, // Added bearing
        accX: Double,
        accY: Double,
        accZ: Double
    ) {
        if (!isSyncing) return

        val url = "http://$serverIp:$serverPort/upload"
        
        val jsonBody = """
            {
                "device_id": "$deviceId",
                "timestamp": $timestamp,
                "latitude": $lat,
                "longitude": $lon,
                "speed": $speed,
                "bearing": $bearing,
                "imu": {
                    "acc_x": $accX,
                    "acc_y": $accY,
                    "acc_z": $accZ
                }
            }
        """.trimIndent()

        // Use extension function
        val body = jsonBody.toRequestBody(MEDIA_TYPE_JSON)
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
            
        // ... rest of function ...

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NetworkSync", "Sync Failed: ${e.message}")
                syncStatus = "Failed: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    syncStatus = "Success: ${response.code}"
                } else {
                    syncStatus = "Error: ${response.code}"
                }
                response.close()
            }
        })
    }

    // --- PEER LOCATION TRACKING ---
    
    data class PeerLocation(
        val id: String,
        val lat: Double,
        val lon: Double,
        val speed: Float,
        val timestamp: Long,
        val alertLevel: String = "NONE" // Added field
    )
    
    // StateFlow for UI to consume
    private val _peerLocations = kotlinx.coroutines.flow.MutableStateFlow<List<PeerLocation>>(emptyList())
    val peerLocations: kotlinx.coroutines.flow.StateFlow<List<PeerLocation>> = _peerLocations
    
    private var pollingJob: kotlinx.coroutines.Job? = null
    
    fun startPollingPeers(scope: kotlinx.coroutines.CoroutineScope) {
        if (pollingJob?.isActive == true) return
        
        pollingJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000) // Poll every second
                if (isSyncing) {
                    try {
                        fetchPeers()
                    } catch (e: Exception) {
                        Log.e("NetworkSync", "Polling error: ${e.message}")
                    }
                }
            }
        }
    }
    
    private fun fetchPeers() {
        val url = "http://$serverIp:$serverPort/locations"
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Silent fail for polling to avoid spamming logs
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (json != null) {
                        try {
                            val jsonArray = org.json.JSONArray(json)
                            val peers = mutableListOf<PeerLocation>()
                            
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val id = obj.optString("id", "unknown")
                                
                                // Skip own device
                                if (id == deviceId) continue
                                
                                peers.add(PeerLocation(
                                    id = id,
                                    lat = obj.optDouble("latitude", 0.0),
                                    lon = obj.optDouble("longitude", 0.0),
                                    speed = obj.optDouble("speed", 0.0).toFloat(),
                                    timestamp = obj.optLong("timestamp", 0),
                                    alertLevel = obj.optString("alertLevel", "NONE")
                                ))
                            }
                            _peerLocations.value = peers
                        } catch (e: Exception) {
                            Log.e("NetworkSync", "JSON Parse error: ${e.message}")
                        }
                    }
                }
                response.close()
            }
        })
    }
}
