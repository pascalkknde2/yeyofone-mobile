import { describe, expect, test } from "bun:test";
import worker from "./worker.js";
const token = "a".repeat(64);
const origin = "https://recordings.example.workers.dev";
const path =
  "/recordings/12345678-1234-1234-1234-123456789abc/call-one-123.wav";
function setup() {
  const objects = new Map();
  const env = {
    RECORDINGS_TOKEN: token,
    RECORDINGS: {
      head: async (key) => objects.get(key) ?? null,
      put: async (key, body) => {
        const bytes = new Uint8Array(await new Response(body).arrayBuffer());
        const object = {
          size: bytes.byteLength,
          etag: "test-etag",
          body: bytes,
        };
        objects.set(key, object);
        return object;
      },
      get: async (key) => objects.get(key) ?? null,
      delete: async (key) => {
        objects.delete(key);
      },
    },
  };
  const request = (route, method = "GET", body, extra = {}) =>
    new Request(origin + route, {
      method,
      body,
      headers: { Authorization: `Bearer ${token}`, ...extra },
    });
  return { env, request, objects };
}
describe("private R2 recordings", () => {
  test("rejects unauthorized requests before touching the bucket", async () => {
    const { env, objects } = setup();
    expect(
      (
        await worker.fetch(
          new Request(origin + path, { method: "DELETE" }),
          env,
        )
      ).status,
    ).toBe(401);
    expect(objects.size).toBe(0);
    expect(
      (
        await worker.fetch(new Request(origin + "/health"), {
          ...env,
          RECORDINGS_TOKEN: "",
        })
      ).status,
    ).toBe(401);
  });
  test("uploads, reads and deletes a recording; deletion is idempotent", async () => {
    const { env, request } = setup();
    const bytes = new Uint8Array(44);
    bytes.set([82, 73, 70, 70]);
    const upload = await worker.fetch(
      request(path, "PUT", bytes, {
        "Content-Length": "44",
        "Content-Type": "audio/wav",
      }),
      env,
    );
    expect(upload.status).toBe(200);
    expect((await upload.json()).size).toBe(44);
    const download = await worker.fetch(request(path), env);
    expect(download.status).toBe(200);
    expect(new Uint8Array(await download.arrayBuffer())).toEqual(bytes);
    expect(download.headers.get("Cache-Control")).toBe("private, no-store");
    expect((await worker.fetch(request(path, "DELETE"), env)).status).toBe(204);
    expect((await worker.fetch(request(path, "DELETE"), env)).status).toBe(204);
    expect((await worker.fetch(request(path), env)).status).toBe(404);
  });
  test("rejects malformed keys, query strings, unsupported methods and invalid uploads", async () => {
    const { env, request, objects } = setup();
    expect(
      (await worker.fetch(request("/recordings/invalid/call.wav"), env)).status,
    ).toBe(404);
    expect((await worker.fetch(request(path + "?token=foo"), env)).status).toBe(
      400,
    );
    expect((await worker.fetch(request(path, "POST"), env)).status).toBe(405);
    expect(
      (
        await worker.fetch(
          request(path, "PUT", new Uint8Array(44), {
            "Content-Type": "audio/wav",
            "Content-Length": String(101 * 1024 * 1024),
          }),
          env,
        )
      ).status,
    ).toBe(413);
    expect(
      (
        await worker.fetch(
          request(path, "PUT", new Uint8Array(44), {
            "Content-Type": "text/plain",
            "Content-Length": "44",
          }),
          env,
        )
      ).status,
    ).toBe(415);
    expect(objects.size).toBe(0);
  });
  test("an incomplete upload is removed and storage errors stay generic", async () => {
    const { env, request, objects } = setup();
    expect(
      (
        await worker.fetch(
          request(path, "PUT", new Uint8Array(44), {
            "Content-Type": "audio/wav",
            "Content-Length": "45",
          }),
          env,
        )
      ).status,
    ).toBe(400);
    expect(objects.size).toBe(0);
    env.RECORDINGS.head = async () => {
      throw Error("sensitive internal information");
    };
    const response = await worker.fetch(request("/health"), env);
    expect(response.status).toBe(503);
    expect(await response.text()).not.toContain("sensitive");
  });
});
