import { describe, expect, test } from "bun:test";
import { recordingPeaks } from "../src/recordingPeaks";

describe("recording waveform", () => {
  test("preserves silence and locates sound in its time interval", () => {
    expect(
      recordingPeaks([new Float32Array([0, 0, 0.5, -0.8, 0, 0, 0.2, 0])], 4),
    ).toEqual([0, 1, 0, 0.25]);
  });
  test("includes sound from either stereo channel", () => {
    expect(
      recordingPeaks([new Float32Array([0, 0]), new Float32Array([0, -1])], 2),
    ).toEqual([0, 1]);
  });
  test("handles silent, short and empty recordings", () => {
    expect(recordingPeaks([new Float32Array(4)], 4)).toEqual([0, 0, 0, 0]);
    expect(recordingPeaks([new Float32Array([1])], 4)).toEqual([1, 1, 1, 1]);
    expect(recordingPeaks([])).toEqual([]);
  });
});
