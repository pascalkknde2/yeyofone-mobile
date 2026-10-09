export function insertDialKey(
  value: string,
  key: string,
  start: number,
  end: number,
): string {
  return value.slice(0, start) + key + value.slice(end);
}
export function parseDestinationResult(
  value: unknown,
  requestId: string,
): string {
  if (!value || typeof value !== "object") throw Error("Invalid response");
  const wire = value as {
    schemaVersion: unknown;
    requestId: unknown;
    sequence: unknown;
    data?: { kind?: unknown; normalized?: unknown; callingAvailable?: unknown };
  };
  if (
    wire.schemaVersion !== 1 ||
    wire.requestId !== requestId ||
    wire.sequence !== 0 ||
    !wire.data ||
    !["extension", "phone_number", "sip_uri", "service_code"].includes(
      String(wire.data.kind),
    ) ||
    typeof wire.data.normalized !== "string" ||
    !wire.data.normalized.length ||
    wire.data.normalized.length > 512 ||
    wire.data.callingAvailable !== false
  )
    throw Error("Invalid response");
  return wire.data.normalized;
}
