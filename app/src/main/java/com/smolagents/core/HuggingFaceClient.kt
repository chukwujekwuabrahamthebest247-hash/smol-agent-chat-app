

package com.smolagent.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Client for interacting with Hugging Face Inference API.
 * Replaces Gemini with SmolChat hosted on Hugging Face.
 */
class HuggingFaceClient(private val apiKey: String, private val endpoint: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val SERVER_URL = "https://ais-dev-esc5rtgxknnq7u73ormhxu-50241664350.europe-west2.run.app" // Replace with APP_URL

    fun syncWithServer(screenshotBase64: String?, uiTree: String, status: String, lastAction: String?) {
        val json = JSONObject()
        json.put("screenshot", screenshotBase64)
        json.put("uiTree", uiTree)
        json.put("status", status)
        json.put("lastAction", lastAction)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$SERVER_URL/api/agent/sync")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e("AgentSync", "Failed to sync with server", e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.d("AgentSync", "Synced successfully: ${response.code}")
            }
        })
    }
}
