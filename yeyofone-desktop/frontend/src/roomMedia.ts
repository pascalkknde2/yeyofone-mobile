// Local captures for the Team room. They are shown only on this device; nothing is sent.

type Track = { stop(): void };
type Stream = { getTracks(): Track[] };
export type CaptureKind = "screen" | "camera" | "microphone";

export const stopTracks = (stream: Stream | null) =>
  stream?.getTracks().forEach((track) => track.stop());

// Holds at most one stream per kind and stops a stream as soon as it is
// replaced or removed, so no camera, mic or screen capture is left running.
export class LocalCaptures<S extends Stream = MediaStream> {
  private streams = new Map<CaptureKind, S>();
  get(kind: CaptureKind) {
    return this.streams.get(kind) ?? null;
  }
  set(kind: CaptureKind, stream: S) {
    this.stop(kind);
    this.streams.set(kind, stream);
  }
  stop(kind: CaptureKind) {
    stopTracks(this.get(kind));
    this.streams.delete(kind);
  }
  stopAll() {
    for (const kind of [...this.streams.keys()]) this.stop(kind);
  }
}

export function mediaFailure(error: unknown, device: "camera" | "microphone") {
  const name =
    error && typeof error === "object" && "name" in error ? error.name : "";
  if (name === "NotAllowedError")
    return device === "camera"
      ? "Camera access was not allowed."
      : "Microphone access was not allowed.";
  if (name === "NotFoundError")
    return device === "camera"
      ? "No camera was found."
      : "No microphone was found.";
  return device === "camera"
    ? "The camera couldn’t start."
    : "The microphone couldn’t start.";
}

// Root-mean-square level (0–1) of 8-bit audio samples centred on 128.
export function micLevel(samples: ArrayLike<number>) {
  if (!samples.length) return 0;
  let sum = 0;
  for (let i = 0; i < samples.length; i++)
    sum += ((samples[i]! - 128) / 128) ** 2;
  return Math.sqrt(sum / samples.length);
}

// Two thresholds so the speaking badge doesn't flicker around a single value:
// it turns on above 0.04 and only turns off again below 0.02.
export function nextTalking(level: number, wasTalking: boolean) {
  return wasTalking ? level > 0.02 : level > 0.04;
}
