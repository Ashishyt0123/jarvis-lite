package com.jarvislite.assistant

import android.util.Base64
import android.util.Log
import com.jarvislite.assistant.automation.PhoneActions
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to Gemini's Live API over a WebSocket. Streams microphone audio up,
 * receives spoken audio back, and handles "function calls" - moments where
 * the model decides it needs to control the phone (open an app, take a
 * photo, make a call, remember something) instead of just talking.
 *
 * Get a free API key at https://aistudio.google.com/apikey and paste it in
 * the Settings screen (never hardcode it here if you plan to share this
 * project with anyone).
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val memoryContext: String,
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onTextReply: (String) -> Unit,
    private val onTurnComplete: () -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // keep the socket open indefinitely
        .build()

    private val url =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

    fun connect() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sendSetupMessage()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(JSONObject(text))
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiLive", "Socket failure: ${t.message}")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "done")
    }

    /** Sends the initial setup message: system prompt (+ remembered facts) and the tools the model may call. */
    private fun sendSetupMessage() {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.0-flash-live-001")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", SYSTEM_PROMPT + "\n\n" + memoryContext)
                    }))
                })
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", buildToolDeclarations())
                }))
            })
        }
        webSocket?.send(setup.toString())
    }

    /** Streams one chunk of raw 16-bit PCM mic audio (16kHz mono) up to the model. */
    fun sendAudioChunk(pcmBytes: ByteArray) {
        val msg = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", Base64.encodeToString(pcmBytes, Base64.NO_WRAP))
                }))
            })
        }
        webSocket?.send(msg.toString())
    }

    /** Sends a photo the assistant just took so the model can "see" and describe/react to it. */
    fun sendImageChunk(jpegBytes: ByteArray) {
        val msg = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
                }))
            })
        }
        webSocket?.send(msg.toString())
    }

    /** Call this the instant the user starts talking again, so Gemini stops its current reply (barge-in). */
    fun interruptPlayback() {
        onTurnComplete()
    }

    private fun handleServerMessage(json: JSONObject) {
        val serverContent = json.optJSONObject("serverContent") ?: run {
            handleToolCall(json)
            return
        }

        val modelTurn = serverContent.optJSONObject("modelTurn")
        modelTurn?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                part.optJSONObject("inlineData")?.let { inline ->
                    val audio = Base64.decode(inline.getString("data"), Base64.NO_WRAP)
                    onAudioChunk(audio)
                }
                part.optString("text", "").takeIf { it.isNotBlank() }?.let { onTextReply(it) }
            }
        }

        if (serverContent.optBoolean("turnComplete", false)) {
            onTurnComplete()
        }
    }

    private fun handleToolCall(json: JSONObject) {
        val toolCall = json.optJSONObject("toolCall") ?: return
        val calls = toolCall.optJSONArray("functionCalls") ?: return
        val responses = JSONArray()

        for (i in 0 until calls.length()) {
            val call = calls.getJSONObject(i)
            val name = call.getString("name")
            val args = call.optJSONObject("args") ?: JSONObject()
            val id = call.optString("id")
            val result = PhoneActions.execute(name, args)

            responses.put(JSONObject().apply {
                put("id", id)
                put("name", name)
                put("response", JSONObject().apply { put("result", result) })
            })
        }

        webSocket?.send(JSONObject().apply {
            put("toolResponse", JSONObject().apply {
                put("functionResponses", responses)
            })
        }.toString())
    }

    private fun buildToolDeclarations(): JSONArray = JSONArray().apply {
        put(functionDecl("open_app", "Opens an installed app by its common name, e.g. YouTube, WhatsApp, Chrome.",
            mapOf("app_name" to "The app to open, e.g. \"YouTube\" or \"WhatsApp\""),
            required = listOf("app_name")))
        put(functionDecl("search_youtube_and_play", "Opens YouTube, searches for a query, and plays the first result.",
            mapOf("query" to "What to search for, e.g. \"Mr Indian Hacker latest video\""),
            required = listOf("query")))
        put(functionDecl("send_whatsapp_message", "Finds a contact in WhatsApp by name and sends them a text message.",
            mapOf("contact_name" to "Name of the contact as saved in WhatsApp", "message" to "The message text to send"),
            required = listOf("contact_name", "message")))
        put(functionDecl("open_chrome_tabs", "Opens one or more websites in Chrome, each in a new tab.",
            mapOf("urls" to "Comma-separated list of URLs or site names, e.g. \"github.com, gmail.com\""),
            required = listOf("urls")))
        put(functionDecl("tap_text_on_screen", "Taps whatever on-screen element contains the given visible text.",
            mapOf("text" to "The visible text or label to tap"),
            required = listOf("text")))
        put(functionDecl("go_back", "Presses the system back button.", emptyMap(), required = emptyList()))
        put(functionDecl("take_photo", "Takes a photo with the phone's camera (no preview shown) and shows it to you so you can describe or react to what's in it.",
            mapOf("camera" to "Either \"back\" or \"front\" (selfie camera). Defaults to back."),
            required = emptyList()))
        put(functionDecl("make_phone_call", "Calls a saved contact by name.",
            mapOf("contact_name" to "The contact's name as saved in Contacts"),
            required = listOf("contact_name")))
        put(functionDecl("remember_fact", "Saves a fact about the user permanently, to be recalled in future conversations.",
            mapOf("key" to "A short label for the fact, e.g. \"mom's name\"", "value" to "The fact itself, e.g. \"Sunita\""),
            required = listOf("key", "value")))
        put(functionDecl("forget_fact", "Deletes a previously remembered fact.",
            mapOf("key" to "The label of the fact to forget"),
            required = listOf("key")))
    }

    private fun functionDecl(
        name: String,
        description: String,
        params: Map<String, String>,
        required: List<String>
    ): JSONObject {
        val properties = JSONObject()
        params.forEach { (key, desc) ->
            properties.put(key, JSONObject().apply {
                put("type", "STRING")
                put("description", desc)
            })
        }
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", properties)
                put("required", JSONArray(required))
            })
        }
    }

    companion object {
        private const val SYSTEM_PROMPT = """
You are a helpful voice assistant living on the user's Android phone.
You can see the phone's screen and camera through the tools you're given,
and can control the phone directly - opening apps, searching YouTube,
sending WhatsApp messages, opening websites, tapping things, taking
photos, and making calls. Keep spoken replies short and natural. When the
user asks you to do something on the phone, call the matching tool
instead of just describing what you would do. When the user tells you to
remember something about them, call remember_fact so you know it next time.
"""
    }
}
