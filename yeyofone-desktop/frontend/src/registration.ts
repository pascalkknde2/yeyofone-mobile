export type RegistrationState =
  | "disabled"
  | "registering"
  | "registered"
  | "refreshing"
  | "failed"
  | "unregistering"
  | "unregistered"
  | "offline";
export type RegistrationStatus = {
  accountId: string;
  state: RegistrationState;
  failure: string | null;
  sipCode: number | null;
  nativeCode: number | null;
  expiresInSeconds: number | null;
  attempt: number;
  retryInSeconds: number | null;
};
const states: RegistrationState[] = [
  "disabled",
  "registering",
  "registered",
  "refreshing",
  "failed",
  "unregistering",
  "unregistered",
  "offline",
];
const failures = [
  "sip_authentication",
  "sip_rejected",
  "dns",
  "tls",
  "transport",
  "offline",
  "timeout",
  "vault_unavailable",
  "engine_unavailable",
  "capacity",
];
export function parseRegistrations(
  value: unknown,
  requestId: string,
): RegistrationStatus[] {
  if (!value || typeof value !== "object")
    throw new Error("Invalid registration response");
  const wire = value as Record<string, unknown>;
  if (
    wire.schemaVersion !== 1 ||
    wire.requestId !== requestId ||
    wire.sequence !== 0 ||
    !Array.isArray(wire.data) ||
    wire.data.length > 32
  )
    throw new Error("Invalid registration response");
  const seen = new Set<string>();
  for (const item of wire.data) {
    if (!item || typeof item !== "object")
      throw new Error("Invalid registration response");
    const s = item as RegistrationStatus;
    if (
      typeof s.accountId !== "string" ||
      !s.accountId ||
      seen.has(s.accountId) ||
      !states.includes(s.state) ||
      !(s.failure === null || failures.includes(s.failure)) ||
      !Number.isSafeInteger(s.attempt) ||
      s.attempt < 0
    )
      throw new Error("Invalid registration response");
    for (const n of [
      s.sipCode,
      s.nativeCode,
      s.expiresInSeconds,
      s.retryInSeconds,
    ])
      if (n !== null && !Number.isSafeInteger(n))
        throw new Error("Invalid registration response");
    if (
      (s.expiresInSeconds !== null && s.expiresInSeconds < 0) ||
      (s.retryInSeconds !== null &&
        (s.retryInSeconds < 0 || s.retryInSeconds > 300))
    )
      throw new Error("Invalid registration response");
    seen.add(s.accountId);
  }
  return wire.data as RegistrationStatus[];
}
export function registrationPresentation(
  status: RegistrationStatus | undefined,
  enabled: boolean,
  available = true,
) {
  const state: RegistrationState | "unavailable" = !enabled
    ? "disabled"
    : !available
      ? "unavailable"
      : (status?.state ?? "unregistered");
  const labels: Record<RegistrationState | "unavailable", string> = {
    unavailable: "Registration unavailable",
    disabled: "Disabled",
    registering: "Registering",
    registered: "Registered",
    refreshing: "Refreshing",
    failed: "Registration failed",
    unregistering: "Unregistering",
    unregistered: "Not registered",
    offline: "Offline",
  };
  const errors: Record<string, string> = {
    sip_authentication:
      "The server rejected authentication. Check the username and password.",
    sip_rejected: "The SIP server rejected registration.",
    dns: "The SIP server could not be resolved.",
    tls: "A secure connection could not be verified. Check the server TLS configuration.",
    transport: "The SIP transport failed. Retrying when possible.",
    offline: "Your network is unavailable.",
    timeout: "The SIP server did not respond in time.",
    vault_unavailable: "Keychain is unavailable. Unlock it and try again.",
    engine_unavailable: "The SIP engine could not configure this account.",
    capacity:
      "The active account limit has been reached. Disable an account and try again.",
  };
  return {
    label: labels[state],
    tone:
      state === "registered"
        ? "connected"
        : state === "failed"
          ? "failed"
          : state === "offline"
            ? "offline"
            : "pending",
    canConnect:
      enabled &&
      available &&
      !["registering", "refreshing", "unregistering"].includes(state),
    canDisconnect:
      enabled &&
      available &&
      ["registered", "registering", "refreshing", "failed", "offline"].includes(
        state,
      ),
    connectLabel: state === "registered" ? "Refresh registration" : "Register",
    error: enabled && status?.failure ? errors[status.failure] : undefined,
  };
}
