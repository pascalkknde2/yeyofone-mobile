import { useEffect, useMemo, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { useLanguage } from "./i18n";
import { useCalls } from "./useCalls";
import { callLabel, callTime } from "./calls";
import { parseRegistrations, type RegistrationStatus } from "./registration";
import "./voicemail.css";

type Account = {
  id: string;
  label: string;
  username: string;
  enabled: boolean;
};
const dialpad = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"];

export function Voicemail() {
  const { t } = useLanguage();
  const calls = useCalls();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [registrations, setRegistrations] = useState<RegistrationStatus[]>([]);
  const [accountId, setAccountId] = useState(
    () => localStorage.getItem("yeyofone.voicemail.account") ?? "",
  );
  const [accessCode, setAccessCode] = useState(
    () => localStorage.getItem("yeyofone.voicemail.accessCode") ?? "*97",
  );
  const [callId, setCallId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    async function load() {
      if (!isTauri()) return;
      try {
        const requestId = crypto.randomUUID();
        const response = await invoke<{
          schemaVersion: number;
          requestId: string;
          sequence: number;
          data: Account[];
        }>("accounts_command", {
          request: { schemaVersion: 1, requestId, action: "list" },
        });
        if (
          response.schemaVersion !== 1 ||
          response.requestId !== requestId ||
          response.sequence !== 0 ||
          !Array.isArray(response.data)
        )
          throw Error();
        const regId = crypto.randomUUID();
        const status = parseRegistrations(
          await invoke("registration_command", {
            request: { schemaVersion: 1, requestId: regId, action: "status" },
          }),
          regId,
        );
        if (active) {
          setAccounts(response.data);
          setRegistrations(status);
          setAccountId((old) =>
            response.data.some((a) => a.id === old)
              ? old
              : (response.data.find(
                  (a) =>
                    a.enabled &&
                    status.some(
                      (s) => s.accountId === a.id && s.state === "registered",
                    ),
                )?.id ?? ""),
          );
        }
      } catch {
        if (active) setError("Unable to load SIP accounts.");
      }
    }
    void load();
    const timer = window.setInterval(() => void load(), 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const selected = accounts.find((a) => a.id === accountId);
  const registered = registrations.some(
    (s) => s.accountId === accountId && s.state === "registered",
  );
  const call = useMemo(
    () => (callId ? calls.rows.find((row) => row.id === callId) : undefined),
    [callId, calls.rows],
  );
  const activeCall = calls.rows.find((row) => row.state !== "ended");
  const validCode = /^[0-9*#]{2,32}$/.test(accessCode);

  async function openMailbox() {
    if (!selected || !registered || !validCode || activeCall || busy) return;
    setBusy(true);
    setError("");
    localStorage.setItem("yeyofone.voicemail.account", selected.id);
    localStorage.setItem("yeyofone.voicemail.accessCode", accessCode);
    const id = crypto.randomUUID();
    setCallId(id);
    const ok = await calls.request("dial", {
      id,
      accountId: selected.id,
      destination: accessCode,
    });
    if (!ok) {
      setError(
        "Unable to call the voicemail service. Check the mailbox access code.",
      );
      setCallId(null);
    }
    setBusy(false);
  }

  async function sendDigit(digit: string) {
    if (!call || call.state !== "connected" || calls.busy) return;
    const ok = await calls.request("dtmf", { id: call.id, digits: digit });
    if (!ok) setError("The voicemail system did not accept that keypad tone.");
  }

  async function endCall() {
    if (!call || call.state === "ended") return;
    setBusy(true);
    await calls.request("hangup", { id: call.id });
    setBusy(false);
  }

  const live = call && call.state !== "ended";
  const connected = call?.state === "connected";
  const canOpen =
    !!selected &&
    registered &&
    validCode &&
    !activeCall &&
    !busy &&
    calls.available;

  return (
    <section className="standalone voicemail-page">
      <header className="section-heading">
        <div>
          <h1>{t("Voicemail")}</h1>
          <p>{t("Listen to messages in your SIP mailbox")}</p>
        </div>
      </header>
      <div className="voicemail-layout">
        {!live ? (
          <div className="voicemail-card">
            <div className="voicemail-card__head">
              <span className="voicemail-badge">
                <VoicemailIcon name="voicemail" size={24} />
              </span>
              <div>
                <h2>{t("Call your mailbox")}</h2>
                <p>{t("Your messages play over a normal call.")}</p>
              </div>
            </div>
            <label className="voicemail-field">
              <span>{t("SIP account")}</span>
              <select
                value={accountId}
                onChange={(event) => {
                  setAccountId(event.target.value);
                  localStorage.setItem(
                    "yeyofone.voicemail.account",
                    event.target.value,
                  );
                }}
              >
                <option value="">{t("Select account")}</option>
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.label} · {account.username}
                  </option>
                ))}
              </select>
              {selected && (
                <span
                  className={`voicemail-registration${registered ? " is-registered" : ""}`}
                >
                  <i />
                  {t(registered ? "Registered" : "Not registered")}
                </span>
              )}
            </label>
            <label className="voicemail-field">
              <span>{t("Voicemail access code")}</span>
              <input
                className="voicemail-code"
                value={accessCode}
                onChange={(event) => setAccessCode(event.target.value)}
                maxLength={32}
                inputMode="tel"
                placeholder="*97"
                aria-invalid={!validCode}
              />
              <small>
                {t(
                  "Many SIP systems use *97 for your own mailbox. Change this to your PBX voicemail code if needed.",
                )}
              </small>
            </label>
            {!registered && selected && (
              <p className="voicemail-warning">
                {t("Register this SIP account before calling voicemail.")}
              </p>
            )}
            {activeCall && (
              <p className="voicemail-warning">
                {t("End your current call before opening voicemail.")}
              </p>
            )}
            <button
              className="voicemail-open"
              disabled={!canOpen}
              onClick={() => void openMailbox()}
            >
              <VoicemailIcon name="phone" size={18} />
              {t(busy ? "Connecting…" : "Call my voicemail")}
            </button>
            {error && (
              <p className="voicemail-error" role="alert">
                {t(error)}
              </p>
            )}
          </div>
        ) : (
          <div className="voicemail-card voicemail-live">
            <span
              className={`voicemail-avatar${connected ? "" : " is-connecting"}`}
            >
              <VoicemailIcon name="voicemail" size={34} />
            </span>
            <span
              className={`voicemail-status${connected ? " is-connected" : ""}`}
            >
              <i />
              {t(callLabel(call.state))}
            </span>
            <strong className="voicemail-account">
              {selected?.label ?? call.destination}
            </strong>
            <span className="voicemail-duration">
              {callTime(call.durationSeconds)}
            </span>
            <p className="voicemail-live__hint">
              {t(
                connected
                  ? "Follow the voice prompts. Use the keypad to enter your mailbox PIN and choose messages."
                  : "Connecting to the SIP voicemail service…",
              )}
            </p>
            {connected && (
              <div
                className="voicemail-keypad"
                role="group"
                aria-label={t("Voicemail keypad")}
              >
                {dialpad.map((digit) => (
                  <button
                    type="button"
                    key={digit}
                    onClick={() => void sendDigit(digit)}
                    disabled={calls.busy}
                  >
                    {digit}
                  </button>
                ))}
              </div>
            )}
            <button
              className="voicemail-hangup"
              disabled={busy}
              onClick={() => void endCall()}
              aria-label={t("End voicemail call")}
              title={t("End voicemail call")}
            >
              <VoicemailIcon name="phone-off" size={26} />
            </button>
            {error && (
              <p className="voicemail-error" role="alert">
                {t(error)}
              </p>
            )}
          </div>
        )}
        <aside className="voicemail-guide">
          <h2>{t("How it works")}</h2>
          <ol>
            <li>
              <strong>{t("Call your mailbox")}</strong>
              <span>
                {t("YeyoFone dials your access code on the selected account.")}
              </span>
            </li>
            <li>
              <strong>{t("Enter your PIN")}</strong>
              <span>{t("Use the on-screen keypad when the system asks.")}</span>
            </li>
            <li>
              <strong>{t("Listen and manage")}</strong>
              <span>
                {t(
                  "Follow the voice prompts to play, save or delete messages.",
                )}
              </span>
            </li>
          </ol>
          <p className="voicemail-privacy">
            <VoicemailIcon name="lock" size={16} />
            {t(
              "Your mailbox PIN is sent as a DTMF tone and is not stored by YeyoFone.",
            )}
          </p>
        </aside>
      </div>
    </section>
  );
}

// Icon paths from Lucide (https://lucide.dev, ISC License); see LICENSES-THIRD-PARTY.md.
function VoicemailIcon({
  name,
  size,
}: {
  name: "voicemail" | "phone" | "phone-off" | "lock";
  size: number;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === "voicemail" && (
        <>
          <circle cx="6" cy="12" r="4" />
          <circle cx="18" cy="12" r="4" />
          <line x1="6" x2="18" y1="16" y2="16" />
        </>
      )}
      {name === "phone" && (
        <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
      )}
      {name === "phone-off" && (
        <>
          <path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.42 19.42 0 0 1-3.33-2.67m-2.67-3.34a19.79 19.79 0 0 1-3.07-8.63A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91" />
          <line x1="22" x2="2" y1="2" y2="22" />
        </>
      )}
      {name === "lock" && (
        <>
          <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
          <path d="M7 11V7a5 5 0 0 1 10 0v4" />
        </>
      )}
    </svg>
  );
}
