const MAX_AUDIO_BYTES = 100 * 1024 * 1024;
const reply = (status, message) =>
  new Response(message, { status, headers: { "Cache-Control": "no-store" } });

async function authorized(request, secret) {
  if (!secret || secret.length < 32) return false;
  const supplied = request.headers.get("Authorization") || "";
  const expected = `Bearer ${secret}`;
  // Compare fixed-length digests instead of branching on secret characters.
  const digest = async (value) =>
    new Uint8Array(
      await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)),
    );
  const [a, b] = await Promise.all([digest(supplied), digest(expected)]);
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}

export default {
  async fetch(request, env) {
    if (!(await authorized(request, env.RECORDINGS_TOKEN)))
      return reply(401, "Unauthorized");
    const url = new URL(request.url);
    if (url.search) return reply(400, "Unexpected query");
    try {
      if (url.pathname === "/health" && request.method === "GET") {
        await env.RECORDINGS.head("health-check");
        return reply(200, "Ready");
      }
      const match =
        /^\/recordings\/([a-f0-9-]{36})\/([a-zA-Z0-9-]{1,96})\.wav$/.exec(
          url.pathname,
        );
      if (!match) return reply(404, "Not found");
      const key = `recordings/${match[1]}/${match[2]}.wav`;
      if (request.method === "PUT") {
        const length = Number(request.headers.get("Content-Length"));
        if (
          !Number.isSafeInteger(length) ||
          length < 44 ||
          length > MAX_AUDIO_BYTES
        )
          return reply(413, "Invalid recording size");
        if (request.headers.get("Content-Type") !== "audio/wav")
          return reply(415, "Expected WAV audio");
        const object = await env.RECORDINGS.put(key, request.body, {
          httpMetadata: {
            contentType: "audio/wav",
            cacheControl: "private, no-store",
          },
        });
        if (!object || object.size !== length) {
          await env.RECORDINGS.delete(key);
          return reply(400, "Incomplete recording");
        }
        return Response.json(
          { size: object.size, etag: object.etag },
          { headers: { "Cache-Control": "no-store" } },
        );
      }
      if (request.method === "GET") {
        const object = await env.RECORDINGS.get(key);
        if (!object) return reply(404, "Recording not found");
        return new Response(object.body, {
          headers: {
            "Content-Type": "audio/wav",
            "Content-Length": String(object.size),
            "Cache-Control": "private, no-store",
            "X-Content-Type-Options": "nosniff",
          },
        });
      }
      if (request.method === "DELETE") {
        await env.RECORDINGS.delete(key);
        return reply(204, null);
      }
      return reply(405, "Method not allowed");
    } catch {
      return reply(503, "Recording storage temporarily unavailable");
    }
  },
};
