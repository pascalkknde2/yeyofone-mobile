import { useState, useId } from "react";
import { CallControlIcon } from "./CallControlIcon";
import { RecordingWave } from "./RecordingStudio";
import { CallIcon } from "./CallWindows";
import "./android-call.css";
import { useLanguage } from "./i18n";
import { callLabel, reasonLabel, callTime } from "./calls";
import type { Account } from "./useAccounts";
import type { CallStatus as Call } from "./calls";
type CallRequest = (
  action: string,
  extra?: Record<string, unknown>,
) => Promise<boolean>;

type Props = {
  session: Call;
  sessions: Call[];
  account: Account | undefined;
  callsAvailable: boolean;
  callsBusy: boolean;
  onRequest: CallRequest;
};

export function CallPanel({
  session,
  sessions,
  account,
  callsAvailable,
  callsBusy,
  onRequest,
}: Props) {
  const { t } = useLanguage();
  const [tool, setTool] = useState<"transfer" | "consult" | "keypad" | null>(
    null,
  );
  const [destination, setDestination] = useState("");
  const [digits, setDigits] = useState("");
  const inputId = useId();
  const consultation = sessions.find(
    (c) => c.consultParentId === session.id && c.state !== "ended",
  );
  const speaking = consultation ?? session;
  const disabled = callsBusy || !callsAvailable || session.transferPending;
  function toggle(next: typeof tool) {
    setTool((current) => (current === next ? null : next));
  }
  async function transfer() {
    const ok = await onRequest(
      tool === "consult" ? "consult_start" : "transfer",
      {
        id: session.id,
        destination: destination.trim(),
        ...(tool === "consult" ? { consultId: crypto.randomUUID() } : {}),
      },
    );
    if (ok) {
      setTool(null);
      setDestination("");
    }
  }
  const connected = session.state === "connected";
  const ending = session.state === "ending";
  const ended = session.state === "ended";

  return (
    <section
      className="call-panel"
      aria-label={t("Current call")}
      aria-live="polite"
    >
      <div className="call-panel__identity">
        <div className="call-panel__avatar" aria-hidden="true">
          {(session.caller || session.destination)
            .split(/[\s.\-_]+/)
            .filter(Boolean)
            .slice(0, 2)
            .map((part) => part[0])
            .join("")
            .toUpperCase() || "?"}
        </div>

        <h3 className="call-panel__peer">
          {session.caller || session.destination}
        </h3>
        <p className="call-panel__account">
          {account?.label ?? session.accountId}
        </p>

        <p className="call-panel__state" role="status">
          {t(
            callsAvailable
              ? session.held && session.state === "connected"
                ? "On hold"
                : callLabel(session.state)
              : "Call status unavailable",
          )}
        </p>

        {connected && (
          <strong className="call-panel__timer">
            {callTime(session.durationSeconds)}
          </strong>
        )}

        {session.reason && (
          <p className="call-panel__reason">{t(reasonLabel(session.reason))}</p>
        )}
        {session.sipCode != null && (
          <p className="call-panel__sip">SIP {session.sipCode}</p>
        )}

        {session.audioError && (
          <p className="call-panel__alert" role="alert">
            {t("Audio unavailable. Check your microphone and speaker.")}
          </p>
        )}
        {connected &&
          !session.held &&
          !session.audioActive &&
          !session.audioError && (
            <p className="call-panel__hint">{t("Audio is connecting…")}</p>
          )}
      </div>
      <div className="call-panel__workspace">
        {!ended && (
          <div
            className="call-panel__actions"
            role="group"
            aria-label={t("Call controls")}
          >
            <button
              type="button"
              className="call-btn call-btn--ghost"
              title={t("Transfer")}
              aria-label={t("Transfer")}
              aria-expanded={tool === "transfer"}
              disabled={disabled || !connected || !!consultation}
              onClick={() => toggle("transfer")}
            >
              <span className="android-control-icon">
                <CallControlIcon name="transfer" />
              </span>
            </button>
            <button
              type="button"
              className="call-btn call-btn--ghost"
              title={t("Consult transfer")}
              aria-label={t("Consult transfer")}
              aria-expanded={tool === "consult"}
              disabled={disabled || !connected || !!consultation}
              onClick={() => toggle("consult")}
            >
              <span className="android-control-icon">
                <CallControlIcon name="consult" />
              </span>
            </button>
            <button
              type="button"
              className={
                session.held
                  ? "call-btn call-btn--warn"
                  : "call-btn call-btn--ghost"
              }
              title={t(session.held ? "Resume" : "Hold")}
              aria-label={t(session.held ? "Resume" : "Hold")}
              disabled={
                disabled || !connected || consultation?.state === "ending"
              }
              onClick={() =>
                void onRequest(
                  consultation
                    ? "consult_cancel"
                    : session.held
                      ? "resume"
                      : "hold",
                  {
                    id: session.id,
                    ...(consultation ? { consultId: consultation.id } : {}),
                  },
                )
              }
            >
              <span className="android-control-icon">
                <CallControlIcon name={session.held ? "resume" : "hold"} />
              </span>
            </button>
            <button
              type="button"
              className={
                speaking.muted
                  ? "call-btn call-btn--warn"
                  : "call-btn call-btn--ghost"
              }
              title={t(speaking.muted ? "Unmute" : "Mute")}
              aria-label={t(speaking.muted ? "Unmute" : "Mute")}
              aria-pressed={speaking.muted}
              disabled={disabled || speaking.state !== "connected"}
              onClick={() =>
                void onRequest("mute", {
                  id: speaking.id,
                  muted: !speaking.muted,
                })
              }
            >
              <span className="android-control-icon">
                <CallIcon name="mic" off={speaking.muted} />
              </span>
            </button>
            <button
              type="button"
              className="call-btn call-btn--ghost"
              title={t("Keypad")}
              aria-label={t("Keypad")}
              aria-expanded={tool === "keypad"}
              disabled={
                disabled || speaking.state !== "connected" || speaking.held
              }
              onClick={() => toggle("keypad")}
            >
              <span className="android-control-icon">
                <CallIcon name="keypad" />
              </span>
            </button>
            <button
              type="button"
              className="call-btn call-btn--end"
              title={t(connected ? "Hang up" : "Cancel call")}
              aria-label={t(connected ? "Hang up" : "Cancel call")}
              disabled={callsBusy || !callsAvailable || ending}
              onClick={() => void onRequest("hangup", { id: session.id })}
            >
              <span className="android-control-icon">
                <CallIcon name="hangup" />
              </span>
            </button>
          </div>
        )}

        {!ended && consultation && (
          <section className="call-tool" aria-label={t("Consult transfer")}>
            <p>
              <strong>{consultation.destination}</strong> ·{" "}
              {t(callLabel(consultation.state))}
            </p>
            <p>{t("Original caller is on hold")}</p>
            <button
              type="button"
              className="call-tool__icon"
              title={t("Complete transfer")}
              aria-label={t("Complete transfer")}
              disabled={disabled || consultation.state !== "connected"}
              onClick={() =>
                void onRequest("consult_complete", {
                  id: session.id,
                  consultId: consultation.id,
                })
              }
            >
              <CallControlIcon name="complete" />
            </button>
            <button
              type="button"
              className="call-tool__icon"
              title={t("Cancel consultation and resume")}
              aria-label={t("Cancel consultation and resume")}
              disabled={disabled || consultation.state === "ending"}
              onClick={() =>
                void onRequest("consult_cancel", {
                  id: session.id,
                  consultId: consultation.id,
                })
              }
            >
              <CallControlIcon name="resume" />
            </button>
          </section>
        )}
        {!ended && session.transferPending && (
          <p role="status">{t("Transferring…")}</p>
        )}
        {!ended && !session.transferPending && session.transferCode >= 300 && (
          <p className="call-panel__alert" role="alert">
            {t("Transfer failed. You can retry or resume the call.")} (SIP{" "}
            {session.transferCode})
          </p>
        )}
        {!ended &&
          connected &&
          (tool === "transfer" || tool === "consult") &&
          !consultation && (
            <form
              className="call-tool"
              onSubmit={(e) => {
                e.preventDefault();
                void transfer();
              }}
            >
              <label htmlFor={inputId}>
                {t(tool === "consult" ? "Consult transfer" : "Transfer")}
              </label>
              <div className="call-tool__destination">
                <input
                  id={inputId}
                  autoFocus
                  value={destination}
                  placeholder={t("Extension or SIP address")}
                  maxLength={512}
                  disabled={disabled}
                  onChange={(e) => setDestination(e.target.value)}
                />
                <button
                  type="submit"
                  className="call-tool__icon"
                  title={t(
                    tool === "consult" ? "Start consultation" : "Transfer",
                  )}
                  aria-label={t(
                    tool === "consult" ? "Start consultation" : "Transfer",
                  )}
                  disabled={disabled || !destination.trim()}
                >
                  <CallControlIcon
                    name={tool === "consult" ? "consult" : "transfer"}
                  />
                </button>
                <button
                  type="button"
                  className="call-tool__icon"
                  title={t("Close")}
                  aria-label={t("Close")}
                  onClick={() => setTool(null)}
                >
                  <CallIcon name="close" />
                </button>
              </div>
            </form>
          )}
        {!ended && tool === "keypad" && (
          <section className="call-tool" aria-label={t("Keypad")}>
            <output className="call-tool__digits" aria-label={t("Sent digits")}>
              {digits || " "}
            </output>
            <div className="call-tool__keypad">
              {["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map(
                (digit) => (
                  <button
                    key={digit}
                    type="button"
                    disabled={
                      disabled ||
                      speaking.state !== "connected" ||
                      speaking.held
                    }
                    onClick={async () => {
                      if (
                        await onRequest("dtmf", {
                          id: speaking.id,
                          digits: digit,
                        })
                      )
                        setDigits((current) => (current + digit).slice(-32));
                    }}
                  >
                    {digit}
                  </button>
                ),
              )}
            </div>
          </section>
        )}

        <section
          className="recording-studio call-recording-live"
          aria-label={t("Call recording")}
        >
          <div className="recording-studio__art" aria-hidden="true">
            <CallIcon name="mic" />
          </div>
          <div className="recording-studio__body">
            <div className="recording-studio__heading">
              <div>
                <small>{t("Call recording")}</small>
                <h3>
                  {t(session.recording ? "Recording" : "Start recording")}
                </h3>
              </div>
            </div>
            <RecordingWave active={session.recording} />
            <div className="recording-studio__controls">
              <button
                type="button"
                className="recording-studio__stop"
                disabled={
                  !connected ||
                  callsBusy ||
                  !callsAvailable ||
                  (!session.recording && session.held)
                }
                onClick={() =>
                  void onRequest(
                    session.recording ? "record_stop" : "record_start",
                    { id: session.id },
                  )
                }
              >
                {t(session.recording ? "Stop recording" : "Start recording")}
              </button>
            </div>
          </div>
        </section>
      </div>
    </section>
  );
}
