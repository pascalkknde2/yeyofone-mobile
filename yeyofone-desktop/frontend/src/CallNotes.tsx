import { useCallback, useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { useLanguage } from "./i18n";
import {
  aiCommand,
  aiError,
  clock,
  followUpText,
  parseRecord,
  talkBand,
  type NotesRecord,
} from "./ai";
import "./ai.css";

const WORKING = ["queued", "transcribing", "summarising"];

/** AI notes for one call: summary, action items, follow-up, insights and transcript. */
export function CallNotes({
  id,
  title,
  onClose,
}: {
  id: string;
  title: string;
  onClose: () => void;
}) {
  const { t } = useLanguage();
  const dialog = useRef<HTMLDialogElement>(null);
  const [record, setRecord] = useState<NotesRecord | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const [tab, setTab] = useState<"notes" | "transcript">("notes");

  const refresh = useCallback(async () => {
    try {
      setRecord(parseRecord(await aiCommand("get", { id })));
    } catch (e) {
      setError(aiError(e, "AI notes are unavailable."));
    } finally {
      setLoaded(true);
    }
  }, [id]);

  useEffect(() => {
    const previous =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    dialog.current?.showModal();
    void refresh();
    let unlisten: (() => void) | undefined;
    let active = true;
    void listen<string>("ai-state", (event) => {
      if (event.payload === id) void refresh();
    }).then((fn) => (active ? (unlisten = fn) : fn()));
    return () => {
      active = false;
      unlisten?.();
      dialog.current?.close();
      previous?.focus();
    };
  }, [id, refresh]);

  const working = !!record && WORKING.includes(record.status);
  useEffect(() => {
    if (!working) return;
    const timer = setInterval(() => void refresh(), 2000);
    return () => clearInterval(timer);
  }, [working, refresh]);

  async function generate() {
    setError("");
    try {
      await aiCommand("process", { id });
      await refresh();
    } catch (e) {
      setError(aiError(e));
    }
  }

  async function copyFollowUp() {
    if (!record?.notes) return;
    try {
      await navigator.clipboard.writeText(followUpText(record.notes));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError("Couldn't copy to the clipboard.");
    }
  }

  const notes = record?.status === "done" ? record.notes : null;
  const stats = record?.stats ?? null;
  const band = talkBand(stats?.youPercent ?? null);

  return (
    <dialog
      ref={dialog}
      className="ai-notes"
      aria-labelledby="ai-notes-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <header className="ai-notes__header">
        <div>
          <span className="ai-notes__eyebrow">✦ {t("AI notes")}</span>
          <h2 id="ai-notes-title">{title}</h2>
        </div>
        <button
          className="ai-icon-button"
          aria-label={t("Close")}
          onClick={onClose}
        >
          ✕
        </button>
      </header>

      {error && (
        <p className="ai-error" role="alert">
          {t(error)}
        </p>
      )}

      {!loaded ? (
        <p className="ai-empty">{t("Loading…")}</p>
      ) : working ? (
        <div className="ai-working" role="status">
          <span className="ai-spinner" aria-hidden="true" />
          <strong>
            {t(
              record!.status === "transcribing"
                ? "Transcribing the call on this Mac…"
                : record!.status === "summarising"
                  ? "Claude is writing the notes…"
                  : "Waiting to start…",
            )}
          </strong>
          <small>{t("You can close this window; notes keep going.")}</small>
        </div>
      ) : !notes ? (
        <div className="ai-empty">
          <p>
            {record?.status === "failed"
              ? t(record.error ?? "Notes couldn't be made.")
              : t("No notes for this call yet.")}
          </p>
          <button className="ai-button" onClick={() => void generate()}>
            {t(record?.status === "failed" ? "Try again" : "Make notes")}
          </button>
        </div>
      ) : (
        <>
          <div className="ai-tabs" role="tablist">
            {(["notes", "transcript"] as const).map((value) => (
              <button
                key={value}
                role="tab"
                aria-selected={tab === value}
                className={tab === value ? "selected" : ""}
                onClick={() => setTab(value)}
              >
                {t(value === "notes" ? "Notes" : "Transcript")}
              </button>
            ))}
          </div>
          {tab === "notes" ? (
            <div className="ai-notes__body">
              <section>
                <div className="ai-chips">
                  <span className={`ai-chip ai-chip--${notes.sentiment}`}>
                    {t(
                      notes.sentiment[0]!.toUpperCase() +
                        notes.sentiment.slice(1),
                    )}
                  </span>
                  {notes.urgency === "high" && (
                    <span className="ai-chip ai-chip--negative">
                      {t("Urgent")}
                    </span>
                  )}
                  {notes.topics.map((topic) => (
                    <span className="ai-chip" key={topic}>
                      {topic}
                    </span>
                  ))}
                </div>
                <h3>{t("Summary")}</h3>
                <p>{notes.summary}</p>
                {notes.outcome && (
                  <p className="ai-outcome">
                    <strong>{t("Outcome")}:</strong> {notes.outcome}
                  </p>
                )}
              </section>

              {notes.actionItems.length > 0 && (
                <section>
                  <h3>{t("Action items")}</h3>
                  <ul className="ai-actions">
                    {notes.actionItems.map((item, i) => (
                      <li key={i}>
                        <span className={`ai-owner ai-owner--${item.owner}`}>
                          {t(
                            item.owner === "caller"
                              ? "Caller"
                              : item.owner === "both"
                                ? "Both"
                                : "You",
                          )}
                        </span>
                        {item.text}
                        {item.due && <em> · {item.due}</em>}
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {notes.decisions.length > 0 && (
                <section>
                  <h3>{t("Decisions")}</h3>
                  <ul>
                    {notes.decisions.map((d, i) => (
                      <li key={i}>{d}</li>
                    ))}
                  </ul>
                </section>
              )}

              {notes.followUp.body && (
                <section>
                  <div className="ai-section-title">
                    <h3>{t("Follow-up draft")}</h3>
                    <button
                      className="ai-button ai-button--quiet"
                      onClick={() => void copyFollowUp()}
                    >
                      {t(copied ? "Copied" : "Copy")}
                    </button>
                  </div>
                  <div className="ai-followup">
                    {notes.followUp.subject && (
                      <strong>{notes.followUp.subject}</strong>
                    )}
                    <p>{notes.followUp.body}</p>
                  </div>
                </section>
              )}

              {(stats || notes.coaching.length > 0) && (
                <section>
                  <h3>{t("Insights & coaching")}</h3>
                  {stats && stats.youPercent !== null && (
                    <div className="ai-talk">
                      <div
                        className="ai-talk__bar"
                        role="img"
                        aria-label={`${t("You talked")} ${stats.youPercent}%`}
                      >
                        <span style={{ width: `${stats.youPercent}%` }} />
                      </div>
                      <div className="ai-talk__legend">
                        <span>
                          {t("You")} {stats.youPercent}%
                        </span>
                        <span>
                          {t("Caller")} {100 - stats.youPercent}%
                        </span>
                      </div>
                      <small>
                        {t(
                          band === "talk"
                            ? "You did most of the talking."
                            : band === "listen"
                              ? "You mostly listened."
                              : "A balanced conversation.",
                        )}{" "}
                        {t("Longest stretch")}: {clock(stats.longestYouMs)} ·{" "}
                        {t("Interruptions")}: {stats.interruptions}
                      </small>
                    </div>
                  )}
                  <ul className="ai-coaching">
                    {notes.coaching.map((c, i) => (
                      <li key={i}>
                        <strong>{c.tip}</strong>
                        {c.why && <small>{c.why}</small>}
                      </li>
                    ))}
                  </ul>
                </section>
              )}
              <footer className="ai-notes__footer">
                <small>
                  {t("Written by")} {record?.model} ·{" "}
                  {t("AI can make mistakes; check important details.")}
                </small>
                <button
                  className="ai-button ai-button--quiet"
                  onClick={() => void generate()}
                >
                  {t("Make notes again")}
                </button>
              </footer>
            </div>
          ) : (
            <ol className="ai-transcript">
              {record!.transcript.map((segment, i) => (
                <li key={i} className={`ai-line ai-line--${segment.speaker}`}>
                  <span className="ai-line__who">
                    {t(
                      segment.speaker === "you"
                        ? "You"
                        : segment.speaker === "them"
                          ? "Caller"
                          : "Speaker",
                    )}{" "}
                    <time>{clock(segment.startMs)}</time>
                  </span>
                  <p>{segment.text}</p>
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </dialog>
  );
}
