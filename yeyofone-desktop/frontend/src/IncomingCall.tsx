import { useEffect, useRef } from "react";
import { listen } from "@tauri-apps/api/event";
import { useCalls } from "./useCalls";
import { callLabel, callTime, reasonLabel } from "./calls";
import { useLanguage } from "./i18n";
import "./incoming-call.css";
import "./android-call.css";
import { CallIcon } from "./CallIcon";
import { CallPanel } from "./CallPanel";
import type { CallTool } from "./CallBar";
export function IncomingCall({
  hiddenId,
  onHide,
  initialTool,
}: {
  // Id of the call whose screen is minimized (the call bar shows instead).
  hiddenId: string | null;
  onHide: (id: string) => void;
  initialTool?: CallTool;
}) {
  const { rows, available, busy, error, request } = useCalls();
  const { t } = useLanguage();
  const dialog = useRef<HTMLDialogElement>(null),
    answer = useRef<HTMLButtonElement>(null);
  const call = rows.find(
    (c) => c.direction === "incoming" && c.state !== "ended",
  );
  useEffect(() => {
    let active = true;
    let unlisten: undefined | (() => void);
    void listen("call-state", (event) => {
      const data = event.payload as { direction?: unknown; state?: unknown };
      if (data?.direction === "incoming" && active) void request("status");
    }).then((fn) => {
      if (active) unlisten = fn;
      else fn();
    });
    return () => {
      active = false;
      unlisten?.();
    };
  }, []);
  useEffect(() => {
    if (!call || hiddenId === call.id) return;
    const node = dialog.current;
    if (node && !node.open) node.showModal();
    if (call.state === "incoming") answer.current?.focus();
  }, [call?.id, call?.state, hiddenId]);
  function close() {
    if (call) onHide(call.id);
    dialog.current?.close();
  }
  if (!call || hiddenId === call.id) return null;
  const ringing = call.state === "incoming";
  return (
    <dialog
      ref={dialog}
      className={`incoming-call-dialog ${ringing ? "" : "incoming-call-dialog--active"}`}
      aria-labelledby="incoming-call-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!ringing) close();
      }}
    >
      <header>
        <span className="incoming-brand">
          <i /> {t(ringing ? "Incoming call" : "Call")}
        </span>
        {call.state !== "ended" && (
          <button type="button" className="incoming-hide" onClick={close}>
            {t("Minimize call")}
          </button>
        )}
      </header>
      {!ringing && (
        <h2 id="incoming-call-title" className="sr-only">
          {t("Current call")}
        </h2>
      )}
      {ringing ? (
        <>
          <div className="incoming-caller-avatar" aria-hidden="true">
            {(call.caller || call.destination || "?")
              .split(/[\s.\-_]+/)
              .filter(Boolean)
              .slice(0, 2)
              .map((part) => part[0])
              .join("")
              .toUpperCase()}
          </div>
          <h2 id="incoming-call-title">{call.caller ?? t("Unknown caller")}</h2>
          <p className="incoming-account">
            {t("Incoming on")} {call.accountId}
          </p>
          <p className="incoming-state" role="status">
            {t(available ? callLabel(call.state) : "Call status unavailable")}
          </p>
          {call.state === "connected" && (
            <strong className="incoming-duration">
              {callTime(call.durationSeconds)}
            </strong>
          )}
          {call.reason && <p>{t(reasonLabel(call.reason))}</p>}
          {call.sipCode !== null && call.state === "ended" && (
            <small>SIP {call.sipCode}</small>
          )}
          {error && <p role="alert">{t(error)}</p>}
        </>
      ) : (
        <CallPanel
          session={call}
          sessions={rows}
          account={undefined}
          callsAvailable={available}
          callsBusy={busy}
          onRequest={request}
          initialTool={initialTool}
        />
      )}
      {ringing ? (
        <div className="incoming-actions">
          <button
            type="button"
            className="incoming-reject"
            disabled={busy || !available}
            onClick={() => void request("reject", { id: call.id })}
          >
            <span>
              <CallIcon name="hangup" />
            </span>
            {t("Decline")}
          </button>
          <button
            ref={answer}
            type="button"
            className="incoming-answer"
            disabled={busy || !available}
            onClick={() => void request("answer", { id: call.id })}
          >
            <span>
              <CallIcon name="phone" />
            </span>
            {t(busy ? "Answering…" : "Answer")}
          </button>
        </div>
      ) : call.state === "ended" ? (
        <button className="incoming-dismiss" onClick={close}>
          {t("Dismiss")}
        </button>
      ) : null}
      {!ringing && error && <p role="alert">{t(error)}</p>}
    </dialog>
  );
}
