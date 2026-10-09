import { test, expect } from "bun:test";
import {
  parseRegistrations,
  registrationPresentation,
} from "../src/registration";
const status = {
  accountId: "one",
  state: "registered" as const,
  failure: null,
  sipCode: 200,
  nativeCode: null,
  expiresInSeconds: 300,
  attempt: 0,
  retryInSeconds: null,
};
test("registered status allows refresh and disconnect, disabled accounts never look connected", () => {
  expect(registrationPresentation(status, true)).toMatchObject({
    label: "Registered",
    canConnect: true,
    canDisconnect: true,
  });
  expect(registrationPresentation(status, false)).toMatchObject({
    label: "Disabled",
    canConnect: false,
    canDisconnect: false,
  });
});
test("in-flight registration can be cancelled and authentication failures show actionable text", () => {
  expect(
    registrationPresentation({ ...status, state: "registering" }, true),
  ).toMatchObject({ canConnect: false, canDisconnect: true });
  expect(
    registrationPresentation(
      { ...status, state: "failed", failure: "sip_authentication" },
      true,
    ).error,
  ).toContain("username and password");
});
test("wire parser rejects stale, duplicated, malformed and unknown status data", () => {
  const wire = {
    schemaVersion: 1,
    requestId: "expected",
    sequence: 0,
    data: [status],
  };
  expect(parseRegistrations(wire, "expected")).toEqual([status]);
  for (const bad of [
    { ...wire, requestId: "old" },
    { ...wire, data: [status, status] },
    { ...wire, data: [{ ...status, state: "connected" }] },
    { ...wire, data: [{ ...status, retryInSeconds: 301 }] },
    { ...wire, data: [{ ...status, expiresInSeconds: -1 }] },
  ])
    expect(() => parseRegistrations(bad, "expected")).toThrow();
});

test("a stale successful snapshot never looks connected when the runtime is unavailable", () => {
  expect(registrationPresentation(status, true, false)).toMatchObject({
    label: "Registration unavailable",
    canConnect: false,
    canDisconnect: false,
  });
});
