import { useCallback, useEffect, useState } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { useLanguage } from "./i18n";
import {
  aiCommand,
  aiError,
  megabytes,
  parseStatus,
  type AiStatus,
} from "./ai";
import "./ai.css";

/** Reads the AI status; polls while a model downloads. */
export function useAiStatus() {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const refresh = useCallback(async () => {
    if (!isTauri()) return;
    try {
      setStatus(parseStatus(await aiCommand("status")));
    } catch {
      setStatus(null);
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  const downloading = !!status?.download && !status.download.error;
  useEffect(() => {
    if (!downloading) return;
    const timer = setInterval(() => void refresh(), 1000);
    return () => clearInterval(timer);
  }, [downloading, refresh]);
  return { status, setStatus, refresh };
}

const MODEL_NAMES: Record<string, string> = {
  "claude-sonnet-5-5": "Claude Sonnet 5.5 · balanced",
  "claude-opus-5-5": "Claude Opus 5.5 · most capable",
  "claude-haiku-5-5": "Claude Haiku 5.5 · fastest",
};

export function AiSettings() {
  const { t } = useLanguage();
  const { status, setStatus } = useAiStatus();
  const [key, setKey] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function run(action: string, extra: Record<string, unknown> = {}) {
    setBusy(true);
    setError("");
    try {
      const next = parseStatus(await aiCommand(action, extra));
      if (next) setStatus(next);
      return true;
    } catch (e) {
      setError(aiError(e));
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (!isTauri())
    return (
      <p className="ai-settings__note">
        {t("AI features are available in the desktop app.")}
      </p>
    );
  if (!status)
    return <p className="ai-settings__note">{t("Loading AI settings…")}</p>;
  const download = status.download;
  const selected = status.models.find(
    (m) => m.id === status.settings.whisperModel,
  );
  const ready = status.hasKey && !!selected?.installed;

  return (
    <div className="ai-settings">
      <p className="ai-settings__intro">
        {t(
          "Calls are transcribed on this Mac; audio never leaves it. Only the text transcript is sent to Anthropic's Claude to write notes.",
        )}
      </p>
      {error && (
        <p className="ai-error" role="alert">
          {t(error)}
        </p>
      )}

      <div className="ai-settings__row">
        <div>
          <strong>{t("Claude API key")}</strong>
          <small>
            {status.hasKey
              ? t("Saved in your Keychain.")
              : t("Create one at console.anthropic.com.")}
          </small>
        </div>
        {status.hasKey ? (
          <button
            disabled={busy}
            onClick={() => void run("clearKey")}
            className="ai-button ai-button--quiet"
          >
            {t("Remove key")}
          </button>
        ) : (
          <form
            className="ai-key-form"
            onSubmit={(e) => {
              e.preventDefault();
              void run("setKey", { key }).then((ok) => ok && setKey(""));
            }}
          >
            <input
              type="password"
              autoComplete="off"
              spellCheck={false}
              placeholder="sk-ant-…"
              aria-label={t("Claude API key")}
              value={key}
              maxLength={256}
              onChange={(e) => setKey(e.target.value)}
            />
            <button className="ai-button" disabled={busy || !key.trim()}>
              {t("Save")}
            </button>
          </form>
        )}
      </div>

      <label className="ai-settings__row">
        <div>
          <strong>{t("Writing model")}</strong>
          <small>{t("Writes summaries, action items and coaching.")}</small>
        </div>
        <select
          value={status.settings.model}
          disabled={busy}
          onChange={(e) => void run("settings", { model: e.target.value })}
        >
          {status.claudeModels.map((id) => (
            <option key={id} value={id}>
              {MODEL_NAMES[id] ?? id}
            </option>
          ))}
        </select>
      </label>

      <div className="ai-settings__models">
        <strong>{t("Speech recognition (on this Mac)")}</strong>
        {!status.transcriptionAvailable && (
          <small className="ai-warning">
            {t("Transcription isn't included in this build of the app.")}
          </small>
        )}
        {status.models.map((model) => {
          const active = model.id === status.settings.whisperModel;
          const loading = download?.model === model.id;
          return (
            <div
              className={`ai-model ${active ? "is-active" : ""}`}
              key={model.id}
            >
              <label>
                <input
                  type="radio"
                  name="whisper-model"
                  checked={active}
                  disabled={busy}
                  onChange={() =>
                    void run("settings", { whisperModel: model.id })
                  }
                />
                {t(model.label)}
              </label>
              {loading && !download.error ? (
                <span className="ai-model__progress">
                  <progress value={download.received} max={download.total} />
                  {megabytes(download.received)} / {megabytes(download.total)}
                  <button
                    className="ai-button ai-button--quiet"
                    onClick={() => void run("cancelDownload")}
                  >
                    {t("Cancel")}
                  </button>
                </span>
              ) : model.installed ? (
                <span className="ai-model__state">
                  ✓ {t("Installed")}
                  <button
                    className="ai-button ai-button--quiet"
                    disabled={busy}
                    onClick={() =>
                      void run("deleteModel", { whisperModel: model.id })
                    }
                  >
                    {t("Delete")}
                  </button>
                </span>
              ) : (
                <button
                  className="ai-button"
                  disabled={
                    busy ||
                    (!!download && !download.error) ||
                    !status.transcriptionAvailable
                  }
                  onClick={() =>
                    void run("download", { whisperModel: model.id })
                  }
                >
                  {t("Download")}
                </button>
              )}
              {loading && download.error && (
                <small className="ai-error">{t(download.error)}</small>
              )}
            </div>
          );
        })}
      </div>

      <label className="ai-settings__row ai-toggle">
        <div>
          <strong>{t("Notes for every recorded call")}</strong>
          <small>
            {t("New recordings are transcribed and summarised automatically.")}
          </small>
        </div>
        <input
          type="checkbox"
          role="switch"
          checked={status.settings.autoNotes}
          disabled={busy}
          onChange={(e) =>
            void run("settings", { autoNotes: e.target.checked })
          }
        />
      </label>
      <label className="ai-settings__row ai-toggle">
        <div>
          <strong>{t("Record every call for AI notes")}</strong>
          <small>
            {t(
              "Recording starts when a call connects. Tell the other party: many places require their consent to record.",
            )}
          </small>
        </div>
        <input
          type="checkbox"
          role="switch"
          checked={status.settings.autoRecord}
          disabled={busy}
          onChange={(e) =>
            void run("settings", { autoRecord: e.target.checked })
          }
        />
      </label>
      <p className="ai-settings__note">
        {ready
          ? t("AI notes are ready.")
          : t(
              "Add your Claude key and download a speech model to turn on AI notes.",
            )}
      </p>
    </div>
  );
}
