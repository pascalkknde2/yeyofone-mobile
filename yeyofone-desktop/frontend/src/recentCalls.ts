import { useEffect, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";

export type HistoryEntry = {
  id: string;
  accountId: string;
  direction: "incoming" | "outgoing";
  remoteParty: string;
  state: string;
  reason: string | null;
  sipCode: number | null;
  startedAtMs: number;
  endedAtMs: number | null;
  durationSeconds: number;
  recordingId: string;
  recordingAvailable: boolean;
  recordingCloud: boolean;
  recordingDeleted: boolean;
  recordingPendingDelete: boolean;
};
export function parseHistory(
  value: unknown,
  requestId: string,
): HistoryEntry[] {
  if (!value || typeof value !== "object")
    throw Error("Invalid call history response");
  const wire = value as {
    schemaVersion: unknown;
    requestId: unknown;
    sequence: unknown;
    data: unknown;
  };
  if (
    wire.schemaVersion !== 1 ||
    wire.requestId !== requestId ||
    wire.sequence !== 0 ||
    !Array.isArray(wire.data) ||
    wire.data.length > 500
  )
    throw Error("Invalid call history response");
  return wire.data.map((item: unknown) => {
    if (!item || typeof item !== "object")
      throw Error("Invalid call history row");
    const row = item as HistoryEntry;
    if (
      typeof row.id !== "string" ||
      row.id.length > 96 ||
      typeof row.accountId !== "string" ||
      !["incoming", "outgoing"].includes(row.direction) ||
      typeof row.remoteParty !== "string" ||
      row.remoteParty.length > 512 ||
      typeof row.state !== "string" ||
      (row.reason !== null && typeof row.reason !== "string") ||
      (row.sipCode !== null &&
        (!Number.isInteger(row.sipCode) ||
          row.sipCode < 0 ||
          row.sipCode > 699)) ||
      !Number.isSafeInteger(row.startedAtMs) ||
      row.startedAtMs < 0 ||
      row.startedAtMs > 8640000000000000 ||
      (row.endedAtMs !== null &&
        (!Number.isSafeInteger(row.endedAtMs) ||
          row.endedAtMs < 0 ||
          row.endedAtMs > 8640000000000000)) ||
      !Number.isSafeInteger(row.durationSeconds) ||
      row.durationSeconds < 0 ||
      typeof row.recordingId !== "string" ||
      !/^[a-zA-Z0-9-]{1,96}$/.test(row.recordingId) ||
      typeof row.recordingAvailable !== "boolean" ||
      typeof row.recordingCloud !== "boolean" ||
      typeof row.recordingDeleted !== "boolean" ||
      typeof row.recordingPendingDelete !== "boolean"
    )
      throw Error("Invalid call history row");
    return row;
  });
}

// The most recently called or calling numbers, newest first, without repeats.
export function recentNumbers(rows: HistoryEntry[], limit = 5) {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const row of [...rows].sort((a, b) => b.startedAtMs - a.startedAtMs)) {
    const number = row.remoteParty.trim();
    if (!number || seen.has(number)) continue;
    seen.add(number);
    out.push(number);
    if (out.length === limit) break;
  }
  return out;
}

// Loads recent numbers once; empty outside the desktop app or on error.
export function useRecentNumbers(limit = 5) {
  const [numbers, setNumbers] = useState<string[]>([]);
  useEffect(() => {
    if (!isTauri()) return;
    let active = true;
    const requestId = crypto.randomUUID();
    invoke("call_history_command", { request: { schemaVersion: 1, requestId } })
      .then((value) => {
        if (active)
          setNumbers(recentNumbers(parseHistory(value, requestId), limit));
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [limit]);
  return numbers;
}
