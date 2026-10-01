package com.racerbot.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Performs the swipes/taps and scans the screen text for verification / CAPTCHA prompts. */
class BotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != packageName && pkg != "com.android.systemui") foregroundPackage = pkg
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun swipe(x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long, done: (Boolean) -> Unit): Boolean {
        val path = Path()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
        val g = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { done(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { done(false) }
        }, null)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** True when on-screen text looks like a verification / anti-automation prompt. */
    fun securityPromptVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 250) {
            val n = queue.removeFirst()
            count++
            val t = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).lowercase()
            if (t.isNotBlank()) for (k in KEYWORDS) if (t.contains(k)) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    companion object {
        @Volatile var instance: BotAccessibilityService? = null
        @Volatile var foregroundPackage: String = ""
        private val KEYWORDS = listOf(
            "captcha", "recaptcha", "not a robot", "verify you are human", "security check",
            "xác minh", "xác thực", "không phải robot", "không phải người máy", "kéo thanh trượt",
            "anti-cheat", "gian lận"
        )
    }
}
