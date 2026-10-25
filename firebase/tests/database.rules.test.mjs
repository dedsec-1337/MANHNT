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
    catchCode: role === "HIDER" ? "1234" : "",
    latitude: 22.5726,
    longitude: 88.3639,
    isOutOfZone: false,
    isInSafeZone: false,
    lastUpdatedEpochMs: Date.now(),
    isInJail: false,
    immunityUntilEpochMs: 0,
    isStealthActive: false,
    stealthUntilEpochMs: 0,
    stealthCooldownUntilEpochMs: 0,
    disputesRemaining: 2,
    jailbreakHoldStartedAtEpochMs: 0
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
    await set(ref(context.database(), roomPath), {
      session: session(),
      players: {
        "host-uid": player("host-uid"),
        "seeker-uid": player("seeker-uid"),
        "hider-uid": player("hider-uid", "HIDER")
      }
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

test("an authenticated host can create a room with its UID as hostPlayerId", async () => {
  const db = testEnv.authenticatedContext("host-uid").database();
  await assertSucceeds(set(ref(db, roomPath), {
    session: session("host-uid"),
    players: { "host-uid": player("host-uid") }
  }));
});

test("a user cannot create a room claiming another user's host UID", async () => {
  const db = testEnv.authenticatedContext("attacker-uid").database();
  await assertFails(set(ref(db, roomPath), {
    session: session("host-uid"),
    players: { "host-uid": player("host-uid") }
  }));
});

test("a joining user can create only their own initial Seeker record", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("new-player-uid").database();
  await assertSucceeds(set(ref(db, `${roomPath}/players/new-player-uid`), player("new-player-uid")));
  await assertFails(set(ref(db, `${roomPath}/players/another-uid`), player("another-uid")));
  await assertFails(set(ref(db, `${roomPath}/players/new-hider-uid`), player("new-hider-uid", "HIDER")));
});

test("a player can update location and profile but cannot change protected gameplay state", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("hider-uid").database();
  await assertSucceeds(update(ref(db, `${roomPath}/players/hider-uid`), {
    latitude: 22.573,
    longitude: 88.364,
    lastUpdatedEpochMs: Date.now(),
    name: "Updated Hider"
  }));
  await assertFails(update(ref(db, `${roomPath}/players/hider-uid`), { isCaught: true }));
  await assertFails(update(ref(db, `${roomPath}/players/hider-uid`), { isInJail: true }));
  await assertFails(update(ref(db, `${roomPath}/players/hider-uid`), { catchCode: "9999" }));
  await assertFails(update(ref(db, `${roomPath}/players/hider-uid`), { role: "SEEKER" }));
  await assertFails(update(ref(db, `${roomPath}/players/hider-uid`), { disputesRemaining: 99 }));
});

test("a Hider can request a jailbreak only through their own player record", async () => {
  await seedRoom();
  const hiderDb = testEnv.authenticatedContext("hider-uid").database();
  await assertSucceeds(update(ref(hiderDb, `${roomPath}/players/hider-uid`), {
    jailbreakHoldStartedAtEpochMs: Date.now()
  }));
  const seekerDb = testEnv.authenticatedContext("seeker-uid").database();
  await assertFails(update(ref(seekerDb, `${roomPath}/players/hider-uid`), {
    jailbreakHoldStartedAtEpochMs: Date.now()
  }));
});

test("the host can update another player's role and session", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("host-uid").database();
  await assertSucceeds(set(ref(db, `${roomPath}/players/hider-uid`), {
    ...player("hider-uid", "SEEKER"),
    catchCode: ""
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
  await assertSucceeds(set(ref(db, `${roomPath}/players/hider-uid`), null));
});
