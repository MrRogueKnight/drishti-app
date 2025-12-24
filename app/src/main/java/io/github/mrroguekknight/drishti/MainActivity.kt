package io.github.mrroguekknight.drishti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import io.github.mrroguekknight.drishti.ui.AppRoot
import io.github.mrroguekknight.drishti.ui.theme.DRISHTITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // --- START SERVER DISCOVERY ---
        // Optimize: Start finding the server immediately on app launch
        io.github.mrroguekknight.drishti.network.ServerDiscovery.startListening { ip ->
            io.github.mrroguekknight.drishti.network.NetworkDataSync.serverIp = ip
            io.github.mrroguekknight.drishti.network.NetworkDataSync.isSyncing = true // Optional: auto-start sync?
            android.util.Log.d("MainActivity", "Server IP Auto-Discovered: $ip")
        }
        
        val preferenceStore = io.github.mrroguekknight.drishti.data.PreferenceStore(applicationContext)
        setContent {
            val isDarkTheme = preferenceStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme()).value
            DRISHTITheme(darkTheme = isDarkTheme) {
                AppRoot(modifier = Modifier)
            }
        }
    }
}
