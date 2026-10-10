import { CallIcon } from "./CallIcon";
import { callLabel, callTime, reasonLabel, type CallStatus } from "./calls";
import { initials } from "./callGroup";
import { useLanguage } from "./i18n";
import "./call-screen.css";

// Outgoing call while it rings: same dark look as the in-call screen.
export function CallingScreen({
  session,
  account,
  busy,
  available,
  onCancel,
}: {
  session: CallStatus;
  account?: string;
  busy: boolean;
  available: boolean;
  onCancel: () => void;
}) {
  const { t } = useLanguage();
  return (
    <section className="call-screen" aria-label={t("Current call")}>
      <header className="call-screen__hero">
        <span className="call-screen__avatar is-pulsing" aria-hidden="true">
          {initials(session.destination)}
        </span>
        <h3 className="call-screen__name">{session.destination}</h3>
        {account && <p className="call-screen__account">{account}</p>}
        <p className="call-screen__status" role="status">
          {t(available ? callLabel(session.state) : "Call status unavailable")}
        </p>
      </header>
      <div className="call-screen__big-row">
        <BigButton
          label="Cancel call"
          tone="end"
          disabled={busy || !available}
          onClick={onCancel}
        >
          <CallIcon name="hangup" />
        </BigButton>
      </div>
    </section>
  );
}

// Shown in the dialer after an outgoing call ends, instead of jumping back to
// the number pad: how it ended, and a quick way to call again.
export function CallEnded({
  session,
  account,
  busy,
  onCallAgain,
  onClose,
}: {
  session: CallStatus;
  account?: string;
  busy: boolean;
  onCallAgain: () => void;
  onClose: () => void;
}) {
  const { t } = useLanguage();
  const talked = session.durationSeconds > 0;
  return (
    <section className="call-screen" aria-label={t("Call ended")}>
      <header className="call-screen__hero">
        <span className="call-screen__avatar is-ended" aria-hidden="true">
          {initials(session.destination)}
        </span>
        <h3 className="call-screen__name">{session.destination}</h3>
        {account && <p className="call-screen__account">{account}</p>}
        <p className="call-screen__status" role="status">
          {t("Call ended")}
          {talked && ` · ${callTime(session.durationSeconds)}`}
        </p>
        {session.reason && !talked && (
          <p className="call-screen__note">
            {t(reasonLabel(session.reason))}
            {session.sipCode != null && ` · SIP ${session.sipCode}`}
          </p>
        )}
      </header>
      <div className="call-screen__big-row">
        <BigButton label="Close" onClick={onClose}>
          <CallIcon name="close" />
        </BigButton>
        <BigButton
          label="Call again"
          tone="answer"
          disabled={busy}
          onClick={onCallAgain}
        >
          <CallIcon name="phone" />
        </BigButton>
      </div>
    </section>
  );
}

export function BigButton({
  label,
  tone,
  disabled,
  buttonRef,
  onClick,
  children,
}: {
  label: string;
  tone?: "end" | "answer";
  disabled?: boolean;
  buttonRef?: React.Ref<HTMLButtonElement>;
  onClick: () => void;
  children: React.ReactNode;
}) {
  const { t } = useLanguage();
  return (
    <button
      type="button"
      ref={buttonRef}
      className={`call-screen__big${tone ? ` call-screen__big--${tone}` : ""}`}
      disabled={disabled}
      onClick={onClick}
    >
      <span className="call-screen__big-icon">{children}</span>
      <span>{t(label)}</span>
    </button>
  );
}
