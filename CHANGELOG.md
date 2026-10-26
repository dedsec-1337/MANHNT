# Changelog

All notable changes to **MANHNT** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Further launcher icon polish (full wordmark where adaptive safe-zone allows)
- Additional in-match UX refinements from field testing

## [1.0.0] - 2026-10-26

### Added
- Last-known player markers on the map when GPS is briefly stale
- CI step that prints Kotlin compile errors into the job log / annotations
- Vector adaptive launcher assets (foreground + monochrome monogram)

### Changed
- Catch flow is code-based only: distance and GPS-freshness gates removed from the catch button and host validation path
- Catch button labels no longer show "Too far" / "Within 25m"
- Create a Hunt navigation resolves the create-room destination without requiring an active session
- Leave-room path uses `FirebaseSyncService.leaveRoom` (replaces non-existent `deletePlayer` calls)

### Fixed
- Android resource linking failures from corrupted `ic_launcher` / monochrome PNGs in mipmap folders
- Adaptive icon clipping that only showed a partial "MAN" glyph
- `GameViewModel` / `GameScreen` truncation and placeholder stubs that broke release compile
- CatchBottomSheet parameter name mismatches (`targetHider`, `onSubmitCatchCode`)
- Firebase sync method name mismatches (`publishCatchAck`, `sendQuickSignal`, `patchPlayer`, `stopSync`)
- Appearance settings and match-history entity field name mismatches in the ViewModel
- Unused `distance` variable warning in `GameViewModel` after ungating catch
- GitHub Actions Android compile job (now green with structure, scripts, and Firebase rules checks)

### Security
- Firebase Realtime Database rules unchanged for catch distance (distance was never enforced server-side); catch-code validation remains host/rules driven

## [0.9.0] - 2026-10-25

### Added
- Core outdoor manhunt loop: rooms, roles, zone, jail, stealth, quick signals
- OSM map integration and proximity haptics
- Firebase Auth (anonymous) + Realtime Database room sync

---

[Unreleased]: https://github.com/dedsec-1337/MANHNT/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/dedsec-1337/MANHNT/releases/tag/v1.0.0
[0.9.0]: https://github.com/dedsec-1337/MANHNT/releases/tag/v0.9.0
