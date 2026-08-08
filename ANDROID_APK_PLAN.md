# Plan: Build and install an Android APK

## Context
This repo has only `core` (upstream game logic) and `desktop` (hand-written
scaffolding, see `RUNNING.md`) modules. There is no `android` module and no
Android SDK on this machine. Constraints that shape the plan:

- **libGDX is pinned to 1.9.10** (root `build.gradle`) — 1.9.11+ breaks
  `YioGdxGame.scrolled()`. The android module must use the same version.
- **Gradle wrapper is 8.7**, which requires **Android Gradle Plugin (AGP) 8.x**
  and therefore JDK 17+ and `compileSdk` 34.
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

1. **Install the Android SDK** (either path):
   - Android Studio (https://developer.android.com/studio) — bundles SDK,
     build tools, license UI, and a device manager.
   - CLI-only: download "command line tools only", then:
     ```sh
     sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
     sdkmanager --licenses
     ```
   - Either way, create `local.properties` at repo root with
     `sdk.dir=/path/to/android-sdk` (git-ignore it).

2. **Wire AGP into the build**:
   - `settings.gradle`: add a `pluginManagement { repositories { google();
     gradlePluginPortal(); mavenCentral() } }` block and `include 'android'`.
   - Root `build.gradle`: add `google()` to `allprojects.repositories`
     (AGP and androidx artifacts live there).
   - Note: the root `subprojects.afterEvaluate` block forcing Java 8
     source/target compatibility also applies to the android module — that is
     fine (AGP 8 accepts it), but verify it doesn't fight AGP's own
     `compileOptions`; if it does, exclude the android project from that block
     and set `compileOptions { sourceCompatibility/targetCompatibility 8 }`
     inside `android/build.gradle` instead.

3. **Hand-write the `android` module** (4 files):
   - `android/build.gradle` — `com.android.application` plugin (AGP 8.x),
     `namespace 'yio.tro.antiyoy'`, `compileSdk 34`, `minSdk 14`,
     `targetSdk 34`, `implementation project(':core')`,
     `gdx-backend-android:1.9.10`, and the per-ABI natives for both
     `gdx-platform` and `gdx-freetype-platform` (armeabi-v7a, arm64-v8a,
     x86, x86_64) with the standard libGDX `copyAndroidNatives` task that
     unpacks them into `jniLibs`.
     Point assets at the shared folder:
     `sourceSets.main.assets.srcDirs = ['../assets']`.
   - `android/src/yio/tro/antiyoy/AndroidLauncher.java` — standard
     `AndroidApplication` subclass that calls
     `initialize(new YioGdxGame(), config)`.
   - `android/AndroidManifest.xml` — activity with
     `android:screenOrientation="portrait"` (phone game). With AGP 8, do NOT
     put a `package` attribute in the manifest (it moved to `namespace` in
     build.gradle).
   - A launcher icon under `android/res/` (any placeholder PNG works;
     upstream icons can be lifted from the Play Store APK later if wanted).

4. **Build the APK**:
   ```sh
   ./gradlew :android:assembleDebug
   ```
   Output: `android/build/outputs/apk/debug/android-debug.apk`.
   A debug APK is auto-signed with the debug keystore and installs fine on
   your own phone — no release signing needed.

5. **Install on the phone**. Note this machine is **WSL2**, where USB
   passthrough is not available by default. Options, easiest first:
   - Copy the APK to the phone (cloud drive, `python3 -m http.server` +
     phone browser, USB file transfer from Windows) and tap it to install
     (enable "install unknown apps" when prompted).
   - `adb.exe` from the **Windows** side (Windows platform-tools) with USB
     debugging enabled: `adb install android-debug.apk`.
   - Wireless debugging: `adb pair` / `adb connect <phone-ip>:port` works
     from inside WSL2 if the phone and PC share a network.

## Verification
- `./gradlew :android:assembleDebug` completes and the APK is >20 MB
  (assets included — a tiny APK means the assets sourceSet is wrong).
- App launches on the phone in portrait, shows the splash, and a hotseat
  game with ships/colonies (this fork's feature) is playable.

## Optional: signed release APK
Only needed for distribution beyond your own device:
- `keytool -genkey -v -keystore release.keystore -alias antiyoy -keyalg RSA`
- Add a `signingConfigs` block in `android/build.gradle`.
- `./gradlew :android:assembleRelease`
