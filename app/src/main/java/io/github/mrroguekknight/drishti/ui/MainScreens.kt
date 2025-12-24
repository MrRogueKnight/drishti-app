
@file:OptIn(ExperimentalMaterial3Api::class)
// To persist settings use PreferenceStore (see data/PreferenceStore.kt)
// Example (in a real app): val store = PreferenceStore(context); collect flows with lifecycleScope and update UI accordingly.
package io.github.mrroguekknight.drishti.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// NOTE: This file implements production-ready Compose screens (stateless & testable) for:
// - LoginScreen, SignUpScreen, SplashWrapper, and Dashboard with Bottom Navigation and Status Cards.
// It wires mock data from the project spec (Data Morphers - SIH25177) into the UI fields so it's ready to be connected to real services.

@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferenceStore = remember { io.github.mrroguekknight.drishti.data.PreferenceStore(context) }
    val scope = rememberCoroutineScope()

    // 1. Initialize Sensor Container (Hoisted State)
    val sensorContainer = remember {
        io.github.mrroguekknight.drishti.ui.SensorContainer(
            locationProvider = io.github.mrroguekknight.drishti.fusion.LocationDataProvider(context),
            sensorProvider = io.github.mrroguekknight.drishti.fusion.SensorDataProvider(context),
            networkHelper = io.github.mrroguekknight.drishti.network.NetworkStatusHelper(context),
            systemHelper = io.github.mrroguekknight.drishti.ui.SystemStatusHelper(context),
            dataLogger = io.github.mrroguekknight.drishti.fusion.DataLogger(context)
        )
    }

    // 2. Start Sensors Immediately
    // 2. Start Sensors Immediately
    
    // Persistent Data State for Syncing (Hoisted to AppRoot)
    var lastLat by remember { mutableDoubleStateOf(0.0) }
    var lastLon by remember { mutableDoubleStateOf(0.0) }
    var lastSpeed by remember { mutableFloatStateOf(0f) }
    var lastBearing by remember { mutableFloatStateOf(0f) } // Added bearing state
    var lastAccX by remember { mutableDoubleStateOf(0.0) }
    var lastAccY by remember { mutableDoubleStateOf(0.0) }
    var lastAccZ by remember { mutableDoubleStateOf(0.0) }
    
    // Kalman Filter for smoothing
    val kalmanFilter = remember { io.github.mrroguekknight.drishti.fusion.KalmanFilter2D() }

    LaunchedEffect(Unit) {
        // Start Data Logger
        sensorContainer.dataLogger.startLogging()

        // Start IMU Sensors
        sensorContainer.sensorProvider.start()
        
        // Collect IMU data for logging and syncing
        launch {
            sensorContainer.sensorProvider.imuUpdates.collect { sample ->
                // Update local state for sync
                lastAccX = sample.accX
                lastAccY = sample.accY
                lastAccZ = sample.accZ
                
                sensorContainer.dataLogger.logData(
                    timestamp = sample.timestamp,
                    speed = 0f, // Will be updated by GPS flow
                    lat = 0.0, // Will be updated by GPS flow
                    lon = 0.0,
                    accX = sample.accX, accY = sample.accY, accZ = sample.accZ,
                    gyroX = sample.gyroX, gyroY = sample.gyroY, gyroZ = sample.gyroZ
                )
            }
        }
        


        // Collect GPS/NavIC location updates
        launch {
            try {
                sensorContainer.locationProvider.getLocationUpdates().collect { location ->
                    // Initialize Filter if needed
                    if (!kalmanFilter.isInitialized()) {
                        kalmanFilter.initialize(location.latitude, location.longitude, System.currentTimeMillis())
                    }
                    
                    // Update Filter
                    kalmanFilter.predict(System.currentTimeMillis(), lastAccX, lastAccY) // Predict using recent IMU
                    kalmanFilter.update(location.latitude, location.longitude, location.accuracy)
                    
                    // Get Smoothed State
                    val smoothed = kalmanFilter.getState()
                    
                    // Update local state for sync using Smoothed Data
                    lastLat = smoothed.latitude
                    lastLon = smoothed.longitude
                    lastSpeed = smoothed.speed.toFloat()
                    // lastBearing = location.bearing // GPS Bearing (poor at low speed)
                    
                    android.util.Log.d("AppRoot", "GPS Update (Smoothed): ${smoothed.latitude}, ${smoothed.longitude}")
                }
            } catch (e: Exception) {
                android.util.Log.e("AppRoot", "GPS tracking error", e)
            }
        }
        
        // Collect Magnetometer Bearing
        launch {
            sensorContainer.sensorProvider.bearingUpdates.collect { bearing ->
                lastBearing = bearing // Smoother, responsive bearing
                // currentBearing = bearing // Update UI state if needed, but currentBearing is likely inside DriveScreen
            }
        }
        
        // GLOBAL DATA SYNC LOOP (Running in AppRoot Scope)
        launch {
            // Start polling for peers (will only run if syncing is active)
            io.github.mrroguekknight.drishti.network.NetworkDataSync.startPollingPeers(this)
            
            while(true) {
                kotlinx.coroutines.delay(500) // High frequency sync (user requested normal behavior)
                if (io.github.mrroguekknight.drishti.network.NetworkDataSync.isSyncing) {
                     io.github.mrroguekknight.drishti.network.NetworkDataSync.sendData(
                        System.currentTimeMillis(),
                        lastLat, lastLon, lastSpeed,
                        lastBearing,
                        lastAccX, lastAccY, lastAccZ
                    )
                }
            }
        }
    }

    // Ensure cleanup on app exit
    DisposableEffect(Unit) {
        onDispose {
            sensorContainer.sensorProvider.stop()
            sensorContainer.dataLogger.stopLogging()
        }
    }

    var authenticated by remember { mutableStateOf(false) }
    var showingSignup by remember { mutableStateOf(false) }
    var showingForgotPassword by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }

    // Sensor initialization state
    var sensorProgress by remember { mutableFloatStateOf(0f) }
    var sensorStatusMessage by remember { mutableStateOf("Initializing...") }
    var sensorsReady by remember { mutableStateOf(false) }

    // Check login status
    val isLoggedIn by preferenceStore.isLoggedIn.collectAsState(initial = false)

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            authenticated = true
        }
    }

    // Track sensor initialization progress
    LaunchedEffect(Unit) {
        var imuReady = false
        var gpsReady = false
        var networkReady = false

        // Monitor IMU
        launch {
            sensorStatusMessage = "Starting IMU sensors..."
            sensorProgress = 0.1f
            delay(300)
            sensorContainer.sensorProvider.imuUpdates.collect { sample ->
                if (!imuReady) {
                    imuReady = true
                    sensorStatusMessage = "IMU sensors active"
                    sensorProgress = 0.33f
                    android.util.Log.d("AppRoot", "IMU Ready")
                }
            }
        }

        // Monitor GPS
        launch {
            delay(200)
            sensorStatusMessage = "Acquiring GPS/NavIC fix..."
            sensorProgress = 0.15f
            try {
                sensorContainer.locationProvider.getLocationUpdates().collect { location ->
                    if (!gpsReady) {
                        gpsReady = true
                        sensorStatusMessage = "GPS/NavIC locked"
                        sensorProgress = 0.66f
                        android.util.Log.d("AppRoot", "GPS Ready: ${location.latitude}, ${location.longitude}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppRoot", "GPS error", e)
                // Still mark as ready even if GPS fails (optional)
                gpsReady = true
                sensorStatusMessage = "GPS unavailable (continuing)"
                sensorProgress = 0.66f
            }
        }

        // Monitor Network
        launch {
            delay(100)
            sensorStatusMessage = "Checking network status..."
            sensorProgress = 0.05f
            sensorContainer.networkHelper.getNetworkStatusFlow().collect { status ->
                if (!networkReady) {
                    networkReady = true
                    sensorStatusMessage = "Network status ready"
                    sensorProgress = 0.25f
                    android.util.Log.d("AppRoot", "Network Ready: ${status.type}")
                }
            }
        }

        // Wait for all sensors or timeout
        launch {
            val startTime = System.currentTimeMillis()
            while (!sensorsReady) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTime
                
                // Check if all ready
                if (imuReady && gpsReady && networkReady) {
                    sensorStatusMessage = "All systems ready!"
                    sensorProgress = 1.0f
                    delay(500) // Show completion briefly
                    sensorsReady = true
                    showSplash = false
                }
                
                // Timeout after 10 seconds
                if (elapsed > 10000 && !sensorsReady) {
                    sensorStatusMessage = "Initialization complete (timeout)"
                    sensorProgress = 1.0f
                    delay(300)
                    sensorsReady = true
                    showSplash = false
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            showSplash -> {
                SplashScreen(
                    modifier = Modifier.fillMaxSize(),
                    progress = sensorProgress,
                    statusMessage = sensorStatusMessage,
                    onTimeout = { showSplash = false }
                )
            }
            !authenticated -> {
                if (showingSignup) {
                    SignUpScreen(onSignUp = { 
                        // For demo, just log in. In real app, create account then login.
                        scope.launch { preferenceStore.setLoginState(true, false) }
                        authenticated = true 
                    }, onBack = { showingSignup = false })
                } else if (showingForgotPassword) {
                    ForgotPasswordScreen(onBack = { showingForgotPassword = false })
                } else {
                    LoginScreen(
                        onLogin = { rememberMe ->
                            scope.launch { preferenceStore.setLoginState(true, rememberMe) }
                            authenticated = true
                        },
                        onSignUpClick = { showingSignup = true },
                        onForgotPasswordClick = { showingForgotPassword = true }
                    )
                }
            }
            else -> {
                DashboardRoot(
                    sensorContainer = sensorContainer,
                    onSignOut = { 
                        scope.launch { preferenceStore.setLoginState(false) }
                        authenticated = false 
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(onLogin: (Boolean) -> Unit, onSignUpClick: () -> Unit, onForgotPasswordClick: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0C29),
                            Color(0xFF302B63),
                            Color(0xFF24243E)
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF6A1B9A).copy(alpha = 0.2f),
                radius = 400f,
                center = center.copy(x = size.width, y = 0f)
            )
            drawCircle(
                color = Color(0xFF283593).copy(alpha = 0.2f),
                radius = 300f,
                center = center.copy(x = 0f, y = size.height)
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(1000)) +
                    androidx.compose.animation.slideInVertically(initialOffsetY = { 50 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Welcome Back",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sign in to continue to DRISHTI",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var email by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }

                        val textFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFc36de6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color(0xFFc36de6),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                            cursorColor = Color(0xFFc36de6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLeadingIconColor = Color(0xFFc36de6),
                            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )

                        Spacer(Modifier.height(24.dp))

                        var rememberMe by remember { mutableStateOf(true) }

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { rememberMe = !rememberMe }
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFc36de6),
                                        uncheckedColor = Color.White.copy(alpha = 0.5f),
                                        checkmarkColor = Color.White
                                    )
                                )
                                Text("Remember me", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Forgot Password?",
                                color = Color(0xFFc36de6),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable { onForgotPasswordClick() }
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = { onLogin(rememberMe) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "LOG IN",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Don't have an account? ", color = Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Sign up",
                                color = Color(0xFFc36de6),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(onClick = onSignUpClick)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignUpScreen(onSignUp: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0E0B1F))) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text("Create an", fontSize = 34.sp, color = Color.White)
                Text("Account!", fontSize = 34.sp, color = Color(0xFFc36de6), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var username by remember { mutableStateOf("") }
                        var email by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }
                        var confirm by remember { mutableStateOf("") }
                        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, leadingIcon = { Icon(Icons.Default.Person, null) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, leadingIcon = { Icon(Icons.Default.Email, null) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onSignUp, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("SIGN UP")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Already have an account? Sign in", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onBack() })
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0E0B1F))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Reset Password", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter your email to receive a reset link",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                if (isSubmitted) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Reset link sent to $email",
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                                Text("Back to Login")
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            val textFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFc36de6),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = Color(0xFFc36de6),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                cursorColor = Color(0xFFc36de6),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLeadingIconColor = Color(0xFFc36de6),
                                unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f)
                            )

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { isSubmitted = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFc36de6))
                            ) {
                                Text("SEND RESET LINK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Back to Login",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.clickable { onBack() }
                    )
                }
            }
        }
    }
}

enum class BottomTab(val title: String, val icon: ImageVector) {
    Drive("Drive", Icons.Filled.DirectionsCar),
    Network("Network", Icons.Filled.Wifi),
    Profile("Profile", Icons.Default.Person),
    Status("Status", Icons.AutoMirrored.Filled.ShowChart),
    Settings("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoot(sensorContainer: io.github.mrroguekknight.drishti.ui.SensorContainer, onSignOut: () -> Unit) {
    var selected by remember { mutableStateOf(BottomTab.Drive) }
    Scaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(top = 12.dp, bottom = 4.dp, start = 0.dp, end = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomTab.entries.forEach { tab ->
                        val selectedColor by animateColorAsState(
                            targetValue = if (selected == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            label = "BottomBarColor"
                        )
                        
                        // Special styling for Profile tab
                        if (tab == BottomTab.Profile) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { selected = tab }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .offset(y = (-8).dp) // Elevate above other tabs
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFF1A73E8),
                                                    Color(0xFF0D47A1)
                                                )
                                            )
                                        )
                                        .clickable { selected = tab },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Add subtle shadow effect with another Box
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f))
                                    )
                                    Icon(
                                        tab.icon, 
                                        contentDescription = null, 
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    tab.title, 
                                    fontSize = 11.sp, 
                                    color = if (selected == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally, 
                                modifier = Modifier.clickable { selected = tab }.padding(8.dp)
                            ) {
                                Icon(tab.icon, contentDescription = null, tint = selectedColor)
                                Text(tab.title, fontSize = 12.sp, color = selectedColor)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Use movableContentOf to preserve DriveScreen state/composition across recompositions
        val driveScreenContent = remember(sensorContainer) {
            movableContentOf {
                DriveScreen(sensorContainer)
            }
        }

        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Keep DriveScreen alive to preserve Map state
            val isDriveSelected = selected == BottomTab.Drive
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isDriveSelected) 1f else 0f)
                    .alpha(if (isDriveSelected) 1f else 0f)
            ) {
                driveScreenContent()
            }

            // Other screens can be composed conditionally
            if (!isDriveSelected) {
                Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                    when (selected) {
                        BottomTab.Network -> NetworkScreen(sensorContainer)
                        BottomTab.Profile -> ProfileScreen()
                        BottomTab.Status -> StatusScreen(sensorContainer)
                        BottomTab.Settings -> SettingsScreen(onSignOut)
                        else -> {} 
                    }
                }
            }
        }
    }
}

// Drive screen: map placeholder + alert card
@Composable
fun DriveScreen(sensorContainer: io.github.mrroguekknight.drishti.ui.SensorContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Use shared providers from container
    val locationProvider = sensorContainer.locationProvider
    val sensorProvider = sensorContainer.sensorProvider
    // DataLogger is handled in AppRoot
    
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Permissions
    val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        // Handle permission results if needed
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }

    // Location and speed state
    var currentSpeed by remember { mutableFloatStateOf(0f) }
    var currentLat by remember { mutableDoubleStateOf(0.0) }
    var currentLon by remember { mutableDoubleStateOf(0.0) }
    var currentAccuracy by remember { mutableFloatStateOf(0f) }
    var currentAddress by remember { mutableStateOf("Fetching location...") }
    
    // Navigation state
    var driveMode by remember { mutableStateOf(false) }
    var currentBearing by remember { mutableFloatStateOf(0f) }
    var destination by remember { mutableStateOf("") }
    var destinationGeoPoint by remember { mutableStateOf<org.osmdroid.util.GeoPoint?>(null) }
    var routeWaypoints by remember { mutableStateOf<List<org.osmdroid.util.GeoPoint>>(emptyList()) }
    var mapController by remember { mutableStateOf<io.github.mrroguekknight.drishti.map.MapController?>(null) }
    var distanceToDestination by remember { mutableDoubleStateOf(0.0) }
    var eta by remember { mutableStateOf<Int?>(null) }
    
    // UI Logic for Padding
    var headerHeight by remember { mutableStateOf(0.dp) }

    var accX by remember { mutableDoubleStateOf(0.0) }
    var accY by remember { mutableDoubleStateOf(0.0) }
    var accZ by remember { mutableDoubleStateOf(0.0) }
    var gyroX by remember { mutableDoubleStateOf(0.0) }
    var gyroY by remember { mutableDoubleStateOf(0.0) }
    var gyroZ by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(Unit) {
        // Collect IMU updates for display
        launch {
            sensorProvider.imuUpdates.collect { sample ->
                accX = sample.accX
                accY = sample.accY
                accZ = sample.accZ
                gyroX = sample.gyroX
                gyroY = sample.gyroY
                gyroZ = sample.gyroZ
            }
        }
    }

    val currentGeoPoint = remember(currentLat, currentLon) {
        if (currentLat != 0.0 && currentLon != 0.0) {
            io.github.mrroguekknight.drishti.map.createGeoPoint(currentLat, currentLon)
        } else null
    }

    // Collect Location updates
    LaunchedEffect(Unit) {
        launch {
            try {
                locationProvider.getLocationUpdates().collect { location ->
                    currentSpeed = location.speed // Speed in m/s
                    currentLat = location.latitude
                    currentLon = location.longitude
                    currentAccuracy = location.accuracy
                    
                    // Geocoding removed as per user request
                    currentAddress = "Current Location"
                }
            } catch (e: Exception) {
                android.util.Log.e("DriveScreen", "Error collecting location updates", e)
                currentAddress = "Location unavailable"
            }
        }
    }

    // No need for onDispose stop() as AppRoot manages lifecycle

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Map Area - Bottom Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f) // Ensure map is behind header
            ) {
                // OpenStreetMap Map with Navigation Features

                
                // Collect peer locations
                val peerLocations by io.github.mrroguekknight.drishti.network.NetworkDataSync.peerLocations.collectAsState()
                
                // Update distance and ETA when location or destination changes
                LaunchedEffect(currentLat, currentLon, destinationGeoPoint) {
                    if (currentGeoPoint != null && destinationGeoPoint != null) {
                        mapController?.let { controller ->
                            distanceToDestination = controller.calculateDistance(currentGeoPoint, destinationGeoPoint!!)
                            eta = controller.calculateETA(distanceToDestination)
                        }
                    }
                }
                
                io.github.mrroguekknight.drishti.map.OSMMapView(
                    modifier = Modifier.fillMaxSize(),
                    currentLocation = currentGeoPoint,
                    driveMode = driveMode,
                    bearing = currentBearing,
                    speed = currentSpeed,
                    accuracy = currentAccuracy,
                    routeWaypoints = routeWaypoints,
                    peerLocations = peerLocations, // Pass peers to map
                    mapPadding = PaddingValues(top = headerHeight + 16.dp), // Add padding for header
                    onMapControllerReady = { controller ->
                        mapController = controller
                    }
                )
            }
            

            // --- COLLISION WARNING OVERLAY ---
            // 30% Screen Size Warning Sign
            val peerLocations by io.github.mrroguekknight.drishti.network.NetworkDataSync.peerLocations.collectAsState()
            val hasCollisionRisk = peerLocations.any { it.alertLevel == "CRITICAL" } 
            
            // Auto-disappear logic (5 seconds)
            var isAlertVisible by remember { mutableStateOf(false) }
            
            LaunchedEffect(hasCollisionRisk) {
                if (hasCollisionRisk) {
                    isAlertVisible = true
                    // VIBRATION LOGIC
                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(1000)
                    }
                    
                    kotlinx.coroutines.delay(5000) // Show for 5 seconds
                    isAlertVisible = false
                } else {
                     // Optionally keep it hidden if risk clears
                     isAlertVisible = false
                }
            }
            
            if (isAlertVisible) { // Check local state instead of raw risk
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Flashing Red Background Effect (Optional)
                    
                    // Warning Icon
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Collision Warning",
                        tint = Color.Red,
                        modifier = Modifier
                            .fillMaxSize(0.3f) // 30% of screen size
                            .background(Color.Yellow, CircleShape) // Contrast background
                            .padding(16.dp)
                    )
                    
                    Text(
                        text = "COLLISION ALERT!",
                        color = Color.Red,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 100.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
            }




                // Map Controls - Top Right
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = headerHeight + 24.dp, end = 16.dp), // Adjust position based on header
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Drive Mode Toggle
                    androidx.compose.material3.FloatingActionButton(
                        onClick = { driveMode = !driveMode },
                        modifier = Modifier.size(48.dp),
                        containerColor = if (driveMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = "Drive Mode",
                            tint = if (driveMode) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // North Up Button
                    androidx.compose.material3.FloatingActionButton(
                        onClick = { mapController?.resetToNorthUp() },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = "North Up", tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    // Center on Location Button
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            currentGeoPoint?.let { location ->
                                mapController?.centerOnLocation(location, animate = true)
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Center Location", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Live Speed Overlay - Bottom Left
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp), 
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SPEED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "%.2f".format(currentSpeed * 3.6), 
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("km/h", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                // GPS Status Indicator - Bottom Right
                if (currentLat == 0.0 && currentLon == 0.0) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.GpsOff, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Acquiring GPS...", 
                                fontSize = 11.sp, 
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
    
            }

            // Header Card - Top Layer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(1f) // Ensure header is above map
                    .onGloballyPositioned { coordinates ->
                        headerHeight = with(density) { coordinates.size.height.toDp() }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(8.dp), // Increased elevation
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) // Visible border
            ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header with Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "DRISHTI Drive",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Max Speed", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("60", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }

                // Input Fields and Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentAddress, onValueChange = {},
                        label = { Text("Start", fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        readOnly = true,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, 
                        contentDescription = null, 
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("Destination", fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Place, null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (destination.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        // Geocode destination and create route
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                                @Suppress("DEPRECATION")
                                                val addresses = geocoder.getFromLocationName(destination, 1)
                                                if (!addresses.isNullOrEmpty()) {
                                                    val destLocation = addresses[0]
                                                    val destGeoPoint = io.github.mrroguekknight.drishti.map.createGeoPoint(
                                                        destLocation.latitude,
                                                        destLocation.longitude
                                                    )
                                                    
                                                    // Get current location
                                                    val currentGeoPoint = if (currentLat != 0.0 && currentLon != 0.0) {
                                                        io.github.mrroguekknight.drishti.map.createGeoPoint(currentLat, currentLon)
                                                    } else null
                                                    
                                                    if (currentGeoPoint != null) {
                                                        // Fetch route from OSRM
                                                        val route = io.github.mrroguekknight.drishti.map.RouteFetcher.getRoute(currentGeoPoint, destGeoPoint)
                                                        
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            destinationGeoPoint = destGeoPoint
                                                            routeWaypoints = route
                                                            driveMode = true
                                                            
                                                            // Center map to show route context
                                                            mapController?.centerOnLocation(currentGeoPoint, animate = true)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("DriveScreen", "Geocoding/Routing error", e)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Send, "Start Navigation", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }
                
                // Distance/ETA row removed as per user request
            }
        }

        }
    }
}

// Helper extension function for formatting doubles
private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

@Composable
fun ProfileScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferenceStore = remember { io.github.mrroguekknight.drishti.data.PreferenceStore(context) }
    val scope = rememberCoroutineScope()

    // Profile state
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Image picker launcher
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        photoUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Cover Section with Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A73E8),
                            Color(0xFF0D47A1)
                        )
                    )
                )
        ) {
            // Decorative circles
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = 150f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.3f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = 100f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.7f)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PROFILE",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                
                // Member Since Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Member since 2025",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Profile Photo (overlapping cover)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-70).dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        )
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        val bitmap = remember(photoUri) {
                            try {
                                android.graphics.BitmapFactory.decodeStream(
                                    context.contentResolver.openInputStream(photoUri!!)
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Add Photo",
                                modifier = Modifier.size(70.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Add Photo",
                            modifier = Modifier.size(70.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Camera icon overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-50).dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Tap photo to change",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(Modifier.height(16.dp))

            // User Details Card
            InfoCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PERSONAL INFORMATION", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(20.dp))

                    // Name Field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.height(14.dp))

                    // Mobile Field
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.height(14.dp))

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Save Button
                Button(
                    onClick = {
                        android.widget.Toast.makeText(context, "Profile saved!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SAVE", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                
                // Edit Button
                OutlinedButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "Edit mode", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EDIT", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun NetworkScreen(sensorContainer: io.github.mrroguekknight.drishti.ui.SensorContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Data Providers (Shared)
    val networkHelper = sensorContainer.networkHelper
    val locationProvider = sensorContainer.locationProvider
    val sensorProvider = sensorContainer.sensorProvider

    // State
    // State
    val networkStatus by networkHelper.getNetworkStatusFlow().collectAsState(initial = io.github.mrroguekknight.drishti.network.NetworkStatusHelper.NetworkStatus())
    
    var gpsStatus by remember { mutableStateOf("Searching...") }
    var locationData by remember { mutableStateOf("Lat: 0.0, Lon: 0.0") }
    
    var imuStatus by remember { mutableStateOf("Standby") }
    var imuData by remember { mutableStateOf("Acc: 0,0,0") }

    LaunchedEffect(Unit) {
        // Start Auto Discovery
        io.github.mrroguekknight.drishti.network.ServerDiscovery.startListening { ip ->
            io.github.mrroguekknight.drishti.network.NetworkDataSync.serverIp = ip
        }
    }
    
    LaunchedEffect(Unit) {
        io.github.mrroguekknight.drishti.network.NetworkDataSync.initialize(context)
    }

    // Effects
    LaunchedEffect(Unit) {
        // GPS
        launch {
            try {
                locationProvider.getLocationUpdates().collect { loc ->
                    gpsStatus = "Active (Fix)"
                    locationData = "Lat: ${"%.4f".format(loc.latitude)}\nLon: ${"%.4f".format(loc.longitude)}"
                }
            } catch (e: Exception) {
                gpsStatus = "Error/Disabled"
            }
        }
        
        // IMU (Collect from SharedFlow)
        launch {
            imuStatus = "Active (Running)" // It's running in AppRoot
            sensorProvider.imuUpdates.collect { sample ->
                imuData = "Acc: ${"%.1f".format(sample.accX)}, ${"%.1f".format(sample.accY)}, ${"%.1f".format(sample.accZ)}"
            }
        }
    }

    // No dispose needed as providers are shared

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "NETWORK & SENSORS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Grid Layout
        val cardModifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)

        // 1. Network Status Card
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("NETWORK LINK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                
                StatusRow("Type", networkStatus.type)
                StatusRow("State", if (networkStatus.isConnected) "Connected" else "Disconnected", 
                    if (networkStatus.isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                StatusRow("Signal", "${networkStatus.signalStrength}/4 Bars")
                StatusRow("Speed", "${networkStatus.linkSpeed} Mbps")
                StatusRow("IP Addr", networkStatus.ipAddress)
                StatusRow("Freq", "${networkStatus.frequency} MHz")
            }
        }
        
        // 1.5 Central Sync Card
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("CENTRAL SERVER SYNC", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                
                // IP Input
                var ipInput by remember { mutableStateOf(io.github.mrroguekknight.drishti.network.NetworkDataSync.serverIp) }
                
                // Poll for Auto-Discovery updates (Simplest hack for cross-component sync without Flow)
                LaunchedEffect(Unit) {
                    while(true) {
                         if (ipInput != io.github.mrroguekknight.drishti.network.NetworkDataSync.serverIp) {
                             ipInput = io.github.mrroguekknight.drishti.network.NetworkDataSync.serverIp
                         }
                         kotlinx.coroutines.delay(1000)
                    }
                }

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { 
                        ipInput = it
                        io.github.mrroguekknight.drishti.network.NetworkDataSync.serverIp = it
                    },
                    label = { Text("Server IP (Auto-detecting...)") },
                    trailingIcon = { Icon(Icons.Default.Autorenew, "Auto") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Toggle Logic
                var isSyncActive by remember { mutableStateOf(io.github.mrroguekknight.drishti.network.NetworkDataSync.isSyncing) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isSyncActive) "Syncing Active" else "Sync Stopped", color = if (isSyncActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isSyncActive,
                        onCheckedChange = { 
                            isSyncActive = it
                            io.github.mrroguekknight.drishti.network.NetworkDataSync.isSyncing = it
                        }
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Debug Status Line
                var statusText by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    while(true) {
                        statusText = io.github.mrroguekknight.drishti.network.NetworkDataSync.syncStatus
                        kotlinx.coroutines.delay(500)
                    }
                }
                Text(
                    text = "Status: $statusText",
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                )
            }
        }

        // 2. GPS Status Card
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("GNSS / NAVIC", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                StatusRow("Status", gpsStatus, if (gpsStatus.contains("Active")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                StatusRow("Data", locationData)
            }
        }

        // 3. IMU Status Card
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Explore, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("IMU SENSORS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                StatusRow("Status", imuStatus, if (imuStatus.contains("Active")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                StatusRow("Readings", imuData)
            }
        }
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
fun StatusRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

// Status screen: performance metrics + workflow
@Composable
fun StatusScreen(sensorContainer: io.github.mrroguekknight.drishti.ui.SensorContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemHelper = sensorContainer.systemHelper
    val systemStatus by systemHelper.getSystemStatusFlow().collectAsState(initial = io.github.mrroguekknight.drishti.ui.SystemStatusHelper.SystemStatus())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "SYSTEM STATUS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Grid Layout
        val cardModifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)

        // 1. System Health Card
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("DEVICE HEALTH", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                
                // Battery
                Text("Battery Level", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { systemStatus.batteryLevel / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (systemStatus.batteryLevel > 20) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${systemStatus.batteryLevel}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    Text(if (systemStatus.isCharging) "Charging" else "Discharging", color = if (systemStatus.isCharging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Memory
                Text("Memory Usage", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { systemStatus.memoryUsagePercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (systemStatus.memoryUsagePercent < 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${systemStatus.memoryUsagePercent}% Used", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    Text("${systemStatus.availableMemoryMb} MB Free", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        // 2. Workflow Status Card (Simulated for now)
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("PIPELINE STATUS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                
                WorkflowRow("1. Camera Feed", "Ready", MaterialTheme.colorScheme.tertiary)
                WorkflowRow("2. Object Detection", "Standby", MaterialTheme.colorScheme.secondary)
                WorkflowRow("3. Collision Logic", "Standby", MaterialTheme.colorScheme.secondary)
                WorkflowRow("4. Alert System", "Ready", MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun SettingsScreen(onSignOut: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferenceStore = remember { io.github.mrroguekknight.drishti.data.PreferenceStore(context) }
    val scope = rememberCoroutineScope()

    // Preferences State
    val visualAlerts by preferenceStore.visualAlerts.collectAsState(initial = true)
    val audioAlerts by preferenceStore.audioAlerts.collectAsState(initial = true)
    val vibrationAlerts by preferenceStore.vibrationAlerts.collectAsState(initial = true)
    val sensitivity by preferenceStore.sensitivityLevel.collectAsState(initial = "Medium")
    val safeDistance by preferenceStore.safeDistance.collectAsState(initial = 50f)
    val isDarkTheme by preferenceStore.isDarkTheme.collectAsState(initial = androidx.compose.foundation.isSystemInDarkTheme())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "SETTINGS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        val cardModifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)

        // 1. Alert Preferences
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("ALERT CONFIGURATION", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))

                // Visual Alerts
                SettingsToggleRow(
                    label = "Visual Alerts",
                    description = "Color-coded warning displays",
                    checked = visualAlerts,
                    onCheckedChange = { scope.launch { preferenceStore.setVisualAlerts(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                // Audio Alerts
                SettingsToggleRow(
                    label = "Audio Alerts",
                    description = "Sound warnings and notifications",
                    checked = audioAlerts,
                    onCheckedChange = { scope.launch { preferenceStore.setAudioAlerts(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                // Vibration Alerts
                SettingsToggleRow(
                    label = "Vibration Alerts",
                    description = "Haptic feedback for immediate attention",
                    checked = vibrationAlerts,
                    onCheckedChange = { scope.launch { preferenceStore.setVibrationAlerts(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                // Dark Mode
                SettingsToggleRow(
                    label = "Dark Mode",
                    description = "Toggle application theme",
                    checked = isDarkTheme,
                    onCheckedChange = { scope.launch { preferenceStore.setDarkTheme(it) } }
                )
            }
        }

        // 2. System Parameters
        InfoCard(modifier = cardModifier) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("SYSTEM PARAMETERS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))

                // Sensitivity
                Text("Collision Sensitivity", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text("Current: $sensitivity", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Low", "Medium", "High").forEach { level ->
                        FilterChip(
                            selected = sensitivity == level,
                            onClick = { scope.launch { preferenceStore.setSensitivityLevel(level) } },
                            label = { Text(level) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Safe Distance
                Text("Minimum Safe Distance: ${safeDistance.toInt()}m", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Slider(
                    value = safeDistance,
                    onValueChange = { scope.launch { preferenceStore.setSafeDistance(it) } },
                    valueRange = 20f..100f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
fun SettingsToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}



@Composable
fun WorkflowRow(step: String, state: String, color: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = color.copy(alpha = 0.1f)
        ) {
            Text(
                text = state,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
// Force update
// Force update
