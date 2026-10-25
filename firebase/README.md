# Firebase Realtime Database security setup

1. In Firebase Console, open **Build → Authentication → Sign-in method** and enable **Anonymous**.
2. Open **Build → Realtime Database → Rules**.
3. Review `database.rules.example.json` and adapt it to the app's current data shape before clicking **Publish**.
4. Ensure the app uses the Firebase Authentication UID as each player's `Player.id`, and sets `session.hostPlayerId` to the authenticated host UID when creating a room.

## Important

Do not publish rules that grant `.write` at `/rooms/$joinCode` for every signed-in user: Firebase Realtime Database rules cascade, so a broad parent write grant bypasses narrower child rules. The example permits only initial room creation at the room parent, then uses child rules for later writes.

The example is a starting point, not a substitute for testing every app operation in the Rules simulator. In particular, validate session updates, player creation/update/deletion, catch attempts, and host role assignment before using it for live games.
