package org.openxtend

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import org.openxtend.model.ConnectionStatus
import org.openxtend.model.NotificationApp
import org.openxtend.model.WatchInfo
import org.openxtend.model.WatchSettings
import org.openxtend.service.WatchSyncService
import org.openxtend.weather.WeatherService
import kotlinx.coroutines.launch
import org.openxtend.ui.screens.ControlsScreen
import org.openxtend.ui.screens.DashboardScreen
import org.openxtend.ui.screens.GeminiScreen
import org.openxtend.ui.screens.NotificationsScreen
import org.openxtend.ui.screens.WatchFaceScreen
import org.openxtend.ui.theme.AccentCyan
import org.openxtend.ui.theme.DarkBackground
import org.openxtend.ui.theme.OpenXtendTheme
import org.openxtend.ui.theme.SurfaceDark

enum class MainTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Watch),
    GEMINI("Gemini AI", Icons.Default.AutoAwesome),
    CONTROLS("Controls", Icons.Default.Tune),
    ALERTS("Alerts", Icons.Default.Notifications),
    FACES("Faces", Icons.Default.Palette)
}

class MainActivity : ComponentActivity() {

    private var syncService: WatchSyncService? = null
    private val isBoundState = mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? WatchSyncService.LocalBinder
            syncService = binder?.getService()
            isBoundState.value = true

            // Set up watch event listeners (e.g. Find My Phone & Voice Assistant)
            syncService?.bleManager?.onFindPhoneRequested = {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Watch triggered: Find Phone!", Toast.LENGTH_LONG).show()
                }
            }
            syncService?.bleManager?.onVoiceAssistantTriggered = { key, _ ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Watch Voice Assistant triggered! (0x${"%02X".format(key)})", Toast.LENGTH_SHORT).show()
                }
            }
            syncService?.bleManager?.onStatusMessage = { msg ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            syncService = null
            isBoundState.value = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        val serviceIntent = Intent(this, WatchSyncService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            OpenXtendTheme {
                MainContent()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBoundState.value) {
            unbindService(serviceConnection)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @Composable
    private fun MainContent() {
        var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
        var settings by remember { mutableStateOf(WatchSettings()) }
        val weatherService = remember { WeatherService(applicationContext) }
        var weatherSummary by remember { mutableStateOf(weatherService.getLastWeatherSummary()) }
        val coroutineScope = rememberCoroutineScope()

        val connectionStatus by (syncService?.bleManager?.connectionStatus?.collectAsState()
            ?: remember { mutableStateOf(ConnectionStatus.DISCONNECTED) })
        val watchInfo by (syncService?.bleManager?.watchInfo?.collectAsState()
            ?: remember { mutableStateOf(WatchInfo()) })
        val discoveredDevices by (syncService?.bleManager?.discoveredDevices?.collectAsState()
            ?: remember { mutableStateOf(emptyList()) })

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark
                ) {
                    MainTab.values().forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = (currentTab == tab),
                            onClick = { currentTab = tab },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DarkBackground,
                                selectedTextColor = AccentCyan,
                                indicatorColor = AccentCyan
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    MainTab.DASHBOARD -> DashboardScreen(
                        connectionStatus = connectionStatus,
                        watchInfo = watchInfo,
                        discoveredDevices = discoveredDevices,
                        onStartScan = { syncService?.bleManager?.startScan() },
                        onStopScan = { syncService?.bleManager?.stopScan() },
                        onConnectDevice = { device -> syncService?.bleManager?.connect(device) },
                        onDisconnect = { syncService?.bleManager?.disconnect() },
                        onPairWatch = { syncService?.bleManager?.pairWatch() },
                        onSyncNow = { syncService?.bleManager?.syncWatch() },
                        onFindWatch = { syncService?.bleManager?.findWatch() }
                    )
                    MainTab.GEMINI -> GeminiScreen(
                        isConnected = (connectionStatus == ConnectionStatus.CONNECTED),
                        onSendToWatch = { replyText ->
                            syncService?.bleManager?.pushVoiceResponse(replyText)
                        }
                    )
                    MainTab.CONTROLS -> ControlsScreen(
                        isConnected = (connectionStatus == ConnectionStatus.CONNECTED),
                        settings = settings,
                        weatherSummary = if (watchInfo.lastWeatherSummary.isNotBlank()) watchInfo.lastWeatherSummary else weatherSummary,
                        onToggleRaiseToWake = { enabled ->
                            settings = settings.copy(raiseToWake = enabled)
                            syncService?.bleManager?.setRaiseToWake(enabled)
                        },
                        onToggleMusicControl = { enabled ->
                            settings = settings.copy(musicControl = enabled)
                            syncService?.bleManager?.setMusicControl(enabled)
                        },
                        onToggleWeather = { enabled ->
                            settings = settings.copy(weatherEnabled = enabled)
                            weatherService.setWeatherEnabled(enabled)
                            syncService?.bleManager?.setWeatherSwitch(enabled)
                        },
                        onPushWeather = {
                            val ble = syncService?.bleManager
                            if (ble != null) {
                                coroutineScope.launch {
                                    val res = weatherService.syncWeatherToWatch(ble)
                                    if (res.isSuccess) {
                                        weatherSummary = res.getOrDefault("")
                                        Toast.makeText(this@MainActivity, "Weather synced: $weatherSummary", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@MainActivity, "Failed to sync weather", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onRebootWatch = {
                            syncService?.bleManager?.rebootWatch()
                        }
                    )
                    MainTab.ALERTS -> NotificationsScreen(
                        isConnected = (connectionStatus == ConnectionStatus.CONNECTED),
                        onSendTestNotification = { app ->
                            syncService?.bleManager?.pushNotification(
                                typeId = app.typeId,
                                sender = "OpenXtend",
                                message = "Test notification on boAt Xtend!"
                            )
                        }
                    )
                    MainTab.FACES -> WatchFaceScreen(
                        isConnected = (connectionStatus == ConnectionStatus.CONNECTED)
                    )
                }
            }
        }
    }
}
