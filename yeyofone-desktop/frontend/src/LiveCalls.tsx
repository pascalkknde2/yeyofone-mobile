import { useEffect, useRef, useState } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { useLanguage } from "./i18n";
import { useCalls } from "./useCalls";
import { aiError } from "./ai";
import {
  elapsed,
  groupLiveCalls,
  MODE_DIGIT,
  parseLegs,
  pbxCommand,
  registeredAccount,
  type EavesdropMode,
  type LiveCall,
} from "./pbx";
import { usePbxStatus } from "./PbxSettings";
import "./ai.css";

type Session = {
  extension: string;
  callId: string;
  mode: EavesdropMode;
  phase: "dialling" | "joining" | "joined";
};

const MODES: { mode: EavesdropMode; label: string; hint: string }[] = [
  {
    mode: "listen",
    label: "Listen",
    hint: "Hear the call; nobody hears you.",
  },
  {
    mode: "whisper",
    label: "Whisper",
    hint: "Coach the agent; the caller can't hear you.",
  },
  {
    mode: "barge",
    label: "Barge",
    hint: "Join the call; both sides hear you.",
  },
];

/** Managers' live view of the PBX: who is on a call, and Listen / Whisper / Barge into it. */
export function LiveCalls({ onSettings }: { onSettings: () => void }) {
  const { t } = useLanguage();
  const { status } = usePbxStatus();
  const calls = useCalls();
  const [rows, setRows] = useState<LiveCall[]>([]);
  const [error, setError] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [session, setSession] = useState<Session | null>(null);
  const [now, setNow] = useState(Date.now());
  const pinSent = useRef<string | null>(null);

  const ready = !!status?.configured;
  const own = status?.me?.extensions ?? [];
  useEffect(() => {
    if (!isTauri() || !ready) return;
    let active = true;
    const load = async () => {
      try {
        const legs = parseLegs(await pbxCommand("liveCalls"));
        if (!active) return;
        setRows(groupLiveCalls(legs, own));
        setError("");
      } catch (e) {
        if (active) setError(aiError(e, "Live calls are unavailable."));
      } finally {
        if (active) setLoaded(true);
      }
    };
    void load();
    const timer = setInterval(() => void load(), 3000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [ready, own.join(",")]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  // Follow the eavesdrop call: key in the PIN once it connects, then choose the mode.
  const call = session
    ? calls.rows.find((row) => row.id === session.callId)
    : undefined;
  useEffect(() => {
    if (!session) return;
    if (call?.state === "ended") {
      setSession(null);
      return;
    }
    if (
      call?.state !== "connected" ||
      session.phase !== "dialling" ||
      pinSent.current === session.callId
    )
      return;
    pinSent.current = session.callId;
    setSession({ ...session, phase: "joining" });
    // Give the PBX a moment to start its PIN prompt.
    const timer = setTimeout(async () => {
      try {
        await pbxCommand("sendPin", { id: session.callId });
        await new Promise((resolve) => setTimeout(resolve, 1500));
        if (session.mode !== "listen")
          await calls.request("dtmf", {
            id: session.callId,
            digits: MODE_DIGIT[session.mode],
          });
        setSession((s) => (s ? { ...s, phase: "joined" } : s));
      } catch (e) {
        setError(aiError(e, "Couldn't join the call."));
      }
    }, 1500);
    return () => clearTimeout(timer);
  }, [call?.state, session?.callId, session?.phase]);

  async function join(extension: string, mode: EavesdropMode) {
    setError("");
    if (!status?.hasPin) {
      setError("Add the eavesdrop PIN in Settings › AI assistant first.");
      return;
    }
    try {
      const accountId = await registeredAccount();
      if (!accountId) {
        setError("Register a SIP account to listen in on calls.");
        return;
      }
      const callId = crypto.randomUUID();
      setSession({ extension, callId, mode, phase: "dialling" });
      const ok = await calls.request("dial", {
        id: callId,
        accountId,
        destination: `*33${extension}`,
      });
      if (!ok) {
        setSession(null);
        setError("Couldn't dial the eavesdrop code.");
      }
    } catch {
      setSession(null);
      setError("Couldn't dial the eavesdrop code.");
    }
  }

  async function switchMode(mode: EavesdropMode) {
    if (!session || session.phase !== "joined") return;
    if (
      await calls.request("dtmf", {
        id: session.callId,
        digits: MODE_DIGIT[mode],
      })
    )
      setSession({ ...session, mode });
  }

  return (
    <section className="standalone ai-insights live-calls">
      <h1>{t("Live calls")}</h1>
      <p className="ai-insights__intro">
        {t(
          "Calls on your phone system right now. Listen in, whisper to coach the agent, or barge in to help.",
        )}
      </p>
      {error && (
        <p className="ai-error" role="alert">
          {t(error)}
        </p>
      )}
      {status && !ready && (
        <div className="ai-callout">
          <span>
            {t(
              "Connect your PBX in Settings › AI assistant to see live calls.",
            )}
          </span>
          <button className="ai-button" onClick={onSettings}>
            {t("Open settings")}
          </button>
        </div>
      )}
      {session && (
        <div className="live-session" role="status">
          <div>
            <strong>
              {t(
                session.phase === "joined"
                  ? "Connected to"
                  : "Joining the call of",
              )}{" "}
              {session.extension}
            </strong>
            <small>{t(MODES.find((m) => m.mode === session.mode)!.hint)}</small>
          </div>
          <div className="live-modes" role="group" aria-label={t("Mode")}>
            {MODES.map(({ mode, label }) => (
              <button
                key={mode}
                aria-pressed={session.mode === mode}
                className={session.mode === mode ? "selected" : ""}
                disabled={session.phase !== "joined"}
                onClick={() => void switchMode(mode)}
              >
                {t(label)}
              </button>
            ))}
          </div>
          <button
            className="ai-button live-leave"
            onClick={() => void calls.request("hangup", { id: session.callId })}
          >
            {t("Leave")}
          </button>
        </div>
      )}
      {ready && !loaded ? (
        <p className="ai-empty">{t("Loading…")}</p>
      ) : ready && rows.length === 0 ? (
        <p className="ai-empty">{t("Nobody else is on a call right now.")}</p>
      ) : (
        <ul className="live-list">
          {rows.map((row) => (
            <li key={row.extension} className="live-row">
              <span className={`live-dot live-dot--${row.state}`} />
              <div>
                <strong>
                  {t("Extension")} {row.extension}
                </strong>
                <small>
                  {t(
                    row.state === "talking"
                      ? "Talking with"
                      : row.state === "held"
                        ? "On hold with"
                        : "Ringing",
                  )}{" "}
                  {row.other || t("Unknown caller")}
                  {row.since ? ` · ${elapsed(row.since, now)}` : ""}
                </small>
              </div>
              <div className="live-actions">
                {MODES.map(({ mode, label, hint }) => (
                  <button
                    key={mode}
                    className="ai-button ai-button--quiet"
                    title={t(hint)}
                    disabled={!!session || row.state !== "talking"}
                    onClick={() => void join(row.extension, mode)}
                  >
                    {t(label)}
                  </button>
                ))}
              </div>
            </li>
          ))}
        </ul>
      )}
      <p className="ai-settings__note">
        {t(
          "Listening in may require telling callers that calls are monitored.",
        )}
      </p>
    </section>
  );
}
