import { test, expect } from "bun:test";
import {
  canCallVoicemail,
  isValidAccessCode,
  pickVoicemailAccount,
} from "../src/mailbox";
import type { RegistrationStatus } from "../src/registration";

const status = (
  accountId: string,
  state: RegistrationStatus["state"],
): RegistrationStatus => ({
  accountId,
  state,
  failure: null,
  sipCode: null,
  nativeCode: null,
  expiresInSeconds: null,
  attempt: 0,
  retryInSeconds: null,
});
const ready = {
  accountSelected: true,
  registered: true,
  accessCode: "*97",
  callInProgress: false,
  busy: false,
  callingAvailable: true,
};

test("voicemail can be called only from a registered account", () => {
  expect(canCallVoicemail(ready)).toBe(true);
  expect(canCallVoicemail({ ...ready, registered: false })).toBe(false);
  expect(canCallVoicemail({ ...ready, accountSelected: false })).toBe(false);
});

test("voicemail waits for a valid code, a free line and the calling engine", () => {
  expect(canCallVoicemail({ ...ready, accessCode: "9" })).toBe(false);
  expect(canCallVoicemail({ ...ready, callInProgress: true })).toBe(false);
  expect(canCallVoicemail({ ...ready, busy: true })).toBe(false);
  expect(canCallVoicemail({ ...ready, callingAvailable: false })).toBe(false);
});

test("access codes are 2-32 keypad characters", () => {
  for (const code of ["*97", "97", "8500#", "*".repeat(32)])
    expect(isValidAccessCode(code)).toBe(true);
  for (const code of ["", "9", "*9a", "*97 ", "1".repeat(33), "+44"])
    expect(isValidAccessCode(code)).toBe(false);
});

test("the default account is the first enabled, registered one", () => {
  const accounts = [
    { id: "off", enabled: false },
    { id: "failed", enabled: true },
    { id: "ok", enabled: true },
  ];
  const registrations = [
    status("off", "registered"),
    status("failed", "failed"),
    status("ok", "registered"),
  ];
  expect(pickVoicemailAccount("", accounts, registrations)).toBe("ok");
  // A remembered account is kept even if it isn't registered right now.
  expect(pickVoicemailAccount("failed", accounts, registrations)).toBe(
    "failed",
  );
  // A remembered account that no longer exists is replaced.
  expect(pickVoicemailAccount("gone", accounts, registrations)).toBe("ok");
  expect(pickVoicemailAccount("", accounts, [])).toBe("");
});
