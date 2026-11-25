import { useEffect, useRef } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { useCalls } from "./useCalls";
import { useAccounts } from "./useAccounts";
import { callGroup, callName } from "./callGroup";
import {
  isExtension,
  parseRoster,
  rosterJson,
  type Roster,
} from "./conferenceRoster";
import { getConferenceState, updateConference } from "./conferenceStore";
import type { CallStatus } from "./calls";

// A voice counts as talking above this signal level (0-255) and keeps its ring for a moment.
const SPEAKING_LEVEL = 8;
const SPEAKING_HOLD_MS = 500;
const RESEND_MS = 5000;

type Wire = { schemaVersion: number; requestId: string; data: unknown };

async function command(
  action: string,
  extra: Record<string, unknown> = {},
): Promise<unknown> {
  const requestId = crypto.randomUUID();
  const wire = await invoke<Wire>("conference_command", {
    request: { schemaVersion: 1, requestId, action, ...extra },
  });
  if (wire.schemaVersion !== 1 || wire.requestId !== requestId)
    throw new Error("bad response");
  return wire.data;
}

const remoteExtension = (call: CallStatus) =>
  call.destination.replace(/[^A-Za-z0-9._+-]/g, "");

/**
 * Keeps conferences in step with the other apps, without showing anything itself. When this app
 * merged two calls it tells each person who is on the conference and who is talking; when someone
 * else organised one it picks up their roster, so the call screen can show the same conference.
 */
export function ConferenceHost() {
  const { rows } = useCalls();
  const { accounts } = useAccounts();
  const latest = useRef({ rows, accounts });
  latest.current = { rows, accounts };

  // Rosters other organisers send this device; only believed from the person on a call with us.
  useEffect(() => {
    if (!isTauri()) return;
    let active = true;
    const timer = setInterval(async () => {
      const { rows } = latest.current;
      const live = rows.filter((c) => c.state !== "ended");
      try {
        const inbox = (await command("inbox")) as {
          from: string;
          body: string;
        }[];
        for (const message of Array.isArray(inbox) ? inbox : []) {
          const parsed = parseRoster(message.body);
          if (!parsed || parsed.roster.host !== message.from) continue;
          if (!live.some((c) => remoteExtension(c) === message.from)) continue;
          if (active)
            updateConference({ remote: parsed.ended ? null : parsed.roster });
        }
      } catch {
        // Engine not ready; try again on the next tick.
      }
      // The conference is over for this device once the call with its organiser is gone.
      const current = getConferenceState().remote;
      if (
        active &&
        current &&
        !live.some((c) => remoteExtension(c) === current.host)
      )
        updateConference({ remote: null });
    }, 500);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  // A conference this device organises: share who is on it and who is talking.
  const shared = useRef({
    key: "",
    at: 0,
    recipients: [] as string[],
    account: "",
    host: "",
  });
  const lastLoud = useRef(new Map<string, number>());
  useEffect(() => {
    if (!isTauri()) return;
    let busy = false;
    const timer = setInterval(async () => {
      if (busy) return;
      busy = true;
      try {
        const { rows, accounts } = latest.current;
        const group = callGroup(rows);
        if (group?.kind !== "merged") {
          const previous = shared.current;
          if (previous.recipients.length) {
            const ended = rosterJson(
              { host: previous.host, members: [], speaking: [] },
              true,
            );
            for (const user of previous.recipients)
              await command("send", {
                accountId: previous.account,
                user,
                body: ended,
              }).catch(() => {});
            shared.current = {
              key: "",
              at: 0,
              recipients: [],
              account: "",
              host: "",
            };
          }
          lastLoud.current.clear();
          updateConference({ speaking: new Set() });
          return;
        }
        const calls = group.calls;
        const account = accounts.find((a) => a.id === calls[0].accountId);
        const self = account?.username ?? "";
        if (!isExtension(self)) return;

        // Who is talking: each voice's level, with the microphone as "you".
        const levels = (await command("levels", {
          ids: calls.map((c) => c.id),
        })) as {
          id: string;
          level: number;
        }[];
        const now = Date.now();
        const extOf = (id: string) =>
          id === "you"
            ? self
            : remoteExtension(calls.find((c) => c.id === id) ?? calls[0]);
        for (const { id, level } of levels) {
          const muted = id === "you" && calls.every((c) => c.muted);
          if (level > SPEAKING_LEVEL && !muted)
            lastLoud.current.set(extOf(id), now);
        }
        const talking = [...lastLoud.current]
          .filter(([, at]) => now - at < SPEAKING_HOLD_MS)
          .map(([ext]) => ext)
          .sort();
        updateConference({ speaking: new Set(talking) });

        const members = [
          { ext: self, name: account?.label || self, calling: false },
          ...calls.map((c) => ({
            ext: remoteExtension(c),
            name: callName(c),
            calling: false,
          })),
        ];
        const roster: Roster = { host: self, members, speaking: talking };
        const recipients = calls.map(remoteExtension).filter(isExtension);
        const key = JSON.stringify([members, talking]);
        if (key !== shared.current.key || now - shared.current.at > RESEND_MS) {
          const body = rosterJson(roster);
          for (const user of recipients)
            await command("send", {
              accountId: calls[0].accountId,
              user,
              body,
            }).catch(() => {});
          shared.current = {
            key,
            at: now,
            recipients,
            account: calls[0].accountId,
            host: self,
          };
        }
      } catch {
        // Engine not ready or busy; try again on the next tick.
      } finally {
        busy = false;
      }
    }, 250);
    return () => clearInterval(timer);
  }, []);
  return null;
}
