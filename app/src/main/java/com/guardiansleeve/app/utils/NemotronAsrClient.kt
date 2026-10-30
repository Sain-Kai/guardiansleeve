package com.guardiansleeve.app.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * NVIDIA Nemotron / Parakeet ASR API Client
 * Provides ultra-fast ~80ms latency speech-to-text recognition via NVIDIA NIM.
 * Endpoint: https://integrate.api.nvidia.com/v1/audio/transcriptions
 */
class NemotronAsrClient(
    private var apiKey: String = "" // Set your NVIDIA API key (nvapi-...)
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun setApiKey(key: String) {
        this.apiKey = key.trim()
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank() && apiKey.startsWith("nvapi-")

    /**
     * Transcribes audio using NVIDIA Nemotron / Parakeet ASR NIM
     */
    suspend fun transcribeAudio(audioFile: File): String? = withContext(Dispatchers.IO) {
        if (!hasApiKey() || !audioFile.exists() || audioFile.length() == 0L) {
            return@withContext null
        }

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "nvidia/parakeet-ctc-1.1b-asr")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("language", "en")
                .build()

            val request = Request.Builder()
                .url(NVIDIA_ASR_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val respBody = response.body?.string() ?: ""

            Log.d(TAG, "NVIDIA Nemotron ASR Response [${response.code}] in ${latency}ms: $respBody")

            if (response.isSuccessful && respBody.isNotBlank()) {
                val json = JSONObject(respBody)
                val transcript = json.optString("text", "")
                response.close()
                return@withContext transcript
            }
            response.close()
        } catch (e: Exception) {
            Log.w(TAG, "NVIDIA Nemotron ASR call failed: ${e.message}")
        }
        return@withContext null
    }

    companion object {
        private const val TAG = "NemotronAsrClient"
        private const val NVIDIA_ASR_ENDPOINT = "https://integrate.api.nvidia.com/v1/audio/transcriptions"
    }
}
