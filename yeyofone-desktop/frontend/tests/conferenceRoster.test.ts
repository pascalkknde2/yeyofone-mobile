import { test, expect } from "bun:test";
import { parseRoster, rosterJson } from "../src/conferenceRoster";

test("round trips a roster with who is talking", () => {
  const body = rosterJson({
    host: "1001",
    members: [
      { ext: "1001", name: "Pascal", calling: false },
      { ext: "1005", name: "Sarah", calling: true },
    ],
    speaking: ["1005"],
  });
  const parsed = parseRoster(body);
  expect(parsed?.ended).toBe(false);
  expect(parsed?.roster.host).toBe("1001");
  expect(parsed?.roster.members).toHaveLength(2);
  expect(parsed?.roster.members[1]).toEqual({
    ext: "1005",
    name: "Sarah",
    calling: true,
  });
  expect(parsed?.roster.speaking).toEqual(["1005"]);
});

test("accepts a roster from an older app that has no speaking list", () => {
  const parsed = parseRoster(
    '{"v":1,"host":"1005","ended":false,"members":[{"ext":"1005","name":"Sarah","calling":false}]}',
  );
  expect(parsed?.roster.speaking).toEqual([]);
});

test("reads the ended flag", () => {
  expect(
    parseRoster('{"v":1,"host":"1005","ended":true,"members":[]}')?.ended,
  ).toBe(true);
});

test("rejects bad hosts, versions and non-JSON", () => {
  expect(parseRoster("nope")).toBeNull();
  expect(parseRoster('{"v":2,"host":"1005","members":[]}')).toBeNull();
  expect(parseRoster('{"v":1,"host":"bad host!","members":[]}')).toBeNull();
});

test("drops invalid extensions, cleans names and caps the list", () => {
  const members = Array.from({ length: 12 }, (_, i) => ({
    ext: `10${i}`,
    name: "x",
    calling: false,
  }));
  members.push({ ext: "bad ext", name: "y", calling: false });
  const parsed = parseRoster(
    JSON.stringify({
      v: 1,
      host: "1001",
      members,
      speaking: ["1000", "bad ext"],
    }),
  );
  expect(parsed?.roster.members).toHaveLength(8);
  expect(parsed?.roster.speaking).toEqual(["1000"]);
  const named = parseRoster(
    JSON.stringify({
      v: 1,
      host: "1001",
      members: [{ ext: "1002", name: "  Li\u0007sa  " }],
    }),
  );
  expect(named?.roster.members[0].name).toBe("Lisa");
});
