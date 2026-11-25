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

import {
  levelForNetwork,
  levelLabelForHeight,
  resolveLevel,
  videoConstraints,
} from "../src/roomMedia";

test("the camera starts at 4K when nothing is known about the network", () => {
  expect(levelForNetwork(null)).toBe("2160p");
  expect(resolveLevel("auto", null)).toBe("2160p");
  expect(videoConstraints("2160p")).toMatchObject({
    width: { ideal: 3840 },
    height: { ideal: 2160 },
  });
});

test("auto steps the camera down as the network gets slower", () => {
  expect(levelForNetwork({ downlink: 10 })).toBe("2160p");
  expect(levelForNetwork({ downlink: 6 })).toBe("1080p");
  expect(levelForNetwork({ downlink: 3 })).toBe("720p");
  expect(levelForNetwork({ downlink: 1.5 })).toBe("480p");
  expect(levelForNetwork({ downlink: 0.4 })).toBe("360p");
  expect(levelForNetwork({ effectiveType: "3g" })).toBe("480p");
  expect(levelForNetwork({ downlink: 10, rtt: 600 })).toBe("1080p");
  expect(levelForNetwork({ downlink: 10, saveData: true })).toBe("480p");
  expect(levelForNetwork({ offline: true })).toBe("360p");
});

test("a level picked by hand is used as it is", () => {
  expect(resolveLevel("720p", { downlink: 10 })).toBe("720p");
  expect(resolveLevel("2160p", { downlink: 0.4 })).toBe("2160p");
});

test("the delivered height is named as a quality level", () => {
  expect(levelLabelForHeight(2160)).toBe("4K");
  expect(levelLabelForHeight(1080)).toBe("1080p");
  expect(levelLabelForHeight(720)).toBe("720p");
  expect(levelLabelForHeight(480)).toBe("480p");
  expect(levelLabelForHeight(undefined)).toBe("");
});
