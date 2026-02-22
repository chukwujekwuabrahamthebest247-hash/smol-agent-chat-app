

package com.smolagent.core

import android.os.Bundle
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var vaultManager: VaultManager
    private lateinit var projectionManager: MediaProjectionManager
    private val REQUEST_CODE_SCREEN_CAPTURE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vaultManager = VaultManager()
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val saveBtn = findViewById<Button>(R.id.saveBtn)
        val startBtn = findViewById<Button>(R.id.startBtn)

        saveBtn.setOnClickListener {
            val key = apiKeyInput.text.toString()
            if (key.isNotEmpty()) {
                val encrypted = vaultManager.encrypt(key)
                // Save encrypted key to SharedPreferences
                getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("hf_key", encrypted).apply()
                Toast.makeText(this, "API Key Saved Securely", Toast.LENGTH_SHORT).show()
            }
        }

        startBtn.setOnClickListener {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            val intent = Intent(this, AgentForegroundService::class.java)
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("data", data)
            startForegroundService(intent)
            Toast.makeText(this, "Agent Started", Toast.LENGTH_SHORT).show()
        }
    }
}
