# Installing Orbit for Lucid

There is no store release. You build both apps yourself and sideload them: an Android app on your phone, and a
Connect IQ app on your Garmin watch. Read the disclaimers and limitations in the [README](README.md) first. This is
unofficial, lightly tested software that controls a real car, and it was written with heavy AI help.

## What you need

- An Android phone (tested on a Pixel 10 Pro) with **Garmin Connect Mobile** installed and your watch paired to it.
- A Garmin watch. Only the **Venu 3** has been tested. Others: see [Other watch models](#other-watch-models).
- A Lucid account that works in the official Lucid app. The app uses the first vehicle on the account.
- A computer (Linux, macOS or Windows) with:
  - **JDK 21** and the **Android SDK** (Android Studio installs both), to build the phone app.
  - The **Garmin Connect IQ SDK**, installed with Garmin's SDK Manager
    (https://developer.garmin.com/connect-iq/sdk/). You accept Garmin's license yourself; the SDK is never part of
    this repo.
  - **Git**, and for contributors [gitleaks](https://github.com/gitleaks/gitleaks) for the commit hook.

## 1. Build and install the phone app

```
cd android
./gradlew assembleDebug
```

(On Windows use `gradlew.bat`.) Set `JAVA_HOME` to JDK 21 and `ANDROID_HOME` to your Android SDK if Gradle can't find
them. The APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

Install it either way:

- **adb:** enable USB debugging on the phone, plug it in, then run
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- **By hand:** copy the APK to the phone, open it from a file manager and allow installs from that source.

It is a debug build signed with a debug key. Android may warn about that, and Play Protect may ask to scan it.

### First launch

1. Open **Orbit**. Allow notifications when asked. This is only for the small "Watch link active" notification; the
   watch link still works if you decline, but you get no sign that it is running.
2. Log in with your Lucid email and password. The password is used once; only a refresh token is kept, encrypted
   with the phone's Keystore.
3. Leave the app installed. A background service keeps the watch link alive after you swipe the app away, and it
   restarts itself after a reboot once you first unlock the phone.

If you **force-stop** the app, Android will not restart it until you open it by hand.

## 2. Build the watch app

You need a Connect IQ **developer key**. It signs the app and must stay private: never commit it or share it, and keep
it outside the repo (`developer_key*` is gitignored anyway). Keep a backup; if you lose it you can't update apps you
signed with it. The easiest way to make one is the Monkey C extension for VS Code: run the command
"Monkey C: Generate a Developer Key" from the command palette and save the file somewhere safe. Without VS Code, Garmin
documents an `openssl` recipe (a 4096-bit RSA key converted to DER) in the Connect IQ SDK docs.

Then, from the repo's `watch` folder (use your SDK's `bin` folder, and the path to your key):

```
cd watch
<sdk>/bin/monkeyc -f monkey.jungle -d venu3 -o build/orbit.prg -y /path/to/developer_key -w
```

`-d` is the device id. The result is `watch/build/orbit.prg`.

## 3. Sideload the watch app

1. Plug the watch into your computer with its USB cable.
2. It appears as a drive or as an MTP device (Venu 3: MTP). On Linux you may need `gvfs-mtp` to mount it; on macOS
   MTP devices need a helper app; on Windows it shows up in Explorer.
3. Copy `orbit.prg` into **`GARMIN/Apps/`** on the watch's internal storage.
4. Eject the watch and unplug it. The watch loads the app when the cable is removed.
5. Orbit appears in the watch's app list. The glance (cached charge and lock state) may need adding in the watch's
   glance settings.

Open the app on the watch, give it a moment, and it should show your state of charge. **Open it once after every
install:** that is when the watch registers to be woken by the phone's 5 minute status updates, and until then the
glance stays as it was.

## Other watch models

The manifest (`watch/manifest.xml`) lists the devices the app will build for. It currently has `venu3`, `venux1` and
`fenix7`. Only the Venu 3 has been run; the other two compile but are untested.

To try a watch that isn't listed:

1. Open the SDK Manager and download that watch's device profile.
2. Add a line to the `<iq:products>` section of `watch/manifest.xml`:
   ```
   <iq:product id="fenix7pro"/>
   ```
   Device ids match the folder names in your SDK's device folder (for example `~/.Garmin/ConnectIQ/Devices` on
   Linux).
3. Build with that id: `monkeyc -f monkey.jungle -d fenix7pro -o build/orbit-fenix7pro.prg -y /path/to/developer_key -w`
4. Sideload the resulting file as in step 3 above.

Things to know:

- A `.prg` is built for one device. A file built for the Venu 3 will not load on another model.
- The app needs Connect IQ API level 5.2 or higher. Older watches will fail the build with an API-level error. The
  Venu X1 needs a newer SDK (API 6.0.3).
- Leave the manifest's app `id` alone. The phone app only talks to that id (`WATCH_APP_ID` in `WatchBridge.kt`). If you
  change one, change both.
- The screens have been run in Garmin's simulator for the fenix 7 and the Venu X1 (layout, buttons, the set point
  list), but never on a real one. The simulator draws stock menus differently from a real watch, so please report what
  works and what doesn't.

## When something doesn't work

| Watch says | Likely cause |
|---|---|
| No phone link | The phone isn't connected, or Orbit's service isn't running. Open Orbit once. |
| No connection | The phone has no internet, or Lucid is unreachable. |
| Open phone app | You're logged out. Open Orbit and log in. |
| Try again soon | Lucid is rate-limiting. Wait. |
| Not confirmed | The command was sent, but the car hasn't reported a change within about 105 seconds. |
| Car didn't wake | The car did not wake in time. Try again. |
| Unknown error | Something else went wrong. Check the phone log (see below). |

Other tips:

- **Watch crash logs:** the watch writes errors to `GARMIN/Apps/LOGS/` (for example `CIQ_LOG.BAK`). Check it if an app
  shows nothing or closes, and attach the error text when you report a problem. Remove anything personal first.
- **Phone logs:** `adb logcat` shows messages tagged `OrbitBridge`.
- **After a reboot,** the watch link comes back once you first unlock the phone. On a slow boot it can take a couple of
  minutes.
- Never post tokens, your VIN, your email or real API responses in an issue.
