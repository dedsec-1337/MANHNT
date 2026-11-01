// Offline replay of the app's real Firebase calls against database.rules.example.json.
// Needs only `npm install` (targaryen); no emulator, no network. Run: npm run test:rules:offline
// Pass a different rules file as the first argument to test what is published in the console.
const targaryen = require('targaryen');
const fs = require('fs');
const path = require('path');
const rulesPath = process.argv[2] || path.join(__dirname, '..', 'database.rules.example.json');
const rules = JSON.parse(fs.readFileSync(rulesPath, 'utf8'));
const CODE = 'ABCDEF';
const H = 'host-uid', S = 'seeker-uid', D = 'hider-uid';
let NOW = Date.now();
let db = targaryen.database(rules, {}, NOW);
let pass = 0, fail = 0;
function show(ok, label, extra) {
  console.log((ok ? 'PASS  ' : 'FAIL  ') + label + (extra ? '  -> ' + extra : ''));
  ok ? pass++ : fail++;
}
function who(uid) { return db.as(uid ? { uid } : null).with({ now: NOW, debug: true }); }
function put(uid, path, value, label, expect = true) {
  const r = who(uid).write(path, value);
  const ok = r.allowed === expect;
  show(ok, `${label} [${uid}] PUT ${path}`, ok ? '' : `allowed=${r.allowed} ${r.info ? String(r.info).split('\n').slice(-6).join(' | ') : ''}`);
  if (r.allowed) db = r.newDatabase.with({ now: NOW });
  return r.allowed;
}
function patch(uid, path, value, label, expect = true) {
  const r = who(uid).update(path, value);
  const ok = r.allowed === expect;
  show(ok, `${label} [${uid}] PATCH ${path}`, ok ? '' : `allowed=${r.allowed} ${r.info ? String(r.info).split('\n').slice(-6).join(' | ') : ''}`);
  if (r.allowed) db = r.newDatabase.with({ now: NOW });
  return r.allowed;
}
function read(uid, path, label, expect = true) {
  const r = who(uid).read(path);
  const ok = r.allowed === expect;
  show(ok, `${label} [${uid}] GET ${path}`, ok ? '' : `allowed=${r.allowed}`);
  return r.allowed;
}
// ---- payload builders: exactly the fields Gson emits for the Kotlin data classes ----
const player = (id, role = 'SEEKER', extra = {}) => ({
  id, name: 'Agent ' + id.slice(0, 3), avatarEmoji: '🏃', role, isCaught: false,
  isInSafeZone: false, isInJail: false, immunityUntilEpochMs: 0, isStealthActive: false,
  stealthUntilEpochMs: 0, stealthActivationRequestedAtEpochMs: 0, stealthCooldownUntilEpochMs: 0,
  disputesRemaining: 2, jailbreakHoldStartedAtEpochMs: 0, ...extra
});
const session = (extra = {}) => ({
  roomId: 'rid', roomName: 'Test', joinCode: CODE, hostPlayerId: H, status: 'LOBBY',
  zoneCenterLat: 22.57, zoneCenterLng: 88.36, zoneRadiusMeters: 1000, durationMinutes: 30,
  remainingSeconds: 1800, isPaused: false, totalCatches: 0, zoneSizePreset: '1000m',
  isUnlimitedZone: false, headStartMinutes: 0, headStartRemainingSeconds: 0,
  isShrinkingZoneEnabled: false, currentDynamicZoneRadiusMeters: 1000, autoPingIntervalMinutes: 0,
  isAutoPingActive: false, autoPingRemainingSeconds: 0, jailEnabled: true, jailLat: 22.57,
  jailLng: 88.36, jailRadiusMeters: 25, isJailbreakInProgress: false, jailbreakProgressSeconds: 0, ...extra
});
const loc = () => ({ latitude: 22.57, longitude: 88.36, isOutOfZone: false, lastUpdatedEpochMs: NOW });

console.log('== ' + rulesPath);
console.log('-- room creation');
put(H, `rooms/${CODE}`, { session: session(), publicPlayers: { [H]: player(H) } }, 'host creates room');
put(H, `rooms/${CODE}/joinInfo`, { roomName: 'Test', joinCode: CODE, hostPlayerId: H }, 'host writes joinInfo');
put(H, `roomPrivate/${CODE}/locations/${H}`, loc(), 'host writes location');
console.log('-- joining');
for (const u of [S, D]) {
  read(u, `rooms/${CODE}/joinInfo`, 'lookup joinInfo');
  read(u, `rooms/${CODE}/publicPlayers/${u}`, 'pre-join own record (expected denied)', false);
  put(u, `rooms/${CODE}/publicPlayers/${u}`, player(u), 'join as seeker');
  put(u, `roomPrivate/${CODE}/locations/${u}`, loc(), 'write own location');
}
console.log('-- host assigns roles');
put(H, `roomPrivate/${CODE}/catchCodes/${D}`, '0427', 'host sets hider code');
put(H, `rooms/${CODE}/publicPlayers/${D}`, player(D, 'HIDER'), 'host marks D as HIDER');
put(H, `roomPrivate/${CODE}/catchCodes/${S}`, '', 'host sets seeker code blank');
console.log('-- polling reads');
for (const u of [S, D]) {
  read(u, `rooms/${CODE}/session`, 'session');
  read(u, `rooms/${CODE}/publicPlayers`, 'players');
  read(u, `roomPrivate/${CODE}/locations`, 'locations');
  read(u, `roomPrivate/${CODE}/catchCodes/${u}`, 'own catch code');
  read(u, `rooms/${CODE}/catchAcks/${u}`, 'own ack');
  read(u, `roomSignals/${CODE}`, 'signals');
  read(u, `rooms/${CODE}/lastCatchAttempt`, 'lastCatchAttempt (non-host denied)', false);
  read(u, `rooms/${CODE}/pendingCatchConfirm`, 'pendingCatchConfirm (denied w/o node)', false);
}
read(H, `roomPrivate/${CODE}/catchCodes`, 'host reads all catch codes');
read(H, `rooms/${CODE}/lastCatchAttempt`, 'host reads lastCatchAttempt');
console.log('-- before start: catch + signal must be refused');
put(S, `rooms/${CODE}/lastCatchAttempt`, { seekerId: S, targetHiderId: D, enteredCode: '0427', timestamp: NOW }, 'catch in LOBBY', false);
put(S, `roomSignals/${CODE}/${S}`, { code: 'P_CATCH_ME', timestampEpochMs: NOW }, 'signal in LOBBY', false);
console.log('-- host starts game');
patch(H, `rooms/${CODE}/session`, {
  status: 'RUNNING', isPaused: false, remainingSeconds: 1800, headStartRemainingSeconds: 0,
  currentDynamicZoneRadiusMeters: 1000, isAutoPingActive: false, autoPingRemainingSeconds: 0
}, 'start game');
for (const p of [player(H), player(S), player(D, 'HIDER')]) put(H, `rooms/${CODE}/publicPlayers/${p.id}`, p, 'host rewrites player flags');
console.log('-- countdown tick');
patch(H, `rooms/${CODE}/session`, { remainingSeconds: 1797, headStartRemainingSeconds: 0, currentDynamicZoneRadiusMeters: 1000, isAutoPingActive: false, autoPingRemainingSeconds: 0 }, 'tick');
console.log('-- quick signals');
put(S, `roomSignals/${CODE}/${S}`, { code: 'P_CATCH_ME', timestampEpochMs: NOW }, 'seeker sends Catch me');
put(D, `roomSignals/${CODE}/${D}`, { code: 'P_CATCH_ME', timestampEpochMs: NOW }, 'hider sends Catch me');
put(D, `roomSignals/${CODE}/${D}`, { code: 'P_CATCH_ME', timestampEpochMs: NOW + 3000 }, 'sender clock 3s ahead');
put(D, `roomSignals/${CODE}/${D}`, { code: 'P_CATCH_ME', timestampEpochMs: NOW + 8000 }, 'sender clock 8s ahead (expected denied)', false);
put(D, `roomSignals/${CODE}/${D}`, { code: 'P_CATCH_ME', timestampEpochMs: NOW - 20000 }, 'sender clock 20s behind');
read(S, `roomSignals/${CODE}`, 'seeker reads signals');
console.log('-- catch flow');
put(S, `rooms/${CODE}/lastCatchAttempt`, { seekerId: S, targetHiderId: D, enteredCode: '1111', timestamp: NOW }, 'catch wrong code (expected denied)', false);
put(S, `rooms/${CODE}/lastCatchAttempt`, { seekerId: S, targetHiderId: D, enteredCode: '0427', timestamp: NOW }, 'catch RIGHT code');
put(H, `rooms/${CODE}/publicPlayers/${D}`, player(D, 'HIDER', { isCaught: true, isInJail: true }), 'host marks D caught');
put(H, `rooms/${CODE}/catchAcks/${S}`, { seekerId: S, targetHiderId: D, targetName: 'Agent hid', accepted: true, timestamp: NOW }, 'host writes ack');
read(S, `rooms/${CODE}/catchAcks/${S}`, 'seeker reads ack');

console.log('-- NEW: server-timestamp signals, PATCH catch, rejoin');
put(S, `roomSignals/${CODE}/${S}`, { code: 'P_CATCH_ME', timestampEpochMs: { '.sv': 'timestamp' } }, 'signal with .sv server timestamp');
put(D, `roomSignals/${CODE}/${D}`, { code: 'P_GG', timestampEpochMs: { '.sv': 'timestamp' } }, 'signal with .sv server timestamp (hider)');
patch(H, `rooms/${CODE}/publicPlayers/${D}`, { isCaught: false, isInJail: false, isStealthActive: false, stealthUntilEpochMs: 0, stealthActivationRequestedAtEpochMs: 0, stealthCooldownUntilEpochMs: 0, immunityUntilEpochMs: 0, jailbreakHoldStartedAtEpochMs: 0, disputesRemaining: 2 }, 'host PATCH flag reset');
put(S, `rooms/${CODE}/lastCatchAttempt`, { seekerId: S, targetHiderId: D, enteredCode: '0427', timestamp: NOW + 1 }, 'second catch attempt (right code)');
patch(H, `rooms/${CODE}/publicPlayers/${D}`, { isCaught: true, isInJail: true, isStealthActive: false }, 'host PATCH marks caught');
// rejoin by existing members (server record unchanged)
put(S, `rooms/${CODE}/publicPlayers/${S}`, player(S), 'seeker rejoin PUT same record');
put(D, `rooms/${CODE}/publicPlayers/${D}`, player(D, 'HIDER', { isCaught: true, isInJail: true }), 'hider rejoin PUT same record');
// hider PATCHes own request fields
patch(D, `rooms/${CODE}/publicPlayers/${D}`, { stealthActivationRequestedAtEpochMs: NOW }, 'hider stealth request PATCH');
patch(D, `rooms/${CODE}/publicPlayers/${D}`, { jailbreakHoldStartedAtEpochMs: NOW }, 'hider jailbreak request PATCH');
patch(D, `rooms/${CODE}/publicPlayers/${D}`, { isCaught: false }, 'hider tries to un-catch self (expected denied)', false);
patch(S, `rooms/${CODE}/publicPlayers/${D}`, { isCaught: true }, 'seeker tries to catch directly (expected denied)', false);
put(S, `rooms/${CODE}/catchAcks/${S}`, { seekerId: S, targetHiderId: D, accepted: true, timestamp: NOW }, 'seeker forges own ack (expected denied)', false);

console.log('-- end game');
patch(H, `rooms/${CODE}/session`, { status: 'FINISHED', winnerRole: 'SEEKER', totalCatches: 1 }, 'end game');
console.log('-- leave');
put(S, `roomSignals/${CODE}/${S}`, null, 'seeker deletes signal');
put(S, `rooms/${CODE}/publicPlayers/${S}`, null, 'seeker deletes self');
console.log(`\n${pass} pass, ${fail} fail`);
process.exit(fail === 0 ? 0 : 1);
