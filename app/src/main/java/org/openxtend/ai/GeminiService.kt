package org.openxtend.ai

import android.content.Context
import android.util.Base64
import android.util.Log
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
        private const val TAG = "GeminiService"
        private const val PREFS_NAME = "openxtend_gemini_prefs"
        private const val KEY_API_KEY = "gemini_api_key"
        private val MODEL_CANDIDATES = listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-3.8-flash",
            "gemini-3.5-flash"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun getApiKey(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    /**
     * Ask Gemini a text prompt with smart fallback across models.
     */
    suspend fun askGemini(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        if (apiKey.startsWith("gen-lang-client", ignoreCase = true)) {
            return@withContext Result.failure(
                Exception("'$apiKey' is a Google Cloud Project ID, NOT an API key!\n\nPlease open https://aistudio.google.com/apikey and copy your Gemini API key.")
            )
        }

        var lastError: Exception? = null
        for (model in MODEL_CANDIDATES) {
            val res = queryModelText(model, apiKey, prompt)
            if (res.isSuccess) {
                return@withContext res
            }
            lastError = res.exceptionOrNull() as? Exception
            Log.w(TAG, "Model $model failed: ${lastError?.message}, trying next candidate...")
        }

        Result.failure(lastError ?: Exception("All Gemini model candidates failed."))
    }

    /**
     * Process audio recorded from the smartwatch's built-in microphone directly with Gemini multimodal.
     */
    suspend fun askGeminiAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/wav"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val promptInstruction = "The user spoke into their smartwatch microphone. Transcribe their question and answer it concisely in under 35 words for the watch screen."

        var lastError: Exception? = null
        for (model in MODEL_CANDIDATES) {
            val res = queryModelAudio(model, apiKey, base64Audio, mimeType, promptInstruction)
            if (res.isSuccess) {
                return@withContext res
            }
            lastError = res.exceptionOrNull() as? Exception
            Log.w(TAG, "Model $model audio failed: ${lastError?.message}, trying next candidate...")
        }

        Result.failure(lastError ?: Exception("Failed to process watch audio with Gemini."))
    }

    private fun queryModelText(modelName: String, apiKey: String, prompt: String): Result<String> {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val systemInstruction = "You are a concise tactical AI assistant for a smartwatch. Provide answers under 35 words, clear, high-contrast, easily readable on wrist. Prompt: "

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
                return Result.failure(Exception("Gemini API Error ($modelName ${response.code}): $responseString"))
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

    private fun queryModelAudio(
        modelName: String,
        apiKey: String,
        base64Audio: String,
        mimeType: String,
        prompt: String
    ): Result<String> {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            val audioPart = JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mime_type", mimeType)
                                    put("data", base64Audio)
                                }
                                put("inline_data", inlineData)
                            }
                            val textPart = JSONObject().apply {
                                put("text", prompt)
                            }
                            put(audioPart)
                            put(textPart)
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
                return Result.failure(Exception("Gemini Audio Error ($modelName ${response.code}): $responseString"))
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

            return Result.failure(Exception("No response content from Gemini audio."))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
