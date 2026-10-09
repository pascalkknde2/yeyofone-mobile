import { test, expect } from "bun:test";
import { parseCalls, callTime, callLabel, reasonLabel } from "../src/calls";
const row = {
  id: "one",
  accountId: "1005",
  destination: "1000",
  startedAtMs: 0,
  direction: "outgoing",
  caller: null,
  recording: false,
  held: false,
  transferPending: false,
  transferCode: 0,
  consultParentId: null,
  state: "ringing",
  reason: null,
  sipCode: 180,
  durationSeconds: 0,
  muted: false,
  audioActive: false,
  audioError: false,
};
test("call snapshots reject stale, malformed, duplicate and unknown data", () => {
  const wire = { schemaVersion: 1, requestId: "one", sequence: 0, data: [row] };
  expect(parseCalls(wire, "one")).toEqual([row]);
  for (const bad of [
    { ...wire, requestId: "old" },
    { ...wire, data: [{ ...row, held: "yes" }] },
    { ...wire, data: [{ ...row, consultParentId: "one" }] },
    { ...wire, data: [{ ...row, transferCode: 700 }] },
    { ...wire, data: [row, row] },
    { ...wire, data: [{ ...row, state: "active" }] },
    { ...wire, data: [{ ...row, durationSeconds: -1 }] },
    { ...wire, data: [{ ...row, reason: "raw SIP text" }] },
    { ...wire, data: [{ ...row, audioActive: "yes" }] },
  ])
    expect(() => parseCalls(bad, "one")).toThrow();
});
test("call presentation distinguishes early media and uses backend connected duration", () => {
  expect(callLabel("early_media")).toBe("Early media");
  expect(callTime(125)).toBe("02:05");
  expect(reasonLabel("busy")).toBe("The line is busy.");
});
