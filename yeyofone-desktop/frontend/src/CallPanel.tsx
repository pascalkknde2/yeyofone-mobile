import { useId, useRef, useState, type ReactNode } from "react";
import { CallControlIcon } from "./CallControlIcon";
import { CallIcon } from "./CallIcon";
import "./call-screen.css";
import { useLanguage } from "./i18n";
import { callLabel, reasonLabel, callTime } from "./calls";
import { callGroup, callName, initials } from "./callGroup";
import { moveInMenu, useCloseOnOutsideClick } from "./menuKeys";
import type { Account } from "./useAccounts";
import type { CallStatus as Call } from "./calls";
type CallRequest = (
  action: string,
  extra?: Record<string, unknown>,
) => Promise<boolean>;
// "consult" is the Add call form (it starts a consultation call).
type Tool = "transfer" | "consult" | "keypad";

type Props = {
  session: Call;
  sessions: Call[];
  account: Account | undefined;
  callsAvailable: boolean;
  callsBusy: boolean;
  onRequest: CallRequest;
  // Open with this tool showing (used when the minimized call bar expands).
  initialTool?: Tool;
};

export function CallPanel({
  session,
  sessions,
  account,
  callsAvailable,
  callsBusy,
  onRequest,
  initialTool,
}: Props) {
  const { t } = useLanguage();
  const [tool, setTool] = useState<Tool | null>(initialTool ?? null);
  const [moreOpen, setMoreOpen] = useState(false);
  const [destination, setDestination] = useState("");
  const [digits, setDigits] = useState("");
  const inputId = useId();
  const moreBox = useRef<HTMLDivElement>(null);
  const moreButton = useRef<HTMLButtonElement>(null);
  useCloseOnOutsideClick(moreOpen, moreBox, () => setMoreOpen(false));

  // An ended call keeps its own summary; otherwise show the live group.
  const live = session.state === "ended" ? null : callGroup(sessions);
  const group = live ?? { kind: "single" as const, call: session };
  const main =
    group.kind === "single"
      ? group.call
      : group.kind === "consult"
        ? group.held
        : group.calls[0];
  // The call you are talking on: the new call during a consultation.
  const talking = group.kind === "consult" ? group.active : main;
  const merged = group.kind === "merged" ? group.calls : null;
  const connected = talking.state === "connected";
  const ended = main.state === "ended";
  const disabled = callsBusy || !callsAvailable || main.transferPending;
  const muted = merged ? merged.every((c) => c.muted) : talking.muted;

  function toggle(next: Tool) {
    setMoreOpen(false);
    setTool((current) => (current === next ? null : next));
  }
  async function submitDestination() {
    const ok = await onRequest(
      tool === "consult" ? "consult_start" : "transfer",
      {
        id: main.id,
        destination: destination.trim(),
        ...(tool === "consult" ? { consultId: crypto.randomUUID() } : {}),
      },
    );
    if (ok) {
      setTool(null);
      setDestination("");
    }
  }
  async function forEachCall(
    action: string,
    extra: Record<string, unknown> = {},
  ) {
    for (const call of merged ?? [talking])
      await onRequest(action, { id: call.id, ...extra });
  }
  const status = (call: Call) =>
    !callsAvailable
      ? "Call status unavailable"
      : call.state === "connected" && call.held
        ? "On hold"
        : call.state === "connected"
          ? callTime(call.durationSeconds)
          : callLabel(call.state);

  return (
    <section
      className="call-screen"
      aria-label={t("Current call")}
      aria-live="polite"
    >
      {group.kind === "single" ? (
        <header className="call-screen__hero">
          <span className="call-screen__avatar" aria-hidden="true">
            {initials(callName(main))}
          </span>
          <h3 className="call-screen__name">{callName(main)}</h3>
          <p className="call-screen__account">
            {account?.label ?? main.accountId}
          </p>
          <p className="call-screen__status" role="status">
            {t(status(main))}
            {main.recording && (
              <span className="call-screen__rec">● {t("Recording")}</span>
            )}
          </p>
          {main.reason && (
            <p className="call-screen__note">
              {t(reasonLabel(main.reason))}
              {main.sipCode != null && ended && ` · SIP ${main.sipCode}`}
            </p>
          )}
        </header>
      ) : (
        <header className="call-screen__people">
          <h3 className="call-screen__title">
            {group.kind === "merged"
              ? `${t("Conference")} · ${t("2 people")}`
              : t("2 calls")}
            {group.kind === "merged" && (
              <span>
                {callTime(
                  Math.max(...group.calls.map((c) => c.durationSeconds)),
                )}
              </span>
            )}
          </h3>
          <ul>
            {(group.kind === "merged"
              ? group.calls
              : [group.held, group.active]
            ).map((call) => (
              <li key={call.id} className={call.held ? "is-held" : ""}>
                <span className="call-screen__avatar" aria-hidden="true">
                  {initials(callName(call))}
                </span>
                <span className="call-screen__who">
                  <strong>{callName(call)}</strong>
                  <small role="status">{t(status(call))}</small>
                </span>
                {group.kind === "merged" ? (
                  <button
                    type="button"
                    className="call-screen__end-one"
                    disabled={callsBusy || !callsAvailable}
                    onClick={() => void onRequest("hangup", { id: call.id })}
                  >
                    {t("End")}
                  </button>
                ) : (
                  call === group.active && (
                    <button
                      type="button"
                      className="call-screen__end-one"
                      disabled={disabled || call.state === "ending"}
                      onClick={() =>
                        void onRequest("consult_cancel", {
                          id: group.held.id,
                          consultId: call.id,
                        })
                      }
                    >
                      {t("End")}
                    </button>
                  )
                )}
              </li>
            ))}
          </ul>
          {group.kind === "consult" && (
            <button
              type="button"
              className="call-screen__merge"
              disabled={disabled || group.active.state !== "connected"}
              onClick={() =>
                void onRequest("consult_merge", {
                  id: group.held.id,
                  consultId: group.active.id,
                })
              }
            >
              <CallControlIcon name="consult" />
              {t("Merge calls")}
            </button>
          )}
        </header>
      )}

      {talking.audioError && (
        <p className="call-screen__alert" role="alert">
          {t("Audio unavailable. Check your microphone and speaker.")}
        </p>
      )}
      {connected &&
        !talking.held &&
        !talking.audioActive &&
        !talking.audioError && (
          <p className="call-screen__note">{t("Audio is connecting…")}</p>
        )}
      {main.transferPending && <p role="status">{t("Transferring…")}</p>}
      {!main.transferPending && main.transferCode >= 300 && (
        <p className="call-screen__alert" role="alert">
          {t("Transfer failed. You can retry or resume the call.")} (SIP{" "}
          {main.transferCode})
        </p>
      )}

      {!ended &&
        (tool === "transfer" || tool === "consult") &&
        group.kind === "single" && (
          <form
            className="call-screen__tool"
            onSubmit={(e) => {
              e.preventDefault();
              void submitDestination();
            }}
          >
            <label htmlFor={inputId}>
              {t(tool === "consult" ? "Add call" : "Transfer")}
            </label>
            <div className="call-screen__destination">
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
                disabled={disabled || !destination.trim() || !connected}
              >
                {t(tool === "consult" ? "Call" : "Transfer")}
              </button>
              <button
                type="button"
                className="call-screen__icon"
                aria-label={t("Close")}
                title={t("Close")}
                onClick={() => setTool(null)}
              >
                <CallIcon name="close" />
              </button>
            </div>
            {tool === "consult" && (
              <small>
                {t("The current call waits on hold while you call.")}
              </small>
            )}
          </form>
        )}

      {!ended && tool === "keypad" && (
        <section className="call-screen__tool" aria-label={t("Keypad")}>
          <output className="call-screen__digits" aria-label={t("Sent digits")}>
            {digits || " "}
          </output>
          <div className="call-screen__keypad">
            {["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map(
              (digit) => (
                <button
                  key={digit}
                  type="button"
                  disabled={disabled || !connected || talking.held}
                  onClick={async () => {
                    if (
                      await onRequest("dtmf", { id: talking.id, digits: digit })
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

      {!ended && (
        <div
          className="call-screen__controls"
          role="group"
          aria-label={t("Call controls")}
        >
          <Control
            label={muted ? "Unmute" : "Mute"}
            pressed={muted}
            disabled={disabled || !connected}
            onClick={() => void forEachCall("mute", { muted: !muted })}
          >
            <CallIcon name="mic" off={muted} />
          </Control>
          <Control
            label="Keypad"
            pressed={tool === "keypad"}
            disabled={disabled || !connected || talking.held}
            onClick={() => toggle("keypad")}
          >
            <CallIcon name="keypad" />
          </Control>
          <Control
            label={main.held && group.kind === "single" ? "Resume" : "Hold"}
            pressed={main.held && group.kind === "single"}
            disabled={
              disabled || group.kind !== "single" || main.state !== "connected"
            }
            onClick={() =>
              void onRequest(main.held ? "resume" : "hold", { id: main.id })
            }
          >
            <CallControlIcon name={main.held ? "resume" : "hold"} />
          </Control>
          <Control
            label="Add call"
            pressed={tool === "consult"}
            disabled={
              disabled || group.kind !== "single" || !connected || main.held
            }
            onClick={() => toggle("consult")}
          >
            <AddCallIcon />
          </Control>
          <div className="call-screen__more" ref={moreBox}>
            <Control
              label="More"
              pressed={moreOpen}
              buttonRef={moreButton}
              disabled={callsBusy || !callsAvailable || group.kind === "merged"}
              popup
              onClick={() => setMoreOpen(!moreOpen)}
            >
              <MoreIcon />
            </Control>
            {moreOpen && (
              <div
                className="call-screen__menu"
                role="menu"
                aria-label={t("More")}
                onKeyDown={(e) => {
                  if (e.key === "Escape") {
                    e.preventDefault();
                    e.stopPropagation();
                    setMoreOpen(false);
                    moreButton.current?.focus();
                  } else moveInMenu(e, () => setMoreOpen(false));
                }}
              >
                {group.kind === "single" && (
                  <button
                    role="menuitem"
                    autoFocus
                    disabled={disabled || !connected}
                    onClick={() => toggle("transfer")}
                  >
                    <CallControlIcon name="transfer" />
                    {t("Transfer")}
                  </button>
                )}
                {group.kind === "consult" && (
                  <button
                    role="menuitem"
                    autoFocus
                    disabled={disabled || group.active.state !== "connected"}
                    onClick={() => {
                      setMoreOpen(false);
                      void onRequest("consult_complete", {
                        id: group.held.id,
                        consultId: group.active.id,
                      });
                    }}
                  >
                    <CallControlIcon name="complete" />
                    {`${t("Transfer to")} ${callName(group.active)}`}
                  </button>
                )}
                {group.kind === "single" && (
                  <button
                    role="menuitem"
                    disabled={
                      disabled || !connected || (!main.recording && main.held)
                    }
                    onClick={() => {
                      setMoreOpen(false);
                      void onRequest(
                        main.recording ? "record_stop" : "record_start",
                        { id: main.id },
                      );
                    }}
                  >
                    <span className="call-screen__rec-dot" aria-hidden="true" />
                    {t(main.recording ? "Stop recording" : "Start recording")}
                  </button>
                )}
              </div>
            )}
          </div>
          <Control
            label={
              merged
                ? "Hang up all"
                : group.kind === "consult"
                  ? "End added call"
                  : connected
                    ? "Hang up"
                    : "Cancel call"
            }
            tone="end"
            disabled={
              callsBusy || !callsAvailable || talking.state === "ending"
            }
            onClick={() =>
              void (group.kind === "consult"
                ? onRequest("consult_cancel", {
                    id: group.held.id,
                    consultId: group.active.id,
                  })
                : forEachCall("hangup"))
            }
          >
            <CallIcon name="hangup" />
          </Control>
        </div>
      )}
    </section>
  );
}

function Control({
  label,
  tone,
  pressed,
  disabled,
  popup,
  buttonRef,
  onClick,
  children,
}: {
  label: string;
  tone?: "end";
  pressed?: boolean;
  disabled?: boolean;
  popup?: boolean;
  buttonRef?: React.Ref<HTMLButtonElement>;
  onClick: () => void;
  children: ReactNode;
}) {
  const { t } = useLanguage();
  return (
    <button
      type="button"
      ref={buttonRef}
      className={`call-screen__control${tone ? ` call-screen__control--${tone}` : ""}`}
      aria-pressed={popup ? undefined : pressed}
      aria-haspopup={popup ? "menu" : undefined}
      aria-expanded={popup ? pressed : undefined}
      disabled={disabled}
      onClick={onClick}
    >
      <span className="call-screen__control-icon">{children}</span>
      <span className="call-screen__control-label">{t(label)}</span>
    </button>
  );
}

// Icon paths from Lucide (https://lucide.dev, ISC License); see LICENSES-THIRD-PARTY.md.
function AddCallIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
      <path d="M19 2v6" />
      <path d="M16 5h6" />
    </svg>
  );
}
function MoreIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="1" />
      <circle cx="19" cy="12" r="1" />
      <circle cx="5" cy="12" r="1" />
    </svg>
  );
}
