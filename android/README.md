# Manhunt — Native Android GPS Chase Game

A premium native Android outdoor game built with **Kotlin**, **Jetpack Compose**, **OSMDroid (OpenStreetMap)**, **Room (SQLite)**, and peer-to-peer **Local Wi-Fi / Hotspot Networking** (UDP Discovery + TCP Sockets).

## Features
- **P2P Local Network Discovery**: No cloud server, no Firebase, completely decentralized. Automatically detects nearby matches over local Wi-Fi / Hotspot via UDP multicast on port 8888.
- **TCP Sync Relay**: Host's phone acts as an in-memory TCP server (port 9876) streaming coordinates and catch events.
- **OSMDroid Map Engine**: Custom OpenStreetMap tile rendering with dynamic circular play zone boundaries (200m – 2km).
- **Asymmetric Role Mechanics**:
  - **Seekers**: See real-time radar markers of fleeing Hiders. Once within 10m, the "Catch" button activates.
  - **Hiders**: Map displays stealth mode (only own position). A minimalist Apple-inspired Compass needle strip points directly to the nearest Seeker with real-time distance readouts.
- **Catch Confirmation**: 4-digit verification PIN displayed on the Hider's screen to confirm tags.
- **Foreground Location Service**: `ManhuntLocationService` with ongoing notification and automatic battery optimization whitelisting intent.
- **Room Database**: Local storage for user profile, device UUID, and match history.

## How to Open in Android Studio & Generate APK

1. **Extract Project**:
   - Extract `manhunt-android-app.zip` into your preferred projects directory.

2. **Open in Android Studio**:
   - Launch **Android Studio** (Hedgehog, Iguana, Jellyfish, or newer).
   - Click **File > Open...** and select the root `Manhunt` directory (where `settings.gradle.kts` is located).
   - Android Studio will automatically recognize Gradle and sync dependencies (Jetpack Compose, OSMDroid, Room, Accompanist).

3. **Requirements**:
   - **JDK**: Java 17 (recommended: Android Studio bundled OpenJDK 17).
   - **Android SDK**: Compile SDK 34, Min SDK 26 (Android 8.0 Oreo+).

4. **Run or Generate Signed APK**:
   - **To Run Directly on Device**: Connect your Android phone via USB with USB Debugging enabled, or enable Wireless Debugging on the same Wi-Fi. Click the green **Run (Shift + F10)** button.
   - **To Build Signed APK**:
     1. In Android Studio, go to the menu: **Build > Generate Signed Bundle / APK...**
     2. Select **APK** and click **Next**.
     3. Choose or create a new Keystore file, enter your password, alias, and key password.
     4. Select **release**, check **V1 (Jar Signature)** and **V2 (Full APK Signature)**.
     5. Click **Create / Finish**.
     6. Locate your signed APK in `app/release/app-release.apk` and install it onto your phones!

## Hotspot / Wi-Fi Setup for Players
1. One player turns on their Android Portable Hotspot (no mobile data required!).
2. Other players connect to that Hotspot Wi-Fi.
3. The organizer taps **Create Room**.
4. Other players tap **Join Chase** or select the automatically discovered game.
