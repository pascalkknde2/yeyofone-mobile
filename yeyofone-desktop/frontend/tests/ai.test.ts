import { test, expect } from "bun:test";
import {
  aiError,
  clock,
  followUpText,
  parseHits,
  parseInsights,
  parseRecord,
  parseStatus,
  parseStatuses,
  snippetParts,
  talkBand,
} from "../src/ai";

test("a notes record is parsed defensively", () => {
  const record = parseRecord({
    id: "call:1",
    status: "done",
    transcript: [
      { speaker: "you", startMs: 0, endMs: 900, text: "Hi" },
      { speaker: "robot", startMs: 1000, endMs: 2000, text: "Hello" },
      { speaker: "them", startMs: 3000, endMs: 4000, text: "" },
    ],
    stats: { youMs: 900, themMs: 1000, youPercent: 47, interruptions: 1 },
    notes: {
      summary: "Noah asked about order 1042.",
      actionItems: [{ text: "Send invoice", owner: "you" }, { text: "" }],
      decisions: ["Refund", 3],
      followUp: { subject: "Order 1042", body: "Hi Noah" },
      sentiment: "furious",
      sentimentScore: 400,
      topics: ["Billing"],
      coaching: [{ tip: "Slow down", why: "Fast intro" }],
      urgency: "high",
    },
    model: "claude-sonnet-5-5",
  })!;
  expect(record.transcript.map((s) => s.speaker)).toEqual(["you", "unknown"]);
  expect(record.stats?.youPercent).toBe(47);
  expect(record.notes?.actionItems).toEqual([
    { text: "Send invoice", owner: "you", due: null },
  ]);
  expect(record.notes?.decisions).toEqual(["Refund"]);
  expect(record.notes?.sentiment).toBe("neutral");
  expect(record.notes?.sentimentScore).toBe(100);
  expect(followUpText(record.notes!)).toBe("Order 1042\n\nHi Noah");
  expect(parseRecord({ status: "done" })).toBeNull();
  expect(parseRecord({ id: "x", status: "weird" })?.status).toBe("failed");
});

test("status, statuses, hits and insights parse", () => {
  const status = parseStatus({
    hasKey: true,
    settings: {
      model: "claude-sonnet-5-5",
      whisperModel: "base",
      autoRecord: false,
      autoNotes: true,
    },
    claudeModels: ["claude-sonnet-5-5", 7],
    transcriptionAvailable: true,
    models: [{ id: "base", label: "Base", bytes: 1, installed: true }],
    download: null,
    busy: null,
    queued: 2,
  })!;
  expect(status.claudeModels).toEqual(["claude-sonnet-5-5"]);
  expect(status.models[0]!.installed).toBe(true);
  expect(parseStatus({})).toBeNull();
  expect(parseStatuses({ a: { status: "done" }, b: 3 })).toEqual({
    a: { status: "done", error: null },
  });
  expect(parseHits([{ id: "a", source: "call", snippet: "x" }, {}])).toEqual([
    { id: "a", source: "call", snippet: "x" },
  ]);
  const insights = parseInsights({
    calls: 2,
    averageSentiment: 10,
    sentiment: { positive: 1 },
    averageTalkShare: null,
    topics: [{ topic: "Billing", calls: 2 }],
  })!;
  expect(insights.averageTalkShare).toBeNull();
  expect(insights.topics[0]).toEqual({ topic: "Billing", calls: 2 });
});

test("helpers format times, snippets, talk bands and errors", () => {
  expect(clock(65_400)).toBe("1:05");
  expect(clock(-5)).toBe("0:00");
  expect(snippetParts("…the [invoice] was [wrong]")).toEqual([
    { text: "…the ", match: false },
    { text: "invoice", match: true },
    { text: " was ", match: false },
    { text: "wrong", match: true },
  ]);
  expect(talkBand(null)).toBeNull();
  expect(talkBand(20)).toBe("listen");
  expect(talkBand(50)).toBe("balanced");
  expect(talkBand(80)).toBe("talk");
  expect(aiError("Add your key.")).toBe("Add your key.");
  expect(aiError(new Error("x"))).toBe("The AI request failed.");
});
