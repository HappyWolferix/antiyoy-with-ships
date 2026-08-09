# Plan: Build and install an Android APK

## Goal
A debug APK that installs and runs on **Android 11 (API 30) or newer**, built
entirely on this machine.

## Build machine
Native **Ubuntu 26.04 x86_64** (not WSL — `/proc/version` is a real kernel).
Relevant state, as verified:

- **JDK 25 is the system default**, and JDK 21 is also installed at
  `/usr/lib/jvm/java-21-openjdk-amd64`. Gradle 8.7's embedded Groovy cannot
  read class file major version 69, so the build fails on JDK 25 before it
  reaches any project code. Fixed by `gradle.properties` at repo root pinning
  `org.gradle.java.home` to JDK 21 — which is also the newest JDK that
  AGP 8.x supports. **This is already done and verified** (`:core:compileJava`
  succeeds).
- **No usable Android SDK.** `/usr/lib/android-sdk` is the Debian
  `google-android-platform-tools-installer` stub: `platform-tools` and
  `licenses` only, no `sdkmanager`, no `platforms/`, no `build-tools/`. It
  cannot serve as `sdk.dir`.
- `adb` 36.0.0 is on `PATH`, USB buses are present, and the user is in
  `plugdev` — so a phone on USB is directly reachable, no WSL workarounds.
- `dl.google.com` and Google's Maven repo are reachable; 306 GB free.

## Context
This repo has only `core` (upstream game logic) and `desktop` (hand-written
scaffolding, see `RUNNING.md`) modules. There is no `android` module.
Constraints that shape the plan:

- **libGDX is pinned to 1.9.10** (root `build.gradle`) — 1.9.11+ breaks
  `YioGdxGame.scrolled()`. The android module must use the same version.
- **Gradle wrapper is 8.7.** AGP 8.5.2 is the version to use: it *requires*
  Gradle 8.7, while AGP 8.7 would require Gradle 8.9. Don't bump AGP without
  bumping the wrapper.
- The game uses **gdx-freetype** (`Fonts.java`), so the android module needs
  the freetype native libraries per ABI, not just the core dependency.
- Assets live at repo root `assets/`; the desktop module points at them via
  `workingDir`. The android module should point its `sourceSets` there rather
  than copying.

## Why not gdx-setup
The original plan said "generate a project with gdx-setup.jar from
libgdx.badlogicgames.com". That site is defunct, and the current generator
(gdx-liftoff) emits a project on the **latest** libGDX and AGP — you'd then
have to downgrade everything to 1.9.10 by hand anyway. Hand-writing the small
android module (consistent with how `desktop/` was added here) is less work
and keeps versions coherent. It's ~4 files.

## Steps

1. **Install the Android SDK** (CLI only — Android Studio is not needed):
   ```sh
   mkdir -p ~/Android/Sdk/cmdline-tools
   cd ~/Android/Sdk/cmdline-tools
   curl -O https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
   unzip commandlinetools-linux-*.zip && mv cmdline-tools latest
   export ANDROID_HOME=~/Android/Sdk
   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager \
       "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
   ```
   `sdkmanager` needs `JAVA_HOME` on a supported JDK; use
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` if it complains.

   Then create `local.properties` at repo root with
   `sdk.dir=/home/dev/Android/Sdk` and git-ignore it.
   (Ignore `/usr/lib/android-sdk`; it is not a real SDK.)

2. **Wire AGP into the build**:
   - `settings.gradle`: add a `pluginManagement { repositories { google();
     gradlePluginPortal(); mavenCentral() } }` block and `include 'android'`.
   - Root `build.gradle`: add `google()` to `allprojects.repositories`
     (AGP and androidx artifacts live there), and add the AGP classpath
     `com.android.tools.build:gradle:8.5.2` to `buildscript.dependencies`.
   - Note: the root `subprojects.afterEvaluate` block forcing Java 8
     source/target compatibility also applies to the android module — that is
     fine (AGP 8 targets Java 8 by default anyway), but verify it doesn't
     fight AGP's own `compileOptions`; if it does, exclude the android project
     from that block and set `compileOptions {
     sourceCompatibility/targetCompatibility 8 }` inside
     `android/build.gradle` instead.

3. **Hand-write the `android` module** (4 files):
   - `android/build.gradle` — `com.android.application` plugin (AGP 8.5.2),
     `namespace 'yio.tro.antiyoy'`, `compileSdk 34`, **`minSdk 30`**
     (= Android 11, the stated floor), `targetSdk 34`,
     `implementation project(':core')`, `gdx-backend-android:1.9.10`, and the
     per-ABI natives for both `gdx-platform` and `gdx-freetype-platform` with
     the standard libGDX `copyAndroidNatives` task that unpacks them into
     `jniLibs`.
     ABIs: **`arm64-v8a`, `armeabi-v7a`, `x86_64`**. Drop the old `x86` ABI —
     no Android 11+ device or emulator image uses it, and it only inflates the
     APK. `arm64-v8a` alone covers essentially every real phone; the other two
     are cheap insurance (32-bit ARM stragglers, x86_64 emulator).
     Point assets at the shared folder:
     `sourceSets.main.assets.srcDirs = ['../assets']`.
   - `android/src/yio/tro/antiyoy/AndroidLauncher.java` — standard
     `AndroidApplication` subclass that calls
     `initialize(new YioGdxGame(), config)`.
   - `android/AndroidManifest.xml` — activity with
     `android:screenOrientation="portrait"` (phone game) and
     `android:exported="true"` on the launcher activity (**required** since
     API 31; the build will fail without it). With AGP 8, do NOT put a
     `package` attribute in the manifest (it moved to `namespace` in
     build.gradle).
   - A launcher icon under `android/res/` (any placeholder PNG works;
     upstream icons can be lifted from the Play Store APK later if wanted).

   No `android.useAndroidX` / desugaring is needed: libGDX 1.9.10's android
   backend pulls no androidx, and minSdk 30 covers the Java 8 APIs the code
   uses.

4. **Build the APK**:
   ```sh
   ./gradlew :android:assembleDebug
   ```
   Output: `android/build/outputs/apk/debug/android-debug.apk`.
   A debug APK is auto-signed with the debug keystore and installs fine on
   your own phone — no release signing needed.

5. **Install on the phone.** Enable Developer Options → USB debugging, plug in
   over USB, accept the RSA prompt, then:
   ```sh
   adb devices          # phone should show as "device", not "unauthorized"
   adb install -r android/build/outputs/apk/debug/android-debug.apk
   ```
   If `adb devices` shows the phone as `no permissions`, add a udev rule for
   the vendor ID (from `lsusb`) and `sudo udevadm control --reload`; the
   `plugdev` membership is already in place.
   Sideloading by copying the APK to the phone also works if USB debugging is
   not wanted.

## Verification
- `./gradlew :android:assembleDebug` completes and the APK is >20 MB
  (assets included — a tiny APK means the assets sourceSet is wrong).
- `aapt dump badging <apk> | grep sdkVersion` reports `minSdkVersion:'30'`.
- App launches on the phone in portrait, shows the splash, and a hotseat
  game with ships/colonies (this fork's feature) is playable.

## Optional: signed release APK
Only needed for distribution beyond your own device:
- `keytool -genkey -v -keystore release.keystore -alias antiyoy -keyalg RSA`
- Add a `signingConfigs` block in `android/build.gradle`.
- `./gradlew :android:assembleRelease`
