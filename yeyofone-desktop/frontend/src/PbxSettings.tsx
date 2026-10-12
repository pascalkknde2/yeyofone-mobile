import { useEffect, useState } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { useLanguage } from "./i18n";
import { aiError } from "./ai";
import { parsePbxStatus, pbxCommand, type PbxStatus } from "./pbx";
import "./ai.css";

/** Hook for the PBX add-on's status; `refresh` re-checks the account on the PBX. */
export function usePbxStatus() {
  const [status, setStatus] = useState<PbxStatus | null>(null);
  useEffect(() => {
    if (!isTauri()) return;
    pbxCommand("status")
      .then((value) => setStatus(parsePbxStatus(value)))
      .catch(() => setStatus(null));
  }, []);
  return { status, setStatus };
}

/** Settings › AI assistant › Phone system: the FusionPBX add-on and the eavesdrop PIN. */
export function PbxSettings() {
  const { t } = useLanguage();
  const { status, setStatus } = usePbxStatus();
  const [url, setUrl] = useState("");
  const [key, setKey] = useState("");
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function run(action: string, extra: Record<string, unknown> = {}) {
    setBusy(true);
    setError("");
    try {
      const next = parsePbxStatus(await pbxCommand(action, extra));
      if (next) setStatus(next);
      return true;
    } catch (e) {
      setError(aiError(e, "The PBX request failed."));
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (!isTauri() || !status) return null;
  return (
    <div className="ai-settings ai-settings--pbx">
      <strong>{t("Phone system (FusionPBX)")}</strong>
      <p className="ai-settings__note">
        {t(
          "Connect the YeyoFone add-on on your PBX to transcribe voicemail and, for managers, to listen in on live calls.",
        )}
      </p>
      {error && (
        <p className="ai-error" role="alert">
          {t(error)}
        </p>
      )}
      {status.configured ? (
        <div className="ai-settings__row">
          <div>
            <strong>{status.url}</strong>
            <small>
              {status.me
                ? `${status.me.domain} · ${t("Extensions")}: ${status.me.extensions.join(", ") || "—"} · ${t(status.me.voicemail ? "Voicemail allowed" : "No voicemail permission")} · ${t(status.me.liveCalls ? "Live calls allowed" : "No live calls permission")}`
                : t("Connected. API key saved in your Keychain.")}
            </small>
            {status.lastError && (
              <small className="ai-error">{t(status.lastError)}</small>
            )}
          </div>
          <span className="ai-model__state">
            <button
              className="ai-button ai-button--quiet"
              disabled={busy}
              onClick={() => void run("refresh")}
            >
              {t("Check")}
            </button>
            <button
              className="ai-button ai-button--quiet"
              disabled={busy}
              onClick={() => void run("disconnect")}
            >
              {t("Disconnect")}
            </button>
          </span>
        </div>
      ) : (
        <form
          className="ai-pbx-form"
          onSubmit={(e) => {
            e.preventDefault();
            void run("connect", { url, key }).then(
              (ok) => ok && (setKey(""), setUrl("")),
            );
          }}
        >
          <input
            type="url"
            placeholder="https://pbx.example.com"
            aria-label={t("PBX address")}
            value={url}
            maxLength={300}
            onChange={(e) => setUrl(e.target.value)}
          />
          <input
            type="password"
            autoComplete="off"
            spellCheck={false}
            placeholder={t("FusionPBX API key")}
            aria-label={t("FusionPBX API key")}
            value={key}
            maxLength={128}
            onChange={(e) => setKey(e.target.value)}
          />
          <button
            className="ai-button"
            disabled={busy || !url.trim() || !key.trim()}
          >
            {t("Connect")}
          </button>
        </form>
      )}
      <div className="ai-settings__row">
        <div>
          <strong>{t("Eavesdrop PIN")}</strong>
          <small>
            {status.hasPin
              ? t("Saved in your Keychain. The app keys it in for you.")
              : t(
                  "The PIN your PBX asks for after dialling *33. Needed for Listen, Whisper and Barge.",
                )}
          </small>
        </div>
        {status.hasPin ? (
          <button
            className="ai-button ai-button--quiet"
            disabled={busy}
            onClick={() => void run("clearPin")}
          >
            {t("Remove PIN")}
          </button>
        ) : (
          <form
            className="ai-key-form"
            onSubmit={(e) => {
              e.preventDefault();
              void run("setPin", { pin }).then((ok) => ok && setPin(""));
            }}
          >
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              aria-label={t("Eavesdrop PIN")}
              value={pin}
              maxLength={12}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
            />
            <button className="ai-button" disabled={busy || pin.length < 3}>
              {t("Save")}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
