import { useCalls } from "./useCalls";
import { callLabel, callTime, type CallStatus } from "./calls";
import { useLanguage } from "./i18n";
import { CallIcon } from "./CallIcon";
import { CallControlIcon } from "./CallControlIcon";
import "./call-bar.css";

export type CallTool = "transfer" | "consult" | "keypad";

// The current call: an outgoing call (not a consultation leg) first, then an incoming one.
export function currentCall(rows: CallStatus[]) {
  return (
    rows.find(
      (c) =>
        c.direction === "outgoing" &&
        c.state !== "ended" &&
        c.consultParentId === null,
    ) ?? rows.find((c) => c.direction === "incoming" && c.state !== "ended")
  );
}

// Compact controls shown at the bottom of the window while the full call
// screen is minimized. Tools that need more room (keypad, transfer) reopen
// the full screen with that tool open.
export function CallBar({
  hidden,
  onExpand,
}: {
  // True while a full call screen for this call is on screen.
  hidden: (call: CallStatus) => boolean;
  onExpand: (call: CallStatus, tool?: CallTool) => void;
}) {
  const { rows, available, busy, request } = useCalls();
  const { t } = useLanguage();
  const call = currentCall(rows);
  if (!call || hidden(call)) return null;

  const ringing = call.direction === "incoming" && call.state === "incoming";
  const connected = call.state === "connected";
  const consultation = rows.find(
    (c) => c.consultParentId === call.id && c.state !== "ended",
  );
  const speaking = consultation ?? call;
  const disabled = busy || !available || call.transferPending;
  const name = call.caller || call.destination;
  const initials =
    name
      .split(/[\s.\-_]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join("")
      .toUpperCase() || "?";
  const status = !available
    ? "Call status unavailable"
    : ringing
      ? "Incoming call"
      : connected && call.held
        ? "On hold"
        : connected
          ? callTime(call.durationSeconds)
          : callLabel(call.state);

  return (
    <section className="call-bar" aria-label={t("Current call")}>
      <button
        type="button"
        className="call-bar__who"
        onClick={() => onExpand(call)}
        title={t("Open call")}
      >
        <span className="call-bar__avatar" aria-hidden="true">
          {initials}
        </span>
        <span className="call-bar__text">
          <strong>{name}</strong>
          <small role="status">{t(status)}</small>
        </span>
      </button>

      <div
        className="call-bar__group"
        role="group"
        aria-label={t("Call controls")}
      >
        <BarButton
          label="Keypad"
          disabled={disabled || speaking.state !== "connected" || speaking.held}
          onClick={() => onExpand(call, "keypad")}
        >
          <CallIcon name="keypad" />
        </BarButton>
        <BarButton
          label={ringing ? "Decline" : connected ? "Hang up" : "Cancel call"}
          tone="end"
          disabled={busy || !available || call.state === "ending"}
          onClick={() =>
            void request(ringing ? "reject" : "hangup", { id: call.id })
          }
        >
          <CallIcon name="hangup" />
        </BarButton>
        {ringing && (
          <BarButton
            label="Answer"
            tone="answer"
            disabled={busy || !available}
            onClick={() => void request("answer", { id: call.id })}
          >
            <CallIcon name="phone" />
          </BarButton>
        )}
        <BarButton
          label={
            consultation
              ? "Cancel consultation and resume"
              : call.held
                ? "Resume"
                : "Hold"
          }
          pressed={call.held}
          disabled={disabled || !connected || consultation?.state === "ending"}
          onClick={() =>
            void request(
              consultation ? "consult_cancel" : call.held ? "resume" : "hold",
              {
                id: call.id,
                ...(consultation ? { consultId: consultation.id } : {}),
              },
            )
          }
        >
          <CallControlIcon name={call.held ? "resume" : "hold"} />
        </BarButton>
        <BarButton
          label="Consult transfer"
          disabled={disabled || !connected || !!consultation}
          onClick={() => onExpand(call, "consult")}
        >
          <CallControlIcon name="consult" />
        </BarButton>
        <BarButton
          label="Transfer"
          disabled={disabled || !connected || !!consultation}
          onClick={() => onExpand(call, "transfer")}
        >
          <CallControlIcon name="transfer" />
        </BarButton>
      </div>

      <div className="call-bar__group">
        <BarButton
          label={speaking.muted ? "Unmute" : "Mute"}
          pressed={speaking.muted}
          disabled={disabled || speaking.state !== "connected"}
          onClick={() =>
            void request("mute", { id: speaking.id, muted: !speaking.muted })
          }
        >
          <CallIcon name="mic" off={speaking.muted} />
        </BarButton>
        <BarButton label="Open call" onClick={() => onExpand(call)}>
          <CallIcon name="expand" />
        </BarButton>
      </div>
    </section>
  );
}

function BarButton({
  label,
  tone,
  pressed,
  disabled,
  onClick,
  children,
}: {
  label: string;
  tone?: "end" | "answer";
  pressed?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  const { t } = useLanguage();
  return (
    <button
      type="button"
      className={`call-bar__btn${tone ? ` call-bar__btn--${tone}` : ""}`}
      aria-label={t(label)}
      title={t(label)}
      aria-pressed={pressed}
      disabled={disabled}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
