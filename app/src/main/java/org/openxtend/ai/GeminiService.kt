package org.openxtend.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "openxtend_gemini_prefs"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val PRIMARY_MODEL = "gemini-3.8-flash"
        private const val FALLBACK_MODEL = "gemini-2.5-flash"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getApiKey(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    suspend fun askGemini(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Enter your key in settings."))
        }

        if (apiKey.startsWith("gen-lang-client", ignoreCase = true)) {
            return@withContext Result.failure(
                Exception("'$apiKey' is a Google Cloud Project ID, NOT an API key!\n\nPlease open https://aistudio.google.com/apikey and copy your Gemini API key.")
            )
        }

        // Try gemini-3.8-flash first
        val firstAttempt = queryModel(PRIMARY_MODEL, apiKey, prompt)
        if (firstAttempt.isSuccess) {
            return@withContext firstAttempt
        }

        val err = firstAttempt.exceptionOrNull()
        if (err != null && (err.message?.contains("404") == true || err.message?.contains("not found") == true)) {
            // Fallback to gemini-2.5-flash
            val fallbackAttempt = queryModel(FALLBACK_MODEL, apiKey, prompt)
            if (fallbackAttempt.isSuccess) {
                return@withContext fallbackAttempt
            }
        }

        return@withContext firstAttempt
    }

    private fun queryModel(modelName: String, apiKey: String, prompt: String): Result<String> {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            // System prompt tailored for 1.69" smartwatch screen readability
            val systemInstruction = "You are a concise tactical AI assistant for a smartwatch. Provide answers under 40 words, clear, high-contrast, easily readable on wrist. Prompt: "

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", systemInstruction + prompt)
                            }
                            put(partObj)
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(Exception("Gemini API Error (${response.code}): $responseString"))
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    return Result.success(text.trim())
                }
            }

            return Result.failure(Exception("No response content from Gemini."))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
