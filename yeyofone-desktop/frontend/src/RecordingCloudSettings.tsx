import { useEffect, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { useLanguage } from "./i18n";

export type RecordingCloudStatus = {
  configured: boolean;
  endpoint: string;
  automaticBackup: boolean;
  lastError: string | null;
};
export async function recordingCloud(
  action: string,
  extra: Record<string, unknown> = {},
): Promise<RecordingCloudStatus> {
  const requestId = crypto.randomUUID();
  const response = await invoke<{
    schemaVersion: number;
    requestId: string;
    sequence: number;
    data: RecordingCloudStatus;
  }>("recording_cloud_command", {
    request: { schemaVersion: 1, requestId, action, ...extra },
  });
  if (
    response.schemaVersion !== 1 ||
    response.requestId !== requestId ||
    response.sequence !== 0 ||
    typeof response.data?.configured !== "boolean" ||
    typeof response.data.endpoint !== "string" ||
    typeof response.data.automaticBackup !== "boolean"
  )
    throw Error("Invalid cloud response");
  return response.data;
}
export function RecordingCloudSettings({ onChange }: { onChange: () => void }) {
  const { t } = useLanguage();
  const [status, setStatus] = useState<RecordingCloudStatus | null>(null);
  const [endpoint, setEndpoint] = useState("");
  const [token, setToken] = useState("");
  const [automatic, setAutomatic] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => {
    if (!isTauri()) return;
    let active = true;
    void recordingCloud("status")
      .then((value) => {
        if (active) {
          setStatus(value);
          setEndpoint(value.endpoint);
          setAutomatic(value.configured ? value.automaticBackup : true);
        }
      })
      .catch(() => {
        if (active) setMessage("Cloud storage settings unavailable.");
      });
    const timer = setInterval(() => {
      void recordingCloud("status")
        .then((value) => {
          if (active) setStatus(value);
        })
        .catch(() => {});
    }, 15000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);
  async function run(action: "configure" | "sync") {
    setBusy(true);
    setMessage("");
    try {
      const result = await recordingCloud(
        action,
        action === "configure"
          ? { endpoint, token: token || undefined, automaticBackup: automatic }
          : {},
      );
      setStatus(result);
      setToken("");
      setMessage(
        action === "configure"
          ? "R2 connected. Cloud settings saved."
          : "Recording backup completed.",
      );
      onChange();
    } catch (error) {
      setToken("");
      setMessage(
        typeof error === "string" ? error : "Cloud storage request failed.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="recording-cloud-settings">
      <summary>
        <strong>{t("Cloudflare R2 storage")}</strong>
        <span>{t(status?.configured ? "Connected" : "Not connected")}</span>
      </summary>
      <p>
        {t(
          "Keep recordings locally and back up completed calls to your private R2 bucket.",
        )}
      </p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void run("configure");
        }}
      >
        <label>
          {t("Worker URL")}
          <input
            type="url"
            required
            placeholder="https://yeyofone-recordings.your-subdomain.workers.dev"
            value={endpoint}
            onChange={(event) => setEndpoint(event.target.value)}
            disabled={busy}
          />
        </label>
        <label>
          {t("Connection token")}
          <input
            type="password"
            autoComplete="new-password"
            placeholder={t(
              status?.configured
                ? "Leave blank to keep the saved token"
                : "Enter your Worker token",
            )}
            value={token}
            onChange={(event) => setToken(event.target.value)}
            disabled={busy}
          />
        </label>
        <label className="recording-cloud-auto">
          <input
            type="checkbox"
            checked={automatic}
            onChange={(event) => setAutomatic(event.target.checked)}
            disabled={busy}
          />
          {t("Automatically back up completed recordings")}
        </label>
        <div className="recording-cloud-buttons">
          <button type="submit" disabled={busy || !isTauri()}>
            {t(busy ? "Working…" : "Test and save connection")}
          </button>
          <button
            type="button"
            disabled={busy || !status?.configured}
            onClick={() => void run("sync")}
          >
            {t("Back up now")}
          </button>
        </div>
      </form>
      {message && <p role="status">{t(message)}</p>}
      {status?.lastError && (
        <p className="call-history-error" role="alert">
          {t(status.lastError)}
        </p>
      )}
    </details>
  );
}
