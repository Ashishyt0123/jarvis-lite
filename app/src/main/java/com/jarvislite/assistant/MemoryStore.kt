package com.jarvislite.assistant

import android.content.Context
import org.json.JSONObject

/**
 * A tiny persistent memory for the assistant - facts the user tells it to
 * remember (e.g. "remember my mom's number is saved as Mummy") survive even
 * after the app is closed and restarted, and are injected into every new
 * Gemini conversation so it stays consistent across sessions.
 *
 * Stored as a single JSON object in SharedPreferences: { "fact key": "value" }
 */
object MemoryStore {

    private const val PREFS = "jarvis_memory"
    private const val KEY_FACTS = "facts_json"

    fun remember(context: Context, key: String, value: String) {
        val facts = getAll(context)
        facts.put(key, value)
        save(context, facts)
    }

    fun forget(context: Context, key: String) {
        val facts = getAll(context)
        facts.remove(key)
        save(context, facts)
    }

    fun forgetAll(context: Context) {
        save(context, JSONObject())
    }

    fun getAll(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_FACTS, "{}") ?: "{}"
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    /** Renders stored facts as plain text to paste into the model's system prompt. */
    fun asContextString(context: Context): String {
        val facts = getAll(context)
        if (facts.length() == 0) return ""
        val sb = StringBuilder("Known facts about the user (remembered from earlier conversations):\n")
        facts.keys().forEach { key -> sb.append("- $key: ${facts.getString(key)}\n") }
        return sb.toString()
    }

    private fun save(context: Context, facts: JSONObject) {
        prefs(context).edit().putString(KEY_FACTS, facts.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
