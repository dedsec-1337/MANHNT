# MANHNT Logo and Accent Palettes

## Intent

Use the supplied white MANHNT wordmark on black as the app's logo, and replace the muted Blue accent with a vivid cobalt. Give players several coordinated accent choices while preserving the current Light, OLED, Follow device, Default, and System colors behavior.

## Goals

- Use the supplied wordmark in the native launcher icon and Home header, with a readable light-theme variant.
- Keep Default, Blue, and System colors preference values stable; add Teal, Violet, Forest, Rose, and Amber.
- Show a colored swatch and readable name for every accent in Settings.
- Give every fixed palette separate light and dark primary/on-primary pairs with at least 4.5:1 contrast.
- Preserve appearance mode, high-refresh preference, gameplay, and Firebase behavior.

## Non-goals

- Change the app name, gameplay, secondary team colors, or system dynamic-color behavior.
- Copy HEVY branding or use an unverified exact HEVY hex value.
- Change any file under `sources/` or deploy Firebase rules.

## Approach

Keep the exact supplied artwork as the source of the launcher logo, derive transparent black and white wordmarks for light and dark Home headers, and retain the existing adaptive-icon setup. Add fixed palette roles to the native theme and display each option as a swatch chip. This follows the user's Native Android selection and keeps preferences compatible because existing enum names remain unchanged.

The Blue palette starts at cobalt `#1D4ED8` in light mode and `#78AFFF` in dark mode. Teal, Violet, Forest, Rose, and Amber use coordinated saturated light colors and bright dark colors. Each primary is paired with a contrasting on-primary color. This choice is informed by HEVY's bright blue action accents on neutral surfaces and Android Material 3's guidance to use primary/on-primary pairs and accessible tonal roles.

## Research

- [HEVY Press Center](https://www.hevyapp.com/press/) provides official brand assets; HEVY's app materials use blue for prominent actions. The color is a visual reference only.
- [Android Developers: Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) describes color roles, dynamic palettes, and accessible `primary`/`onPrimary` pairings.
- [Material 3 Color Roles](https://m3.material.io/styles/color/roles) explains primary, secondary, tertiary, and corresponding on-color roles.
