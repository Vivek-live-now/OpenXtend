package org.openxtend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openxtend.model.WatchSettings
import org.openxtend.ui.theme.*

@Composable
fun ControlsScreen(
    isConnected: Boolean,
    settings: WatchSettings,
    onToggleRaiseToWake: (Boolean) -> Unit,
    onToggleMusicControl: (Boolean) -> Unit,
    onRebootWatch: () -> Unit
) {
    var showRebootDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "HARDWARE CONTROLS",
            color = AccentCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        if (!isConnected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Connect your boAt Xtend to configure device settings.",
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return
        }

        // Gesture & Display Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "WRIST GESTURES & MEDIA",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                ControlSwitchRow(
                    icon = Icons.Default.ScreenRotation,
                    title = "Raise to Wake",
                    subtitle = "Turn display on automatically when lifting wrist",
                    checked = settings.raiseToWake,
                    onCheckedChange = onToggleRaiseToWake
                )

                HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 12.dp))

                ControlSwitchRow(
                    icon = Icons.Default.MusicNote,
                    title = "Music Control",
                    subtitle = "Control phone media playback from watch menu",
                    checked = settings.musicControl,
                    onCheckedChange = onToggleMusicControl
                )
            }
        }

        // Hardware Power Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DEVICE MANAGEMENT",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showRebootDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f), contentColor = AccentRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reboot Watch (F0 01)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text("Reboot boAt Xtend?", color = TextPrimary) },
            text = { Text("This will send a hardware reboot command to the Realtek MCU.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebootDialog = false
                        onRebootWatch()
                    }
                ) {
                    Text("Reboot", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
private fun ControlSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBackground,
                checkedTrackColor = AccentCyan,
                uncheckedTrackColor = SurfaceBorder
            )
        )
    }
}
