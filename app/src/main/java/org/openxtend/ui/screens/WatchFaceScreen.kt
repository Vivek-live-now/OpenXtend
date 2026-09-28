package org.openxtend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openxtend.ui.theme.*

@Composable
fun WatchFaceScreen(
    isConnected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "WATCH FACES (.IWF)",
            color = AccentCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "OFFLINE SIDELOADING",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Unlike the official boAt app (which relied on abandoned cloud servers that broke the dial store), OpenXtend supports direct local sideloading.",
                    color = TextPrimary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "• Format: .iwf / .iwf.lz (LZ4 compressed)\n" +
                            "• Transport: Bulk characteristic 0x0AF1 with V3 frame handshakes\n" +
                            "• Zero Cloud: Upload any community dial directly from your phone storage",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { /* File picker invocation */ },
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select .iwf File to Flash", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
