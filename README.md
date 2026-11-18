# Projector Alarm (`projector-alarm`)

A full-screen sunrise alarm clock Android TV application built specifically for **Xiaomi Smart Projectors** (and other Android TV / Google TV projectors).

## Why "Turn Off Screen" Mode?

On Xiaomi Android TV projectors, third-party Android apps cannot physically power on the projector's optical engine (LED/laser lamp) once the device enters deep firmware standby or full shutdown. `AlarmManager` can wake the CPU, not the projection hardware.

To solve this reliably without external IR blasters or smart plugs, **Projector Alarm** implements a **Screen-Off / Fake Sleep Mode**:

1. **Night Mode (Blackout):** Pressing **"Turn Off Screen & Arm Alarm"** switches the projector output to a 100% pure `#000000` canvas, drops the window brightness to minimum (`0.01f`), hides all system bars, and holds `FLAG_KEEP_SCREEN_ON` so the projector remains ready to illuminate on schedule.
2. **Progressive Sunrise:** At your scheduled alarm time, the sky smoothly transitions from pitch black through deep indigo, crimson dawn, and golden hour to bright morning daylight while a glowing sun rises behind silhouette hills and birds glide across the sky.
3. **Procedural Birdsong:** A built-in `AudioTrack` synthesizer (`BirdSoundGenerator`) generates natural multi-syllable morning bird chirps and trills on the fly, gently fading in from a whisper as the sun climbs—zero external audio files required.
4. **Remote Control Friendly:** Full D-Pad navigation and instant wake/dismiss via **OK / Enter / Back** on your Xiaomi remote.

## How to Build the APK

### Method 1 — GitHub Actions (No Local Setup Required)
This repository includes `.github/workflows/build-apk.yml`, which automatically builds an installable debug `.apk` on every push to `main` or `master` (or manually via **Run workflow**):

1. Open the **Actions** tab in your GitHub repository.
2. Click the latest **Build Android APK** workflow run (or click **Run workflow** to trigger a new build).
3. Wait for the job to turn green, then scroll down to **Artifacts** and download **`projector-alarm-apk`**.
4. Unzip the downloaded archive to extract `app-debug.apk`.

### Method 2 — Android Studio (GUI on Windows / macOS / Linux)
1. Install **Android Studio** (comes bundled with JDK 17 and the Android SDK Manager).
2. Launch Android Studio, click **Open**, and select the `projector-alarm` folder.
3. Wait for Gradle Sync to finish (accept any prompt to download Android SDK Platform 34 if missing).
4. In the top menu bar, choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. When the notification appears at the bottom-right, click **locate** (or open `app/build/outputs/apk/debug/app-debug.apk`).

### Method 3 — Command Line (Gradle + JDK 17 + Android SDK)
1. Make sure **JDK 17** (`JAVA_HOME`), **Android SDK 34** (`ANDROID_HOME`), and **Gradle 8.5** are installed and on your `PATH`.
2. From the project root, run:
   ```bash
   gradle assembleDebug
   ```
   *(If you generate local wrapper scripts first with `gradle wrapper`, you can run `./gradlew assembleDebug` instead.)*
3. Grab the generated APK at `app/build/outputs/apk/debug/app-debug.apk`.
4. **Optional signed release APK:** To publish or sign a release build, configure a keystore in Android Studio via **Build → Generate Signed Bundle / APK → APK**.

## Installing on Your Xiaomi Projector

### Option A: USB Drive (Simplest)
1. Copy `app-debug.apk` onto a FAT32/exFAT USB flash drive and plug it into the projector's USB port.
2. On the projector's Android TV home screen, open a file manager app (such as **File Manager+**, **X-plore**, or **TV File Commander** from the Google Play Store).
3. Browse to the USB drive and select `app-debug.apk`.
4. If prompted, go to **Settings → Apps → Security & Restrictions → Unknown Sources** (or **Install unknown apps**) and allow installations for your file manager.
5. Complete the installation. **Projector Alarm** will appear on your Android TV launcher.

### Option B: Wireless Transfer from Phone ("Send Files to TV")
1. Install **Send Files to TV** from Google Play on both your Android phone and your Xiaomi projector.
2. Send `app-debug.apk` from your phone to the projector's `Download` folder.
3. Open your file manager on the projector and install `app-debug.apk` from `Download`.

### Option C: ADB over Wi-Fi
1. Enable **Developer Options → USB / Network Debugging** on the projector.
2. From your computer on the same Wi-Fi network:
   ```bash
   adb connect <PROJECTOR_IP>:5555
   adb install -r app-debug.apk
   ```

## Recommended Xiaomi Projector Power Settings

To prevent Xiaomi's firmware from turning off the lamp overnight while **Turn Off Screen** mode is active:

1. Open **Settings → Device Preferences → Power** (or **Screen Saver**).
2. Disable or extend **Idle TV Standby** / **Auto Sleep Timer** while using the overnight alarm.
3. Keep the projector's physical **Screen Saver** disabled or longer than your sleep window.
