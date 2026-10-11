// Calls on another device of the same account that can be continued on this one.
export type HandoffOffer = {
  id: string;
  accountId: string;
  remote: string;
  remoteName: string | null;
  deviceName: string;
  connectedAtMs: number;
};

const identifier = (value: unknown) =>
  typeof value === "string" &&
  value.length > 0 &&
  value.length <= 64 &&
  /^[A-Za-z0-9-]+$/.test(value);

const text = (value: unknown, max: number) =>
  typeof value === "string" && value.length > 0 && value.length <= max;

export function parseHandoffs(
  value: unknown,
  requestId: string,
): HandoffOffer[] {
  if (!value || typeof value !== "object") throw Error();
  const wire = value as {
    schemaVersion: unknown;
    requestId: unknown;
    sequence: unknown;
    data: unknown;
  };
  if (
    wire.schemaVersion !== 1 ||
    wire.requestId !== requestId ||
    wire.sequence !== 0 ||
    !Array.isArray(wire.data) ||
    wire.data.length > 16
  )
    throw Error();
  return wire.data.map((row: unknown) => {
    if (!row || typeof row !== "object") throw Error();
    const r = row as HandoffOffer;
    if (
      !identifier(r.id) ||
      !identifier(r.accountId) ||
      !text(r.remote, 64) ||
      (r.remoteName !== null && !text(r.remoteName, 64)) ||
      !text(r.deviceName, 32) ||
      typeof r.connectedAtMs !== "number" ||
      !Number.isSafeInteger(r.connectedAtMs) ||
      r.connectedAtMs < 0
    )
      throw Error();
    return r;
  });
}
