import type { RegistrationStatus } from "./registration";

type Account = { id: string; enabled: boolean };

// PBX mailbox codes are short keypad sequences such as *97 or 8500#.
export function isValidAccessCode(code: string) {
  return /^[0-9*#]{2,32}$/.test(code);
}

export function isRegistered(
  accountId: string,
  registrations: RegistrationStatus[],
) {
  return registrations.some(
    (s) => s.accountId === accountId && s.state === "registered",
  );
}

// Keep the remembered account while it still exists; otherwise pick the first
// enabled, registered one ("" when there is none).
export function pickVoicemailAccount(
  previous: string,
  accounts: Account[],
  registrations: RegistrationStatus[],
) {
  if (accounts.some((a) => a.id === previous)) return previous;
  return (
    accounts.find((a) => a.enabled && isRegistered(a.id, registrations))?.id ??
    ""
  );
}

// The mailbox can be called only when every condition holds; the button and
// openMailbox() share this so they can't disagree.
export function canCallVoicemail(state: {
  accountSelected: boolean;
  registered: boolean;
  accessCode: string;
  callInProgress: boolean;
  busy: boolean;
  callingAvailable: boolean;
}) {
  return (
    state.accountSelected &&
    state.registered &&
    isValidAccessCode(state.accessCode) &&
    !state.callInProgress &&
    !state.busy &&
    state.callingAvailable
  );
}
