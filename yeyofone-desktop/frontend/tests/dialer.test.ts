import { test, expect } from "bun:test";
import { insertDialKey, parseDestinationResult } from "../src/destinationWire";
test("keypad preserves caret insertion and replaces selected text", () => {
  expect(insertDialKey("1005", "2", 2, 2)).toBe("10205");
  expect(insertDialKey("1005", "7", 1, 3)).toBe("175");
});
test("destination response requires correlation and cannot enable calling", () => {
  const wire = {
    schemaVersion: 1,
    requestId: "one",
    sequence: 0,
    data: { kind: "extension", normalized: "1005", callingAvailable: false },
  };
  expect(parseDestinationResult(wire, "one")).toBe("1005");
  for (const bad of [
    null,
    { ...wire, requestId: "old" },
    { ...wire, sequence: 1 },
    { ...wire, data: { ...wire.data, callingAvailable: true } },
    { ...wire, data: { ...wire.data, kind: "unknown" } },
    { ...wire, data: null },
  ])
    expect(() => parseDestinationResult(bad, "one")).toThrow();
});
