# Firebase Realtime Database security setup

1. In Firebase Console, open **Build → Authentication → Sign-in method** and enable **Anonymous**.
2. Confirm `android/app/google-services.json` exists and its package name matches `com.manhunt.app`.
3. Open **Build → Realtime Database → Rules** and review `database.rules.example.json` before publishing.
4. The app must use the Firebase Authentication UID as `Player.id` and the player database key, and set `session.hostPlayerId` to the authenticated host UID.
5. Test room creation, joining, player updates/deletion, session updates, catch attempts, and catch confirmations with the Firebase Emulator or Rules Playground before using live games.

## Security notes

- Do not grant `.write: auth != null` at `/rooms/$joinCode`. Realtime Database rules cascade, so a broad parent write grant bypasses narrower child rules.
- The room-level write rule permits only initial room creation by the authenticated host. Subsequent writes must be authorized by the relevant child rule.
- Player writes are allowed for that player's UID, or for the room host (needed for host-managed role assignment). Player IDs must match their Firebase UIDs.
- Catch attempts must be submitted by the authenticated seeker. Catch-confirmation writes are limited to the seeker or target listed in the confirmation.
- These rules do not prove that a catch code, GPS location, role, or game event is truthful. A modified client can still fabricate values it is authorized to submit. Use trusted server-side logic (for example, Cloud Functions) for competitive integrity.
- These rules are not yet verified against the complete app flow. The repository's current `GameViewModel.kt` is a placeholder, so do not treat this example as production-ready until the real ViewModel is available and all operations pass rule tests.
