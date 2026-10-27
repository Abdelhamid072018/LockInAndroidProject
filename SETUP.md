# LockIn — Android Device Owner build

LockIn is a native Android starter project that uses Android Device Owner APIs to suspend user-selected launchable apps for a fixed commitment. The default duration is 30 days; 7, 60 and 90 days are also available. App selection is editable before the commitment begins and locked while active. The app schedules automatic expiry and reapplies suspension after reboot.

## Important limitations

- This is a source project, not a prebuilt or signed APK. Build it in Android Studio with Android SDK 35 installed.
- Device Owner provisioning is intended for a personally controlled device and usually requires a factory-reset/freshly provisioned device. Back up your phone first. Do not run device-owner provisioning on a work-managed phone or a phone with data you cannot afford to lose.
- A recovery-mode factory reset, firmware reflash, or some OEM-specific/privileged access can defeat this setup. LockIn also restricts debugging features and Settings-based reset while active, but those controls cannot stop every recovery or hardware-level route. It is strong friction, not an unbreakable security boundary.
- Device Owner APIs and behavior vary by Android version and manufacturer. Test on the target device before relying on it.
- The project intentionally does not block emergency calling, Settings, the launcher, or core Android/Google/Samsung packages. App visibility is broad for sideloaded personal use; Google Play distribution has additional package-visibility policy requirements.
- Some system, carrier, enterprise, or OEM apps cannot be suspended. LockIn checks the result returned by Android and warns if selected apps did not become suspended. Review that warning before relying on the commitment.
- The lock expiry uses Android's inexact idle-allowed alarm. It is designed to expire automatically, but delivery may be delayed under system power management. Reboot handling rechecks the deadline.

## Build in Android Studio

1. Install Android Studio and Android SDK Platform 35.
2. Open the `LockIn` folder as a project and allow Gradle sync.
3. Build > Build APK(s). Android Studio will produce a debug APK under `app/build/outputs/apk/debug/`.
4. Install the APK on a dedicated/test Android phone. Enable Developer options and USB debugging, connect it to the computer, and authorize the computer.
5. **Device Owner provisioning generally requires a freshly reset device with no accounts and no other device owner/profile owner. Back up data first.** After initial setup and installing LockIn, run this from a terminal with Android Platform Tools (`adb`) installed:

   ```bash
   adb shell dpm set-device-owner com.lockin.focus/.LockInAdminReceiver
   ```

   If Android reports that the device is already provisioned or the command is not allowed, do not attempt to bypass organizational management. A factory reset and clean provisioning may be required. Check your device manufacturer's documentation.

6. Reopen LockIn. Select the apps to block, choose 7/30/60/90 days, then start the commitment.
7. Verify on a test device that selected apps cannot launch and that they are automatically restored after expiry. Do not rely on it until tested on your exact Android version/OEM.

## How it works

- `DevicePolicyManager.setPackagesSuspended(...)` suspends selected packages when their combined 40-minute daily foreground-use allowance is exhausted.
- `UsageMonitorService` uses Android Accessibility events to track the foreground package and account for elapsed time. Accessibility must remain enabled; this is a practical anti-impulse mechanism, not tamper-proof metering.
- `setUninstallBlocked(...)` and `setUserControlDisabledPackages(...)` make normal removal/control of LockIn harder while active.
- A persisted wall-clock deadline is checked by an expiry receiver and after boot/time changes.
- No unlock control is exposed in the app during an active commitment.

## Known next steps before production

1. Build and run on a real target device; this environment did not compile an APK.
2. Add a results view for packages Android refuses to suspend.
3. Add instrumentation tests for expiry, reboot, clock changes, and OEM behavior.
4. Add a deliberately external recovery process (for example, a trusted accountability partner holding the ADB setup credentials) if stronger commitment is needed. Avoid deleting data or creating a design that traps the owner permanently.
