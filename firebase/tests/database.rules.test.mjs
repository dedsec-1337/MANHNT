import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import { ref, set, get, update } from "firebase/database";

const projectId = "demo-manhnt-rules";
const rules = await readFile(new URL("../database.rules.example.json", import.meta.url), "utf8");
let testEnv;

const roomCode = "ABC123";
const roomPath = `rooms/${roomCode}`;

function session(hostPlayerId = "host-uid") {
  return {
    roomId: "room-id",
    joinCode: roomCode,
    roomName: "Rules Test",
    status: "LOBBY",
    hostPlayerId,
    zoneCenterLat: 22.5726,
    zoneCenterLng: 88.3639,
    jailLat: 22.5726,
    jailLng: 88.3639,
    remainingSeconds: 600,
    isPaused: false
  };
}

function player(id, role = "SEEKER") {
  return {
    id,
    name: id,
    avatarEmoji: "🏃",
    role,
    isCaught: false,
    isInSafeZone: false,
    isInJail: false,
    immunityUntilEpochMs: 0,
    isStealthActive: false,
    stealthUntilEpochMs: 0,
    stealthCooldownUntilEpochMs: 0,
    disputesRemaining: 2,
    jailbreakHoldStartedAtEpochMs: 0,
    stealthActivationRequestedAtEpochMs: 0
  };
}

function location() {
  return {
    latitude: 22.5726,
    longitude: 88.3639,
    isOutOfZone: false,
    lastUpdatedEpochMs: Date.now()
  };
}

function pendingCatchConfirm() {
  const now = Date.now();
  return {
    seekerId: "seeker-uid",
    targetHiderId: "hider-uid",
    seekerName: "seeker-uid",
    distanceMeters: 7.5,
    createdAtEpochMs: now,
    expiresAtEpochMs: now + 12000,
    status: "PENDING"
  };
}

async function seedRoom() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.database();
    await set(ref(db, roomPath), {
      session: session(),
      joinInfo: {
        roomName: "Rules Test",
        joinCode: roomCode,
        hostPlayerId: "host-uid"
      },
      publicPlayers: {
        "host-uid": player("host-uid"),
        "seeker-uid": player("seeker-uid"),
        "hider-uid": player("hider-uid", "HIDER")
      },
      // Simulate records created by the old app; these must not be readable anymore.
      players: {
        "hider-uid": {
          id: "hider-uid",
          name: "hider-uid",
          role: "HIDER",
          catchCode: "1234",
          latitude: 22.5726,
          longitude: 88.3639
        }
      }
    });
    await set(ref(db, `roomPrivate/${roomCode}/locations`), {
      "host-uid": location(),
      "seeker-uid": location(),
      "hider-uid": location()
    });
    await set(ref(db, `roomPrivate/${roomCode}/catchCodes`), {
      "host-uid": "",
      "seeker-uid": "",
      "hider-uid": "1234"
    });
  });
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    database: {
      host: "127.0.0.1",
      port: 9000,
      rules
    }
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearDatabase();
});

test("unauthenticated users cannot read rooms", async () => {
  await seedRoom();
  await assertFails(get(ref(testEnv.unauthenticatedContext().database(), roomPath)));
});

test("authenticated non-members cannot read the room tree but can look up the join session", async () => {
  await seedRoom();
  const visitorDb = testEnv.authenticatedContext("visitor-uid").database();
  await assertFails(get(ref(visitorDb, roomPath)));
  const memberDb = testEnv.authenticatedContext("seeker-uid").database();
  await assertFails(get(ref(memberDb, roomPath)));
  await assertFails(get(ref(memberDb, `${roomPath}/players`)));
  await assertFails(get(ref(visitorDb, `${roomPath}/players`)));
  await assertFails(get(ref(visitorDb, `${roomPath}/session`)));
  const joinInfoSnapshot = await assertSucceeds(get(ref(visitorDb, `${roomPath}/joinInfo`)));
  assert.equal(joinInfoSnapshot.val().roomName, "Rules Test");
  assert.equal(joinInfoSnapshot.child("zoneCenterLat").val(), null);
  const sessionSnapshot = await assertSucceeds(get(ref(memberDb, `${roomPath}/session`)));
  assert.equal(sessionSnapshot.child("zoneCenterLat").val(), 22.5726);
  await assertSucceeds(get(ref(memberDb, `${roomPath}/publicPlayers`)));
  await assertFails(get(ref(visitorDb, `${roomPath}/publicPlayers`)));
});

test("only room members can read private player locations", async () => {
  await seedRoom();
  const memberDb = testEnv.authenticatedContext("seeker-uid").database();
  const visitorDb = testEnv.authenticatedContext("visitor-uid").database();
  const locations = await assertSucceeds(get(ref(memberDb, `roomPrivate/${roomCode}/locations`)));
  assert.equal(locations.child("hider-uid/latitude").val(), 22.5726);
  const publicPlayers = await assertSucceeds(get(ref(memberDb, `${roomPath}/publicPlayers`)));
  assert.equal(publicPlayers.child("hider-uid/catchCode").val(), null);
  assert.equal(publicPlayers.child("hider-uid/latitude").val(), null);
  await assertFails(get(ref(visitorDb, `roomPrivate/${roomCode}/locations`)));
  await assertSucceeds(set(ref(memberDb, `roomPrivate/${roomCode}/locations/seeker-uid`), location()));
  await assertFails(set(ref(memberDb, `roomPrivate/${roomCode}/locations/hider-uid`), location()));
});

test("catch codes are private to their owner and the host", async () => {
  await seedRoom();
  const hostDb = testEnv.authenticatedContext("host-uid").database();
  const hiderDb = testEnv.authenticatedContext("hider-uid").database();
  const seekerDb = testEnv.authenticatedContext("seeker-uid").database();
  const visitorDb = testEnv.authenticatedContext("visitor-uid").database();
  const ownCode = await assertSucceeds(get(ref(hiderDb, `roomPrivate/${roomCode}/catchCodes/hider-uid`)));
  assert.equal(ownCode.val(), "1234");
  await assertFails(get(ref(seekerDb, `roomPrivate/${roomCode}/catchCodes/hider-uid`)));
  await assertFails(get(ref(visitorDb, `roomPrivate/${roomCode}/catchCodes`)));
  const allCodes = await assertSucceeds(get(ref(hostDb, `roomPrivate/${roomCode}/catchCodes`)));
  assert.equal(allCodes.child("hider-uid").val(), "1234");
  await assertFails(set(ref(hiderDb, `roomPrivate/${roomCode}/catchCodes/hider-uid`), "9999"));
  await assertSucceeds(set(ref(hostDb, `roomPrivate/${roomCode}/catchCodes/hider-uid`), "5678"));
});

test("an authenticated host can create a room with its UID as hostPlayerId", async () => {
  const db = testEnv.authenticatedContext("host-uid").database();
  await assertSucceeds(set(ref(db, roomPath), {
    session: session("host-uid"),
    publicPlayers: { "host-uid": player("host-uid") }
  }));
});

test("a user cannot create a room claiming another user's host UID", async () => {
  const db = testEnv.authenticatedContext("attacker-uid").database();
  await assertFails(set(ref(db, roomPath), {
    session: session("host-uid"),
    publicPlayers: { "host-uid": player("host-uid") }
  }));
});

test("a joining user can create only their own initial Seeker record", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("new-player-uid").database();
  await assertSucceeds(set(ref(db, `${roomPath}/publicPlayers/new-player-uid`), player("new-player-uid")));
  await assertFails(set(ref(db, `${roomPath}/publicPlayers/another-uid`), player("another-uid")));
  await assertFails(set(ref(db, `${roomPath}/publicPlayers/new-hider-uid`), player("new-hider-uid", "HIDER")));
});

test("a player can update profile but cannot put private location or protected state in public player data", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("hider-uid").database();
  await assertSucceeds(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { name: "Updated Hider" }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { latitude: 22.573, longitude: 88.364 }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { isCaught: true }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { isInJail: true }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { catchCode: "9999" }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { role: "SEEKER" }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { disputesRemaining: 99 }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { isInSafeZone: true }));
  await assertFails(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { isStealthActive: true }));
  await assertSucceeds(update(ref(db, `${roomPath}/publicPlayers/hider-uid`), { stealthActivationRequestedAtEpochMs: Date.now() }));
});

test("a Hider can request a jailbreak only through their own player record", async () => {
  await seedRoom();
  const hiderDb = testEnv.authenticatedContext("hider-uid").database();
  await assertSucceeds(update(ref(hiderDb, `${roomPath}/publicPlayers/hider-uid`), {
    jailbreakHoldStartedAtEpochMs: Date.now()
  }));
  const seekerDb = testEnv.authenticatedContext("seeker-uid").database();
  await assertFails(update(ref(seekerDb, `${roomPath}/publicPlayers/hider-uid`), {
    jailbreakHoldStartedAtEpochMs: Date.now()
  }));
});

test("the host can update another player's role and session", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("host-uid").database();
  await assertSucceeds(set(ref(db, `${roomPath}/publicPlayers/hider-uid`), {
    ...player("hider-uid", "SEEKER")
  }));
  await assertSucceeds(update(ref(db, `${roomPath}/session`), { remainingSeconds: 540 }));
});

test("non-host players cannot update the session", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("seeker-uid").database();
  await assertFails(update(ref(db, `${roomPath}/session`), { remainingSeconds: 1 }));
  await assertFails(update(ref(db, `${roomPath}/session`), { hostPlayerId: "seeker-uid" }));
});

test("only a live authenticated Seeker may submit a catch attempt for a Hider", async () => {
  await seedRoom();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await update(ref(context.database(), `${roomPath}/session`), { status: "RUNNING", isPaused: false });
  });
  const seekerDb = testEnv.authenticatedContext("seeker-uid").database();
  await assertSucceeds(set(ref(seekerDb, `${roomPath}/lastCatchAttempt`), {
    seekerId: "seeker-uid",
    targetHiderId: "hider-uid",
    enteredCode: "1234",
    timestamp: Date.now()
  }));

  const hiderDb = testEnv.authenticatedContext("hider-uid").database();
  await assertFails(set(ref(hiderDb, `${roomPath}/lastCatchAttempt`), {
    seekerId: "seeker-uid",
    targetHiderId: "hider-uid",
    enteredCode: "1234",
    timestamp: Date.now()
  }));
  await assertFails(set(ref(seekerDb, `${roomPath}/lastCatchAttempt`), {
    seekerId: "seeker-uid",
    targetHiderId: "seeker-uid",
    enteredCode: "1234",
    timestamp: Date.now()
  }));
  await assertFails(set(ref(seekerDb, `${roomPath}/lastCatchAttempt`), {
    seekerId: "seeker-uid",
    targetHiderId: "hider-uid",
    enteredCode: "not-a-code",
    timestamp: Date.now()
  }));
});

test("host can create a pending catch confirmation and only its target can answer it", async () => {
  await seedRoom();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await update(ref(context.database(), `${roomPath}/session`), { status: "RUNNING", isPaused: false });
  });
  const hostDb = testEnv.authenticatedContext("host-uid").database();
  await assertSucceeds(set(ref(hostDb, `${roomPath}/pendingCatchConfirm`), pendingCatchConfirm()));

  const targetDb = testEnv.authenticatedContext("hider-uid").database();
  await assertSucceeds(get(ref(targetDb, `${roomPath}/pendingCatchConfirm`)));
  await assertFails(get(ref(testEnv.authenticatedContext("seeker-uid").database(), `${roomPath}/pendingCatchConfirm`)));
  await assertSucceeds(update(ref(targetDb, `${roomPath}/pendingCatchConfirm`), { status: "ACCEPTED" }));

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `${roomPath}/pendingCatchConfirm`), pendingCatchConfirm());
  });
  const seekerDb = testEnv.authenticatedContext("seeker-uid").database();
  await assertFails(update(ref(seekerDb, `${roomPath}/pendingCatchConfirm`), { status: "ACCEPTED" }));
  const otherDb = testEnv.authenticatedContext("attacker-uid").database();
  await assertFails(update(ref(otherDb, `${roomPath}/pendingCatchConfirm`), { status: "ACCEPTED" }));
  await assertSucceeds(set(ref(hostDb, `${roomPath}/pendingCatchConfirm`), null));
});

test("a player may delete their own record when leaving a room", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("hider-uid").database();
  await assertSucceeds(set(ref(db, `${roomPath}/publicPlayers/hider-uid`), null));
});
