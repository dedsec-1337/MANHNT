# Field Play Android UI/UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the approved Field Play design into MANHNT’s native Android app while preserving current multiplayer and location behavior.

**Architecture:** Keep the existing Compose screens and ViewModel as the source of gameplay actions, and replace the single-screen routing with four persistent top-level tabs plus transient create-room and results destinations. Store appearance and refresh preferences locally, surface existing Room match history on Basecamp, and compose the live Hider and Seeker views from existing session data.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, Android Window APIs; existing Firebase and OSM integrations remain in place.

**Spec:** `docs/superpowers/specs/2026-10-25-field-play-android-design.md`

## Global Constraints

- Native Android Compose is the implementation target; the Vite/React companion is out of scope.
- Keep existing Firebase sync, OSM map, location service, geofence, timer, catch-code, stealth, jailbreak, pause, and quick-signal behavior.
- Use only current live game data and saved match-history fields; do not invent a venue or player count. Show Hider distance and bearing only from the existing fresh nearest-Seeker calculation.
- A finished match opens Results immediately from every selected tab; ordinary status updates do not steal the selected tab.
- The high-refresh option is opt-in and requests the highest rate supported by the active display; Android, battery saver, thermal state, and device policy may override it.
- Use the exported light and OLED palettes, Roboto, 8 dp spacing, 16 dp page insets, and 24 dp outline icons. Keep motion responsive and non-blocking.
- Do not edit anything under the project mirror’s `sources/` directory or modify the Figma file.
- Do not add automated tests; use build, diff checks, and manual screen review only.

## Review Focus

- A user with no room sees working Basecamp create/join actions; creating/joining opens Crew and starting a match selects Hunt. Manually review these states in Task 2.
- A match update while the user is on Crew, You, or Settings leaves that tab selected; `FINISHED` opens Results immediately. Manually review these transitions in Task 2.
- An empty history has a truthful empty state, and a saved run recap uses only its persisted fields. Manually review both states in Task 3.
- The refresh switch reflects the saved user preference; unsupported devices and system overrides are described as unable to honor the request, not as a global display change. Manually review supported and unsupported display behavior in Task 1 when available.
- A Hider without a valid compass heading still has clear status and controls; quick signals and all existing gameplay actions remain reachable. Manually review role/state variants in Task 4.

---

### Task 1: Appearance preferences and refresh-rate request

**Files:**
- Create: `android/app/src/main/java/com/manhunt/app/data/model/AppearanceSettings.kt`
- Create: `android/app/src/main/java/com/manhunt/app/util/RefreshRateController.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/viewmodel/GameViewModel.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/theme/Color.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/theme/Theme.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/MainActivity.kt`

**Interfaces:**
- Produce `enum class AppearanceMode { LIGHT, OLED, FOLLOW_DEVICE }`, `enum class AccentMode { DEFAULT, BLUE, SYSTEM_COLORS }`, and `data class AppearanceSettings(val mode: AppearanceMode = AppearanceMode.FOLLOW_DEVICE, val accent: AccentMode = AccentMode.DEFAULT, val highRefreshRateEnabled: Boolean = false)`.
- `GameUiState.appearanceSettings: AppearanceSettings`; ViewModel methods `setAppearanceMode(mode: AppearanceMode)`, `setAccentMode(accent: AccentMode)`, and `setHighRefreshRateEnabled(enabled: Boolean)` persist and publish changes immediately.
- `RefreshRateController.hasHighRefreshRate(display: Display?): Boolean` detects any supported mode above 60 Hz. `RefreshRateController.apply(window: Window, enabled: Boolean): Boolean` requests the highest supported rate when enabled, restores the system default when disabled, and reports whether a higher rate is available.
- `ManhuntTheme(settings: AppearanceSettings, content: @Composable () -> Unit)` selects Light, true-black OLED, or system-following appearance and the Default, Blue, or System colors accent. System colors use Android dynamic colors on API 31+ and the neutral fallback on older devices.

- [x] Migrate a stored legacy `is_dark_mode` value once to OLED or Light; use Follow device for first-time installs, Default accent, and High Refresh Rate off. Persist all selections in the existing `manhunt_settings` preferences without changing gameplay storage.
- [x] Add semantic color tokens for Light and OLED, plus Default, Blue, and System colors accents; update `ManhuntTheme` to consume `AppearanceSettings` and fall back cleanly below API 31.
- [x] Implement the window-level refresh request using supported display modes; use the system default if disabled or no supported mode exceeds 60 Hz.
- [ ] Apply the current preference when it changes and when the Activity resumes. Manually confirm the selection persists, turning it off resets the window preference, and unsupported/overridden states remain honestly described in Settings.

### Task 2: Four-tab navigation, Crew, You, and Settings

**Files:**
- Create: `android/app/src/main/java/com/manhunt/app/ui/components/AppBottomBar.kt`
- Create: `android/app/src/main/java/com/manhunt/app/ui/screens/AppearanceScreen.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/MainActivity.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/screens/LobbyScreen.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/screens/ProfileScreen.kt`

**Interfaces:**
- Produce `enum class AppTab { HUNT, CREW, YOU, SETTINGS }` and `@Composable fun AppBottomBar(selectedTab: AppTab, onSelectTab: (AppTab) -> Unit)`.
- `AppearanceScreen` receives `settings: AppearanceSettings`, `highRefreshRateAvailable: Boolean`, `onAppearanceModeChange: (AppearanceMode) -> Unit`, `onAccentModeChange: (AccentMode) -> Unit`, `onHighRefreshRateChange: (Boolean) -> Unit`, and the existing battery-protection status/action.
- Keep create-room and results as transient destinations above the selected tab. Reuse `HomeScreen`, `CreateRoomScreen`, `LobbyScreen`, `GameScreen`, `ResultsScreen`, and `ProfileScreen` callbacks.

- [x] Replace the mutually exclusive root `Screen` routing with a selected tab and transient destination, preserving room/session state while tabs change.
- [x] Make Hunt show Basecamp without a room and the live game when running; make Crew show the existing lobby or existing create/join entry actions when no room exists. In Crew during an active match, show the current roster read-only and hide lobby-only role/start controls. Keep profile content under You; move appearance and battery-protection controls into Settings.
- [x] Route successful create/join to Crew and a running session to Hunt. Keep the selected tab through ordinary session updates; immediately show Results and stop the location service on `FINISHED` from any tab.
- [ ] Add a clear selected-tab indicator and short, restrained transitions that do not delay taps. Manually review no-room, lobby, active-match tab switching, returning to Hunt, and finish-from-each-tab flows.

### Task 3: Basecamp and last field run recap

**Files:**
- Create: `android/app/src/main/java/com/manhunt/app/ui/components/MatchRecapSheet.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/viewmodel/GameViewModel.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/screens/HomeScreen.kt`

**Interfaces:**
- Add `GameUiState.latestMatch: MatchHistoryEntity?`, fed from the existing `MatchHistoryDao.getAllMatchesFlow()`; no Room schema change.
- `MatchRecapSheet(match: MatchHistoryEntity, onDismiss: () -> Unit)` renders the saved date, role/result, duration, and catches.

- [x] Observe the existing history flow and expose only its newest record in `GameUiState`.
- [x] Rework HomeScreen as Basecamp, preserving existing create/join callbacks and adding the Last field run card.
- [ ] Make “View recap” open a dismissible recap based only on saved fields; show an honest no-runs state when history is empty. Manually review both states and a record with zero catches.

### Task 4: Live Hunt, Hide & Survive, and gameplay motion

**Files:**
- Create: `android/app/src/main/java/com/manhunt/app/ui/theme/Motion.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/screens/GameScreen.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/components/CompassStrip.kt`
- Modify: `android/app/src/main/java/com/manhunt/app/ui/components/OSMMapView.kt` only if needed to match the palette without changing map behavior.

**Interfaces:**
- Redesign existing `CompassStrip(deviceHeading: Float, targetBearing: Float, distanceMeters: Double, hasHeading: Boolean = true, modifier: Modifier = Modifier)` around live nearest-Seeker bearing/distance, animate its needle, and show a clear unavailable state when the heading or location is missing.
- Motion tokens expose short enter/exit and spring timings for tab/card/selection feedback; action callbacks remain immediate.
- Keep `GameScreen`’s existing parameters and connect its redesigned controls to the same callbacks.

- [x] Restyle Seeker Live Hunt around the existing live OSM map, timer, zone, player and network states, safe-zone alerts, and catch flow.
- [x] Restyle Hider Hide & Survive around the role state, device compass, existing fresh nearest-Seeker distance, concealed catch code with reveal action, stealth/jailbreak controls, safe-zone state, quick signals, and existing organizer-only safety pause.
- [x] Preserve all nine existing emoji reactions and preset messages; add smooth compass needle motion, subtle tab/card entrance, and quick-signal/catch confirmation feedback. Respect reduced-animation settings where supported; never animate fake map movement or gate gameplay actions behind motion.
- [ ] Manually review Seeker and Hider screens, missing heading, head-start, paused/safe-zone states, quick signals, catch flow, and leave confirmation.

### Task 5: Integration build and visual review

**Files:**
- Review the integrated Android changes; no additional source files unless a specific issue requires one.

- [x] Build the debug APK with `.\gradlew.bat :app:assembleDebug` from the Android project directory; expected result is a successful Android debug build.
- [x] Run `git diff --check`; expected result is no whitespace errors.
- [ ] Manually review Basecamp, Live Hunt, Appearance, and Hide & Survive at phone size in both light and OLED themes, plus tab navigation, saved recap, preference persistence, and refresh-rate unavailable/override messaging where supported.
