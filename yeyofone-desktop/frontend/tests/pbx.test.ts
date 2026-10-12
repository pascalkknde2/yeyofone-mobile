import { test, expect } from "bun:test";
import {
  elapsed,
  groupLiveCalls,
  MODE_DIGIT,
  parseLegs,
  parsePbxStatus,
  parseVoicemails,
} from "../src/pbx";

const leg = (over: Record<string, unknown>) => ({
  uuid: "u",
  callUuid: "c",
  extension: "1001",
  direction: "inbound",
  state: "ACTIVE",
  createdEpoch: 100,
  callerName: "",
  callerNumber: "1001",
  destination: "1002",
  calleeName: "",
  calleeNumber: "",
  ...over,
});

test("live call legs become one row per extension with the other party", () => {
  const rows = groupLiveCalls(
    parseLegs([
      // 1001 called 1002: FreeSWITCH sees 1001's leg as inbound.
      leg({ extension: "1001", calleeName: "Maya" }),
      // FreeSWITCH rings 1002: outbound leg, the other party is the caller.
      leg({ extension: "1002", direction: "outbound", callerName: "Alex" }),
      leg({ extension: "1003", state: "RINGING", destination: "2000" }),
      leg({ extension: "1004", state: "HANGUP" }),
      leg({ extension: "1005" }),
      "junk",
    ]),
    ["1005"],
  );
  expect(rows).toEqual([
    { extension: "1001", other: "Maya", state: "talking", since: 100 },
    { extension: "1002", other: "Alex", state: "talking", since: 100 },
    { extension: "1003", other: "2000", state: "ringing", since: 100 },
  ]);
});

test("an extension's active leg wins over a ringing one", () => {
  const rows = groupLiveCalls(
    parseLegs([
      leg({ extension: "1001", state: "ACTIVE", calleeNumber: "1002" }),
      leg({ extension: "1001", state: "RINGING", destination: "1009" }),
    ]),
  );
  expect(rows).toEqual([
    { extension: "1001", other: "1002", state: "talking", since: 100 },
  ]);
});

test("status, voicemails, durations and mode keys", () => {
  const status = parsePbxStatus({
    url: "https://pbx/app/yeyofone/api.php",
    configured: true,
    hasPin: false,
    me: {
      domain: "d",
      extensions: ["1005", 3],
      liveCalls: true,
      voicemail: true,
    },
  })!;
  expect(status.me?.extensions).toEqual(["1005"]);
  expect(parsePbxStatus(null)).toBeNull();
  expect(
    parseVoicemails([{ uuid: "a", seconds: 12, callerNumber: "1002" }])[0],
  ).toMatchObject({ uuid: "a", seconds: 12, callerNumber: "1002" });
  expect(elapsed(100, 365_000)).toBe("4:25");
  expect(elapsed(500, 100_000)).toBe("0:00");
  expect(MODE_DIGIT).toEqual({ listen: "0", whisper: "2", barge: "3" });
});
