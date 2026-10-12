// The FusionPBX add-on: connection status, live calls (for Listen / Whisper / Barge) and voicemail.
import { invoke } from "@tauri-apps/api/core";
import { parseRegistrations } from "./registration";
import { pickVoicemailAccount } from "./mailbox";

export type PbxStatus = {
  url: string;
  configured: boolean;
  hasPin: boolean;
  me: {
    domain: string;
    extensions: string[];
    liveCalls: boolean;
    voicemail: boolean;
  } | null;
  lastError: string | null;
};
export type LiveLeg = {
  uuid: string;
  callUuid: string;
  extension: string;
  direction: string;
  state: string;
  createdEpoch: number;
  callerName: string;
  callerNumber: string;
  destination: string;
  calleeName: string;
  calleeNumber: string;
};
/** One extension on a call, as the Live calls page shows it. */
export type LiveCall = {
  extension: string;
  other: string;
  state: "talking" | "ringing" | "held";
  since: number;
};
export type VoicemailMessage = {
  uuid: string;
  mailbox: string;
  createdEpoch: number;
  callerName: string;
  callerNumber: string;
  seconds: number;
  status: string;
};
/** How a manager joins an agent's call; the digits are FreeSWITCH's eavesdrop keys. */
export type EavesdropMode = "listen" | "whisper" | "barge";
export const MODE_DIGIT: Record<EavesdropMode, string> = {
  listen: "0",
  whisper: "2",
  barge: "3",
};

type Json = Record<string, unknown>;
const isObject = (v: unknown): v is Json =>
  !!v && typeof v === "object" && !Array.isArray(v);
const str = (v: unknown) => (typeof v === "string" ? v : "");
const num = (v: unknown) =>
  typeof v === "number" && Number.isFinite(v) ? v : 0;

export async function pbxCommand(
  action: string,
  extra: Record<string, unknown> = {},
): Promise<unknown> {
  const requestId = crypto.randomUUID();
  const value: unknown = await invoke("pbx_command", {
    request: { schemaVersion: 1, requestId, action, ...extra },
  });
  if (
    !isObject(value) ||
    value.schemaVersion !== 1 ||
    value.requestId !== requestId
  )
    throw Error("Invalid PBX response.");
  return value.data;
}

export function parsePbxStatus(v: unknown): PbxStatus | null {
  if (!isObject(v)) return null;
  const me = isObject(v.me) ? v.me : null;
  return {
    url: str(v.url),
    configured: v.configured === true,
    hasPin: v.hasPin === true,
    me: me
      ? {
          domain: str(me.domain),
          extensions: (Array.isArray(me.extensions)
            ? me.extensions
            : []
          ).filter((e): e is string => typeof e === "string"),
          liveCalls: me.liveCalls === true,
          voicemail: me.voicemail === true,
        }
      : null,
    lastError: typeof v.lastError === "string" ? v.lastError : null,
  };
}

export function parseLegs(v: unknown): LiveLeg[] {
  return (Array.isArray(v) ? v : []).filter(isObject).map((c) => ({
    uuid: str(c.uuid),
    callUuid: str(c.callUuid),
    extension: str(c.extension),
    direction: str(c.direction),
    state: str(c.state),
    createdEpoch: num(c.createdEpoch),
    callerName: str(c.callerName),
    callerNumber: str(c.callerNumber),
    destination: str(c.destination),
    calleeName: str(c.calleeName),
    calleeNumber: str(c.calleeNumber),
  }));
}

/**
 * One row per extension on a call. FreeSWITCH reports legs from its own side: an "inbound" leg
 * is the extension calling out (the other party is the callee or destination); an "outbound" leg
 * is FreeSWITCH ringing the extension (the other party is the caller).
 */
export function groupLiveCalls(legs: LiveLeg[], exclude: string[] = []) {
  const rows = new Map<string, LiveCall>();
  for (const leg of legs) {
    if (!leg.extension || exclude.includes(leg.extension)) continue;
    const state = leg.state.toUpperCase();
    const mapped =
      state === "ACTIVE"
        ? "talking"
        : state === "HELD"
          ? "held"
          : state === "RINGING" || state === "EARLY" || state === "RING_WAIT"
            ? "ringing"
            : null;
    if (!mapped) continue;
    const other =
      leg.direction === "inbound"
        ? leg.calleeName || leg.calleeNumber || leg.destination
        : leg.callerName || leg.callerNumber;
    const existing = rows.get(leg.extension);
    // An extension with several legs shows its active one.
    if (existing && existing.state === "talking") continue;
    rows.set(leg.extension, {
      extension: leg.extension,
      other,
      state: mapped,
      since: leg.createdEpoch,
    });
  }
  return [...rows.values()].sort((a, b) =>
    a.extension.localeCompare(b.extension, undefined, { numeric: true }),
  );
}

export function parseVoicemails(v: unknown): VoicemailMessage[] {
  return (Array.isArray(v) ? v : []).filter(isObject).map((m) => ({
    uuid: str(m.uuid),
    mailbox: str(m.mailbox),
    createdEpoch: num(m.createdEpoch),
    callerName: str(m.callerName),
    callerNumber: str(m.callerNumber),
    seconds: num(m.seconds),
    status: str(m.status),
  }));
}

/** "4:05" since a Unix time in seconds, for a live call's duration. */
export function elapsed(sinceEpoch: number, nowMs = Date.now()) {
  const seconds = Math.max(0, Math.floor(nowMs / 1000 - sinceEpoch));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}

/** The first enabled, registered account, for dialling the eavesdrop code ("" when none). */
export async function registeredAccount(previous = ""): Promise<string> {
  const requestId = crypto.randomUUID();
  const accounts: unknown = await invoke("accounts_command", {
    request: { schemaVersion: 1, requestId, action: "list" },
  });
  if (
    !isObject(accounts) ||
    accounts.requestId !== requestId ||
    !Array.isArray(accounts.data)
  )
    return "";
  const list = accounts.data
    .filter(isObject)
    .map((a) => ({ id: str(a.id), enabled: a.enabled === true }));
  const regId = crypto.randomUUID();
  const registrations = parseRegistrations(
    await invoke("registration_command", {
      request: { schemaVersion: 1, requestId: regId, action: "status" },
    }),
    regId,
  );
  return pickVoicemailAccount(previous, list, registrations);
}
