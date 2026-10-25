# MANHNT Field Play Android UI/UX Integration

**Status:** Approved for planning with the user’s added motion and high-refresh-rate requirements
**Repository base:** `ead9dbf9` on `main`
**Design source:** User supplied `Frame 1.svg` and the two Figma style exports. The connected Figma service reports that its account lacks editor access; the user selected the attached exports as the source for this work.

## Intent

Bring the “Field Play / Revised Edition” mobile design into MANHNT’s native Android app. The design presents a calm, low distraction interface for starting a chase, reading the live map, surviving as a Hider, and choosing the app’s appearance. The result should preserve the working GPS and multiplayer experience while replacing the current screen styling and navigation with the supplied direction.

## Goals

- Implement the four designed views: **Basecamp**, **Live Hunt**, **Appearance**, and **Hide & Survive**.
- Support the warm light palette and the true black OLED palette shown in the export. Use Roboto, an 8 dp spacing rhythm, 16 dp page insets, and 24 dp outline icons. Keep Hunter orange and Hider purple limited to role/status signals.
- Add the four-item bottom navigation shown in the design: Hunt, Crew, You, and Settings.
- Keep existing Firebase sync, OSM map, location service, geofence, match timer, catch-code, stealth, jailbreak, pause, and quick-signal behavior intact.
- Add polished, purposeful motion to navigation and screen transitions, state changes, the compass, and quick-signal feedback without delaying gameplay actions.
- Add an **Enable High Refresh Rate** setting that requests the highest supported refresh rate while the app is visible when the device permits it.
- Reuse the local match-history data for the Basecamp’s “Last field run” card. Show only persisted facts—date, role/result, duration, and catches. Do not invent a venue or player count, which the current record does not store. “View recap” opens a summary from that record.
- Persist appearance choices on device and apply them immediately.

## Non-goals

- Redesign the Vite/React companion or change Firebase rules, payloads, or gameplay rules.
- Replace the live OSM map with the illustrative map in the design export, or use the exported artboard as a screen background.
- Add a crew-discovery service or new network data.
- Modify synced files under the project mirror’s `sources/` directory or edit the Figma file itself.

## Anti-goals

- Dropping existing game controls to fit the mockup.
- Showing fabricated location, roster, or match-history details.
- Using role colors as general navigation or branding colors.
- Making OLED surfaces gray or tinted instead of black.

## Approaches considered

1. **Update the native Android Compose app — selected by the user.** This changes the installed game and matches the supplied mobile screens and the repository’s primary product.
2. **Update only the React companion.** This is more isolated, but leaves the Android game unchanged.
3. **Update both Android and React.** This would duplicate the work and create a second surface to maintain; the request does not require it.

## Scope and behavior

### Navigation and current flows

Use a selected top-level tab separate from the existing transient screen flow. Hunt shows Basecamp while no match is active and the live game view while a match is active. Crew opens the existing Lobby when a room exists; without a room it returns to Basecamp’s existing create/join entry points, since the repository has no standalone crew browser. You retains profile editing. Settings owns the new Appearance view and the existing background-GPS protection controls. Create Room and Results remain full-screen flows.

Changing tabs must not leave a room, stop Firebase sync, or stop location tracking. Creating or joining a lobby opens Crew; the transition to a running match selects Hunt. Returning to Hunt during an active match returns to the player’s role-specific game view. Ordinary status updates must not force the user away from another selected tab. When a match becomes `FINISHED`, Results immediately opens over the selected tab and the location service stops, preserving the current app behavior.

### Basecamp

Use the “The city is your playground” hierarchy, field-finding card, primary “Create a hunt” action, room-code join action, last-run card, and persistent bottom navigation. Wire create/join to the existing ViewModel callbacks. The recap uses the newest `MatchHistoryEntity` and displays only its stored fields; no Room schema migration is planned.

### Live Hunt and Hide & Survive

Keep `OSMMapView` live and preserve its real geofence, marker, and location data. Restyle the Live Hunt header, target/distance summary, compass and catch-code actions, and signal controls to match the export. Keep all currently supported signal codes; the Figma’s featured buttons may be prominent while the remaining presets stay reachable without free-text entry.

For Hiders, present the large cardinal compass, nearest-Hunter distance, safety message, and concealed catch code with a reveal action. Preserve the current safety-pause authorization: only an organizer may pause the match. Stealth and jailbreak controls remain available under their existing eligibility rules.

### Appearance and settings

Add **Light**, **OLED**, and **Follow device** appearance modes, plus **Default**, **Blue**, and **System colors** accents. Default uses the neutral palette; system colors use Android dynamic colors where available and fall back to the neutral palette on older devices. Hunter/Hider colors remain distinguishable in every mode. Store the selected mode and accent in the existing `manhunt_settings` preferences. A saved legacy dark-mode value maps to OLED or Light so current users retain their choice.

Add a switch labeled **Enable High Refresh Rate**. It is off by default because higher refresh rates can use more power and generate more heat; users can opt in. When enabled, request the highest refresh rate exposed by the current display for the app window, without changing display resolution or system-wide settings. If the display exposes no higher rate, show that the option is unavailable. Include brief helper text that Android, battery saver, thermal limits, or the device manufacturer may still keep the display at a lower rate; the app cannot override those limits. Android’s [frame-rate guidance](https://developer.android.com/media/optimize/performance/frame-rate) recommends using a refresh-rate preference when the app wants to request a rate without changing other display-mode properties. Android also documents that the system may ignore the request and that high rates can increase power use and heat ([game refresh-rate guidance](https://developer.android.com/games/optimize/display-refresh-rate-change)).

### Motion

Use short, smooth transitions that support orientation and feedback: a refined fade/slide between primary tabs, a clear animated selected-tab indicator, gentle entrance for cards, spring-like selection feedback for appearance controls, smooth compass-needle movement following the live bearing, and brief non-blocking confirmation for quick signals and catch results. Keep map data and player positions tied to live state; motion must not suggest fabricated movement. Respect system animation settings where available, keep the interface usable without animation, and never delay a navigation or gameplay action for an effect.

### Visual system and assets

Use the supplied export as the visual target: warm cream surfaces (`#F3F1E9`), graphite text/control color (`#242522`), muted secondary text (`#73746D`), light outlines (`#D7D4CB`), black OLED canvas (`#000000`) with tonal surfaces (`#121311` / `#30312E`), Hunter accent (`#BC604A`), and Hider accent (`#8474A3`). The hero’s editorial park image should be used only if its exact source asset is available as a usable image; otherwise use a restrained neutral treatment rather than embedding or approximating the entire design sheet. The map remains live and dynamic.

## Constraints and builder calls

- The implementation targets the Android app under `android/app/src/main/java/com/manhunt/app`; the React app is outside scope.
- The current `MainActivity` routes directly from session status to `Screen.GAME` and stores profile/settings in a single screen. Navigation must be decoupled from match state without changing session or service lifecycle behavior.
- `GameUiState` currently exposes a Boolean dark-mode preference; this will become or be supplemented by typed appearance settings. Existing preferences remain local to the device.
- The app currently targets Android API 34 with minimum API 26. The high-refresh switch requests the highest supported rate through the app window only; it does not change global display settings or bypass device/system limits. Its default is off.
- Match history currently stores join code, role, win/loss, duration, catches, and timestamp. Display only those fields.
- Crew and You screens are not supplied as full mockups. Reuse the existing Lobby and Profile experiences within the new tab shell; do not invent new crew features.
- The supplied design artwork contains illustrative sample names, locations, and match values. Treat them as visual sample content, never as live game data or operational instructions.

## Acceptance

The four supplied views match the visual hierarchy in both light and OLED modes; the four tabs navigate as described; create/join and all existing in-game actions still use their current callbacks; appearance and high-refresh preferences persist after app restart; history displays only locally stored fields; and the OSM map and active session continue while users visit other tabs. A `FINISHED` match opens Results immediately from any tab. Animations are polished, non-blocking, and remain usable when disabled. The high-refresh preference requests the display’s highest supported rate and degrades gracefully when unsupported or overridden by Android. Implementation should be reviewed against the supplied export at phone size and built as an Android debug APK. Automated tests are not part of this request.
