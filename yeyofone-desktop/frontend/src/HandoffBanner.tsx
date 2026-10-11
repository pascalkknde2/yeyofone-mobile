import { useState } from "react";
import { useCalls } from "./useCalls";
import { useHandoffs } from "./useHandoffs";
import { useLanguage } from "./i18n";
import "./handoff-banner.css";

// "Continue call on this device?": offers a call that is live on another device of the same
// account. One click moves it here; the other party stays connected without noticing.
export function HandoffBanner({ onTakenOver }: { onTakenOver: () => void }) {
  const { offers, busy, error, takeOver } = useHandoffs();
  const { rows } = useCalls();
  const { t } = useLanguage();
  const [dismissed, setDismissed] = useState<ReadonlySet<string>>(new Set());
  const offer = offers.find((o) => !dismissed.has(o.id));
  // A call here takes priority; the offer comes back once it ends.
  if (!offer || rows.some((c) => c.state !== "ended")) return null;
  const who = offer.remoteName ?? offer.remote;
  return (
    <div
      className="handoff-banner"
      role="alertdialog"
      aria-labelledby="handoff-title"
    >
      <span className="handoff-banner__icon" aria-hidden="true">
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <rect x="2" y="4" width="14" height="10" rx="2" />
          <path d="M6 18h6M9 14v4" />
          <rect x="17" y="8" width="5" height="12" rx="1.5" />
        </svg>
      </span>
      <div className="handoff-banner__text">
        <strong id="handoff-title">{t("Continue call on this device?")}</strong>
        <span>
          {who} · {t("On your {device}").replace("{device}", offer.deviceName)}
        </span>
        {error && <span className="handoff-banner__error">{t(error)}</span>}
      </div>
      <button
        type="button"
        className="handoff-banner__dismiss"
        onClick={() => setDismissed(new Set(dismissed).add(offer.id))}
      >
        {t("Not now")}
      </button>
      <button
        type="button"
        className="handoff-banner__continue"
        disabled={busy}
        onClick={() => {
          void takeOver(offer).then((moved) => {
            if (moved) onTakenOver();
          });
        }}
      >
        {busy ? t("Moving call…") : t("Continue here")}
      </button>
    </div>
  );
}
