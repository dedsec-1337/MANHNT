# MANHNT 1.0.0 — Release Notes

**Released:** 26 October 2026  
**Tag:** `v1.0.0`  
**Platform:** Android 8.0+ (API 26–34)

The first stable public cut of MANHNT: outdoor GPS manhunt that builds cleanly, tags without distance gates, and keeps the map usable when GPS briefly drops.

---

## Highlights

- **Tag with a code** — no more “Too far” blocking a real face-to-face catch  
- **Last-known map pins** — other players stay on the map when GPS is briefly stale  
- **Create a Hunt works** — room creation navigation fixed  
- **Solid launcher icon** — vector adaptive assets (no broken PNG mipmaps)  
- **Green CI** — Android compile and checks pass on GitHub Actions  

---

## What’s new for players

### Catch (tagging)
Seekers tag hiders by entering the **4-digit catch code**.  
Distance and “GPS too old” checks no longer block the catch button.  

Still enforced:
- Correct code  
- Target is a free hider (not already caught)  
- Safe-zone protection when the hider is inside a safe zone  

### Map
Other players’ **last-known positions** remain visible even if a location update is briefly late.  
Stealth still hides a hider while it is active.

### Rooms
**Create a Hunt** opens the create-room flow reliably, including when you are not already in a session.

### App identity
New **vector adaptive launcher icon** (foreground + monochrome monogram) that fits Android’s safe zone and no longer clips to a partial “MAN” glyph.

---

## Fixes

- Corrupted launcher PNGs that caused AAPT / resource-linking failures  
- Truncated or placeholder `GameViewModel` / `GameScreen` sources that broke compile  
- Catch sheet and Firebase method name mismatches after large-file restores  
- Leave-room path using the correct `leaveRoom` API  
- Unused-variable warning after removing the distance gate  
- GitHub Actions Android compile job (structure checks, compile, rules tests)

---

## For builders

```bash
git clone https://github.com/dedsec-1337/MANHNT.git
cd MANHNT
# Open android/ in Android Studio → Sync → Run
```

- **JDK 17**, SDK **34**  
- Firebase: Anonymous Auth + your Realtime Database URL in `Constants.kt`  
- Prefer secure rules (not open read/write). Catch distance was never enforced in rules.

Full technical history: [CHANGELOG.md](CHANGELOG.md)

---

## Known limits (1.0.0)

- Tag proof is **code entry** (QR / Bluetooth assist landed in later work on `main`)  
- Location between auto-pings can still feel “live” depending on build; frozen-snapshot reveals are documented in **1.1.0** notes / changelog  
- Outdoor GPS quality still matters for good reveals and navigation  

---

## Credits

MANHNT is open source under the **Apache License 2.0**.  
Play outside. Stay off roads. Don’t be an idiot.
