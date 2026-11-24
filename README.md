# TV Home: a Google TV-style launcher for Fire TV Stick 2nd Gen

Dark, cinematic home screen with a featured banner, large app tiles and horizontal rows.
Written in Java + XML for Android 5.1.1 (API 22) / Fire OS 5. No Google Play Services,
no third-party libraries, no root.

**Status:** the source has been written and statically checked (XML validity, resource
references, Java syntax), but it has **not been compiled or run on a real device**, because
the environment it was written in has no Android SDK. Build it with the steps below and
report any build error; it is a quick fix.

---

## 1. Get the APK (pick ONE way)

### Way A: Android Studio (most reliable)
1. Install **Android Studio** from https://developer.android.com/studio (free, Windows installer).
2. Unzip `FireTVLauncher.zip` somewhere simple, e.g. `C:\FireTVLauncher`.
3. Android Studio: **File > Open**, choose the `FireTVLauncher` folder. Click **Trust** if asked.
4. Wait for "Gradle sync" to finish (first time downloads about 1 GB; let it run). If it offers to install
   the Android SDK Platform 34, click **Install**.
5. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**. When it says "APK(s) generated successfully",
   click **locate**. The file is `app\build\outputs\apk\debug\app-debug.apk`.

### Way B: GitHub (nothing to install, needs a free GitHub account)
1. Create a new empty repository on github.com and upload the **contents** of the unzipped project
   (include the hidden `.github` folder).
2. Open the **Actions** tab, wait for "Build APK" to turn green (about 3 minutes).
3. Open that run and download the **TVHome-apk** file under *Artifacts*. Unzip it to get `app-debug.apk`.

---

## 2. Install on your Fire TV Stick (Windows)

**On the Fire TV** (one time):
1. Settings > My Fire TV > About > click your device name 7 times to enable Developer options.
2. Settings > My Fire TV > Developer options: turn **ADB debugging** ON and **Apps from Unknown Sources** ON.

**On Windows:**
1. Download **SDK Platform-Tools for Windows** from https://developer.android.com/tools/releases/platform-tools
   and unzip to `C:\platform-tools`. (Android Studio users already have it in
   `%LOCALAPPDATA%\Android\Sdk\platform-tools`.)
2. Copy `app-debug.apk` into that folder.
3. Open **Command Prompt** and run:

```
cd C:\platform-tools
adb connect 192.168.1.16:5555
adb install -r app-debug.apk
```

The Fire TV may show an "Allow USB debugging?" box the first time: choose **Always allow**.
You should see `Success`.

4. Start it on the TV: **Settings > Applications > Manage Installed Applications > TV Home > Launch**,
   or from the "Your Apps & Channels" list, or from Windows:

```
adb shell am start -n com.firelauncher.home/.MainActivity
```

## 3. Remove it again (always possible)

```
adb uninstall com.firelauncher.home
```

This removes only TV Home. Amazon's launcher is never touched.

---

## 4. Using it with the Fire TV remote

| Button | Action |
|---|---|
| Arrows | Move between tiles, rows and the banner |
| Select | Open the app / banner |
| Hold Select, or Menu (three lines) | App menu: Favorite, Featured banner, Hide, App info |
| Menu with no app selected | Launcher options |
| Back | Jump to top; at top, close the launcher |
| Home | Normally Amazon's home screen (see section 6) |

Rows: Favorites, Recently used, Apps (everything installed, sorted A to Z), System
(Home button switch, Settings, Launcher options). New and removed apps appear automatically.

## 5. Your own wallpaper and banner

Put two files on the Fire TV in the folder `FireLauncher` on internal storage:

```
adb shell mkdir -p /sdcard/FireLauncher
adb push wallpaper.jpg /sdcard/FireLauncher/wallpaper.jpg
adb push banner.jpg    /sdcard/FireLauncher/banner.jpg
```

Best sizes: wallpaper 1920x1080, banner 1800x400. JPG or PNG. Close and reopen TV Home.
Delete the files to return to the built-in pictures. Large pictures are shrunk when loaded
so they do not use much of the 1 GB RAM.

---

## 6. What happens with the Home button (read this)

TV Home is a **normal app first**. That is the safe default: the Home button keeps opening
Amazon's launcher, and you open TV Home from your apps list.

The project includes an **experimental, off-by-default switch** (System row > "Home button").
Turning it ON only declares in Android that TV Home may act as a Home screen (an Android
"activity-alias", reversible, no system change). Whether Fire OS 5.2.9.5 then lets you choose it is
**not confirmed**:

* Fire OS has no normal "default home app" setting, and Amazon has been closing off custom
  launchers. In late 2022 Amazon's Fire OS 6 and 7 updates were reported to block custom
  launchers; the same report says it was unclear whether the Fire OS 5 update did too.
* I could not test on your device. Expect that the Amazon home screen may still open when you
  press Home. If so, switch it OFF; nothing is harmed.
* Community tools (Launcher Manager, FireStarter) get around this by watching the system log over
  an internal ADB connection and re-opening another app after Amazon's home appears. Limits: ADB
  debugging has to stay ON, Amazon's home may flash for a moment, and on Fire OS 5 FireStarter
  needed an ADB toggle after every restart. These are separate, third-party apps and are not
  included here.

Nothing in this project uninstalls, disables or replaces Amazon's launcher, touches system
partitions, or needs root.

## 7. Compatibility notes

* minSdk 22 (Android 5.1.1), uses only API 22 calls (the one newer call is guarded by an SDK check).
* Java 8 source level, no AndroidX, no Google Play Services.
* RAM: icons are scaled to tile size and held in an 8 MB cache, wallpaper decoded at 1280x720 RGB_565
  (about 1.8 MB), two background threads at most.
* 32-bit ARM: pure Java, no native code, so it runs on armeabi-v7a.
* Build tools: Android Gradle Plugin 8.7.3, Gradle 8.9 (wrapper), JDK 17 or newer, compileSdk 34.
