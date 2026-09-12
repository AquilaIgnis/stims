# About

Stims lets you select apps that should prevent your screen from turning off. When a "stimmed" app is active in the foreground, the screen stays on . When you switch to a different app, the screen resumes normal sleep behavior.

### Why use over alternatives

Stims is designed to handle aggressive power managers like samsung's one UI , alternatives like caffeine won't work on these devices. If you encounter a problem please open a New Issue

---

## Permissions

### Usage Access (required)

Required to detect which app is in the foreground. On first launch the app will redirect you to the system settings screen.

1. Go to **Settings → Apps → Special app access → Usage access**
2. Find **Stims** and toggle it on
3. Return to the app

Without this permission the background service cannot detect foreground apps and the screen will not be kept awake.

### Display over other apps (required on Samsung / OneUI)

Samsung's One UI disables the standard wake lock mechanism, so Stims uses a transparent overlay window instead to keep the screen on. The overlay is invisible and non-interactive.

1. Go to **Settings → Apps → Stims → Display over other apps**
2. Toggle it on
3. Return to the app — the warning banner will disappear

On stock Android and most other devices this permission is not needed and the app will work without it. It can also be enabled manually via the in-app settings for any device.

### Auto-start after reboot (Samsung / Realme / Xiaomi and similar)

Some OEM ROMs block that boot broadcast until the app is exempted from their battery manager. If the screen stops staying awake after a reboot and only works again once you open Stims, whitelist the app:

- **Samsung:** Settings → Battery → Background usage limits → add Stims to **Never sleeping apps** (and make sure it is not in _Deep sleeping apps_)
- **Realme / Oppo:** Settings → Apps → App management → Stims → enable **Auto-start**, and set battery usage to _Allow background activity_
- **Xiaomi / Redmi:** Settings → Apps → Manage apps → Stims → enable **Autostart**, and set _Battery saver_ to **No restrictions**

Stock Android does not need any of this.

---

# Building from source

**Requirements**

- Android Studio or the Android SDK command-line tools
- JDK 11+

**Steps**

```bash
git clone https://github.com/acidburnmonkey/stims.git
cd stims
./gradlew assembleDebug
```

The APK will be output to:

```
app/build/outputs/apk/debug/app-debug.apk
```

For a release bundle (AAB):

```bash
./gradlew bundleRelease
```

---

# Tests

```bash
./gradlew testDebugUnitTest

# Instrumented tests — needs a connected device or running emulator
./gradlew connectedDebugAndroidTest
```

---

## Compatibility

- **Minimum:** Android 7.0 (Nougat, API 24)
- **Target:** Android 16 (API 36)
