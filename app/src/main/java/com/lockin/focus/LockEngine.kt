package com.lockin.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager

object LockEngine {
    private const val PREFS = "lockin_state"
    private const val KEY_DEADLINE = "deadline"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_ACTIVE = "active"
    private const val KEY_USAGE_DAY = "usage_day"
    private const val KEY_USAGE_MS = "usage_ms"
    const val DAILY_ALLOWANCE_MS = 40L * 60L * 1000L

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun admin(context: Context) = ComponentName(context, LockInAdminReceiver::class.java)
    fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)
    fun isOwner(context: Context) = dpm(context).isDeviceOwnerApp(context.packageName)
    fun isActive(context: Context) = prefs(context).getBoolean(KEY_ACTIVE, false) &&
        prefs(context).getLong(KEY_DEADLINE, 0L) > System.currentTimeMillis()
    fun deadline(context: Context) = prefs(context).getLong(KEY_DEADLINE, 0L)
    fun selected(context: Context): Set<String> = prefs(context).getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun saveSelection(context: Context, packages: Set<String>) {
        check(!isActive(context)) { "Selection cannot change during an active commitment." }
        prefs(context).edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    fun start(context: Context, days: Int) {
        require(isOwner(context)) { "Device Owner mode is not enabled. Complete the computer setup first." }
        val packages = selected(context).filter { it != context.packageName }.toSet()
        require(packages.isNotEmpty()) { "Select at least one app to block." }
        val until = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
        prefs(context).edit().putLong(KEY_DEADLINE, until).putBoolean(KEY_ACTIVE, true).apply()
        val policy = dpm(context)
        policy.setUninstallBlocked(admin(context), context.packageName, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Blocks normal Settings actions such as force-stop, clear storage and uninstall.
            policy.setUserControlDisabledPackages(admin(context), listOf(context.packageName))
        }
        // Keep the wall clock synchronized so changing the phone's date is less useful
        // as a way to manipulate the fixed commitment deadline.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { policy.setAutoTimeRequired(admin(context), true) } catch (_: Exception) { }
        }
        // Defense in depth: restrict common Settings-based escape routes for the duration.
        listOf(
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_DEBUGGING_FEATURES
        ).forEach { restriction ->
            try { policy.addUserRestriction(admin(context), restriction) } catch (_: Exception) { }
        }
        // Selected apps are available at the beginning of each daily allowance.
        // The AccessibilityService measures foreground time and suspends them at 40 minutes.
        policy.setPackagesSuspended(admin(context), packages.toTypedArray(), false)
        resetUsageIfNewDay(context)
        scheduleExpiry(context, until)
    }

    fun applySuspensions(context: Context): Set<String> {
        val p = prefs(context)
        val dpm = dpm(context)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return emptySet()
        val deadline = p.getLong(KEY_DEADLINE, 0L)
        val active = p.getBoolean(KEY_ACTIVE, false)
        val targets = p.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()
        if (active && deadline > System.currentTimeMillis()) {
            resetUsageIfNewDay(context)
            val valid = targets.filter { it != context.packageName && it != context.packageName + ".provider" }
            val shouldSuspend = usageMs(context) >= DAILY_ALLOWANCE_MS
            val failures = if (valid.isNotEmpty()) {
                dpm.setPackagesSuspended(admin(context), valid.toTypedArray(), shouldSuspend).toSet()
            } else emptySet()
            scheduleExpiry(context, deadline)
            return failures
        } else if (active) {
            end(context)
        }
        return emptySet()
    }

    fun usageMs(context: Context): Long = prefs(context).getLong(KEY_USAGE_MS, 0L).coerceAtLeast(0L)

    fun resetUsageIfNewDay(context: Context) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val p = prefs(context)
        if (p.getString(KEY_USAGE_DAY, null) != today) {
            p.edit().putString(KEY_USAGE_DAY, today).putLong(KEY_USAGE_MS, 0L).apply()
        }
    }

    fun addUsage(context: Context, deltaMs: Long): Long {
        resetUsageIfNewDay(context)
        val p = prefs(context)
        val updated = (p.getLong(KEY_USAGE_MS, 0L) + deltaMs.coerceAtLeast(0L)).coerceAtMost(DAILY_ALLOWANCE_MS)
        p.edit().putLong(KEY_USAGE_MS, updated).apply()
        return updated
    }

    fun end(context: Context) {
        val dpm = dpm(context)
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            val targets = selected(context).filter { it != context.packageName }
            if (targets.isNotEmpty()) dpm.setPackagesSuspended(admin(context), targets.toTypedArray(), false)
            dpm.setUninstallBlocked(admin(context), context.packageName, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setUserControlDisabledPackages(admin(context), emptyList())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { dpm.setAutoTimeRequired(admin(context), false) } catch (_: Exception) { }
            }
            listOf(
                android.os.UserManager.DISALLOW_APPS_CONTROL,
                android.os.UserManager.DISALLOW_UNINSTALL_APPS,
                android.os.UserManager.DISALLOW_SAFE_BOOT,
                android.os.UserManager.DISALLOW_FACTORY_RESET,
                android.os.UserManager.DISALLOW_DEBUGGING_FEATURES
            ).forEach { restriction ->
                try { dpm.clearUserRestriction(admin(context), restriction) } catch (_: Exception) { }
            }
        }
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply()
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(expiryPendingIntent(context))
    }

    private fun scheduleExpiry(context: Context, whenMillis: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, expiryPendingIntent(context))
    }

    private fun expiryPendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 341, Intent(context, ExpiryReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
