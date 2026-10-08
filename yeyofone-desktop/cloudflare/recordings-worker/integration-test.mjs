import { Miniflare, convertV4MiniflareOptions } from "miniflare";
import assert from "node:assert/strict";
const token = "local-test-" + "0".repeat(64);
const runtime = new Miniflare(
  convertV4MiniflareOptions({
    modules: true,
    scriptPath: new URL("./worker.js", import.meta.url).pathname,
    compatibilityDate: "2026-10-08",
    bindings: { RECORDINGS_TOKEN: token },
    r2Buckets: ["RECORDINGS"],
  }),
);
try {
  const url =
    "https://local.test/recordings/12345678-1234-1234-1234-123456789abc/call-123.wav";
  const wav = new Uint8Array(48);
  wav.set(new TextEncoder().encode("RIFF"));
  wav.set(new TextEncoder().encode("WAVE"), 8);
  const request = (method, body, headers = {}) =>
    runtime.dispatchFetch(url, {
      method,
      body,
      headers: { Authorization: `Bearer ${token}`, ...headers },
    });
  assert.equal((await runtime.dispatchFetch(url)).status, 401);
  assert.equal(
    (
      await runtime.dispatchFetch("https://local.test/health", {
        headers: { Authorization: `Bearer ${token}` },
      })
    ).status,
    200,
  );
  const upload = await request("PUT", wav, {
    "Content-Type": "audio/wav",
    "Content-Length": "48",
  });
  assert.equal(upload.status, 200);
  assert.equal((await upload.json()).size, 48);
  const bucket = await runtime.getR2Bucket("RECORDINGS");
  const key = "recordings/12345678-1234-1234-1234-123456789abc/call-123.wav";
  assert.equal((await bucket.head(key)).size, 48);
  const audio = await request("GET");
  assert.equal(audio.status, 200);
  assert.deepEqual(new Uint8Array(await audio.arrayBuffer()), wav);
  assert.equal((await request("DELETE")).status, 204);
  assert.equal(await bucket.head(key), null);
  assert.equal((await request("GET")).status, 404);
  assert.equal((await request("DELETE")).status, 204);
  console.log(
    "Local Worker/R2 integration passed: authentication, upload, stored bytes, playback download, deletion and retry.",
  );
} finally {
  await runtime.dispose();
}
