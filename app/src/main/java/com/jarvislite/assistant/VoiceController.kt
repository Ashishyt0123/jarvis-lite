package com.jarvislite.assistant

import android.annotation.SuppressLint
import android.media.*
import kotlinx.coroutines.*

/**
 * Continuously records the microphone and streams it to Gemini. While the
 * model is speaking back (via AudioTrack playback), it keeps listening in
 * the background - if it detects the user's voice volume crossing a
 * threshold, it immediately stops playback and lets the new audio interrupt
 * the model's turn (this is the "barge-in" behaviour from the reference app).
 */
class VoiceController(
    private val geminiClient: GeminiLiveClient
) {
    private val sampleRate = 16000
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordJob: Job? = null
    private var isModelSpeaking = false

    private val minBufSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(24000) // Gemini Live returns 24kHz PCM audio
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
        .setBufferSizeInBytes(minBufSize.coerceAtLeast(4096))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    /** Call once RECORD_AUDIO permission is granted. Runs until [stop] is called. */
    @SuppressLint("MissingPermission")
    fun start() {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufSize.coerceAtLeast(4096)
        )
        recorder.startRecording()
        audioTrack.play()

        recordJob = scope.launch {
            val buffer = ByteArray(1280) // ~40ms chunks at 16kHz/16-bit
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    if (isModelSpeaking && isLoudEnough(buffer, read)) {
                        // user started talking over the model - barge in
                        audioTrack.pause()
                        audioTrack.flush()
                        isModelSpeaking = false
                        geminiClient.interruptPlayback()
                    }
                    geminiClient.sendAudioChunk(buffer.copyOf(read))
                }
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        audioTrack.stop()
    }

    /** Feed audio bytes received from Gemini here to play them back. */
    fun playModelAudio(pcmBytes: ByteArray) {
        isModelSpeaking = true
        audioTrack.write(pcmBytes, 0, pcmBytes.size)
    }

    fun onModelTurnComplete() {
        isModelSpeaking = false
    }

    /** Very simple energy-based voice-activity check used only for barge-in detection. */
    private fun isLoudEnough(buffer: ByteArray, length: Int): Boolean {
        var sum = 0L
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += Math.abs(sample)
            i += 2
        }
        val avg = sum / (length / 2)
        return avg > 1500 // tune this threshold if barge-in feels too sensitive or too sluggish
    }
}
