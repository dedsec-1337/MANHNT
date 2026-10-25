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
    joinCode: roomCode,
    roomName: "Rules Test",
    status: "LOBBY",
    hostPlayerId,
    remainingSeconds: 600
  };
}

function player(id, role = "SEEKER") {
  return { id, name: id, role };
}

async function seedRoom() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), roomPath), {
      session: session(),
      players: {
        "host-uid": player("host-uid"),
        "seeker-uid": player("seeker-uid"),
        "hider-uid": { ...player("hider-uid", "HIDER"), catchCode: "1234" }
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

test("an authenticated player can create and update only their own player record", async () => {
  await seedRoom();
  const ownDb = testEnv.authenticatedContext("new-player-uid").database();
  await assertSucceeds(set(ref(ownDb, `${roomPath}/players/new-player-uid`), player("new-player-uid", "HIDER")));
  await assertFails(set(ref(ownDb, `${roomPath}/players/hider-uid`), player("hider-uid", "SEEKER")));
});

test("the host can update another player's role and the session", async () => {
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
});

test("only the authenticated seeker may submit a catch attempt for another player", async () => {
  await seedRoom();
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
});

test("a player may delete their own record when leaving a room", async () => {
  await seedRoom();
  const db = testEnv.authenticatedContext("hider-uid").database();
  await assertSucceeds(set(ref(db, `${roomPath}/players/hider-uid`), null));
});
