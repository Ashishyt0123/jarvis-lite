package com.jarvislite.assistant

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvislite.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    // Every permission the assistant might need, requested together up front.
    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startAssistantService()
        else toast("Assistant needs all permissions to work fully - grant them in Settings if you skipped one")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("jarvis_lite", MODE_PRIVATE)

        binding.enableAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.memoryButton.setOnClickListener {
            startActivity(Intent(this, MemoryActivity::class.java))
        }

        binding.startButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                toast("Turn on Jarvis Lite in Accessibility settings first")
                return@setOnClickListener
            }
            val apiKey = prefs.getString("gemini_api_key", "") ?: ""
            if (apiKey.isEmpty()) {
                toast("Add your Gemini API key in Settings first")
                return@setOnClickListener
            }
            val missing = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) startAssistantService() else permissionRequest.launch(missing.toTypedArray())
        }

        binding.stopButton.setOnClickListener {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_STOP
            }
            startService(intent)
            binding.statusText.text = "Stopped"
        }
    }

    override fun onResume() {
        super.onResume()
        binding.statusText.text = if (JarvisForegroundService.isRunning) "Listening (running in background)" else "Idle"
    }

    private fun startAssistantService() {
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_START
            putExtra(JarvisForegroundService.EXTRA_API_KEY, apiKey)
        }
        ContextCompat.startForegroundService(this, intent)
        binding.statusText.text = "Listening (running in background)"
        toast("Assistant started - you can leave the app, it keeps listening")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "$packageName/${JarvisAccessibilityService::class.java.canonicalName}"
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
