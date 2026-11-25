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

// ---------- Camera quality ----------
// The camera starts at 4K. "Auto" steps down when the network looks slow; the user can also
// pick a lower level by hand. Cameras that can't do a level give the closest one they can.
export const VIDEO_LEVELS = [
  { id: "2160p", label: "4K", width: 3840, height: 2160, frameRate: 30 },
  { id: "1080p", label: "1080p", width: 1920, height: 1080, frameRate: 30 },
  { id: "720p", label: "720p", width: 1280, height: 720, frameRate: 30 },
  { id: "480p", label: "480p", width: 854, height: 480, frameRate: 24 },
  { id: "360p", label: "360p", width: 640, height: 360, frameRate: 24 },
] as const;
export type VideoLevelId = (typeof VIDEO_LEVELS)[number]["id"];
export type VideoChoice = "auto" | VideoLevelId;

export function videoConstraints(id: VideoLevelId): MediaTrackConstraints {
  const level = VIDEO_LEVELS.find((item) => item.id === id) ?? VIDEO_LEVELS[0];
  return {
    width: { ideal: level.width },
    height: { ideal: level.height },
    frameRate: { ideal: level.frameRate },
  };
}

// What the browser's Network Information API reports, when the window has it.
export type NetworkHint = {
  downlink?: number; // Mbit/s (Chromium reports at most 10)
  effectiveType?: string;
  rtt?: number; // ms
  saveData?: boolean;
  offline?: boolean;
} | null;

// The best level the network supports. With no network information it is 4K, so the
// camera starts at full quality and the user lowers it if needed.
export function levelForNetwork(hint: NetworkHint): VideoLevelId {
  if (!hint) return "2160p";
  if (hint.offline) return "360p";
  let rank = 0; // index into VIDEO_LEVELS
  const { downlink, effectiveType } = hint;
  if (typeof downlink === "number" && downlink > 0) {
    rank = downlink >= 10 ? 0 : downlink >= 5 ? 1 : downlink >= 2.5 ? 2 : downlink >= 1.2 ? 3 : 4;
  } else if (effectiveType) {
    rank =
      effectiveType === "4g" ? 0 : effectiveType === "3g" ? 3 : 4;
  }
  if (typeof hint.rtt === "number" && hint.rtt > 400) rank += 1;
  if (hint.saveData) rank = Math.max(rank, 3);
  return VIDEO_LEVELS[Math.min(rank, VIDEO_LEVELS.length - 1)]!.id;
}

export function resolveLevel(choice: VideoChoice, hint: NetworkHint): VideoLevelId {
  return choice === "auto" ? levelForNetwork(hint) : choice;
}

// The label for what the camera is really delivering, e.g. 3840x2160 -> "4K".
export function levelLabelForHeight(height: number | undefined) {
  if (!height) return "";
  if (height >= 2000) return "4K";
  if (height >= 1000) return "1080p";
  if (height >= 700) return "720p";
  if (height >= 440) return "480p";
  return "360p";
}

export function readNetworkHint(): NetworkHint {
  if (typeof navigator === "undefined") return null;
  const connection = (
    navigator as Navigator & {
      connection?: {
        downlink?: number;
        effectiveType?: string;
        rtt?: number;
        saveData?: boolean;
      };
    }
  ).connection;
  if (navigator.onLine === false) return { offline: true };
  if (!connection) return null;
  return {
    downlink: connection.downlink,
    effectiveType: connection.effectiveType,
    rtt: connection.rtt,
    saveData: connection.saveData,
  };
}
