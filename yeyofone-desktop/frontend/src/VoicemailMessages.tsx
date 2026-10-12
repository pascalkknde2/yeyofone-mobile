import { useCallback, useEffect, useState } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useLanguage } from "./i18n";
import {
  aiCommand,
  aiError,
  parseRecord,
  parseStatuses,
  type NotesStatus,
} from "./ai";
import { parseVoicemails, pbxCommand, type VoicemailMessage } from "./pbx";
import { usePbxStatus } from "./PbxSettings";
import { CallNotes } from "./CallNotes";
import "./ai.css";

/** Voicemail messages from the PBX add-on, with AI transcripts and summaries. */
export function VoicemailMessages() {
  const { t, language } = useLanguage();
  const { status } = usePbxStatus();
  const [messages, setMessages] = useState<VoicemailMessage[]>([]);
  const [notes, setNotes] = useState<
    Record<string, { status: NotesStatus; error: string | null }>
  >({});
  const [summaries, setSummaries] = useState<Record<string, string>>({});
  const [open, setOpen] = useState<VoicemailMessage | null>(null);
  const [error, setError] = useState("");

  const ready = !!status?.configured;
  const refresh = useCallback(async () => {
    if (!isTauri() || !ready) return;
    try {
      setMessages(parseVoicemails(await pbxCommand("voicemails")));
      setNotes(parseStatuses(await aiCommand("list")));
      setError("");
    } catch (e) {
      setError(aiError(e, "Voicemail messages are unavailable."));
    }
  }, [ready]);

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), 60_000);
    let active = true;
    let unlisten: (() => void) | undefined;
    void listen("ai-state", () => void refresh()).then((fn) =>
      active ? (unlisten = fn) : fn(),
    );
    return () => {
      active = false;
      clearInterval(timer);
      unlisten?.();
    };
  }, [refresh]);

  // One-line summaries for messages that have notes.
  useEffect(() => {
    for (const message of messages) {
      const id = `vm:${message.uuid}`;
      if (notes[id]?.status !== "done" || summaries[id]) continue;
      aiCommand("get", { id })
        .then((value) => {
          const summary = parseRecord(value)?.notes?.summary;
          if (summary) setSummaries((old) => ({ ...old, [id]: summary }));
        })
        .catch(() => {});
    }
  }, [messages, notes]);

  if (!isTauri() || !status) return null;
  if (!ready)
    return (
      <p className="ai-settings__note">
        {t(
          "Connect your PBX in Settings › AI assistant to read voicemail transcripts here.",
        )}
      </p>
    );

  return (
    <div className="voicemail-messages">
      <h2>✦ {t("Messages")}</h2>
      {error && (
        <p className="ai-error" role="alert">
          {t(error)}
        </p>
      )}
      {open && (
        <CallNotes
          id={`vm:${open.uuid}`}
          title={`${t("Voicemail from")} ${open.callerName || open.callerNumber || t("Unknown caller")}`}
          onClose={() => setOpen(null)}
        />
      )}
      {messages.length === 0 ? (
        <p className="ai-empty">{t("No voicemail messages.")}</p>
      ) : (
        <ul className="live-list">
          {messages.map((message) => {
            const id = `vm:${message.uuid}`;
            const state = notes[id]?.status;
            const date = new Date(message.createdEpoch * 1000);
            return (
              <li key={message.uuid} className="live-row voicemail-row">
                <span
                  className={`live-dot ${message.status === "saved" ? "" : "live-dot--talking"}`}
                />
                <div>
                  <strong>
                    {message.callerName ||
                      message.callerNumber ||
                      t("Unknown caller")}
                    {message.callerName && message.callerNumber
                      ? ` · ${message.callerNumber}`
                      : ""}
                  </strong>
                  <small>
                    {Number.isNaN(date.getTime())
                      ? ""
                      : date.toLocaleString(language, {
                          dateStyle: "medium",
                          timeStyle: "short",
                        })}{" "}
                    · {Math.floor(message.seconds / 60)}:
                    {String(message.seconds % 60).padStart(2, "0")} ·{" "}
                    {t("Mailbox")} {message.mailbox}
                  </small>
                  {summaries[id] && (
                    <p className="voicemail-summary">{summaries[id]}</p>
                  )}
                </div>
                <button
                  className={`ai-row-button ${state === "failed" ? "is-failed" : ""}`}
                  onClick={() => setOpen(message)}
                >
                  ✦{" "}
                  {t(
                    !state
                      ? "Transcribe"
                      : state === "done"
                        ? "Notes"
                        : state === "failed"
                          ? "Notes failed"
                          : "Making notes…",
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
