package com.assistivetouch.lite

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PowerAccessibilityService : AccessibilityService() {

    companion object {
        var instance: PowerAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used - this service only needs to trigger global actions.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun showPowerMenu() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
