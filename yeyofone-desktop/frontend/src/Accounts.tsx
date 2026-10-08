import {
  parseRegistrations,
  registrationPresentation,
  type RegistrationStatus,
} from "./registration";
import { useEffect, useState, useRef } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { useLanguage } from "./i18n";
import "./accounts.css";
type Account = {
  id: string;
  label: string;
  username: string;
  host: string;
  port: number;
  transport: string;
  enabled: boolean;
  stunServer: string | null;
  ice: boolean;
  turnServer: string | null;
  turnUsername: string | null;
};
const blank = (): Account => ({
  id: crypto.randomUUID(),
  label: "",
  username: "",
  host: "",
  port: 5060,
  transport: "udp",
  enabled: true,
  stunServer: null,
  ice: false,
  turnServer: null,
  turnUsername: null,
});
export function Accounts() {
  const { t } = useLanguage();
  const [rows, setRows] = useState<Account[]>([]),
    [form, setForm] = useState<Account | null>(null),
    [password, setPassword] = useState(""),
    [turnPassword, setTurnPassword] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const [registrations, setRegistrations] = useState<RegistrationStatus[]>([]),
    [registrationAvailable, setRegistrationAvailable] = useState(false);
  const registrationGeneration = useRef(0),
    registrationPending = useRef(false);
  async function registration(action: string, id?: string) {
    const generation = ++registrationGeneration.current,
      requestId = crypto.randomUUID();
    if (action !== "status") {
      setBusy(true);
      setError("");
      registrationPending.current = true;
    }
    try {
      const wire = await invoke<unknown>("registration_command", {
        request: { schemaVersion: 1, requestId, action, ...(id ? { id } : {}) },
      });
      const result = parseRegistrations(wire, requestId);
      if (generation === registrationGeneration.current) {
        setRegistrations(result);
        setRegistrationAvailable(true);
      }
      return true;
    } catch {
      if (generation === registrationGeneration.current)
        setRegistrationAvailable(false);
      if (action !== "status")
        setError(
          t("Registration operation failed. Check the account and try again."),
        );
      return false;
    } finally {
      if (action !== "status") {
        setBusy(false);
        registrationPending.current = false;
      }
    }
  }
  useEffect(() => {
    if (!isTauri()) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      if (!registrationPending.current) await registration("status");
      if (active) timer = setTimeout(() => void poll(), 1000);
    }
    void poll();
    return () => {
      active = false;
      clearTimeout(timer);
      ++registrationGeneration.current;
    };
  }, []);
  async function command(action: string, extra: Record<string, unknown> = {}) {
    setBusy(true);
    setError("");
    try {
      const result = await invoke<{
        schemaVersion: number;
        requestId: string;
        data: Account[];
      }>("accounts_command", {
        request: {
          schemaVersion: 1,
          requestId: crypto.randomUUID(),
          action,
          ...extra,
        },
      });
      if (result.schemaVersion !== 1 || !Array.isArray(result.data))
        throw new Error();
      setRows(result.data);
      return true;
    } catch (e) {
      setError(
        e === "account_in_call"
          ? t("End the active call before changing this account.")
          : e === "vault_unavailable"
            ? t("Keychain is unavailable. Unlock it and try again.")
            : t("Account operation failed. Check the fields and try again."),
      );
      return false;
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (isTauri()) void command("list");
  }, []);
  function edit(account: Account) {
    setPassword("");
    setTurnPassword("");
    setForm({ ...account });
    setError("");
  }
  const original = form ? rows.find((a) => a.id === form.id) : undefined;
  const identityChanged =
    !!form &&
    !!original &&
    (form.username !== original.username ||
      form.host.toLowerCase() !== original.host.toLowerCase() ||
      form.port !== original.port ||
      form.transport !== original.transport);
  const turnIdentityChanged =
    !!form?.turnServer &&
    (!original ||
      form.turnServer !== original.turnServer ||
      form.turnUsername !== original.turnUsername);
  if (!isTauri())
    return (
      <section className="accounts-panel">
        <h2>{t("SIP accounts")}</h2>
        <p>{t("Open the desktop app to manage secure SIP accounts.")}</p>
      </section>
    );
  return (
    <section className="accounts-panel">
      <div className="section-heading">
        <h2>{t("SIP accounts")}</h2>
        <button
          className="account-primary"
          disabled={busy}
          onClick={() => edit(blank())}
        >
          {t("Add account")}
        </button>
      </div>
      <p>
        {t(
          "Passwords stay in your OS vault. Enabled accounts register automatically.",
        )}
      </p>
      {error && <p role="alert">{error}</p>}
      <p>{t("Password cleanup retries when Keychain is available.")}</p>
      {!registrationAvailable && (
        <p role="status">{t("SIP registration is currently unavailable.")}</p>
      )}
      <div className="account-list">
        {rows.map((a) => {
          const status = registrations.find((s) => s.accountId === a.id);
          const presentation = registrationPresentation(
            status,
            a.enabled,
            registrationAvailable,
          );
          return (
            <article key={a.id}>
              <div>
                <strong>{a.label}</strong>
                <p>
                  {a.username}@{a.host}:{a.port} · {a.transport.toUpperCase()}
                </p>
                <span
                  className={`registration-badge ${presentation.tone}`}
                  role="status"
                >
                  {t(presentation.label)}
                </span>
                {presentation.error && (
                  <p className="registration-error">{t(presentation.error)}</p>
                )}
                {status?.retryInSeconds != null && (
                  <p>
                    {t("Retry in")} {status.retryInSeconds} {t("seconds")}
                  </p>
                )}
                {status?.sipCode != null && <small>SIP {status.sipCode}</small>}
              </div>
              <div className="account-actions">
                {presentation.canConnect && (
                  <button
                    className="account-primary"
                    disabled={busy || !registrationAvailable}
                    onClick={() =>
                      void registration(
                        status?.state === "registered" ? "refresh" : "register",
                        a.id,
                      )
                    }
                  >
                    {t(presentation.connectLabel)}
                  </button>
                )}
                {presentation.canDisconnect && (
                  <button
                    disabled={busy || !registrationAvailable}
                    onClick={() => void registration("unregister", a.id)}
                  >
                    {t("Unregister")}
                  </button>
                )}
                <button disabled={busy} onClick={() => edit(a)}>
                  {t("Details / Edit")}
                </button>
                <button
                  disabled={busy}
                  onClick={() =>
                    void command("save", {
                      account: { ...a, enabled: !a.enabled },
                    })
                  }
                >
                  {t(a.enabled ? "Disable" : "Enable")}
                </button>
                <button
                  disabled={busy}
                  onClick={() => {
                    if (
                      window.confirm(
                        t("Delete this account and its stored passwords?"),
                      )
                    )
                      void command("delete", { id: a.id });
                  }}
                >
                  {t("Delete")}
                </button>
              </div>
            </article>
          );
        })}
      </div>
      {!rows.length && !busy && <p>{t("No SIP accounts saved.")}</p>}
      {form && (
        <form
          className="account-form"
          onSubmit={async (e) => {
            e.preventDefault();
            const secret = password,
              turn = turnPassword;
            setPassword("");
            setTurnPassword("");
            if (
              await command("save", {
                account: form,
                ...(secret ? { password: secret } : {}),
                ...(turn ? { turnPassword: turn } : {}),
              })
            )
              setForm(null);
          }}
        >
          <h3>
            {t(
              rows.some((a) => a.id === form.id)
                ? "Edit account"
                : "Add account",
            )}
          </h3>
          {(["label", "username", "host"] as const).map((key) => (
            <label key={key}>
              {t(
                {
                  label: "Account name",
                  username: "SIP username",
                  host: "SIP server",
                }[key],
              )}
              <input
                required
                maxLength={key === "host" ? 253 : 128}
                value={form[key]}
                autoComplete="off"
                onChange={(e) => setForm({ ...form, [key]: e.target.value })}
              />
            </label>
          ))}
          <label>
            {t("Port")}
            <input
              required
              type="number"
              min={1}
              max={65535}
              value={form.port}
              onChange={(e) =>
                setForm({ ...form, port: Number(e.target.value) })
              }
            />
          </label>
          <label>
            {t("Transport")}
            <select
              value={form.transport}
              onChange={(e) =>
                setForm({
                  ...form,
                  transport: e.target.value,
                  port: e.target.value === "tls" ? 5061 : 5060,
                })
              }
            >
              {["udp", "tcp", "tls"].map((s) => (
                <option key={s} value={s}>
                  {s.toUpperCase()}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t("SIP password")}
            <input
              type="password"
              maxLength={4096}
              autoComplete="new-password"
              required={!rows.some((a) => a.id === form.id) || identityChanged}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <small>
              {t(
                identityChanged
                  ? "Enter the password again when changing connection details."
                  : "Leave blank to keep the stored password.",
              )}
            </small>
          </label>
          <label>
            {t("STUN server")}
            <input
              maxLength={253}
              value={form.stunServer ?? ""}
              onChange={(e) =>
                setForm({ ...form, stunServer: e.target.value || null })
              }
            />
          </label>
          <label className="account-check">
            <input
              type="checkbox"
              checked={form.ice}
              onChange={(e) => setForm({ ...form, ice: e.target.checked })}
            />
            {t("Enable ICE")}
          </label>
          <label>
            {t("TURN server")}
            <input
              maxLength={253}
              value={form.turnServer ?? ""}
              onChange={(e) =>
                setForm({
                  ...form,
                  turnServer: e.target.value || null,
                  turnUsername: e.target.value ? form.turnUsername : null,
                })
              }
            />
          </label>
          {form.turnServer && (
            <>
              <label>
                {t("TURN username")}
                <input
                  required
                  maxLength={128}
                  value={form.turnUsername ?? ""}
                  onChange={(e) =>
                    setForm({ ...form, turnUsername: e.target.value || null })
                  }
                />
              </label>
              <label>
                {t("TURN password")}
                <input
                  type="password"
                  required={turnIdentityChanged}
                  maxLength={4096}
                  autoComplete="new-password"
                  value={turnPassword}
                  onChange={(e) => setTurnPassword(e.target.value)}
                />
              </label>
            </>
          )}
          <div className="account-actions">
            <button className="account-primary" disabled={busy}>
              {t(busy ? "Saving…" : "Save account")}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setForm(null);
                setPassword("");
                setTurnPassword("");
              }}
            >
              {t("Cancel")}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
