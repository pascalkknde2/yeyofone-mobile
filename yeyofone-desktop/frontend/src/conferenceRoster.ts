// Who is on a conference, as the organiser shares it with everyone else on it. The JSON matches
// the Android and iOS apps (SIP MESSAGE content type application/vnd.yeyofone.conference+json).
export type RosterMember = { ext: string; name: string; calling: boolean };
export type Roster = {
  host: string;
  members: RosterMember[];
  // Extensions of the people talking right now, as seen by the organiser.
  speaking: string[];
};

const MAX_MEMBERS = 8;
const EXTENSION = /^[A-Za-z0-9._+-]{1,32}$/;

export function isExtension(value: unknown): value is string {
  return typeof value === "string" && EXTENSION.test(value);
}

export function rosterJson(roster: Roster, ended = false): string {
  return JSON.stringify({
    v: 1,
    host: roster.host,
    ended,
    speaking: roster.speaking,
    members: roster.members,
  });
}

/** The roster and whether the conference has ended, or null if the text isn't a valid roster. */
export function parseRoster(
  body: string,
): { roster: Roster; ended: boolean } | null {
  let json: unknown;
  try {
    json = JSON.parse(body);
  } catch {
    return null;
  }
  if (!json || typeof json !== "object") return null;
  const wire = json as Record<string, unknown>;
  if (wire.v !== 1 || !isExtension(wire.host)) return null;
  const members = (Array.isArray(wire.members) ? wire.members : [])
    .slice(0, MAX_MEMBERS)
    .flatMap((item): RosterMember[] => {
      if (!item || typeof item !== "object") return [];
      const m = item as Record<string, unknown>;
      if (!isExtension(m.ext)) return [];
      const name =
        typeof m.name === "string"
          ? // eslint-disable-next-line no-control-regex
            m.name
              .replace(/[\u0000-\u001f\u007f]/g, "")
              .trim()
              .slice(0, 40)
          : "";
      return [{ ext: m.ext, name: name || m.ext, calling: m.calling === true }];
    });
  const speaking = (Array.isArray(wire.speaking) ? wire.speaking : [])
    .slice(0, MAX_MEMBERS)
    .filter(isExtension);
  return {
    roster: { host: wire.host, members, speaking },
    ended: wire.ended === true,
  };
}
