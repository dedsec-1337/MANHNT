# Firebase Realtime Database security setup

1. In Firebase Console, open **Build → Authentication → Sign-in method** and enable **Anonymous**.
2. Confirm `android/app/google-services.json` exists and its package name matches `com.manhunt.app`.
3. Open **Build → Realtime Database → Rules** and review `database.rules.example.json` before publishing.
4. `GameViewModel` now signs in anonymously when needed and uses the Firebase Authentication UID as `Player.id` and the player database key, with `session.hostPlayerId` set to the authenticated host UID.
5. Test room creation, joining, player updates/deletion, session updates, catch attempts, and catch confirmations with the Firebase Emulator or Rules Playground before using live games.

For automated rules checks, run `npm install` and `npm run test:rules` from this directory. The test command starts the Realtime Database Emulator and checks representative allowed and denied operations. These tests do not publish rules to your live Firebase project.

## Security notes

- Do not grant `.write: auth != null` at `/rooms/$joinCode`. Realtime Database rules cascade, so a broad parent write grant bypasses narrower child rules.
- The room-level write rule permits only initial room creation by the authenticated host. Subsequent writes must be authorized by the relevant child rule.
- Player writes are allowed for that player's UID, or for the room host (needed for host-managed role assignment). Player IDs must match their Firebase UIDs.
- Catch attempts must be submitted by the authenticated seeker. Catch-confirmation writes are limited to the seeker or target listed in the confirmation.
- These rules do not prove that a catch code, GPS location, role, or game event is truthful. A modified client can still fabricate values it is authorized to submit. Use trusted server-side logic (for example, Cloud Functions) for competitive integrity.
- These rules are not yet verified against the complete app flow. The ViewModel has been restored and updated, and GitHub source-structure checks pass, but that is not an Android build or Firebase Rules Emulator test. Do not publish these rules to a live database until all listed operations pass rule tests.
