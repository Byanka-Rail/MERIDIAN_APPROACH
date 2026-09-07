# MERIDIAN APPROACH v0.29.80 Android wrapper

This project packages the current single-file MERIDIAN APPROACH HTML as an offline Android WebView app.

## Included behavior

- Game HTML is bundled at `app/src/main/assets/MERIDIAN_APPROACH.html`.
- No Internet permission is requested.
- Hardware-accelerated WebView is enabled for Three.js/WebGL.
- Immersive full-screen mode and screen-on are enabled.
- Rotation does not recreate the Activity, reducing accidental game reloads.
- HTML file chooser is bridged to Android's document picker for JSON import.
- Both portrait and landscape are allowed (`fullSensor`).

## Build with GitHub Actions

1. Create a GitHub repository.
2. Upload the **contents** of this project folder to the repository root.
3. Open **Actions** → **Build MERIDIAN APPROACH APK**.
4. Run the workflow, or push to `main`/`master`.
5. Download the artifact named `MERIDIAN_APPROACH_v0.29.80_ANDROID`.
6. Install `MERIDIAN_APPROACH_v0.29.80_DEBUG.apk` on Android.

The debug APK is automatically signed with the standard debug signing key and is directly installable for testing.

## Local Android Studio / command line

The project uses Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17, and compileSdk 35.
Run:

    gradle --no-daemon assembleDebug

Result:

    app/build/outputs/apk/debug/app-debug.apk

## Updating only the game later

Replace:

    app/src/main/assets/MERIDIAN_APPROACH.html

with the newer HTML, then update `versionCode` and `versionName` in `app/build.gradle` and rebuild. The Android wrapper itself does not need to be rewritten.


## v0.29.80 SAFE FIX
- Android native shell is byte-for-byte the known-launching v0.29.78 MainActivity.
- Removed only the 3 stray literal `\n` tokens that rendered at the top in WebView.
- AUTOFLIGHT manual collapse now overrides routine gear/flap reminders; emergency states may reopen it.
- The v0.29.79 edge-to-edge/cutout and renderer-recreate changes are intentionally removed.
