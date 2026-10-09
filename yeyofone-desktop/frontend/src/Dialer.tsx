import { useEffect, useMemo, useRef, useState } from "react";
import { useLanguage } from "./i18n";
import { useAccounts } from "./useAccounts";
import { useCalls } from "./useCalls";
import { CallingScreen } from "./CallingScreen";
import { CallPanel } from "./CallPanel";
import { AccountPicker } from "./AccountPicker";
import { Keypad } from "./Keypad";
import { DialInput } from "./DialInput";
import { useDialSession } from "./useDialSession";
import { isTauri } from "@tauri-apps/api/core";

import "./dialer.css";

export function Dialer({
  onClose,
  initialDestination = "",
}: {
  onClose: () => void;
  initialDestination?: string;
}) {
  const { t } = useLanguage();
  const calls = useCalls();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const generationRef = useRef(0);

  const [selected, setSelected] = useState("");
  const [destination, setDestination] = useState(initialDestination);
  const dialSession = useDialSession();
  const { busy, intent, error } = dialSession;

  const { accounts, statuses, available, loading } = useAccounts();

  const account = useMemo(
    () => accounts.find((a) => a.id === selected),
    [accounts, selected],
  );
  const status = useMemo(
    () => statuses.find((s) => s.accountId === selected),
    [statuses, selected],
  );

  const ready =
    !!account?.enabled && available && status?.state === "registered";
  const callUnavailable =
    !isTauri() || !accounts.length || !ready || !calls.available;

  const active = calls.rows.find(
    (c) => c.state !== "ended" && c.consultParentId === null,
  );
  const session = active ?? calls.rows.find((c) => c.id === intent?.id);

  // Default the selected account once accounts load.
  useEffect(() => {
    if (accounts.some((a) => a.id === selected)) return;
    const preferred =
      accounts.find((a) =>
        statuses.some((s) => s.accountId === a.id && s.state === "registered"),
      ) ??
      accounts.find((a) => a.enabled) ??
      accounts[0];
    if (preferred) setSelected(preferred.id);
  }, [accounts, statuses, selected]);

  // Modal lifecycle.
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialogRef.current?.showModal();
    inputRef.current?.focus();
    return () => {
      ++generationRef.current;
      dialogRef.current?.close();
      previous?.focus();
    };
  }, []);

  function changeDestination(value: string) {
    dialSession.reset();
    setDestination(value);
  }
  async function dial() {
    if (
      !ready ||
      !calls.available ||
      calls.busy ||
      active ||
      busy ||
      !destination.trim()
    )
      return;
    await dialSession.dial(selected, destination, calls.request);
  }

  const calling =
    session?.direction === "outgoing" &&
    ["dialing", "ringing", "early_media", "connecting"].includes(session.state);
  const title = active ? "Current call" : "Make a call";

  return (
    <dialog
      ref={dialogRef}
      className={`dialer ${calling ? "dialer--calling" : session && session.state !== "ended" ? "dialer--in-call" : "dialer--number-entry"}`}
      role="dialog"
      aria-modal="true"
      aria-labelledby="dialer-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <header className="dialer__header">
        <h2 id="dialer-title" className="dialer__title dialer__sr-only">
          {t(title)}
        </h2>
        <button
          type="button"
          className="dialer__close"
          aria-label={t("Close")}
          onClick={onClose}
        >
          <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path
              d="M6 6l12 12M18 6L6 18"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </header>

      {calling && session ? (
        <CallingScreen
          session={session}
          busy={calls.busy}
          available={calls.available}
          onCancel={() => void calls.request("hangup", { id: session.id })}
        />
      ) : session && session.state !== "ended" ? (
        <CallPanel
          session={session}
          sessions={calls.rows}
          account={accounts.find((a) => a.id === session.accountId)}
          callsAvailable={calls.available}
          callsBusy={calls.busy}
          onRequest={calls.request}
        />
      ) : (
        <form
          className="dialer__form"
          onSubmit={(e) => {
            e.preventDefault();
            void dial();
          }}
        >
          {/* With a single account there's nothing to choose: it's used automatically, and only a
              problem with it (not registered, disabled) is shown. */}
          <AccountPicker
            statusOnly={accounts.length <= 1}
            accounts={accounts}
            statuses={statuses}
            selected={selected}
            onSelect={(id) => {
              ++generationRef.current;
              setSelected(id);
              dialSession.reset();
            }}
            ready={ready}
            available={available}
            loading={loading}
            disabled={!!active || calls.busy || busy}
          />

          <DialInput
            ref={inputRef}
            value={destination}
            onChange={changeDestination}
            invalid={!!error}
            errorId={error ? "dialer-error" : undefined}
          />

          {busy && (
            <p className="calling-screen__status" role="status">
              {t("Dialing…")}
            </p>
          )}

          <Keypad
            value={destination}
            onChange={changeDestination}
            inputRef={inputRef}
          />

          {error && (
            <p id="dialer-error" role="alert" className="dialer__error">
              {t(error)}
            </p>
          )}

          <button
            type="submit"
            className="dialer__submit"
            aria-busy={busy}
            aria-label={t(
              busy ? "Dialing…" : calls.busy ? "Starting call…" : "Call",
            )}
            title={t("Call")}
            disabled={
              callUnavailable || !destination.trim() || busy || calls.busy
            }
          >
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
              <path
                d="M4 4h5l2 5-3 2a12 12 0 0 0 5 5l2-3 5 2v5a1 1 0 0 1-1 1A17 17 0 0 1 3 5a1 1 0 0 1 1-1z"
                fill="currentColor"
              />
            </svg>
          </button>

          {!calls.available && (
            <p className="dialer__hint">
              {t("Calling is unavailable in this runtime.")}
            </p>
          )}
        </form>
      )}

      {calls.error && (
        <p role="alert" className="dialer__error">
          {t(calls.error)}
        </p>
      )}
    </dialog>
  );
}
