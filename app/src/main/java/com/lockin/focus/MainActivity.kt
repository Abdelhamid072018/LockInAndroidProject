package com.lockin.focus

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private var durationDays = 30
    private val checks = mutableMapOf<String, CheckBox>()
    private lateinit var root: LinearLayout
    private lateinit var appList: LinearLayout
    private lateinit var status: TextView
    private lateinit var durationRow: LinearLayout
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(20))
            setBackgroundColor(Color.rgb(11, 18, 32))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(label("LOCKIN", 30, Color.WHITE, true))
        content.addView(label("Make the decision once. Keep the commitment.", 15, Color.rgb(178, 190, 210), false).apply { setPadding(0, dp(4), 0, dp(18)) })

        status = label("", 14, Color.WHITE, false)
        status.setPadding(dp(14), dp(14), dp(14), dp(14))
        status.background = rounded(Color.rgb(24, 35, 55), 14)
        content.addView(status, matchWrap())
        content.addView(label("COMMITMENT", 13, Color.rgb(147, 163, 190), true).apply { setPadding(0, dp(22), 0, dp(8)) })

        durationRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listOf(7, 30, 60, 90).forEach { days ->
            val b = Button(this).apply {
                text = "$days days"
                isAllCaps = false
                setOnClickListener {
                    if (LockEngine.isActive(this@MainActivity)) toast("Commitment is active. Duration cannot be changed.")
                    else { durationDays = days; updateDurationButtons(durationRow) }
                }
                tag = days
            }
            durationRow.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        }
        content.addView(durationRow)
        updateDurationButtons(durationRow)

        content.addView(label("DAILY ALLOWANCE: 40 MINUTES TOTAL", 13, Color.rgb(96, 165, 250), true).apply { setPadding(0, dp(22), 0, dp(4)) })
        content.addView(label("Your selected apps share one 40-minute allowance per calendar day. When it runs out, all selected apps are blocked until local midnight.", 13, Color.rgb(178, 190, 210), false).apply { setPadding(0, 0, 0, dp(8)) })
        content.addView(label("CHOOSE APPS TO LIMIT", 13, Color.rgb(147, 163, 190), true).apply { setPadding(0, dp(12), 0, dp(4)) })
        content.addView(label("Select only the apps you want locked. You cannot change this list while the commitment is active.", 13, Color.rgb(178, 190, 210), false).apply { setPadding(0, 0, 0, dp(8)) })
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.rgb(19, 29, 46), 14); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        content.addView(appList, matchWrap())
        populateApps()

        startButton = Button(this).apply {
            text = "START LOCKED COMMITMENT"
            isAllCaps = false
            textSize = 15f
            setOnClickListener { startCommitment() }
        }
        content.addView(startButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(18) })
        val note = label("LockIn requires Device Owner setup from a computer. Starting a commitment suspends selected apps until the timer expires.", 12, Color.rgb(147, 163, 190), false)
        note.setPadding(0, dp(12), 0, dp(8))
        content.addView(note)
        setContentView(root)
        refreshStatus()
        setSelectionEnabled(!LockEngine.isActive(this))
    }

    private fun populateApps() {
        appList.removeAllViews(); checks.clear()
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (android.os.Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        val selected = LockEngine.selected(this)
        val own = packageName
        // Exclude only LockIn and essential system surfaces. Do not exclude every Google
        // package: YouTube is commonly preinstalled as com.google.android.youtube.
        val protectedPackages = setOf(
            "com.android.settings", "com.android.systemui", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.android.phone", "com.android.dialer",
            "com.google.android.dialer", "com.android.emergency", "com.google.android.emergency",
            "com.android.launcher", "com.google.android.setupwizard"
        )
        val apps = activities.mapNotNull { ri ->
            val ai = ri.activityInfo.applicationInfo
            if (ai.packageName == own || ai.packageName.startsWith("com.lockin.") || ai.packageName in protectedPackages) null
            else Triple(ai.packageName, ri.loadLabel(pm)?.toString() ?: ai.packageName, ri.loadIcon(pm))
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        for ((pkg, name, icon) in apps) {
            val cb = CheckBox(this).apply {
                text = name
                textSize = 15f
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(96, 165, 250))
                isChecked = pkg in selected
                contentDescription = "$name ($pkg)"
                setOnCheckedChangeListener { _, _ -> persistSelectionIfUnlocked() }
            }
            appList.addView(cb, LinearLayout.LayoutParams(-1, dp(48)))
            checks[pkg] = cb
        }
        if (apps.isEmpty()) appList.addView(label("No launchable apps found.", 14, Color.WHITE, false))
    }

    private fun persistSelectionIfUnlocked() {
        if (LockEngine.isActive(this)) return
        LockEngine.saveSelection(this, checks.filterValues { it.isChecked }.keys)
    }

    private fun startCommitment() {
        try {
            persistSelectionIfUnlocked()
            if (!LockEngine.isOwner(this)) {
                toast("Device Owner mode is not enabled. Follow SETUP.md on your computer, then reopen LockIn.")
                return
            }
            if (!isUsageMonitorEnabled()) {
                toast("Enable LockIn daily allowance monitor in Android Accessibility settings before starting.")
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return
            }
            LockEngine.start(this, durationDays)
            setSelectionEnabled(false)
            refreshStatus()
            val failures = LockEngine.applySuspensions(this)
            if (failures.isNotEmpty()) {
                toast("Commitment started, but Android did not suspend: ${failures.joinToString()}. Do not rely on these apps being blocked.")
            } else {
                toast("Commitment started. Selected apps are now suspended.")
            }
        } catch (e: Exception) {
            toast(e.message ?: "Could not start commitment")
        }
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        status.text = when {
            LockEngine.isActive(this) -> "LOCK ACTIVE\nEnds ${DateFormat.getDateTimeInstance().format(Date(LockEngine.deadline(this)))}\nDaily allowance used: ${LockEngine.usageMs(this) / 60000} / 40 minutes. Selected apps are blocked when the allowance is exhausted."
            LockEngine.isOwner(this) -> "PROTECTION READY\nDevice Owner mode enabled. Choose apps and start a fixed commitment."
            else -> "SETUP REQUIRED\nDevice Owner: not enabled. App selection works, but blocking cannot start until computer setup is complete."
        }
        setSelectionEnabled(!LockEngine.isActive(this))
    }

    private fun isUsageMonitorEnabled(): Boolean {
        val expected = "$packageName/${UsageMonitorService::class.java.name}"
        val enabled = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun setSelectionEnabled(enabled: Boolean) {
        if (::appList.isInitialized) {
            for (i in 0 until appList.childCount) {
                val child = appList.getChildAt(i)
                child.isEnabled = enabled
                if (child is CheckBox) child.isClickable = enabled
            }
        }
        if (::durationRow.isInitialized) {
            for (i in 0 until durationRow.childCount) durationRow.getChildAt(i).isEnabled = enabled
        }
        if (::startButton.isInitialized) startButton.isEnabled = enabled && !LockEngine.isActive(this)
    }

    private fun updateDurationButtons(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val b = row.getChildAt(i) as Button
            b.alpha = if ((b.tag as Int) == durationDays) 1f else 0.65f
        }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
