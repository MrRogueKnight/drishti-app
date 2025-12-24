package io.github.mrroguekknight.drishti.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

object ServerDiscovery {
    private const val DISCOVERY_PORT = 5001
    private var isListening = false
    
    fun startListening(onServerFound: (String) -> Unit) {
        if (isListening) return
        isListening = true
        
        GlobalScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket.broadcast = true
                val buffer = ByteArray(1024)
                
                Log.d("ServerDiscovery", "Listening for server beacon on port $DISCOVERY_PORT...")
                
                while (isListening) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    
                    val message = String(packet.data, 0, packet.length)
                    // {"server_ip": "...", "server_port": ...}
                    
                    try {
                        val json = JSONObject(message)
                        if (json.has("server_ip")) {
                            val ip = json.getString("server_ip")
                            Log.d("ServerDiscovery", "Found Server: $ip")
                            
                            // Callback with Found IP
                            onServerFound(ip)
                            
                            // Optional: Stop listening once found? 
                            // Or keep updating in case it changes? 
                            // Let's keep listening but we can debounce in UI.
                        }
                    } catch (e: Exception) {
                        // Not JSON or bad format
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerDiscovery", "Discovery Error: ${e.message}")
            } finally {
                socket?.close()
                isListening = false
            }
        }
    }
    
    fun stopListening() {
        isListening = false
    }
}
