<div align="center">

# 🏃 MANHNT
### Free & Open-Source Real-Life GPS Manhunt for Android

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%201.9.23-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/dedsec-1337/MANHNT/actions/workflows/check.yml/badge.svg)](https://github.com/dedsec-1337/MANHNT/actions/workflows/check.yml)

*City-wide GPS chase game. Seekers hunt. Runners hide. The phone is the referee.*  
*Inspired by Manhunt IRL, SCRAM, Hunting Game, HideZone and classic playground rules — rebuilt free and open source.*

</div>

---

## Why this exists

Commercial manhunt apps charge subscriptions, lock player counts, or put core rules behind paywalls.  
MANHNT is the opposite: native Android, no accounts required, no ads, no tiers. Clone it, build the APK, play with friends.

---

## Core gameplay

| Role | What you see | Goal |
|------|--------------|------|
| **Seeker** | Map with **periodic location reveals** (frozen pins), optional **Bluetooth nearby** hints | Tag runners with their **4-digit code** (or QR on their screen) |
| **Hider / Runner** | Own map + compass toward nearest seeker; show code/QR only when tagged | Survive the timer or free teammates from Jail |

**Location model:** runners are **not** live-tracked the whole match. On each auto-ping interval (e.g. 2 or 5 minutes), seekers get a **snapshot** of last known positions. Pins **freeze locally** until the next ping so the map does not jump when the network glitches.

**Tag model:** proof is **face-to-face** (code or QR). Firebase records the catch and runs **game over**. Bluetooth is **optional assist only** — never required to tag.

---

## Features

### Match & map
- Firebase Auth (anonymous) + Realtime Database room sync
- Circular play zone (1 km / 2 km / unlimited) with optional shrinking zone
- Head start, safe zones, jail + jailbreak, stealth power-up
- **Timed auto-ping reveals** with **frozen last-known pins** between pings
- OSM map (OSMDroid), compass, proximity haptics
- Foreground GPS service + battery-exemption helpers
- OLED dark mode, appearance settings, on-device match history (Room)

### Catch & connectivity
- **Code-based tag** — no GPS distance gate (“Too far” removed)
- **QR code** on the hider’s catch-code card (local proof payload)
- **Optional BLE nearby assist** — hiders advertise, seekers see “Nearby (Bluetooth)”
- Host validates catch code; all clients follow `session.status == FINISHED` for results
- Explicit **game over** when all hiders are caught or the timer hits zero

### App polish
- Vector adaptive launcher icon (safe-zone monogram)
- GitHub Actions: structure checks, Android compile, Firebase rules tests

---

## How to play outdoors

1. Everyone enables **GPS**, **cellular data**, and (optional) **Bluetooth**.
2. Host creates a room, sets duration / zone / head start / auto-ping interval, shares the 6-character code.
3. Others join. Host assigns roles and starts.
4. Head start ends → seekers hunt using map clues and proximity.
5. **Tag** = enter the runner’s **4-digit code** (they can show the code or QR). No distance lock.
6. Caught runners go to **Jail** (if enabled). A free runner can jailbreak teammates.
7. Timer ends with anyone free → **Hiders win**. Everyone caught → **Seekers win**.

---

## Build the APK

**Prerequisites:** Android Studio (Hedgehog+), JDK 17, Android SDK 34.

```bash
git clone https://github.com/dedsec-1337/MANHNT.git
cd MANHNT
```

Open the **`android/`** folder in Android Studio → Gradle sync → Run, or  
**Build → Generate Signed Bundle / APK → APK → release**.

Output: `android/app/build/outputs/apk/release/app-release.apk`

If Gradle fails to download the distribution (`SocketTimeoutException`), retry, check VPN/firewall, or download [gradle-8.6-bin.zip](https://services.gradle.org/distributions/gradle-8.6-bin.zip) manually into the Gradle wrapper dists cache.

### Firebase

Configure the Realtime Database URL in:

`android/app/src/main/java/com/manhunt/app/data/Constants.kt`

**Do not use open rules** (`".read": true` / `".write": true`).  
Use `firebase/database.rules.example.json` (or your published rules) so only authenticated members can access room data. Catch codes stay private; hosts validate tags. **No rules change is required** for BLE, QR, frozen pins, or ungated catch — those are client-side.

Enable **Anonymous Authentication** in the Firebase console.

---

## Architecture (short)

| Layer | Stack |
|-------|--------|
| UI | Kotlin, Jetpack Compose |
| Map | OSMDroid |
| Sync | Retrofit → Firebase Realtime Database REST + Anonymous Auth |
| Local | Room DB, DataStore preferences |
| Sensors | Fused location (foreground service), compass, optional BLE LE |
| Tag proof | 4-digit code + QR (ZXing); BLE nearby assist only |

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

---

## License

Apache License 2.0. Free to use, fork, and improve.

---

*Play outside. Stay off roads. Don’t be an idiot.*
