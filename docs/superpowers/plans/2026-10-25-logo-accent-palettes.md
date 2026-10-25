# Logo and Accent Palettes Implementation Plan

**Goal:** Apply the supplied MANHNT wordmark to the native app and offer six fixed accent palettes plus System colors.

**Architecture:** Keep the existing `AccentMode` string preference and adaptive-icon resource structure. Store palette colors centrally in the Compose theme, expose each accent as a swatch in Settings, and derive the launcher and Home wordmark assets from the user's PNG.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android adaptive-icon PNG resources, PowerShell/.NET image processing.

**Spec:** `docs/superpowers/specs/2026-10-25-logo-accent-palettes-design.md`

## Global Constraints

- Preserve the `DEFAULT`, `BLUE`, and `SYSTEM_COLORS` enum names so existing preferences load unchanged.
- Preserve Light, OLED, and Follow device appearance selection independently from accent selection.
- Keep visual foregrounds readable; each fixed palette pair must exceed 4.5:1.
- Do not edit `sources/`, alter Firebase rules, or change gameplay.

## Review Focus

- Existing users with a stored `BLUE` preference still receive the refreshed cobalt palette.
- Legacy preferences with an unknown accent still fall back to Default.
- Adaptive and monochrome launcher icons preserve the supplied wordmark within the icon mask.
- Every swatch uses the matching palette color in light, OLED, and Follow device modes.
- Theme changes retain accessible foreground colors for buttons and selected controls.

## Tasks

### Task 1: Add the supplied MANHNT wordmark

**Files:** Add light/dark wordmark assets under `android/app/src/main/res/drawable-nodpi/`; replace the five density variants of `ic_launcher_foreground.png` and `ic_launcher_monochrome.png`; update `android/app/src/main/java/com/manhunt/app/ui/screens/HomeScreen.kt`.

- Derive transparent dark-ink and white wordmarks from the attached PNG without editing the original download.
- Use the exact supplied logo in the adaptive launcher foreground and its white silhouette for themed icons.
- Replace Home's text logo with the theme-appropriate wordmark image and a meaningful content description.
- Check generated assets visually at full and launcher sizes.

### Task 2: Add coordinated accent presets

**Files:** `android/app/src/main/java/com/manhunt/app/data/model/AppearanceSettings.kt`, `android/app/src/main/java/com/manhunt/app/ui/theme/Theme.kt`, `android/app/src/main/java/com/manhunt/app/ui/screens/AppearanceScreen.kt`.

- Keep existing enum values and add `TEAL`, `VIOLET`, `FOREST`, `ROSE`, and `AMBER`.
- Define light, dark, and on-primary values for each fixed palette; leave Default and System colors behavior intact.
- Render the eight accent choices as two-column chips with swatches, including System colors.
- Preserve existing setting persistence through enum names; keep Light, OLED, and Follow device choices unchanged.

### Task 3: Verify the native result

- Run `git diff --check`.
- Build with `:app:assembleDebug` and confirm the APK packages all launcher assets.
- Inspect generated logo variants and calculate contrast for each fixed primary/on-primary pair.
- Review the final diff for preference compatibility and confirm no gameplay, Firebase, or `sources/` changes.
