package com.jarvislite.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.jarvislite.assistant.automation.PhoneActions

/**
 * Runs the assistant (microphone + Gemini Live connection + camera) as a
 * proper Android foreground service, with a persistent notification. This
 * is what lets it keep listening after you leave the app or lock the
 * screen - without this, Android would kill the mic/socket within seconds
 * of the app losing focus.
 */
class JarvisForegroundService : LifecycleService() {

    private var voiceController: VoiceController? = null
    private var geminiClient: GeminiLiveClient? = null

    companion object {
        const val ACTION_START = "com.jarvislite.assistant.START"
        const val ACTION_STOP = "com.jarvislite.assistant.STOP"
        const val EXTRA_API_KEY = "api_key"
        private const val CHANNEL_ID = "jarvis_listening"
        private const val NOTIFICATION_ID = 1001

        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopAssistant()
                stopSelf()
            }
            else -> {
                val apiKey = intent?.getStringExtra(EXTRA_API_KEY) ?: return START_NOT_STICKY
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification("Listening..."), foregroundServiceType()
                )
                startAssistant(apiKey)
            }
        }
        return START_STICKY
    }

    private fun startAssistant(apiKey: String) {
        if (isRunning) return

        // CameraX needs a Lifecycle to bind to - LifecycleService gives us one for free.
        PhoneActions.cameraCapture = CameraCapture(this, this)

        val memoryContext = MemoryStore.asContextString(applicationContext)

        geminiClient = GeminiLiveClient(
            apiKey = apiKey,
            memoryContext = memoryContext,
            onAudioChunk = { bytes -> voiceController?.playModelAudio(bytes) },
            onTextReply = { text -> updateNotification(text.take(80)) },
            onTurnComplete = { voiceController?.onModelTurnComplete() }
        )
        PhoneActions.imageSender = { bytes -> geminiClient?.sendImageChunk(bytes) }
        geminiClient?.connect()

        voiceController = VoiceController(geminiClient!!)
        voiceController?.start()

        isRunning = true
    }

    private fun stopAssistant() {
        voiceController?.stop()
        geminiClient?.disconnect()
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAssistant()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Jarvis Lite", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows when the assistant is actively listening" }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Lite")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else 0
}

