import { useEffect, useRef, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { parseRegistrations, type RegistrationStatus } from "./registration";
import { useLanguage } from "./i18n";

export type Account = {
  id: string;
  label: string;
  username: string;
  enabled: boolean;
};

const POLL_MS = 2000;

export function useAccounts() {
  const { t } = useLanguage();
  const tRef = useRef(t);
  tRef.current = t;

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [statuses, setStatuses] = useState<RegistrationStatus[]>([]);
  const [available, setAvailable] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;

    async function poll() {
      if (!isTauri()) {
        if (active) setLoading(false);
        return;
      }
      try {
        const requestId = crypto.randomUUID();
        const wire = await invoke<{
          schemaVersion: number;
          requestId: string;
          sequence: number;
          data: Account[];
        }>("accounts_command", {
          request: { schemaVersion: 1, requestId, action: "list" },
        });
        if (
          wire.schemaVersion !== 1 ||
          wire.requestId !== requestId ||
          wire.sequence !== 0 ||
          !Array.isArray(wire.data) ||
          wire.data.length > 32 ||
          !wire.data.every(
            (a) =>
              typeof a.id === "string" &&
              typeof a.label === "string" &&
              typeof a.username === "string" &&
              typeof a.enabled === "boolean",
          )
        ) {
          throw new Error("malformed accounts response");
        }

        let rows: RegistrationStatus[] = [];
        let native = false;
        try {
          const id = crypto.randomUUID();
          rows = parseRegistrations(
            await invoke("registration_command", {
              request: { schemaVersion: 1, requestId: id, action: "status" },
            }),
            id,
          );
          native = true;
        } catch {
          // Native registration unavailable (e.g. preview build).
        }

        if (active) {
          setAccounts(wire.data);
          setStatuses(rows);
          setAvailable(native);
          setError("");
        }
      } catch {
        if (active) {
          setAvailable(false);
          setError(tRef.current("Unable to load SIP accounts."));
        }
      } finally {
        if (active) {
          setLoading(false);
          timer = setTimeout(() => void poll(), POLL_MS);
        }
      }
    }

    void poll();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, []);

  return { accounts, statuses, available, loading, error };
}
