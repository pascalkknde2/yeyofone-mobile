// AI call notes: types, the validated `ai_command` wrapper and small formatting helpers.
import { invoke } from "@tauri-apps/api/core";

export type Speaker = "you" | "them" | "unknown";
export type Segment = {
  speaker: Speaker;
  startMs: number;
  endMs: number;
  text: string;
};
export type TalkStats = {
  youMs: number;
  themMs: number;
  youPercent: number | null;
  longestYouMs: number;
  interruptions: number;
  turns: number;
};
export type CallNotes = {
  summary: string;
  outcome: string;
  actionItems: { text: string; owner: string; due: string | null }[];
  decisions: string[];
  followUp: { subject: string; body: string };
  sentiment: "positive" | "neutral" | "negative" | "mixed";
  sentimentScore: number;
  topics: string[];
  coaching: { tip: string; why: string }[];
  urgency: "low" | "normal" | "high";
};
export type NotesStatus =
  "queued" | "transcribing" | "summarising" | "done" | "failed";
export type NotesRecord = {
  id: string;
  status: NotesStatus;
  error: string | null;
  transcript: Segment[];
  stats: TalkStats | null;
  notes: CallNotes | null;
  model: string | null;
};
export type AiStatus = {
  hasKey: boolean;
  settings: {
    model: string;
    whisperModel: string;
    autoRecord: boolean;
    autoNotes: boolean;
  };
  claudeModels: string[];
  transcriptionAvailable: boolean;
  models: { id: string; label: string; bytes: number; installed: boolean }[];
  download: {
    model: string;
    received: number;
    total: number;
    error: string | null;
  } | null;
  busy: string | null;
  queued: number;
  lastError: string | null;
};
export type SearchHit = { id: string; source: string; snippet: string };
export type Insights = {
  calls: number;
  averageSentiment: number | null;
  sentiment: Record<string, number>;
  averageTalkShare: number | null;
  interruptionsPerCall: number | null;
  actionItems: number;
  topics: { topic: string; calls: number }[];
  coaching: { id: string; tip: string; why: string }[];
};

type Json = Record<string, unknown>;
const STATUSES = [
  "queued",
  "transcribing",
  "summarising",
  "done",
  "failed",
] as const;
const isObject = (v: unknown): v is Json =>
  !!v && typeof v === "object" && !Array.isArray(v);
const str = (v: unknown, fallback = "") =>
  typeof v === "string" ? v : fallback;
const num = (v: unknown, fallback = 0) =>
  typeof v === "number" && Number.isFinite(v) ? v : fallback;
const list = (v: unknown) => (Array.isArray(v) ? v : []);
const oneOf = <T extends string>(v: unknown, allowed: readonly T[], f: T) =>
  allowed.includes(v as T) ? (v as T) : f;

/** Sends an `ai_command` and returns its data after checking the response envelope. */
export async function aiCommand(
  action: string,
  extra: Record<string, unknown> = {},
): Promise<unknown> {
  const requestId = crypto.randomUUID();
  const value: unknown = await invoke("ai_command", {
    request: { schemaVersion: 1, requestId, action, ...extra },
  });
  if (
    !isObject(value) ||
    value.schemaVersion !== 1 ||
    value.requestId !== requestId
  )
    throw Error("Invalid AI response.");
  return value.data;
}

/** The message of a failed command (the backend returns user-facing text). */
export function aiError(error: unknown, fallback = "The AI request failed.") {
  return typeof error === "string" && error.length < 300 ? error : fallback;
}

export function parseStatus(v: unknown): AiStatus | null {
  if (!isObject(v) || !isObject(v.settings)) return null;
  const s = v.settings;
  const d = isObject(v.download) ? v.download : null;
  return {
    hasKey: v.hasKey === true,
    settings: {
      model: str(s.model),
      whisperModel: str(s.whisperModel),
      autoRecord: s.autoRecord === true,
      autoNotes: s.autoNotes === true,
    },
    claudeModels: list(v.claudeModels).filter(
      (m): m is string => typeof m === "string",
    ),
    transcriptionAvailable: v.transcriptionAvailable === true,
    models: list(v.models)
      .filter(isObject)
      .map((m) => ({
        id: str(m.id),
        label: str(m.label),
        bytes: num(m.bytes),
        installed: m.installed === true,
      })),
    download: d
      ? {
          model: str(d.model),
          received: num(d.received),
          total: num(d.total),
          error: typeof d.error === "string" ? d.error : null,
        }
      : null,
    busy: typeof v.busy === "string" ? v.busy : null,
    queued: num(v.queued),
    lastError: typeof v.lastError === "string" ? v.lastError : null,
  };
}

function parseNotes(v: unknown): CallNotes | null {
  if (!isObject(v) || typeof v.summary !== "string") return null;
  const follow = isObject(v.followUp) ? v.followUp : {};
  return {
    summary: v.summary,
    outcome: str(v.outcome),
    actionItems: list(v.actionItems)
      .filter(isObject)
      .map((a) => ({
        text: str(a.text),
        owner: str(a.owner, "you"),
        due: typeof a.due === "string" ? a.due : null,
      }))
      .filter((a) => a.text),
    decisions: list(v.decisions).filter(
      (d): d is string => typeof d === "string",
    ),
    followUp: { subject: str(follow.subject), body: str(follow.body) },
    sentiment: oneOf(
      v.sentiment,
      ["positive", "neutral", "negative", "mixed"] as const,
      "neutral",
    ),
    sentimentScore: Math.max(-100, Math.min(100, num(v.sentimentScore))),
    topics: list(v.topics).filter((t): t is string => typeof t === "string"),
    coaching: list(v.coaching)
      .filter(isObject)
      .map((c) => ({ tip: str(c.tip), why: str(c.why) }))
      .filter((c) => c.tip),
    urgency: oneOf(v.urgency, ["low", "normal", "high"] as const, "normal"),
  };
}

export function parseRecord(v: unknown): NotesRecord | null {
  if (!isObject(v) || typeof v.id !== "string") return null;
  const stats = isObject(v.stats) ? v.stats : null;
  return {
    id: v.id,
    status: oneOf(v.status, STATUSES, "failed"),
    error: typeof v.error === "string" ? v.error : null,
    transcript: list(v.transcript)
      .filter(isObject)
      .map((s) => ({
        speaker: oneOf(
          s.speaker,
          ["you", "them", "unknown"] as const,
          "unknown",
        ),
        startMs: num(s.startMs),
        endMs: num(s.endMs),
        text: str(s.text),
      }))
      .filter((s) => s.text),
    stats: stats
      ? {
          youMs: num(stats.youMs),
          themMs: num(stats.themMs),
          youPercent:
            typeof stats.youPercent === "number" ? stats.youPercent : null,
          longestYouMs: num(stats.longestYouMs),
          interruptions: num(stats.interruptions),
          turns: num(stats.turns),
        }
      : null,
    notes: parseNotes(v.notes),
    model: typeof v.model === "string" ? v.model : null,
  };
}

/** `{ "<call id>": { status, error } }` from the `list` action. */
export function parseStatuses(
  v: unknown,
): Record<string, { status: NotesStatus; error: string | null }> {
  if (!isObject(v)) return {};
  const out: Record<string, { status: NotesStatus; error: string | null }> = {};
  for (const [id, row] of Object.entries(v)) {
    if (!isObject(row)) continue;
    out[id] = {
      status: oneOf(row.status, STATUSES, "failed"),
      error: typeof row.error === "string" ? row.error : null,
    };
  }
  return out;
}

export function parseHits(v: unknown): SearchHit[] {
  return list(v)
    .filter(isObject)
    .map((h) => ({
      id: str(h.id),
      source: str(h.source),
      snippet: str(h.snippet),
    }))
    .filter((h) => h.id);
}

export function parseInsights(v: unknown): Insights | null {
  if (!isObject(v)) return null;
  const nullable = (x: unknown) => (typeof x === "number" ? x : null);
  return {
    calls: num(v.calls),
    averageSentiment: nullable(v.averageSentiment),
    sentiment: isObject(v.sentiment)
      ? Object.fromEntries(
          Object.entries(v.sentiment).map(([k, n]) => [k, num(n)]),
        )
      : {},
    averageTalkShare: nullable(v.averageTalkShare),
    interruptionsPerCall: nullable(v.interruptionsPerCall),
    actionItems: num(v.actionItems),
    topics: list(v.topics)
      .filter(isObject)
      .map((t) => ({ topic: str(t.topic), calls: num(t.calls) })),
    coaching: list(v.coaching)
      .filter(isObject)
      .map((c) => ({ id: str(c.id), tip: str(c.tip), why: str(c.why) })),
  };
}

/** "1:05" from milliseconds. */
export function clock(ms: number) {
  const seconds = Math.max(0, Math.floor(ms / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}

/** Splits an FTS snippet ("…the [invoice] was…") into text and highlighted parts. */
export function snippetParts(snippet: string) {
  return snippet
    .split(/(\[[^\]]*\])/)
    .filter(Boolean)
    .map((part) =>
      part.startsWith("[") && part.endsWith("]")
        ? { text: part.slice(1, -1), match: true }
        : { text: part, match: false },
    );
}

/** Talk-share band: under 30% you mostly listened, over 65% you mostly talked. */
export function talkBand(
  percent: number | null,
): "listen" | "balanced" | "talk" | null {
  if (percent === null) return null;
  return percent < 30 ? "listen" : percent > 65 ? "talk" : "balanced";
}

/** The follow-up as one copyable message. */
export function followUpText(notes: CallNotes) {
  return [notes.followUp.subject, notes.followUp.body]
    .filter(Boolean)
    .join("\n\n");
}

export function megabytes(bytes: number) {
  return `${Math.round(bytes / 1_000_000)} MB`;
}
