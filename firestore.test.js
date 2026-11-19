const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_viewer_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function validUserProfile(uid) {
  const ts = pastDate();
  return {
    userId: uid,
    displayName: "Aarav Studio",
    channelName: "Aarav Live",
    email: "aarav@example.com",
    bio: "Live gaming and tech streams",
    preferredLanguage: "en",
    darkModePreference: "dark",
    accessTier: "standard",
    notificationsEnabled: true,
    backgroundPlaybackEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validStream(hostUid, visibility = "public") {
  const ts = pastDate();
  return {
    hostUserId: hostUid,
    hostName: "Aarav Live",
    title: "Evening Tech Talk Live",
    category: "Technology",
    visibility,
    resolution: "720p",
    sourceType: "camera",
    status: "live",
    lowLatencyWs: true,
    viewerCount: 1,
    peakViewers: 1,
    totalDonationsInr: 0,
    durationSeconds: 0,
    bitrateKbps: 3500,
    latencyMs: 120,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("streams").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("streams").doc("stream_1").set(validStream(ALICE_UID)));
});

test("User profile: owner CRUD succeeds, cross-user PII read/write & shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Partial update of mutable fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "hi",
      accessTier: "pro",
      activeKeyCode: "PULSE-PRO-2026",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: pastDate(500) }));

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Immutable createdAt cannot change
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );
});

test("Private streamConfigs, activationKeys, notifications, and feedback are strictly owner-isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  // StreamConfig
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("streamConfigs").doc("default").set({
      userId: ALICE_UID,
      label: "YouTube Primary",
      rtmpUrl: "rtmps://a.rtmps.youtube.com/live2",
      streamKey: "xxxx-yyyy-zzzz-1234",
      wsRelayUrl: "wss://ws.postman-echo.com/raw",
      resolution: "1080p",
      lowLatencyEnabled: true,
      audioBitrateKbps: 128,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("streamConfigs").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("streamConfigs").get());

  // ActivationKeys
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activationKeys").doc("key_1").set({
      userId: ALICE_UID,
      keyCode: "PULSE-PRO-2026",
      tier: "pro",
      status: "active",
      label: "Studio Pro License",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activationKeys").doc("key_1").update({
      status: "revoked",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("activationKeys").get());

  // Notifications
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      title: "New Donation!",
      body: "Bob donated INR 500",
      category: "donation",
      readFlag: false,
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      readFlag: true,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").get());

  // Feedback (immutable after create)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedback").doc("fb_1").set({
      userId: ALICE_UID,
      category: "streaming",
      rating: 5,
      comment: "Ultra smooth 1080p streaming with zero lag!",
      appVersion: "1.0",
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedback").doc("fb_1").update({
      rating: 1,
    })
  );
});

test("Streams: filtered public & creator queries succeed, unfiltered fails, terminal ended state locks updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("streams").doc("stream_live_1").set(validStream(ALICE_UID, "public")));
  await assertSucceeds(aliceDb.collection("streams").doc("stream_priv_1").set(validStream(ALICE_UID, "private")));

  // Filtered queries aligned with Kotlin repositories
  await assertSucceeds(bobDb.collection("streams").where("visibility", "==", "public").get());
  await assertSucceeds(aliceDb.collection("streams").where("hostUserId", "==", ALICE_UID).get());

  // Unfiltered list query rejected
  await assertFails(bobDb.collection("streams").get());

  // Private stream denied to Bob
  await assertFails(bobDb.collection("streams").doc("stream_priv_1").get());

  // Bob can increment donation tally on public live stream
  await assertSucceeds(
    bobDb.collection("streams").doc("stream_live_1").update({
      totalDonationsInr: 500,
      viewerCount: 2,
      peakViewers: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot hijack stream title or end Alice's stream
  await assertFails(
    bobDb.collection("streams").doc("stream_live_1").update({
      title: "Hijacked Stream",
      updatedAt: pastDate(500),
    })
  );

  // Alice ends stream -> terminal state lock prevents further updates
  await assertSucceeds(
    aliceDb.collection("streams").doc("stream_live_1").update({
      status: "ended",
      durationSeconds: 3600,
      updatedAt: pastDate(800),
    })
  );
  await assertFails(
    aliceDb.collection("streams").doc("stream_live_1").update({
      title: "Edit after ended",
      updatedAt: pastDate(400),
    })
  );
});

test("Stream comments & donations: real-time chat/donation creation, parent existence check, and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(aliceDb.collection("streams").doc("stream_chat_1").set(validStream(ALICE_UID, "public")));

  // Bob sends regular chat message
  await assertSucceeds(
    bobDb.collection("streams").doc("stream_chat_1").collection("comments").doc("c_1").set({
      streamId: "stream_chat_1",
      authorId: BOB_UID,
      authorName: "Bob Viewer",
      visibility: "public",
      message: "Awesome 1080p stream quality!",
      messageType: "chat",
      donationAmount: 0,
      currency: "INR",
      createdAt: ts,
    })
  );

  // Bob sends SuperChat donation
  await assertSucceeds(
    bobDb.collection("streams").doc("stream_chat_1").collection("comments").doc("c_2").set({
      streamId: "stream_chat_1",
      authorId: BOB_UID,
      authorName: "Bob Viewer",
      visibility: "public",
      message: "Keep going! Great content!",
      messageType: "donation",
      donationAmount: 500,
      currency: "INR",
      createdAt: ts,
    })
  );

  // Filtered list query succeeds
  await assertSucceeds(
    aliceDb.collection("streams").doc("stream_chat_1").collection("comments").where("visibility", "==", "public").get()
  );

  // Donation with amount 0 fails validation
  await assertFails(
    bobDb.collection("streams").doc("stream_chat_1").collection("comments").doc("c_bad").set({
      streamId: "stream_chat_1",
      authorId: BOB_UID,
      authorName: "Bob Viewer",
      visibility: "public",
      message: "Fake donation",
      messageType: "donation",
      donationAmount: 0,
      currency: "INR",
      createdAt: ts,
    })
  );

  // Orphaned comment on non-existent stream fails exists() guard
  await assertFails(
    bobDb.collection("streams").doc("missing_stream").collection("comments").doc("c_orphan").set({
      streamId: "missing_stream",
      authorId: BOB_UID,
      authorName: "Bob Viewer",
      visibility: "public",
      message: "Orphan message",
      messageType: "chat",
      donationAmount: 0,
      currency: "INR",
      createdAt: ts,
    })
  );

  // Comments are immutable
  await assertFails(
    bobDb.collection("streams").doc("stream_chat_1").collection("comments").doc("c_1").update({
      message: "Edited message",
    })
  );
});
