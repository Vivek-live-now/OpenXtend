package org.openxtend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.openxtend.ai.GeminiService
import org.openxtend.ui.theme.*

@Composable
fun GeminiScreen(
    isConnected: Boolean,
    onSendToWatch: (String) -> Unit
) {
    val context = LocalContext.current
    val geminiService = remember { GeminiService(context) }
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(geminiService.getApiKey()) }
    var prompt by remember { mutableStateOf("") }
    var lastResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isApiKeySaved by remember { mutableStateOf(apiKey.isNotBlank()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GEMINI ON WRIST",
                        color = AccentCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Smart AI Assistant for boAt Xtend",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentCyan))
                ) {
                    Text(
                        text = "GEMINI 3.8 FLASH",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // API Key Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GEMINI API CONFIGURATION",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { 
                            apiKey = it
                            isApiKeySaved = false
                        },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("Paste API key from AI Studio...") },
                        singleLine = true,
                        isError = apiKey.startsWith("gen-lang-client", ignoreCase = true),
                        supportingText = {
                            if (apiKey.startsWith("gen-lang-client", ignoreCase = true)) {
                                Text(
                                    "⚠️ This is a Project ID. Enter your API Key from aistudio.google.com/apikey",
                                    color = AccentRed,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            errorBorderColor = AccentRed,
                            errorTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            geminiService.saveApiKey(apiKey)
                            isApiKeySaved = apiKey.isNotBlank()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = DarkBackground)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isApiKeySaved) "API Key Saved" else "Save Key", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick AI Prompt Chips
        item {
            Text(
                text = "QUICK AI TELEMETRY & BRIEFINGS",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            val quickPrompts = listOf(
                "🌅 Give me a 2-line energizing morning brief and focus tip.",
                "🏃 Provide an energetic workout encouragement quote in 15 words.",
                "💡 Tell me one fascinating science fact in 20 words.",
                "🧘 30-second breathwork guidance for quick stress relief."
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickPrompts.forEach { q ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceDark,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorder)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                prompt = q
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = q,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Ask Prompt Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ASK & BEAM TO WRIST",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("What should Gemini beam to your watch?") },
                        placeholder = { Text("e.g. Summarize today's goals...") },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (prompt.isBlank()) return@Button
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                val result = geminiService.askGemini(prompt)
                                isLoading = false
                                result.onSuccess { reply ->
                                    lastResponse = reply
                                    if (isConnected) {
                                        onSendToWatch(reply)
                                    }
                                }.onFailure { err ->
                                    errorMessage = err.localizedMessage ?: "Unknown error"
                                }
                            }
                        },
                        enabled = !isLoading && prompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Querying Gemini...", color = DarkBackground, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ask & Beam to Watch", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Error message if any
        if (errorMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentRed.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentRed))
                ) {
                    Text(
                        text = "Error: $errorMessage",
                        color = AccentRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Response Card
        if (lastResponse.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BEAMED TO WRIST",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            if (isConnected) {
                                Button(
                                    onClick = { onSendToWatch(lastResponse) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f), contentColor = AccentCyan),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resend", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceDark,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorder)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lastResponse,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
