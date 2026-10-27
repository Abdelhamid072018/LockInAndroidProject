package com.lockin.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (LockEngine.deadline(context) <= System.currentTimeMillis()) LockEngine.end(context)
        else LockEngine.applySuspensions(context)
    }
}
