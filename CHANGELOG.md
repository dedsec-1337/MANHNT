# Changelog

All notable changes to **MANHNT** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Optional camera-based QR scan for seekers (hider QR display already ships)
- Further launcher icon polish (full wordmark where adaptive safe-zone allows)
- Field-test polish for BLE reliability across OEMs

## [1.1.0] - 2026-10-28

### Added
- **Frozen location reveals** — on each auto-ping, hider positions are snapshotted once and held locally so map pins do not jump with every network/GPS update
- `LocationRevealSnapshot` model and map wiring (`OSMMapView` prefers frozen coords when present)
- **QR catch proof** — hiders can reveal a QR encoding `MANHNT|joinCode|playerId|catchCode` (`QrCodeUtil` + ZXing)
- **Optional Bluetooth LE nearby assist** — hiders advertise, seekers scan; UI shows “Nearby (Bluetooth)” without blocking tag (`BleNearbyAssist`)
- BLE runtime permissions (Android 12+) requested alongside location in `MainActivity`
- Config knobs for reveal/catch behaviour in `Constants`

### Changed
- Catch validation no longer rejects on GPS freshness or mutual distance; **code (and QR display) is the tag proof**
- Game-over after the last successful catch updates local `session.status` to `FINISHED` immediately so all clients can navigate to Results
- README updated for timer-based reveals, ungated catch, QR, and BLE

### Fixed
- MainActivity compile regressions (`modifier` → `Modifier`, `onCreateRoomSubmit` vs `onCreateClick`)
- CI Android compile job after BLE integration commits

### Security
- Firebase Realtime Database **rules unchanged** — BLE and QR are client-side; catch still goes through existing attempt/ack paths

## [1.0.0] - 2026-10-26

### Added
- Last-known player markers on the map when GPS is briefly stale
- CI step that prints Kotlin compile errors into the job log / annotations
- Vector adaptive launcher assets (foreground + monochrome monogram)

### Changed
- Catch flow is code-based only: distance and GPS-freshness gates removed from the catch button and host validation path
- Catch button labels no longer show “Too far” / “Within 25m”
- Create a Hunt navigation resolves the create-room destination without requiring an active session
- Leave-room path uses `FirebaseSyncService.leaveRoom` (replaces non-existent `deletePlayer` calls)

### Fixed
- Android resource linking failures from corrupted `ic_launcher` / monochrome PNGs in mipmap folders
- Adaptive icon clipping that only showed a partial “MAN” glyph
- `GameViewModel` / `GameScreen` truncation and placeholder stubs that broke release compile
- CatchBottomSheet parameter name mismatches (`targetHider`, `onSubmitCatchCode`)
- Firebase sync method name mismatches (`publishCatchAck`, `sendQuickSignal`, `patchPlayer`, `stopSync`)
- Appearance settings and match-history entity field name mismatches in the ViewModel
- Unused `distance` variable warning in `GameViewModel` after ungating catch
- GitHub Actions Android compile job (structure, scripts, and Firebase rules checks)

### Security
- Firebase Realtime Database rules unchanged for catch distance (distance was never enforced server-side); catch-code validation remains host/rules driven

## [0.9.0] - 2026-10-25

### Added
- Core outdoor manhunt loop: rooms, roles, zone, jail, stealth, quick signals
- OSM map integration and proximity haptics
- Firebase Auth (anonymous) + Realtime Database room sync

---

[Unreleased]: https://github.com/dedsec-1337/MANHNT/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/dedsec-1337/MANHNT/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/dedsec-1337/MANHNT/releases/tag/v1.0.0
[0.9.0]: https://github.com/dedsec-1337/MANHNT/releases/tag/v0.9.0
