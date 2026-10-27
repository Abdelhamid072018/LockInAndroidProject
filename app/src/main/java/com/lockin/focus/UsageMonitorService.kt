package com.lockin.focus

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/** Tracks foreground time for the selected apps. Accessibility must be enabled during setup. */
class UsageMonitorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private var lastTick = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.elapsedRealtime()
            if (lastTick != 0L && LockEngine.isActive(this@UsageMonitorService)) {
                val pkg = foregroundPackage
                if (pkg != null && pkg in LockEngine.selected(this@UsageMonitorService)) {
                    val used = LockEngine.addUsage(this@UsageMonitorService, now - lastTick)
                    if (used >= LockEngine.DAILY_ALLOWANCE_MS) {
                        LockEngine.applySuspensions(this@UsageMonitorService)
                        foregroundPackage = null
                    }
                }
                // Daily allowance renews at local midnight; if it has renewed, restore selected apps.
                val before = LockEngine.usageMs(this@UsageMonitorService)
                LockEngine.resetUsageIfNewDay(this@UsageMonitorService)
                if (before >= LockEngine.DAILY_ALLOWANCE_MS && LockEngine.usageMs(this@UsageMonitorService) == 0L) {
                    LockEngine.applySuspensions(this@UsageMonitorService)
                }
            }
            lastTick = now
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        lastTick = android.os.SystemClock.elapsedRealtime()
        LockEngine.applySuspensions(this)
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                foregroundPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() }
            }
        }
    }

    override fun onInterrupt() { foregroundPackage = null }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
