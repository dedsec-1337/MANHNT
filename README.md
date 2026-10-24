<div align="center">

# 🏃 MANHNT
### Premium Native Android GPS Chase Game

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%201.9.23-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%202024.04-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Sync-Firebase%20RTDB%20REST-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![Maps](https://img.shields.io/badge/Maps-OSMDroid%206.1.18-7EBC6F?logo=openstreetmap&logoColor=white)](https://github.com/osmdroid/osmdroid)
[![Local Storage](https://img.shields.io/badge/Storage-Room%202.6.1%20SQLite-4285F4)](https://developer.android.com/training/data-storage/room)

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

## 🏗 Architecture

```
MANHNT/
├── app/
│   ├── build.gradle.kts                      # Jetpack Compose, Retrofit 2, OSMDroid, Room, Accompanist
│   └── src/main/
│       ├── AndroidManifest.xml               # Foreground Service & Fine/Background Location permissions
│       ├── res/values/
│       │   ├── strings.xml                   # App Name "MANHNT" & Channel descriptions
│       │   ├── colors.xml                    # Light & OLED Dark color palette
│       │   └── themes.xml
│       └── java/com/manhunt/app/
│           ├── ManhuntApp.kt                 # Application class; initializes OSMDroid user-agent & Room DB
│           ├── MainActivity.kt               # Single Activity hosting Compose Nav & Accompanist Permissions
│           ├── data/
│           │   ├── Constants.kt              # Pre-configured Firebase Realtime Database REST URL
│           │   ├── model/Models.kt           # Player, GameSession, Role, NetworkSyncStatus, RoomNetworkModel
│           │   └── local/
│           │       ├── AppDatabase.kt        # Room database definition
│           │       ├── Entities.kt           # PlayerProfileEntity & MatchHistoryEntity
│           │       └── Daos.kt               # Room DAOs
│           ├── network/
│           │   └── FirebaseSyncService.kt    # Retrofit 2 REST API (PUT, GET, PATCH) for room & player sync
│           ├── service/
│           │   └── ManhuntLocationService.kt # Foreground Service pushing GPS fixes to Firebase every 5s
│           ├── sensor/
│           │   └── CompassSensorManager.kt   # Rotation Vector sensor manager for smooth azimuth flow
│           ├── util/
│           │   ├── GeoUtils.kt               # Haversine distance & bearing formulas
│           │   ├── GeofenceManager.kt        # Circular geofence boundary & buffer evaluation
│           │   └── BatteryOptimizationHelper.kt # OEM task killer & battery exemption launcher
│           ├── viewmodel/
│           │   └── GameViewModel.kt          # Central StateFlow ViewModel managing match lifecycle
│           └── ui/
│               ├── theme/                    # Theme.kt (Dark & Light), Color.kt, Type.kt
│               ├── components/
│               │   ├── OSMMapView.kt         # OSMDroid MapView wrapped in Compose AndroidView
│               │   ├── CompassStrip.kt       # Directional needle & proximity distance HUD
│               │   ├── CatchBottomSheet.kt   # 4-digit PIN verification bottom sheet
│               │   └── AppleComponents.kt    # Rounded cards, buttons, chips, badges
│               └── screens/
│                   ├── HomeScreen.kt         # Join Code entry & New Room launcher
│                   ├── CreateRoomScreen.kt   # Circular zone slider (200m-2km), duration & ratio chips
│                   ├── LobbyScreen.kt        # Player list, role balance, cellular data warning
│                   ├── GameScreen.kt         # Map HUD, Asymmetric controls, Safety Pause, Geofence alert
│                   ├── ResultsScreen.kt      # Winning team, total catches & MVP standings
│                   └── ProfileScreen.kt      # Dark mode toggle, Battery optimization whitelist, avatar picker
```

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
In your [Firebase Console](https://console.firebase.google.com/), open **Realtime Database > Rules** and set:

```json
{
  "rules": {
    "rooms": {
      ".read": true,
      ".write": true
    }
  }
}
```

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

### Prerequisites
- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: Java 17 (recommended: Android Studio bundled OpenJDK 17)
- **Android SDK**: Compile SDK **34**, Min SDK **26** (Android 8.0 Oreo+)

### Steps
1. Clone or extract the repository:
   ```bash
   git clone https://github.com/your-username/manhnt.git
   cd manhnt
   ```
2. Open **Android Studio** and choose **File > Open...** > select the project root.
3. Wait for Gradle Sync to complete (dependencies will be resolved from Google and Maven Central).
4. Connect an Android phone via USB (with Developer Mode & USB Debugging enabled).
5. Click **Run (Shift + F10)**.

### Generating a Signed Release APK
1. In Android Studio, go to **Build > Generate Signed Bundle / APK...**
2. Choose **APK** and click **Next**.
3. Create or select a keystore file, set passwords and key alias.
4. Select build variant: **release**.
5. Check both **V1 (Jar Signature)** and **V2 (Full APK Signature)**.
6. Click **Finish**. Your APK will be generated at `app/release/app-release.apk`.

---

## 🎮 How to Play Outdoors

1. All players enable **Mobile Cellular Data (5G/4G/LTE)** and **Location (GPS)**.
2. The **Organizer** opens MANHNT, taps **Create Room**, adjusts the play zone radius (e.g., 500m) and duration, and shares the 6-character code (e.g. `HUNT42`).
3. Other players tap **Join Chase**, enter the code, and appear in the lobby.
4. The Organizer taps **Start Manhunt**.
5. **Seekers** use the map to hunt fleeing orange dots. Once within 10m, tap **Tag** and enter the Hider's 4-digit code.
6. **Hiders** keep stealth, monitor the compass needle, and evade until the timer reaches zero!

---

## 📄 License
Licensed under the Apache License, Version 2.0.
