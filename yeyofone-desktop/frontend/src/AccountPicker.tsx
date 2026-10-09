import { useLanguage } from "./i18n";
import type { Account } from "./useAccounts";
import type { RegistrationStatus } from "./registration";

type Props = {
  accounts: Account[];
  statuses: RegistrationStatus[];
  selected: string;
  onSelect: (id: string) => void;
  ready: boolean;
  available: boolean;
  loading: boolean;
  disabled: boolean;
  /** Only show the account's status, and only when it can't be used for calls. */
  statusOnly?: boolean;
};

export function AccountPicker({
  accounts,
  statuses,
  selected,
  onSelect,
  available,
  loading,
  disabled,
  statusOnly = false,
}: Props) {
  const { t } = useLanguage();
  const account = accounts.find((a) => a.id === selected);
  const status = statuses.find((s) => s.accountId === selected);

  const { tone, label } = describeStatus({
    loading,
    hasAccounts: accounts.length > 0,
    available,
    enabled: !!account?.enabled,
    registered: status?.state === "registered",
    t,
  });

  if (statusOnly) {
    if (tone === "ok") return null;
    return (
      <div className="account-picker account-picker--status-only">
        <span
          className={`status-pill status-pill--${tone}`}
          role="status"
          aria-live="polite"
        >
          <span className="status-pill__dot" aria-hidden="true" />
          {account ? `${account.label} · ${label}` : label}
        </span>
      </div>
    );
  }

  return (
    <div className="account-picker">
      <label className="account-picker__label" htmlFor="dialer-account">
        {t("SIP account")}
      </label>
      <div className="account-picker__row">
        <select
          id="dialer-account"
          className="account-picker__select"
          value={selected}
          onChange={(e) => onSelect(e.target.value)}
          disabled={disabled || loading || !accounts.length}
        >
          <option value="" disabled>
            {t(loading ? "Loading…" : "Select account")}
          </option>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.label} · {a.username}
              {a.enabled ? "" : ` · ${t("Disabled")}`}
            </option>
          ))}
        </select>
        <span
          className={`status-pill status-pill--${tone}`}
          role="status"
          aria-live="polite"
        >
          <span className="status-pill__dot" aria-hidden="true" />
          {label}
        </span>
      </div>
    </div>
  );
}

function describeStatus({
  loading,
  hasAccounts,
  available,
  enabled,
  registered,
  t,
}: {
  loading: boolean;
  hasAccounts: boolean;
  available: boolean;
  enabled: boolean;
  registered: boolean;
  t: (s: string) => string;
}): { tone: "ok" | "warn" | "err" | "muted"; label: string } {
  if (loading) return { tone: "muted", label: t("Loading…") };
  if (!hasAccounts) return { tone: "warn", label: t("No accounts") };
  if (!enabled) return { tone: "warn", label: t("Disabled") };
  if (!available) return { tone: "muted", label: t("Unavailable") };
  if (registered) return { tone: "ok", label: t("Registered") };
  return { tone: "err", label: t("Not registered") };
}
