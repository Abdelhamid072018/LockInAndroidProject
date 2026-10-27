# Build the APK online (no Android Studio required)

This repository includes `.github/workflows/build-apk.yml` to compile a debug APK on GitHub Actions.

1. Create a GitHub repository and upload the **contents** of this folder (the `LockIn` project), keeping the `.github/workflows/build-apk.yml` path.
2. In the repository, open **Actions** → **Build LockIn APK** → **Run workflow**.
3. Wait for the workflow to finish successfully.
4. Open the completed run and download the **LockIn-debug-apk** artifact. Extract it to get `app-debug.apk`.
5. Transfer `app-debug.apk` to your Android phone and install it. Android may ask you to allow installation from that source.
6. Follow `SETUP.md` for Device Owner provisioning. Installing the APK alone does not enable strong blocking.

Important: this workflow has been added but has not been run from this environment. The APK is not prebuilt or verified. Do not activate the lock on your primary phone until you have tested commitment expiry and recovery on a spare/freshly reset device. Device Owner setup may require a factory reset and can restrict normal device management.
