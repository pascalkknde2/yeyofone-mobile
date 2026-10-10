import { test, expect } from "bun:test";
import { recentNumbers, type HistoryEntry } from "../src/recentCalls";

const row = (remoteParty: string, startedAtMs: number): HistoryEntry => ({
  id: `${remoteParty}-${startedAtMs}`,
  accountId: "account-1005",
  direction: "outgoing",
  remoteParty,
  state: "ended",
  reason: null,
  sipCode: null,
  startedAtMs,
  endedAtMs: startedAtMs + 1000,
  durationSeconds: 1,
  recordingId: "r1",
  recordingAvailable: false,
  recordingCloud: false,
  recordingDeleted: false,
  recordingPendingDelete: false,
});

test("recent numbers are newest first, unique and limited", () => {
  const rows = [
    row("1001", 1),
    row("1002", 5),
    row("1001", 9),
    row(" ", 7),
    row("1003", 3),
  ];
  expect(recentNumbers(rows)).toEqual(["1001", "1002", "1003"]);
  expect(recentNumbers(rows, 2)).toEqual(["1001", "1002"]);
  expect(recentNumbers([])).toEqual([]);
});
