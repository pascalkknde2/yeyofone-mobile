import { RecordingActionIcon } from "./RecordingActionIcon";
import { recordingCloud } from "./RecordingCloudSettings";
import { RecordingStudio } from "./RecordingStudio";
import { useCallback, useEffect, useRef, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useLanguage } from "./i18n";
import "./call-history.css";
import { parseHistory, type HistoryEntry } from "./recentCalls";
import { CallNotes } from "./CallNotes";
import {
  aiCommand,
  parseHits,
  parseStatuses,
  snippetParts,
  type NotesStatus,
  type SearchHit,
} from "./ai";

export function CallHistory({ search = "" }: { search?: string }) {
  const { t, language } = useLanguage();
  const [rows, setRows] = useState<HistoryEntry[]>([]),
    [direction, setDirection] = useState<"all" | "incoming" | "outgoing">(
      "all",
    ),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true);
  const [playing, setPlaying] = useState<{ id: string; url: string } | null>(
    null,
  );
  const [recordingError, setRecordingError] = useState("");
  const [recordingBusy, setRecordingBusy] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<HistoryEntry | null>(null);
  // AI notes: each call's notes status, the open notes window and transcript search hits.
  const [notes, setNotes] = useState<
    Record<string, { status: NotesStatus; error: string | null }>
  >({});
  const [notesFor, setNotesFor] = useState<HistoryEntry | null>(null);
  const [hits, setHits] = useState<SearchHit[]>([]);
  const deleteDialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (!deleteTarget) return;
    const previous =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    const dialog = deleteDialog.current;
    dialog?.showModal();
    return () => {
      dialog?.close();
      previous?.focus();
    };
  }, [deleteTarget?.id]);
  const refresh = useCallback(async () => {
    if (!isTauri()) {
      setRows([]);
      setLoading(false);
      return;
    }
    const requestId = crypto.randomUUID();
    try {
      const value = await invoke("call_history_command", {
        request: { schemaVersion: 1, requestId },
      });
      setRows(parseHistory(value, requestId));
      setError("");
      aiCommand("list")
        .then((value) => setNotes(parseStatuses(value)))
        .catch(() => {});
    } catch {
      setError("Call history is temporarily unavailable.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    let active = true;
    let unlisten: (() => void) | undefined;
    void refresh();
    const timer = setInterval(() => void refresh(), 3000);
    void listen("call-state", () => {
      if (active) void refresh();
    }).then((fn) => {
      if (active) unlisten = fn;
      else fn();
    });
    let unlistenAi: (() => void) | undefined;
    void listen("ai-state", () => {
      if (active) void refresh();
    }).then((fn) => {
      if (active) unlistenAi = fn;
      else fn();
    });
    return () => {
      active = false;
      clearInterval(timer);
      unlisten?.();
      unlistenAi?.();
    };
  }, [refresh]);
  // Search also looks through what was said on calls with AI notes.
  useEffect(() => {
    const query = search.trim();
    if (!isTauri() || query.length < 2) {
      setHits([]);
      return;
    }
    let active = true;
    const timer = setTimeout(() => {
      aiCommand("search", { query })
        .then((value) => active && setHits(parseHits(value)))
        .catch(() => active && setHits([]));
    }, 250);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [search]);
  const hitIds = new Set(hits.map((hit) => hit.id));
  const visible = rows.filter(
    (row) =>
      (direction === "all" || row.direction === direction) &&
      (hitIds.has(row.id) ||
        `${row.remoteParty} ${row.accountId} ${row.reason ?? ""}`
          .toLowerCase()
          .includes(search.toLowerCase())),
  );
  const callHits = hits.filter((hit) => rows.some((row) => row.id === hit.id));
  async function recordingUrl(id: string): Promise<string> {
    await recordingCloud("restore", { id });
    const requestId = crypto.randomUUID();
    const value: unknown = await invoke("recording_file_command", {
      request: { schemaVersion: 1, requestId, id },
    });
    if (!value || typeof value !== "object") throw Error();
    const response = value as {
      schemaVersion?: unknown;
      requestId?: unknown;
      sequence?: unknown;
      data?: unknown;
    };
    if (
      response.schemaVersion !== 1 ||
      response.requestId !== requestId ||
      response.sequence !== 0 ||
      typeof response.data !== "string"
    )
      throw Error();
    return response.data;
  }
  async function playRecording(id: string) {
    try {
      setRecordingError("");
      setRecordingBusy(true);
      const url = await recordingUrl(id);
      setPlaying({ id, url });
    } catch {
      setRecordingError("Recording could not be played or downloaded.");
    } finally {
      setRecordingBusy(false);
    }
  }
  async function downloadRecording(id: string) {
    try {
      setRecordingError("");
      setRecordingBusy(true);
      const url = await recordingUrl(id);
      const response = await fetch(url);
      if (!response.ok) throw Error();
      const objectUrl = URL.createObjectURL(await response.blob());
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = `call-${id}.wav`;
      anchor.click();
      URL.revokeObjectURL(objectUrl);
    } catch {
      setRecordingError("Recording could not be played or downloaded.");
    } finally {
      setRecordingBusy(false);
    }
  }
  async function recordingAction(action: "upload" | "delete", id: string) {
    setRecordingBusy(true);
    setRecordingError("");
    if (action === "delete" && playing?.id === id) setPlaying(null);
    try {
      await recordingCloud(action, { id });
      setDeleteTarget(null);
      await refresh();
    } catch (error) {
      setRecordingError(
        typeof error === "string" ? error : "Recording action failed.",
      );
    } finally {
      setRecordingBusy(false);
    }
  }
  return (
    <section className="standalone call-history-page">
      <div className="section-heading">
        <div>
          <h1>{t("Call history")}</h1>
          <p className="call-history-subtitle">
            {t("Your recent incoming and outgoing calls")}
          </p>
        </div>
        <button className="call-history-refresh" onClick={() => void refresh()}>
          {t("Refresh")}
        </button>
      </div>
      <div
        className="call-history-filters"
        role="group"
        aria-label={t("Filter call history")}
      >
        {(["all", "incoming", "outgoing"] as const).map((value) => (
          <button
            key={value}
            aria-pressed={direction === value}
            className={direction === value ? "selected" : ""}
            onClick={() => setDirection(value)}
          >
            {t(
              value === "all"
                ? "All calls"
                : value === "incoming"
                  ? "Incoming"
                  : "Outgoing",
            )}
          </button>
        ))}
      </div>
      {error && (
        <p className="call-history-error" role="alert">
          {t(error)}
        </p>
      )}
      {recordingError && (
        <p className="call-history-error" role="alert">
          {t(recordingError)}
        </p>
      )}
      {deleteTarget && (
        <dialog
          ref={deleteDialog}
          className="recording-delete-confirm"
          role="alertdialog"
          aria-modal="true"
          onCancel={(event) => {
            event.preventDefault();
            if (!recordingBusy) setDeleteTarget(null);
          }}
          aria-labelledby="delete-recording-title"
          aria-describedby="delete-recording-description"
        >
          <div>
            <strong id="delete-recording-title">
              {t("Delete recording")} · {deleteTarget.remoteParty}
            </strong>
            <p id="delete-recording-description">
              {t(
                "Permanently delete this audio from this device and R2. The call history will remain.",
              )}
            </p>
          </div>
          <button
            autoFocus
            disabled={recordingBusy}
            onClick={() => setDeleteTarget(null)}
          >
            {t("Cancel")}
          </button>
          <button
            className="recording-delete"
            disabled={recordingBusy}
            onClick={() => void recordingAction("delete", deleteTarget.id)}
          >
            {t(recordingBusy ? "Deleting…" : "Delete recording")}
          </button>
        </dialog>
      )}
      {playing && (
        <RecordingStudio
          key={playing.id}
          url={playing.url}
          title={
            rows.find((row) => row.id === playing.id)?.remoteParty ||
            t("Call recording")
          }
          onClose={() => setPlaying(null)}
          onDelete={() => {
            const row = rows.find((row) => row.id === playing.id);
            if (row) setDeleteTarget(row);
          }}
          onError={() =>
            setRecordingError("Recording could not be played or downloaded.")
          }
        />
      )}
      {notesFor && (
        <CallNotes
          id={notesFor.id}
          title={notesFor.remoteParty || t("Unknown caller")}
          onClose={() => setNotesFor(null)}
        />
      )}
      {callHits.length > 0 && (
        <div className="ai-search-hits">
          <h2>✦ {t("Said on calls")}</h2>
          {callHits.map((hit) => {
            const row = rows.find((r) => r.id === hit.id)!;
            return (
              <button
                key={hit.id}
                className="ai-search-hit"
                onClick={() => setNotesFor(row)}
              >
                <strong>{row.remoteParty || t("Unknown caller")}</strong>
                <small>
                  {snippetParts(hit.snippet).map((part, i) =>
                    part.match ? (
                      <mark key={i}>{part.text}</mark>
                    ) : (
                      <span key={i}>{part.text}</span>
                    ),
                  )}
                </small>
              </button>
            );
          })}
        </div>
      )}
      {loading ? (
        <p className="empty-state">{t("Loading call history…")}</p>
      ) : visible.length === 0 ? (
        <p className="empty-state">
          {t(
            rows.length
              ? "No calls match your search."
              : "No call history yet. Calls will appear here after you make or receive them.",
          )}
        </p>
      ) : (
        <div className="call-history-list">
          {visible.map((row) => {
            const start = new Date(row.startedAtMs);
            const date = Number.isNaN(start.getTime())
              ? ""
              : start.toLocaleString(language, {
                  dateStyle: "medium",
                  timeStyle: "short",
                });
            const state =
              row.state === "ended" ? (row.reason ?? "Call ended") : row.state;
            return (
              <article className="call-history-row" key={row.id}>
                <span
                  className={`call-history-direction ${row.direction}`}
                  aria-label={t(
                    row.direction === "incoming" ? "Incoming" : "Outgoing",
                  )}
                >
                  {row.direction === "incoming" ? "↙" : "↗"}
                </span>
                <div className="call-history-person">
                  <strong>{row.remoteParty || t("Unknown caller")}</strong>
                  <small>
                    {t(row.direction === "incoming" ? "Incoming" : "Outgoing")}{" "}
                    · {t("Account")} {row.accountId}
                  </small>
                </div>
                <time dateTime={start.toISOString()}>{date}</time>
                <span className="call-history-outcome">{t(state)}</span>
                <span className="call-history-duration">
                  {row.durationSeconds
                    ? `${Math.floor(row.durationSeconds / 60)
                        .toString()
                        .padStart(
                          2,
                          "0",
                        )}:${(row.durationSeconds % 60).toString().padStart(2, "0")}`
                    : "—"}
                </span>
                <div className="call-history-tools">
                  {(row.recordingAvailable || notes[row.id]) && (
                    <button
                      type="button"
                      className={`ai-row-button ${notes[row.id]?.status === "failed" ? "is-failed" : ""}`}
                      title={t("AI notes")}
                      onClick={() => setNotesFor(row)}
                    >
                      ✦{" "}
                      {t(
                        !notes[row.id]
                          ? "AI notes"
                          : notes[row.id]!.status === "done"
                            ? "Notes"
                            : notes[row.id]!.status === "failed"
                              ? "Notes failed"
                              : "Making notes…",
                      )}
                    </button>
                  )}
                  {row.recordingAvailable ? (
                    <div className="call-history-recording-actions">
                      <button
                        type="button"
                        className="recording-icon-button"
                        aria-label={t("Play recording")}
                        title={t("Play recording")}
                        disabled={recordingBusy}
                        onClick={() => void playRecording(row.id)}
                      >
                        <RecordingActionIcon name="play" />
                      </button>
                      <button
                        type="button"
                        className="recording-icon-button"
                        aria-label={t("Download recording")}
                        title={t("Download recording")}
                        disabled={recordingBusy}
                        onClick={() => void downloadRecording(row.id)}
                      >
                        <RecordingActionIcon name="download" />
                      </button>
                      {row.recordingCloud ? (
                        <span
                          className="recording-cloud-badge recording-icon-status"
                          role="img"
                          aria-label={t("Backed up to R2")}
                          title={t("Backed up to R2")}
                        >
                          <RecordingActionIcon name="cloud-check" />
                        </span>
                      ) : (
                        <button
                          className="recording-icon-button"
                          aria-label={t("Back up to R2")}
                          title={t("Back up to R2")}
                          disabled={recordingBusy}
                          onClick={() => void recordingAction("upload", row.id)}
                        >
                          <RecordingActionIcon name="upload" />
                        </button>
                      )}
                      <button
                        className="recording-delete recording-icon-button"
                        aria-label={t("Delete recording")}
                        title={t("Delete recording")}
                        disabled={recordingBusy}
                        onClick={() => setDeleteTarget(row)}
                      >
                        <RecordingActionIcon name="delete" />
                      </button>
                    </div>
                  ) : row.recordingPendingDelete ? (
                    <button
                      className="recording-delete"
                      disabled={recordingBusy}
                      onClick={() => setDeleteTarget(row)}
                    >
                      {t("Retry deletion")}
                    </button>
                  ) : (
                    <span className="call-history-no-recording">
                      {row.recordingDeleted ? t("Recording deleted") : ""}
                    </span>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
