import { test, expect } from "bun:test";
import { callGroup, currentCall, initials } from "../src/callGroup";
import type { CallStatus } from "../src/calls";

const call = (id: string, extra: Partial<CallStatus> = {}): CallStatus => ({
  id,
  accountId: "1005",
  startedAtMs: 0,
  destination: "100" + id.length,
  direction: "outgoing",
  caller: null,
  state: "connected",
  reason: null,
  sipCode: null,
  durationSeconds: 5,
  muted: false,
  audioActive: true,
  audioError: false,
  recording: false,
  held: false,
  transferPending: false,
  transferCode: 0,
  consultParentId: null,
  mergedWith: null,
  ...extra,
});

test("no live call means no group", () => {
  expect(callGroup([])).toBeNull();
  expect(callGroup([call("a", { state: "ended" })])).toBeNull();
});

test("a single call", () => {
  expect(callGroup([call("a")])).toEqual({ kind: "single", call: call("a") });
});

test("a consultation pairs the held call with the new one", () => {
  const held = call("a", { held: true });
  const added = call("b", { consultParentId: "a" });
  expect(callGroup([added, held])).toEqual({
    kind: "consult",
    held,
    active: added,
  });
  // A finished consultation leaves just the original call.
  const done = call("b", { consultParentId: "a", state: "ended" });
  expect(callGroup([held, done])).toEqual({ kind: "single", call: held });
});

test("merged calls form a conference until one side ends", () => {
  const a = call("a", { mergedWith: "b" });
  const b = call("b", { mergedWith: "a" });
  expect(callGroup([a, b])).toEqual({ kind: "merged", calls: [a, b] });
  const gone = call("b", { mergedWith: "a", state: "ended" });
  expect(callGroup([a, gone])).toEqual({ kind: "single", call: a });
});

test("the current call prefers our outgoing call over its consultation leg", () => {
  const parent = call("a", { held: true });
  const child = call("b", { consultParentId: "a" });
  expect(currentCall([child, parent])).toBe(parent);
  const incoming = call("c", { direction: "incoming" });
  expect(currentCall([incoming])).toBe(incoming);
});

test("initials use the first two words", () => {
  expect(initials("Melisa Evance")).toBe("ME");
  expect(initials("1001")).toBe("1");
  expect(initials("")).toBe("?");
});
