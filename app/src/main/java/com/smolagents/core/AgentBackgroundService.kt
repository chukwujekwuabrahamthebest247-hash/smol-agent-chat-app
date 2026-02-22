
package com.smolagent.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background Service for SmolAgent.
 * Observes screen, sends UI + commands to Hugging Face SmolChat,
 * executes actions via AccessibilityService, and retries if needed.
 */
class AgentBackgroundService : Service() {

    companion object {
        private const val TAG = "AgentBGService"
        private const val NOTIFICATION_ID = 1234
        private const val CHANNEL_ID = "agent_channel"
        private const val CHANNEL_NAME = "Agent Background Service"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private val MAX_RETRIES = 5
    private val RETRY_DELAY = 1000L // ms

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val vault by lazy { SecurityVault(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        Log.d(TAG, "AgentBackgroundService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            serviceScope.launch { startAgentLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        wakeLock?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::WakeLock")
        wakeLock?.acquire()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setLargeIcon(BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_info_details))
            .setColor(Color.BLUE)
            .setContentTitle("SmolAgent Running")
            .setContentText("Agent is monitoring and planning...")
            .setOngoing(true)
            .build()
    }

    // ---------------------------
    // AGENT LOOP
    // ---------------------------
    private suspend fun startAgentLoop() {
        while (isRunning) {
            try {
                val accessibility = AgentAccessibilityService.instance ?: continue

                // 1. Capture UI tree
                val uiTree = accessibility.captureUiTree()

                // 2. Compose prompt with user command (for now placeholder)
                val userCommand = "Perform next planned action"
                val promptJson = JSONObject().apply {
                    put("ui_tree", uiTree)
                    put("user_command", userCommand)
                }

                // 3. Send to Hugging Face SmolChat
                val apiKey = vault.getApiKey() ?: continue
                val endpoint = vault.getEndpoint() ?: continue
                val actions = querySmolChat(endpoint, apiKey, promptJson.toString())

                // 4. Execute actions via AccessibilityService
                for (action in actions) {
                    executeActionWithRetry(accessibility, action)
                }

                delay(2000) // Poll interval
            } catch (e: Exception) {
                Log.e(TAG, "Agent loop error: ${e.message}", e)
                delay(2000)
            }
        }
    }

    // ---------------------------
    // ACTION EXECUTION WITH RETRY
    // ---------------------------
    private fun executeActionWithRetry(
        accessibility: AgentAccessibilityService,
        action: AgentAccessibilityService.AiAction
    ) {
        var attempts = 0
        while (attempts < MAX_RETRIES) {
            try {
                accessibility.executeAiAction(action)
                // Optional: verify success using UI capture & AI
                break
            } catch (e: Exception) {
                attempts++
                Thread.sleep(RETRY_DELAY)
            }
        }
    }

    // ---------------------------
    // HUGGING FACE INFERENCE
    // ---------------------------
    private fun querySmolChat(endpoint: String, apiKey: String, promptJson: String): List<AgentAccessibilityService.AiAction> {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.use { it.write(promptJson.toByteArray()) }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        Log.d(TAG, "SmolChat Response: $response")

        // Parse JSON response to AiAction list
        val actions = mutableListOf<AgentAccessibilityService.AiAction>()
        try {
            val jsonArray = JSONObject(response).getJSONArray("actions")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = AgentAccessibilityService.AiAction.Type.valueOf(obj.getString("type"))
                val action = AgentAccessibilityService.AiAction(
                    type = type,
                    x = obj.optDouble("x", 0.0).toFloat(),
                    y = obj.optDouble("y", 0.0).toFloat(),
                    startX = obj.optDouble("startX", 0.0).toFloat(),
                    startY = obj.optDouble("startY", 0.0).toFloat(),
                    endX = obj.optDouble("endX", 0.0).toFloat(),
                    endY = obj.optDouble("endY", 0.0).toFloat(),
                    text = obj.optString("text", null)
                )
                actions.add(action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SmolChat response: ${e.message}", e)
        }

        return actions
    }
}
