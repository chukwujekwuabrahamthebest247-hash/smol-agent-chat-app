package com.smolagent.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground Service responsible for keeping the Agent alive in background.
 * Holds CPU WakeLock while agent reasoning/execution is ongoing.
 * Fully connected to AgentStopController for emergency shutdown.
 */
class AgentForegroundService : Service() {

    private val CHANNEL_ID = "SmolAgentServiceChannel"
    private val TAG = "AgentForegroundService"

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmolAgent Active")
            .setContentText("Agent is running in background...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager

        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmolAgent::WakeLock"
        )

        wakeLock.acquire()

        AgentStopController.wakeLock = wakeLock

        Log.d(TAG, "Foreground Service Started & WakeLock Acquired")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            "STOP_AGENT" -> {
                Log.d(TAG, "Stop command received")
                AgentStopController.emergencyStop(this)
            }

            "START_AGENT" -> {
                Log.d(TAG, "Agent Start command received")
                AgentStopController.isAgentRunning = true
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {

        AgentStopController.wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock Released")
            }
        }

        AgentStopController.wakeLock = null

        super.onDestroy()

        Log.d(TAG, "Foreground Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SmolAgent Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
