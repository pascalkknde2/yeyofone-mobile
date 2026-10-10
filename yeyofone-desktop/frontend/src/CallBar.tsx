import type { ReactNode } from "react";
import { useCalls } from "./useCalls";
import { callLabel, callTime, type CallStatus } from "./calls";
import { callGroup, callName, initials } from "./callGroup";
import { useLanguage } from "./i18n";
import { CallIcon } from "./CallIcon";
import { CallControlIcon } from "./CallControlIcon";
import "./call-bar.css";

export type CallTool = "transfer" | "consult" | "keypad";

// Compact controls shown at the bottom of the window while the full call
// screen is minimized. Tools that need more room (keypad, add call,
// transfer) reopen the full screen with that tool open.
export function CallBar({
  hidden,
  onExpand,
}: {
  // True while a full call screen for these calls is on screen.
  hidden: (calls: CallStatus[]) => boolean;
  onExpand: (calls: CallStatus[], tool?: CallTool) => void;
}) {
  const { rows, available, busy, request } = useCalls();
  const { t } = useLanguage();
  const group = callGroup(rows);
  if (!group) return null;
  const calls =
    group.kind === "single"
      ? [group.call]
      : group.kind === "consult"
        ? [group.held, group.active]
        : group.calls;
  if (hidden(calls)) return null;

  const main = calls[0]!;
  const talking = group.kind === "consult" ? group.active : main;
  const merged = group.kind === "merged";
  const ringing = main.direction === "incoming" && main.state === "incoming";
  const connected = talking.state === "connected";
  const disabled = busy || !available || main.transferPending;
  const muted = merged ? calls.every((c) => c.muted) : talking.muted;
  const single = group.kind === "single";
  const name =
    group.kind === "merged"
      ? t("Conference")
      : group.kind === "consult"
        ? t("2 calls")
        : callName(main);
  const status = !available
    ? t("Call status unavailable")
    : ringing
      ? t("Incoming call")
      : group.kind === "consult"
        ? `${t("Added")}: ${callName(talking)} · ${t(
            connected
              ? callTime(talking.durationSeconds)
              : callLabel(talking.state),
          )}`
        : connected && main.held
          ? t("On hold")
          : connected
            ? callTime(Math.max(...calls.map((c) => c.durationSeconds)))
            : t(callLabel(main.state));
  const each = (action: string, extra: Record<string, unknown> = {}) => {
    for (const call of merged ? calls : [talking])
      void request(action, { id: call.id, ...extra });
  };

  return (
    <section className="call-bar" aria-label={t("Current call")}>
      <button
        type="button"
        className="call-bar__who"
        onClick={() => onExpand(calls)}
        title={t("Open call")}
      >
        <span className="call-bar__avatar" aria-hidden="true">
          {single ? initials(name) : calls.length}
        </span>
        <span className="call-bar__text">
          <strong>{name}</strong>
          <small role="status">{status}</small>
        </span>
      </button>

      <div
        className="call-bar__group"
        role="group"
        aria-label={t("Call controls")}
      >
        <BarButton
          label="Keypad"
          disabled={disabled || !connected || talking.held}
          onClick={() => onExpand(calls, "keypad")}
        >
          <CallIcon name="keypad" />
        </BarButton>
        <BarButton
          label={
            ringing
              ? "Decline"
              : merged
                ? "Hang up all"
                : group.kind === "consult"
                  ? "End added call"
                  : connected
                    ? "Hang up"
                    : "Cancel call"
          }
          tone="end"
          disabled={busy || !available || talking.state === "ending"}
          onClick={() =>
            group.kind === "consult"
              ? void request("consult_cancel", {
                  id: group.held.id,
                  consultId: group.active.id,
                })
              : ringing
                ? void request("reject", { id: main.id })
                : each("hangup")
          }
        >
          <CallIcon name="hangup" />
        </BarButton>
        {ringing && (
          <BarButton
            label="Answer"
            tone="answer"
            disabled={busy || !available}
            onClick={() => void request("answer", { id: main.id })}
          >
            <CallIcon name="phone" />
          </BarButton>
        )}
        {group.kind === "consult" ? (
          <BarButton
            label="Merge calls"
            tone="merge"
            disabled={disabled || group.active.state !== "connected"}
            onClick={() =>
              void request("consult_merge", {
                id: group.held.id,
                consultId: group.active.id,
              })
            }
          >
            <CallControlIcon name="consult" />
          </BarButton>
        ) : (
          <>
            <BarButton
              label={main.held ? "Resume" : "Hold"}
              pressed={main.held}
              disabled={disabled || !single || main.state !== "connected"}
              onClick={() =>
                void request(main.held ? "resume" : "hold", { id: main.id })
              }
            >
              <CallControlIcon name={main.held ? "resume" : "hold"} />
            </BarButton>
            <BarButton
              label="Add call"
              disabled={disabled || !single || !connected || main.held}
              onClick={() => onExpand(calls, "consult")}
            >
              <CallControlIcon name="consult" />
            </BarButton>
            <BarButton
              label="Transfer"
              disabled={disabled || !single || !connected}
              onClick={() => onExpand(calls, "transfer")}
            >
              <CallControlIcon name="transfer" />
            </BarButton>
          </>
        )}
      </div>

      <div className="call-bar__group">
        <BarButton
          label={muted ? "Unmute" : "Mute"}
          pressed={muted}
          disabled={disabled || !connected}
          onClick={() => each("mute", { muted: !muted })}
        >
          <CallIcon name="mic" off={muted} />
        </BarButton>
        <BarButton label="Open call" onClick={() => onExpand(calls)}>
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
  tone?: "end" | "answer" | "merge";
  pressed?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
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
