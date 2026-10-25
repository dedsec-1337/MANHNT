<div align="center">

# 🏃 MANHNT
### Free & Open-Source Real-Life GPS Manhunt for Android

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%201.9.23-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

*City-wide GPS chase game. Seekers hunt. Hiders run. The phone is the referee.*  
*Inspired by Manhunt IRL, SCRAM, Hunting Game, HideZone and the classic playground rules — rebuilt free and open source.*

</div>

---

## Why this exists

Commercial manhunt apps charge subscriptions, lock player counts, or put core rules behind paywalls.  
MANHNT is the opposite: native Android, no accounts, no ads, no tiers. Clone it, build the APK, play with friends.

---

## Core gameplay

| Role | What you see | Goal |
|------|--------------|------|
| **Seeker** | Live map of free Hiders (except when they Stealth) | Tag within 25 m + enter their 4-digit code |
| **Hider** | Stealth map + rotating compass toward nearest Seeker | Survive the timer or free teammates from Jail |

---

## Features

### Already solid
- Firebase REST long-range sync (no Google Services JSON required)
- Circular play zone 200 m – 2 km (or unlimited)
- 25 m boundary warning + out-of-bounds grace
- Head start (Seekers frozen while Hiders scatter)
- Optional shrinking zone
- Timed auto-ping reveal
- Safe zones (untouchable while inside)
- Foreground GPS service + OEM battery-exemption helpers
- OLED dark mode
- Room history on device (Room DB)

### Added for real Manhunt parity
- **Heartbeat haptics** — pocket vibration ramps at 80 / 50 / 25 / 10 m
- **Jail + Jailbreak** — caught Hiders go to jail; a free Hider holds 5 s inside the jail circle to free everyone; Seekers get an alarm
- **Post-jailbreak immunity** (30 s)
- **Stealth power-up** — Hider vanishes from Seeker map for 45 s (3 min cooldown)
- **Catch disputes** — each Hider gets 2 disputes per match
- Adaptive launcher icons + night theme

---

## How to play outdoors

1. Everyone enables **cellular data + GPS**.
2. Host creates a room, sets radius / duration / head start, shares the 6-character code.
3. Others join. Host starts.
4. Head start ends → Seekers hunt.
5. Tag = within 25 m + correct 4-digit code → Hider goes to **Jail** (if enabled).
6. Free Hider enters Jail circle and holds jailbreak → prisoners freed with short immunity.
7. Timer hits zero with anyone free → Hiders win. All caught → Seekers win.

---

## Build the APK

**Prerequisites:** Android Studio (Hedgehog+), JDK 17, SDK 34.

```bash
git clone https://github.com/dedsec-1337/MANHNT.git
cd MANHNT
```

Open the **`android/`** folder in Android Studio → Gradle sync → Run, or  
**Build → Generate Signed Bundle / APK → APK → release**.

Output: `android/app/build/outputs/apk/release/app-release.apk`

### Firebase rules

**Do not use open database rules** such as `".read": true` and `".write": true`. They let anyone read or overwrite live rooms.

Use `firebase/database.rules.example.json` as the version-controlled rules source. The app stores public player profiles at `rooms/{joinCode}/publicPlayers`, precise coordinates at `roomPrivate/{joinCode}/locations`, and catch codes at `roomPrivate/{joinCode}/catchCodes`. Room-root reads are disabled. Authenticated users can read only minimal `joinInfo` to look up a room; full session data (including zone/jail coordinates) and live locations require membership. A Hider can read only their own catch code; the host can read codes to validate catch attempts. The legacy `rooms/{joinCode}/players` path is intentionally unreadable so old records containing catch codes or coordinates cannot leak through the room response. Publish the rules from this file together with the matching app update. GPS values are still client-reported, so rules restrict who can read them but cannot prove the coordinates are physically authentic.

The Firebase Realtime Database URL is configured in `android/app/src/main/java/com/manhunt/app/data/Constants.kt`.
---

## Architecture (short)

Kotlin + Jetpack Compose · OSMDroid maps · Retrofit Firebase REST · Room local DB · Foreground location service · Rotation-vector compass.

---

## License

Apache License 2.0. Free to use, fork, and improve.

---

*Play outside. Stay off roads. Don’t be an idiot.*
