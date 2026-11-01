# MANHNT 1.2.0 — Release Notes

**Released:** 1 November 2026  
**Tag:** `v1.2.0`  
**Platform:** Android 8.0+ (API 26–34)

A fix release for the two things that broke games: tagging a runner, and quick chat between phones.

---

## Before you install

1. **Uninstall MANHNT 1.1.0** from every phone first. Debug builds are signed with a different key each time, so Android will refuse to update over the old one.
2. Install **1.2.0** on **every** phone in the match. Mixed versions will not behave correctly.
3. **The organizer must publish the Firebase rules** once (see below). Without this, chat and tag results stay broken no matter which app version is installed.

### Publishing the Firebase rules (organizer, one time)

1. Firebase console → your project → **Build → Realtime Database → Rules**.
2. Replace everything with the contents of [`firebase/database.rules.example.json`](firebase/database.rules.example.json).
3. Click **Publish**.

Quick check: in the Rules editor, search for `roomSignals` and `catchAcks`. If either is missing, the rules in the console are out of date.

---

## What’s fixed

### Tagging
- **A correct catch code now finishes the tag.** Before, the host phone could refuse to save a valid tag, so the game carried on as if nothing happened.
- **Pick who you tagged.** With two or more runners, the tag screen now asks which runner you caught. Before, the app always checked your code against the first runner in the list.
- **Tag works without a fresh GPS fix.** The tag button no longer depends on your own location; the code is the proof.
- **You find out what happened.** Seekers see the result when the runner is marked caught, even if the host’s acknowledgement is delayed. If the host never answers, you are told after about 12 seconds and can try again.
- **Old tags are not replayed** when the host app restarts.

### Quick chat and reactions
- **Messages now reach the other phones.** They are stamped with Firebase’s clock, so phones whose clocks disagree by a few seconds no longer drop them.
- **Each message shows once** as a banner for a few seconds, with the sender’s name.
- **You see your own message only after it really sent.** If it fails, you get the reason (for example “blocked by database rules”) instead of a false “sent”.

### Match flow
- **The match clock resumes** if the host app is closed and reopened mid-game.
- **The “match finished” result is retried**, so a single dropped network request no longer leaves the game running after everyone is caught.

---

## Known limits

- **The host’s phone validates every tag.** The host app must stay open and online during the match.
- Everyone needs a connection; there is no offline mode.
- Outdoor GPS quality still matters for the map and reveals.
- This release was verified by replaying the app’s database calls against the rules and by static checks. Field-test it with at least three phones (one host, one seeker, one runner) before a real game.

---

## For builders

```bash
git clone https://github.com/dedsec-1337/MANHNT.git
cd MANHNT
# Open android/ in Android Studio → Sync → Run
```

GitHub Actions builds a debug APK on every push. Open the run under **Actions**, then download **manhunt-1.2.0-debug-apk** from the Artifacts section.

Rules check without the emulator: `cd firebase && npm install && npm run test:rules:offline`

Full technical history: [CHANGELOG.md](CHANGELOG.md)

---

## Credits

MANHNT is open source under the **Apache License 2.0**.  
Play outside. Stay off roads. Don’t be an idiot.
