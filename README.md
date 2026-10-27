# LockIn — Android fixed-commitment app blocker

LockIn is a native Kotlin Android app designed for a personally owned Android phone. You choose launchable apps and a fixed duration (7, 30, 60, or 90 days). The selected apps share a 40-minute daily allowance. A required AccessibilityService tracks foreground use and Device Owner mode suspends the selected packages when the allowance is exhausted. Device Owner restrictions are intended to make ordinary bypassing difficult.

**This is source code, not a compiled APK.** Build it in Android Studio using Android SDK Platform 35. Read `SETUP.md` carefully before provisioning.

## Enforcement model

- Device Owner provisioning is required; enabling ordinary Device Admin is not enough.
- Selected apps are suspended using `DevicePolicyManager.setPackagesSuspended` after 40 minutes of combined foreground use per local calendar day. The allowance resets at local midnight.
- A user-enabled AccessibilityService tracks foreground app transitions and accrues usage every second. This service must remain enabled for the allowance to be enforced.
- During an active commitment, LockIn blocks its own uninstall and uses `setUserControlDisabledPackages` (Android 11+) to restrict normal force-stop, clear-data and uninstall controls.
- It adds restrictions against app control, app uninstall, safe boot, Settings-based factory reset, and debugging features; it also requests automatic time to reduce clock-tampering opportunities.
- A persisted deadline is checked after reboot and by an expiry receiver. On expiry, LockIn removes the restrictions and restores selected apps.

## Limits

No Android app can guarantee that the device owner has no possible bypass. Recovery-mode factory reset, firmware reflashing, some OEM-specific paths, and privileged access may still defeat the lock. The accessibility-based usage monitor is not a tamper-proof metering boundary: OEM process management, accessibility-service disabling, and event delivery can affect accuracy. Device Owner behavior differs by Android version and manufacturer. Some packages cannot be suspended by Android. Test on a spare or freshly reset phone before relying on this for a real commitment.
