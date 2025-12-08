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
        val preferenceStore = io.github.mrroguekknight.drishti.data.PreferenceStore(applicationContext)
        setContent {
            val isDarkTheme = preferenceStore.isDarkTheme.collectAsState(initial = isSystemInDarkTheme()).value
            DRISHTITheme(darkTheme = isDarkTheme) {
                AppRoot(modifier = Modifier)
            }
        }
    }
}
