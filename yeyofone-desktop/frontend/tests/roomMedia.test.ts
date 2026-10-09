import { test, expect } from "bun:test";
import {
  LocalCaptures,
  mediaFailure,
  micLevel,
  nextTalking,
} from "../src/roomMedia";

function fakeStream() {
  const tracks = [
    {
      stopped: false,
      stop() {
        this.stopped = true;
      },
    },
  ];
  return { tracks, getTracks: () => tracks };
}

test("leaving the room stops screen, camera and mic captures", () => {
  const captures = new LocalCaptures<ReturnType<typeof fakeStream>>();
  const screen = fakeStream();
  const camera = fakeStream();
  const mic = fakeStream();
  captures.set("screen", screen);
  captures.set("camera", camera);
  captures.set("microphone", mic);
  captures.stopAll();
  for (const stream of [screen, camera, mic])
    expect(stream.tracks.every((t) => t.stopped)).toBe(true);
  expect(captures.get("screen")).toBeNull();
  expect(captures.get("camera")).toBeNull();
  expect(captures.get("microphone")).toBeNull();
});

test("replacing or stopping one capture leaves the others running", () => {
  const captures = new LocalCaptures<ReturnType<typeof fakeStream>>();
  const first = fakeStream();
  const second = fakeStream();
  const camera = fakeStream();
  captures.set("screen", first);
  captures.set("camera", camera);
  captures.set("screen", second);
  expect(first.tracks[0]!.stopped).toBe(true);
  expect(captures.get("screen")).toBe(second);
  captures.stop("screen");
  expect(second.tracks[0]!.stopped).toBe(true);
  expect(camera.tracks[0]!.stopped).toBe(false);
  expect(captures.get("camera")).toBe(camera);
});

test("device errors become readable messages", () => {
  const error = (name: string) => Object.assign(new Error(name), { name });
  expect(mediaFailure(error("NotAllowedError"), "camera")).toBe(
    "Camera access was not allowed.",
  );
  expect(mediaFailure(error("NotFoundError"), "microphone")).toBe(
    "No microphone was found.",
  );
  expect(mediaFailure(error("NotReadableError"), "camera")).toBe(
    "The camera couldn’t start.",
  );
  expect(mediaFailure("oops", "microphone")).toBe(
    "The microphone couldn’t start.",
  );
});

test("mic level is 0 for silence and grows with loudness", () => {
  expect(micLevel(new Uint8Array(512).fill(128))).toBe(0);
  expect(micLevel([])).toBe(0);
  const quiet = Uint8Array.from({ length: 512 }, (_, i) => (i % 2 ? 131 : 125));
  const loud = Uint8Array.from({ length: 512 }, (_, i) => (i % 2 ? 200 : 56));
  expect(micLevel(quiet)).toBeLessThan(0.04);
  expect(micLevel(loud)).toBeGreaterThan(0.5);
});

test("the speaking badge needs a clear rise and a clear fall", () => {
  expect(nextTalking(0.03, false)).toBe(false); // not loud enough to start
  expect(nextTalking(0.05, false)).toBe(true);
  expect(nextTalking(0.03, true)).toBe(true); // stays on between thresholds
  expect(nextTalking(0.01, true)).toBe(false);
});
