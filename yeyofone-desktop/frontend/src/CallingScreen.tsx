import { CallIcon } from "./CallIcon";
import { DIAL_KEYS } from "./Keypad";
import { callLabel, type CallStatus } from "./calls";
import { useLanguage } from "./i18n";

export function CallingScreen({
  session,
  busy,
  available,
  onCancel,
}: {
  session: CallStatus;
  busy: boolean;
  available: boolean;
  onCancel: () => void;
}) {
  const { t } = useLanguage();
  return (
    <section className="calling-screen" aria-label={t("Current call")}>
      <h3 className="calling-screen__number">{session.destination}</h3>
      <p className="calling-screen__status" role="status">
        {t(available ? callLabel(session.state) : "Call status unavailable")}
      </p>
      <div
        className="keypad calling-screen__keypad"
        role="group"
        aria-label={t("Keypad")}
      >
        {DIAL_KEYS.map(({ key, letters, long }) => (
          <button
            type="button"
            key={key}
            className="keypad__key"
            disabled
            aria-label={key}
          >
            <span className="keypad__digit">{key}</span>
            <span className="keypad__letters" aria-hidden="true">
              {letters ?? long ?? " "}
            </span>
          </button>
        ))}
      </div>
      <button
        type="button"
        className="dialer__submit dialer__submit--cancel"
        title={t("Cancel call")}
        aria-label={t("Cancel call")}
        disabled={busy || !available}
        onClick={onCancel}
      >
        <CallIcon name="hangup" />
      </button>
    </section>
  );
}
