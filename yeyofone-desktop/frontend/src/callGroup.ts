import type { CallStatus } from "./calls";

// What the call screens show: one call, a call plus a consultation leg
// (the first call waits on hold), or two calls merged into a conference.
export type CallGroup =
  | { kind: "single"; call: CallStatus }
  | { kind: "consult"; held: CallStatus; active: CallStatus }
  | { kind: "merged"; calls: [CallStatus, CallStatus] };

const live = (c: CallStatus) => c.state !== "ended";

// The main call: an outgoing call that isn't a consultation leg first, then an incoming one.
export function currentCall(rows: CallStatus[]) {
  return (
    rows.find(
      (c) =>
        c.direction === "outgoing" && live(c) && c.consultParentId === null,
    ) ?? rows.find((c) => c.direction === "incoming" && live(c))
  );
}

export function callGroup(rows: CallStatus[]): CallGroup | null {
  for (const call of rows) {
    if (!live(call) || !call.mergedWith) continue;
    const partner = rows.find((c) => c.id === call.mergedWith && live(c));
    if (partner) return { kind: "merged", calls: [call, partner] };
  }
  const call = currentCall(rows);
  if (!call) return null;
  const child = rows.find((c) => c.consultParentId === call.id && live(c));
  return child
    ? { kind: "consult", held: call, active: child }
    : { kind: "single", call };
}

export function callName(call: CallStatus) {
  return call.caller || call.destination;
}

export function initials(name: string) {
  return (
    name
      .split(/[\s.\-_]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join("")
      .toUpperCase() || "?"
  );
}
