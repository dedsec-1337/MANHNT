<p align="center">
  <img src="https://img.shields.io/badge/MANHNT-v1.1.0-0D1117?style=for-the-badge&labelColor=161B22&color=3DDC84" alt="MANHNT" />
</p>

<h1 align="center">MANHNT</h1>

<p align="center">
  <strong>Real-life GPS manhunt for Android</strong><br/>
  <em>Seekers hunt · Runners hide · Your phone is the referee</em>
</p>

<p align="center">
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Compose-UI-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-0B5FFF?style=flat-square" alt="License" /></a>
  <a href="https://github.com/dedsec-1337/MANHNT/actions"><img src="https://img.shields.io/github/actions/workflow/status/dedsec-1337/MANHNT/check.yml?branch=main&style=flat-square&label=CI" alt="CI" /></a>
  <a href="https://dedsec-1337.github.io/MANHNT/"><img src="https://img.shields.io/badge/Website-live-6EE7D1?style=flat-square&logo=githubpages&logoColor=white" alt="Website" /></a>
</p>

<p align="center">
  <a href="https://dedsec-1337.github.io/MANHNT/"><strong>Website</strong></a> ·
  <a href="#features">Features</a> ·
  <a href="#how-to-play">How to play</a> ·
  <a href="#build">Build</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="RELEASE_NOTES_1.0.0.md">1.0.0 notes</a>
</p>

---

<table>
<tr>
<td width="50%" valign="top">

### Free by design

No subscriptions. No ads. No forced accounts.  
Clone the repo, build the APK, play with friends.

</td>
<td width="50%" valign="top">

### Built for outside

Parks · campuses · city blocks.  
Timed map reveals. Face-to-face tags. Optional Bluetooth assist.

</td>
</tr>
</table>

---

## Why MANHNT?

| Typical apps | **MANHNT** |
|:-------------|:-----------|
| Subscriptions & paywalled modes | **Free forever** |
| Closed source | **Apache 2.0** — fork it |
| Live-track every second (jittery pins) | **Timed reveals** + **frozen pins** |
| GPS “too far” blocking real tags | **Code / QR** when you’re there |
| Accounts required | **Anonymous** Firebase only |

---

## The loop

```
┌──────────┐     ┌──────────┐     ┌──────────────┐     ┌────────────┐
│  CREATE  │ ──► │   JOIN   │ ──► │  HEAD START  │ ──► │   HUNT     │
│   room   │     │ 6-letter │     │ runners run  │     │ reveals +  │
└──────────┘     └──────────┘     └──────────────┘     │   tags     │
                                                       └─────┬──────┘
                                                             │
                              ┌──────────────────────────────┴───────┐
                              ▼                                      ▼
                    all runners caught                      timer hits zero
                         SEEKERS WIN                         RUNNERS WIN
```

| | Seeker | Runner |
|:--|:-------|:-------|
| **Map** | Frozen reveal pins + live self | Own GPS + zone |
| **Clues** | Auto-ping · optional BLE nearby | Compass to nearest seeker |
| **Tag** | Enter 4-digit code | Show code or QR |
| **Win** | Catch everyone | Survive / jailbreak |

---

## Features

### Match & map

- Anonymous Firebase rooms · live roster & session
- Zones: **1 km · 2 km · unlimited** · optional shrinking
- Head start · safe zones · jail + jailbreak · stealth
- **Auto-ping reveals** (2 / 5 min) → pins **freeze** until next ping
- OSM maps · compass · proximity haptics
- Foreground GPS · battery-exemption helpers
- OLED dark UI · appearance presets · local match history

### Catch & connectivity

| Piece | Role |
|:------|:-----|
| **4-digit code** | Primary tag proof |
| **QR** | Same proof on the runner’s screen |
| **Bluetooth LE** | Optional “nearby” only — never required |
| **Firebase** | Roster · caught · timer · `FINISHED` · results |

No distance gate on catch. Host validates code. Game ends when everyone is caught or the clock hits zero.

### Engineering

Kotlin · Jetpack Compose · Material 3 · OSMDroid · Fused Location · Room · DataStore · Retrofit → Firebase RTDB · GitHub Actions CI · vector adaptive icon

---

## How to play

| Step | What happens |
|:----:|:-------------|
| **1** | GPS on · data on · Bluetooth optional |
| **2** | Host creates room · sets duration / zone / head start / auto-ping · shares code |
| **3** | Everyone joins · host assigns Seeker / Runner |
| **4** | Match starts · head start ends · hunt begins |
| **5** | Each ping: runner positions snapshot onto the map (frozen until next) |
| **6** | Tag = enter their **4-digit code** (they can show QR) |
| **7** | Jail (if on): free runners can jailbreak the group |
| **8** | All caught → seekers · time up with anyone free → runners |

> Stay off roads. Respect private property. Don’t be reckless.

---

## Build

**Requires** Android Studio (Hedgehog+) · **JDK 17** · SDK **34**

```bash
git clone https://github.com/dedsec-1337/MANHNT.git
cd MANHNT
```

Open **`android/`** in Android Studio → **Sync Gradle** → **Run**.

```text
Release APK
  Build → Generate Signed Bundle / APK → APK → release
  → android/app/build/outputs/apk/release/app-release.apk
```

<details>
<summary><b>Gradle download timeout?</b></summary>

<br/>

If `SocketTimeoutException` appears while fetching `gradle-8.6-bin.zip`:

1. Retry or switch network / VPN  
2. Manual zip: [gradle-8.6-bin.zip](https://services.gradle.org/distributions/gradle-8.6-bin.zip) → drop into the Gradle wrapper dists cache  

</details>

### Firebase

1. Enable **Anonymous Authentication**  
2. Set RTDB URL in `android/app/src/main/java/com/manhunt/app/data/Constants.kt`  
3. Publish **secure** rules — never world-open `read` / `write`  

BLE · QR · frozen pins · ungated catch need **no new rules** (client-side). Catch still uses existing attempt / ack paths.

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  Compose UI · GameScreen · Lobby · Results               │
├──────────────────────────────────────────────────────────┤
│  GameViewModel · session · catch · reveals               │
├───────────────┬──────────────────┬───────────────────────┤
│  OSMDroid     │  Fused Location  │  BLE Nearby (opt.)    │
│  map + pins  │  foreground svc  │  advertise / scan     │
├───────────────┴──────────────────┴───────────────────────┤
│  FirebaseSyncService (REST) · Anonymous Auth             │
├──────────────────────────────────────────────────────────┤
│  Room (history) · DataStore (appearance)                 │
└──────────────────────────────────────────────────────────┘
```

```
MANHNT/
├── android/          ← open in Android Studio
│   └── app/src/main/java/com/manhunt/app/
│       ├── ui/
│       ├── viewmodel/
│       ├── network/
│       ├── util/
│       └── service/
├── website/          ← marketing site (GitHub Pages)
├── CHANGELOG.md
├── RELEASE_NOTES_1.0.0.md
└── README.md
```

---

## Docs

| Doc | Contents |
|:----|:---------|
| [**Website**](https://dedsec-1337.github.io/MANHNT/) | Product landing page |
| [CHANGELOG.md](CHANGELOG.md) | Full version history (Keep a Changelog) |
| [RELEASE_NOTES_1.0.0.md](RELEASE_NOTES_1.0.0.md) | Player-facing 1.0.0 notes |
| [RELEASE_NOTES.md](RELEASE_NOTES.md) | Version index |

---

## Contributing

Issues and PRs welcome. Prefer small, focused changes. Keep CI green (`check.yml`).

---

## License

[Apache License 2.0](LICENSE) — use it, fork it, ship it.

---

<p align="center">
  <sub>Play outside. Stay off roads. Don’t be an idiot.</sub><br/><br/>
  <code>git clone</code> · build · hunt
</p>
