const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shader_01";
const BOB_UID = "bob_shader_02";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validShaderPayload(userId, now = new Date(Date.now() - 2000)) {
  return {
    userId,
    title: "Vibrant RTX Horizon",
    prompt: "Vibrant Visuals with warm volumetric sunset god rays and RTX water reflections",
    presetCategory: "RTX_RAYTRACED",
    targetPlatform: "BEDROCK_VIBRANT",
    summary: "Ray-marched volumetric sun shafts, PBR metallic highlights, and Fresnel water caustics.",
    fragmentShader: "precision mediump float; varying vec2 vUv; void main() { gl_FragColor = vec4(1.0, 0.7, 0.3, 1.0); }",
    sunWarmth: 1.35,
    sunElevation: 0.38,
    fogDensity: 0.45,
    godRaysIntensity: 1.4,
    waterReflectivity: 1.15,
    giBounceIntensity: 1.25,
    bloomIntensity: 0.9,
    saturation: 1.3,
    exposure: 1.15,
    shadowSoftness: 0.85,
    pbrMetallicBoost: 1.2,
    skyZenithHex: "#142B5E",
    skyHorizonHex: "#FF9E5E",
    sunLightHex: "#FFD28A",
    ambientTintHex: "#3A548A",
    waterTintHex: "#1B6B82",
    fogTintHex: "#E8A97C",
    favorite: false,
    createdAt: now,
    updatedAt: now,
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

test("Unauthenticated user cannot read or write shaders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("shaders").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_1").set(
      validShaderPayload(ALICE_UID)
    )
  );
});

test("Authenticated owner: valid create, list, favorite toggle, and full update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_1");

  await assertSucceeds(ref.set(validShaderPayload(ALICE_UID, created)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("shaders").get());

  // Favorite toggle partial update
  await assertSucceeds(
    ref.update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Full shader parameter & code update preserving createdAt and userId
  await assertSucceeds(
    ref.update({
      title: "Vibrant RTX Horizon v2",
      sunWarmth: 1.5,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Security guards reject @DocumentId field, immutable createdAt edit, invalid hex, and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_1").set(
      validShaderPayload(ALICE_UID, created)
    )
  );

  // Reject cross-user read, list, update, delete
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shaders").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_1").update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_1").delete());

  // Reject ghost `id` field on create
  const withId = validShaderPayload(ALICE_UID, created);
  withId.id = "shader_2";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_2").set(withId)
  );

  // Reject immutable createdAt mutation on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid hex color
  const badHex = validShaderPayload(ALICE_UID, created);
  badHex.skyZenithHex = "NOT_HEX";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_3").set(badHex)
  );

  // Reject out-of-range numeric parameter
  const badRange = validShaderPayload(ALICE_UID, created);
  badRange.giBounceIntensity = 99.0;
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("shaders").doc("shader_4").set(badRange)
  );
});
