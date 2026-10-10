import { useEffect, useRef } from "react";
import { listen } from "@tauri-apps/api/event";
import { useCalls } from "./useCalls";
import { callLabel } from "./calls";
import { callName, initials } from "./callGroup";
import { BigButton } from "./CallingScreen";
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
        <section className="call-screen" aria-label={t("Incoming call")}>
          <header className="call-screen__hero">
            <span className="call-screen__avatar is-pulsing" aria-hidden="true">
              {initials(callName(call))}
            </span>
            <h2 id="incoming-call-title" className="call-screen__name">
              {call.caller ?? t("Unknown caller")}
            </h2>
            <p className="call-screen__account">
              {t("Incoming on")} {call.accountId}
            </p>
            <p className="call-screen__status" role="status">
              {t(available ? callLabel(call.state) : "Call status unavailable")}
            </p>
            {error && (
              <p className="call-screen__alert" role="alert">
                {t(error)}
              </p>
            )}
          </header>
          <div className="call-screen__big-row">
            <BigButton
              label="Decline"
              tone="end"
              disabled={busy || !available}
              onClick={() => void request("reject", { id: call.id })}
            >
              <CallIcon name="hangup" />
            </BigButton>
            <BigButton
              label={busy ? "Answering…" : "Answer"}
              tone="answer"
              buttonRef={answer}
              disabled={busy || !available}
              onClick={() => void request("answer", { id: call.id })}
            >
              <CallIcon name="phone" />
            </BigButton>
          </div>
        </section>
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
      {call.state === "ended" ? (
        <button className="incoming-dismiss" onClick={close}>
          {t("Dismiss")}
        </button>
      ) : null}
      {!ringing && error && <p role="alert">{t(error)}</p>}
    </dialog>
  );
}
