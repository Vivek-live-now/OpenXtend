package org.openxtend.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openxtend.model.ConnectionStatus
import org.openxtend.model.WatchInfo
import org.openxtend.ui.theme.*

@Composable
fun DashboardScreen(
    connectionStatus: ConnectionStatus,
    watchInfo: WatchInfo,
    discoveredDevices: List<BluetoothDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onFindWatch: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OPENXTEND",
                        color = AccentCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Free & Open boAt Xtend Manager",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Connection status chip
                val (chipColor, text) = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> AccentGreen to "CONNECTED"
                    ConnectionStatus.CONNECTING -> AccentCyan to "CONNECTING"
                    ConnectionStatus.SCANNING -> AccentCyan to "SCANNING"
                    ConnectionStatus.DISCONNECTED -> AccentRed to "OFFLINE"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = chipColor.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(chipColor))
                ) {
                    Text(
                        text = text,
                        color = chipColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Connection & Scan Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DEVICE LINK",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (connectionStatus == ConnectionStatus.CONNECTED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = watchInfo.deviceName,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "FW: v${watchInfo.firmwareVersion} | ID: 0x${watchInfo.deviceId.toString(16).uppercase()}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f), contentColor = AccentRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "No watch connected. Turn on Bluetooth and scan.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (connectionStatus == ConnectionStatus.SCANNING) {
                            Button(
                                onClick = onStopScan,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = DarkBackground,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Scanning", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onStartScan,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = DarkBackground)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan for boAt Xtend", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Discovered Devices list (during scan)
        if (connectionStatus == ConnectionStatus.SCANNING && discoveredDevices.isNotEmpty()) {
            item {
                Text(
                    text = "DISCOVERED DEVICES",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            items(discoveredDevices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConnectDevice(device) },
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            @Suppress("MissingPermission")
                            Text(device.name ?: "Unknown Watch", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(device.address, color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Text("Connect", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Battery & Power Card
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "BATTERY TELEMETRY",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(AccentCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (watchInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                        contentDescription = null,
                                        tint = AccentCyan
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = "${watchInfo.batteryPercent}%",
                                        color = TextPrimary,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = if (watchInfo.isCharging) "Charging..." else if (watchInfo.isLowPower) "Low Power Mode" else "Normal",
                                        color = if (watchInfo.isLowPower) AccentRed else TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (watchInfo.batteryMv > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SurfaceDark,
                                    border = BorderStroke(1.dp, SurfaceBorder)
                                ) {
                                    Text(
                                        text = "${watchInfo.batteryMv} mV",
                                        color = AccentCyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Health Telemetry Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "LIVE ACTIVITY",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Steps
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${watchInfo.liveSteps}",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text("Steps Today", color = TextSecondary, fontSize = 12.sp)
                            }

                            // Heart Rate
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentRed, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (watchInfo.liveHeartRate > 0) "${watchInfo.liveHeartRate}" else "--",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text("BPM Heart Rate", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSyncNow,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Time", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onFindWatch,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.RingVolume, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Find Watch", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
