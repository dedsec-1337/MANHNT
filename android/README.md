<div align="center">

# 🏃 MANHNT
### Premium Native Android GPS Chase Game

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%201.9.23-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%202024.04-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Sync-Firebase%20RTDB%20REST-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![Maps](https://img.shields.io/badge/Maps-OSMDroid%206.1.18-7EBC6F?logo=openstreetmap&logoColor=white)](https://github.com/osmdroid/osmdroid)

*City-wide, long-range real-life Manhunt referee running natively on Android with 100% Jetpack Compose, OpenStreetMap, and Firebase Realtime Database REST sync over 5G/4G cellular data.*

[Features](#-features) • [Architecture](#-architecture) • [Firebase Setup](#-firebase-database-setup) • [Battery Optimization](#-battery-optimization--background-gps) • [Geofencing](#-geofencing-engine) • [Building the APK](#-how-to-build--run-in-android-studio)

</div>

---

## ⚡ Highlights

- 🌐 **Long-Range Cellular Tracking**: Players can spread out across parks, neighborhoods, and miles. Coordinates sync via Firebase Realtime Database REST API using Retrofit 2 & OkHttp 3.
- 🎯 **Asymmetric Role Mechanics**:
  - **Seekers**: Track fleeing prey markers live on OpenStreetMap. When within **10 meters** of a target, the "Catch" button activates.
  - **Hiders**: Stealth map (own location only). An Apple-inspired **rotating Compass Strip** (`Sensor.TYPE_ROTATION_VECTOR`) points continuously toward the nearest Seeker with real-time distance readouts.
- ⭕ **Dynamic Circular Geofencing**:
  - Organizers draw a circular play zone between **200m and 2km** radius.
  - Automatic **25m boundary buffer warning** alerts players before they exit the perimeter.
  - Out-of-bounds detection initiates a return grace countdown and notifies the match referee.
- 🔋 **Battery Optimization & Background GPS**:
  - Foreground Service (`ManhuntLocationService`) with `FOREGROUND_SERVICE_TYPE_LOCATION` and ongoing notification.
  - Integrated `BatteryOptimizationHelper` to whitelist the app from Android and OEM task killers (Xiaomi MIUI/HyperOS, Samsung OneUI, Oppo ColorOS).
- 🌙 **OLED Dark Mode**:
  - Instant toggle between Apple-inspired clean light mode and deep OLED stealth dark mode for night chases.
- 📱 **Pure Jetpack Compose UI**:
  - Single-activity architecture (`MainActivity.kt`). Zero XML layouts. 16dp rounded surfaces, soft diffuse shadows, and Roboto typography.
- 💾 **On-Device Persistence**:
  - **Room (SQLite)** stores persistent player UUID, codenames, and match statistics.

---

## 🔥 Firebase Database Setup

MANHNT connects directly to Firebase Realtime Database using its **REST API** (`PUT`, `GET`, `PATCH`, `DELETE`). No native `google-services.json` setup is required!

### 1. Database URL Configuration
The database URL is pre-configured in `app/src/main/java/com/manhunt/app/data/Constants.kt`:

```kotlin
object Constants {
    const val FIREBASE_DATABASE_URL = "https://manhnt-ba00c-default-rtdb.asia-southeast1.firebasedatabase.app/"
}
```

### 2. Realtime Database Security Rules
Use the restricted, validated rules in [`firebase/database.rules.example.json`](../firebase/database.rules.example.json) as the version-controlled source. The rules deny reads and writes by default; authenticated users can read the minimal `joinInfo` record for room lookup, while session, location, and gameplay data use narrower membership or role checks.

Do not publish broad rules such as `".read": true` or `".write": true`; they expose live room data and let anyone change it. Check the Firebase Emulator rule checks and verify the app's room flows before publishing an updated ruleset. Appearance, animation, and other local UI changes do not require Firebase rule changes.

---

## 🔋 Battery Optimization & Background GPS

Modern Android versions and aggressive OEM operating systems (Xiaomi MIUI/HyperOS, Samsung OneUI, Huawei EMUI, Oppo ColorOS) terminate background apps when the screen is locked to save battery.

MANHNT tackles this with a multi-layered approach:
1. **Foreground Service**: `ManhuntLocationService` runs with `FOREGROUND_SERVICE_TYPE_LOCATION` and a persistent ongoing notification with live countdown (`"Cellular GPS active — 22:45 remaining"`).
2. **Exemption Intent**: `BatteryOptimizationHelper.createOptimizationExemptionIntent()` launches `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` so the user can whitelist MANHNT with one tap.
3. **OEM Auto-Start Fallback**: Detects device manufacturer and opens manufacturer-specific background manager settings.

---

## ⭕ Geofencing Engine

- **High-Accuracy Haversine Calculation**: Distance between players and play zone center calculated in meters.
- **25m Warning Buffer**: Players approaching within 25m of the boundary receive a yellow warning indicator on their HUD.
- **Out-of-Bounds Detection**: Crossing the boundary turns the HUD red, prompts an alert notification, and begins a 30-second return grace timer before referee penalties are triggered.

---

## 🚀 How to Build & Run in Android Studio

1. Open **Android Studio** and choose **File > Open...** > select this extracted folder.
2. Wait for Gradle Sync to complete.
3. Connect an Android phone via USB (with Developer Mode & USB Debugging enabled).
4. Click **Run (Shift + F10)**.

### Generating a Signed Release APK
1. In Android Studio, go to **Build > Generate Signed Bundle / APK...**
2. Choose **APK** and click **Next**.
3. Create or select a keystore file, set passwords and key alias.
4. Select build variant: **release**.
5. Check both **V1 (Jar Signature)** and **V2 (Full APK Signature)**.
6. Click **Finish**. Your APK will be generated at `app/release/app-release.apk`.
