<div align="center">

# MANHNT

### Real-life GPS manhunt. Free. Open source. Android.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-0B5FFF?style=for-the-badge)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/dedsec-1337/MANHNT/check.yml?branch=main&style=for-the-badge&label=CI)](https://github.com/dedsec-1337/MANHNT/actions)

**Seekers hunt. Runners hide. Your phone is the referee.**

No subscriptions · No ads · No account walls · Build the APK and play

[Features](#features) · [How to play](#how-to-play) · [Build](#build) · [Architecture](#architecture) · [Changelog](CHANGELOG.md)

</div>

---

## Why MANHNT?

Most manhunt apps charge rent for playground rules.

| Them | MANHNT |
|------|--------|
| Subscriptions & paywalled modes | Free forever |
| Closed source | Apache 2.0 — fork it |
| Live-track every second (glitchy pins) | **Timed reveals** + **frozen map pins** |
| GPS “too far” blocking real tags | **Code / QR proof** when you’re actually there |
| Accounts required | Anonymous Firebase auth only |

Built for parks, campuses, and city blocks — not for microtransactions.

---

## The game in 30 seconds

```text
  HOST creates room ──► friends join with 6-letter code
           │
           ▼
     roles assigned (Seeker / Runner)
           │
           ▼
   head start → runners scatter
           │
           ▼
  every 2–5 min: location REVEAL (snapshot → frozen pin)
           │
           ▼
  seeker finds runner → enter 4-digit code (or show QR)
           │
           ├─ all caught  → Seekers win
           └─ timer ends  → Runners win
```

| | **Seeker** | **Runner (Hider)** |
|---|------------|---------------------|
| **Map** | Frozen reveal pins + own live GPS | Own position + zone; not a live enemy tracker |
| **Clues** | Auto-ping reveals, optional BLE “nearby” | Compass toward nearest seeker |
| **Tag** | Type their catch code | Show code or QR only when tagged |
| **Win** | Catch everyone | Survive the clock / free jail |

---

## Features

<details open>
<summary><strong>Match & map</strong></summary>

- Anonymous Firebase rooms with live roster & session state
- Play zones: **1 km · 2 km · unlimited** · optional **shrinking** zone
- **Head start**, **safe zones**, **jail + jailbreak**, **stealth** (45s hide / 3 min cooldown)
- **Auto-ping reveals** (2 / 5 min) → pins **freeze locally** until the next ping
- OSM maps, compass strip, proximity **haptics**
- Foreground GPS service + battery-optimization helpers
- OLED-friendly dark UI, appearance presets, local match history

</details>

<details open>
<summary><strong>Catch & connectivity</strong></summary>

| Piece | Role |
|-------|------|
| **4-digit code** | Primary tag proof |
| **QR** | Same proof, shown on runner’s screen (`QrCodeUtil` + ZXing) |
| **Bluetooth LE** | Optional “someone nearby” assist — **never required to tag** |
| **Firebase** | Match brain: roster, caught flags, timer, **FINISHED**, results |

- No distance gate on the catch button
- Host validates code → all clients follow `session.status == FINISHED`
- Game over when **all runners caught** or **timer hits zero**

</details>

<details>
<summary><strong>Engineering</strong></summary>

- Kotlin · Jetpack Compose · Material 3
- OSMDroid · Fused Location · Room · DataStore
- Retrofit → Firebase Realtime Database REST
- GitHub Actions: structure checks, Android compile, rules tests
- Vector adaptive launcher icon

</details>

---

## How to play

1. **Prep** — GPS on, mobile data on, Bluetooth optional  
2. **Host** — Create room → set duration, zone, head start, auto-ping → share code  
3. **Join** — Everyone enters the code; host assigns Seeker / Runner  
4. **Start** — Head start ends; seekers hunt  
5. **Reveal** — On each ping, runner positions snapshot onto the map (frozen until next)  
6. **Tag** — Face-to-face: enter their **4-digit code** (they can flash the QR)  
7. **Jail** (if on) — Caught runners go to jail; a free runner can jailbreak the group  
8. **End** — All caught → seekers win · time up with anyone free → runners win  

> Stay off roads. Respect private property. Don’t be reckless.

---

## Build

**Need:** Android Studio (Hedgehog+), **JDK 17**, SDK **34**

```bash
git clone https://github.com/dedsec-1337/MANHNT.git
cd MANHNT
```

Open the **`android/`** folder in Android Studio → **Sync Gradle** → **Run**.

Or ship a release APK:

```text
Build → Generate Signed Bundle / APK → APK → release
→ android/app/build/outputs/apk/release/app-release.apk
```

<details>
<summary>Gradle download timeout?</summary>

If you see `SocketTimeoutException` fetching `gradle-8.6-bin.zip`:

1. Retry / switch network or VPN  
2. Or download manually: [gradle-8.6-bin.zip](https://services.gradle.org/distributions/gradle-8.6-bin.zip) into your Gradle wrapper dists cache  

</details>

### Firebase setup

1. Enable **Anonymous Authentication**  
2. Set the Realtime Database URL in  
   `android/app/src/main/java/com/manhunt/app/data/Constants.kt`  
3. Publish **secure** rules (never `read/write: true` for the world)  
   — start from `firebase/database.rules.example.json` if present  

**BLE, QR, frozen pins, and ungated catch do not require new rules** — they are client-side. Catch still uses the existing attempt / ack paths.

---

## Architecture

```text
┌─────────────────────────────────────────────────────────┐
│  Jetpack Compose UI  ·  GameScreen · Lobby · Results    │
├─────────────────────────────────────────────────────────┤
│  GameViewModel  ·  session state · catch · reveals      │
├──────────────┬──────────────────┬───────────────────────┤
│  OSMDroid    │  Fused Location  │  BLE Nearby (optional)│
│  map + pins │  foreground svc  │  advertise / scan     │
├──────────────┴──────────────────┴───────────────────────┤
│  FirebaseSyncService (REST)  ·  Anonymous Auth          │
│  rooms · publicPlayers · catch · signals                │
├─────────────────────────────────────────────────────────┤
│  Room (history)  ·  DataStore (appearance)              │
└─────────────────────────────────────────────────────────┘
```

| Concern | Implementation |
|---------|----------------|
| UI | Kotlin, Jetpack Compose |
| Map | OSMDroid + frozen reveal overlays |
| Sync | Retrofit → Firebase RTDB REST |
| Tag proof | Code + QR (ZXing); BLE assist only |
| Local | Room, DataStore |

---

## Project layout

```text
MANHNT/
├── android/                 ← open this in Android Studio
│   ├── app/src/main/java/com/manhunt/app/
│   │   ├── ui/              screens & components
│   │   ├── viewmodel/       GameViewModel
│   │   ├── network/         Firebase sync
│   │   ├── util/            Geo, QR, BLE helpers
│   │   └── service/         location foreground service
│   └── app/build.gradle.kts
├── firebase/                rules examples (if present)
├── CHANGELOG.md
└── README.md
```

---

## Changelog

See **[CHANGELOG.md](CHANGELOG.md)** — latest: **v1.1.0** (frozen reveals, QR, BLE nearby, ungated catch).

---

## Contributing

Issues and PRs welcome. Prefer small, focused changes. Keep CI green (`check.yml`).

---

## License

[Apache License 2.0](LICENSE) — use it, fork it, ship it.

---

<div align="center">

**Play outside. Stay off roads. Don’t be an idiot.**

`git clone` · build · hunt

</div>
