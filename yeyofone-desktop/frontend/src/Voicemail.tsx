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

  return (
    <section className="standalone voicemail-page">
      <header className="section-heading">
        <div>
          <h1>{t("Voicemail")}</h1>
          <p>{t("Listen to messages in your SIP mailbox")}</p>
        </div>
      </header>
      <div className="voicemail-card">
        {!call || call.state === "ended" ? (
          <>
            <label>
              {t("SIP account")}
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
            </label>
            <label>
              {t("Voicemail access code")}
              <input
                value={accessCode}
                onChange={(event) => setAccessCode(event.target.value)}
                maxLength={32}
                inputMode="tel"
                placeholder="*97"
              />
            </label>
            <p className="voicemail-hint">
              {t(
                "Many SIP systems use *97 for your own mailbox. Change this to your PBX voicemail code if needed.",
              )}
            </p>
            {!registered && selected && (
              <p className="voicemail-muted">
                {t("Register this SIP account before calling voicemail.")}
              </p>
            )}
            <button
              className="voicemail-open"
              disabled={
                !selected ||
                !registered ||
                !validCode ||
                !!activeCall ||
                busy ||
                !calls.available
              }
              onClick={() => void openMailbox()}
            >
              {t(busy ? "Connecting…" : "Call my voicemail")}
            </button>
            {activeCall && (
              <p className="voicemail-muted">
                {t("End your current call before opening voicemail.")}
              </p>
            )}
          </>
        ) : (
          <>
            <div className="voicemail-call-status">
              <i className={call.state === "connected" ? "connected" : ""} />
              {t(callLabel(call.state))}
            </div>
            <strong className="voicemail-account">
              {selected?.label ?? call.destination}
            </strong>
            <span className="voicemail-duration">
              {callTime(call.durationSeconds)}
            </span>
            <p className="voicemail-hint">
              {t(
                call.state === "connected"
                  ? "Follow the voice prompts. Use the keypad to enter your mailbox PIN and choose messages."
                  : "Connecting to the SIP voicemail service…",
              )}
            </p>
            {call.state === "connected" && (
              <div
                className="voicemail-keypad"
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
            {call.state === "connected" && (
              <p className="voicemail-privacy">
                {t(
                  "Your mailbox PIN is sent as a DTMF tone and is not stored by YeyoFone.",
                )}
              </p>
            )}
            <button
              className="voicemail-hangup"
              disabled={busy}
              onClick={() => void endCall()}
            >
              {t("End voicemail call")}
            </button>
          </>
        )}
        {error && (
          <p className="voicemail-error" role="alert">
            {t(error)}
          </p>
        )}
      </div>
    </section>
  );
}
