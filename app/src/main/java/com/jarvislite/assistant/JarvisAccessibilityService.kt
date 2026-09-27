package com.jarvislite.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * This service is the "hands" of the assistant. Once the user turns it on
 * manually in Settings -> Accessibility, it can:
 *  - read every piece of text currently visible on screen
 *  - find a UI element by its visible text or content-description
 *  - tap it, long-press it, or type text into it
 *  - launch other apps
 *
 * It does nothing on its own - every action here is only triggered when
 * GeminiLiveClient receives a function-call from the model in response to
 * something the user said out loud.
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        // A static reference so other classes (GeminiLiveClient, automation
        // helpers) can call into the running service instance.
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty - we don't react to every event, we act only
        // when explicitly commanded (see functions below).
    }

    override fun onInterrupt() {}

    /** Returns all visible text on the current screen, for giving the model context. */
    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb)
        return sb.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.text.isNullOrBlank()) sb.append(node.text).append(" | ")
        if (!node.contentDescription.isNullOrBlank()) sb.append(node.contentDescription).append(" | ")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb) }
        }
    }

    /** Finds the first clickable node whose text or description contains [query] and taps it. */
    fun tapByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNodeByText(root, query.lowercase()) ?: return false
        return clickNode(target)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text.contains(query) || desc.contains(query)) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                val found = findNodeByText(it, query)
                if (found != null) return found
            }
        }
        return null
    }

    /** Walks up to the nearest clickable ancestor and performs a click. */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        // fall back to a tap gesture at the node's on-screen location
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /** Taps at raw screen coordinates using a gesture (works even on non-clickable views). */
    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Types [text] into whichever editable field currently has focus, or the first one found. */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                val found = findEditable(it)
                if (found != null) return found
            }
        }
        return null
    }

    /** Presses the system back button. */
    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Goes to the home screen. */
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Launches an app by its package name (e.g. com.whatsapp, com.google.android.youtube). */
    fun launchApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }
}
