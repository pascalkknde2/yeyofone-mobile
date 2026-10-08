export type CallStatus = {
  id: string;
  accountId: string;
  startedAtMs: number;
  destination: string;
  direction: "incoming" | "outgoing";
  caller: string | null;
  state: string;
  reason: string | null;
  sipCode: number | null;
  durationSeconds: number;
  muted: boolean;
  audioActive: boolean;
  audioError: boolean;
  recording: boolean;
  held: boolean;
  transferPending: boolean;
  transferCode: number;
  consultParentId: string | null;
};
const states = [
  "incoming",
  "dialing",
  "ringing",
  "early_media",
  "connecting",
  "connected",
  "ending",
  "ended",
];
const reasons = [
  "local_hangup",
  "remote_hangup",
  "cancelled",
  "busy",
  "declined",
  "not_found",
  "authentication",
  "timeout",
  "unavailable",
  "media_rejected",
  "sip_failure",
  "engine_failure",
  "termination_timeout",
  "engine_stopped",
  "audio_unavailable",
  "native_failure",
  "missed_call",
  "dns",
  "tls",
  "transport",
  "offline",
];
export function parseCalls(value: unknown, requestId: string): CallStatus[] {
  if (!value || typeof value !== "object") throw Error();
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
    wire.data.length > 128
  )
    throw Error();
  const ids = new Set<string>();
  return wire.data.map((row: unknown) => {
    if (!row || typeof row !== "object") throw Error();
    const r = row as CallStatus;
    if (
      typeof r.id !== "string" ||
      !r.id.length ||
      r.id.length > 64 ||
      ids.has(r.id) ||
      typeof r.accountId !== "string" ||
      !Number.isSafeInteger(r.startedAtMs) ||
      r.startedAtMs < 0 ||
      typeof r.destination !== "string" ||
      !["incoming", "outgoing"].includes(r.direction) ||
      (r.caller !== null && typeof r.caller !== "string") ||
      r.destination.length > 512 ||
      !states.includes(r.state) ||
      (r.reason !== null && !reasons.includes(r.reason)) ||
      (r.sipCode !== null &&
        (!Number.isInteger(r.sipCode) || r.sipCode < 0 || r.sipCode > 699)) ||
      !Number.isSafeInteger(r.durationSeconds) ||
      r.durationSeconds < 0 ||
      typeof r.muted !== "boolean" ||
      typeof r.audioActive !== "boolean" ||
      typeof r.audioError !== "boolean" ||
      typeof r.recording !== "boolean" ||
      typeof r.held !== "boolean" ||
      typeof r.transferPending !== "boolean" ||
      !Number.isInteger(r.transferCode) ||
      r.transferCode < 0 ||
      r.transferCode > 699 ||
      (r.consultParentId !== null &&
        (typeof r.consultParentId !== "string" ||
          !/^[a-zA-Z0-9-]{1,64}$/.test(r.consultParentId) ||
          r.consultParentId === r.id))
    )
      throw Error();
    ids.add(r.id);
    return r;
  });
}
export function callLabel(state: string): string {
  return (
    (
      {
        incoming: "Incoming call",
        dialing: "Dialing…",
        ringing: "Ringing…",
        early_media: "Early media",
        connecting: "Connecting…",
        connected: "Connected",
        ending: "Ending call…",
        ended: "Call ended",
      } as Record<string, string>
    )[state] ?? "Call status unavailable"
  );
}
export function reasonLabel(reason: string | null): string {
  return (
    (
      {
        local_hangup: "You ended the call.",
        remote_hangup: "The other party ended the call.",
        cancelled: "Call cancelled.",
        busy: "The line is busy.",
        declined: "Call declined.",
        not_found: "Destination not found.",
        authentication: "Call authentication failed.",
        timeout: "The call timed out.",
        unavailable: "The destination is unavailable.",
        media_rejected: "The server rejected the audio format.",
        sip_failure: "The server could not complete the call.",
        engine_failure: "The call engine failed.",
        termination_timeout: "Call termination could not be confirmed.",
        engine_stopped: "The call engine stopped.",
        audio_unavailable:
          "Microphone or speaker unavailable. Check system permissions and devices.",
        native_failure: "Unable to start the call.",
        missed_call: "Missed call.",
        dns: "The SIP server could not be resolved.",
        tls: "The secure SIP connection failed.",
        transport: "The SIP connection failed.",
        offline: "The network is offline.",
      } as Record<string, string>
    )[reason ?? ""] ?? ""
  );
}
export function callTime(seconds: number): string {
  return `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
}
