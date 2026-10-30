package com.example.gemmarbt.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String,
    val toolCallName: String? = null,
    val toolCallArgs: String? = null
)

class LlamaClient(private val baseUrl: String = "http://127.0.0.1:8080") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun chat(
        messages: List<ChatMessage>,
        temperature: Double = 0.2,
        maxTokens: Int = 2048
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val msgsArray = JSONArray()

        for (msg in messages) {
            val obj = JSONObject()
            obj.put("role", msg.role)
            obj.put("content", msg.content)
            msgsArray.put(obj)
        }

        root.put("messages", msgsArray)
        root.put("temperature", temperature)
        root.put("top_p", 0.95)
        root.put("max_tokens", maxTokens)

        val body = root.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string() ?: ""
                throw IllegalStateException("Llama server error: ${resp.code} $errBody")
            }
            val jsonStr = resp.body?.string() ?: ""
            val json = JSONObject(jsonStr)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.optString("content", "")
            } else {
                ""
            }
        }
    }
}
