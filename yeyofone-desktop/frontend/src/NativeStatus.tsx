import { useEffect, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { useLanguage } from "./i18n";

type RuntimeResponse = {
  schemaVersion: number;
  requestId: string;
  sequence: number;
  data: { engine: string; callingAvailable: boolean };
};
export function NativeStatus() {
  const { t } = useLanguage();
  const [status, setStatus] = useState(
    isTauri() ? "Connecting to desktop runtime" : "Browser preview",
  );
  useEffect(() => {
    if (!isTauri()) return;
    let active = true;
    const requestId = crypto.randomUUID();
    void invoke<RuntimeResponse>("runtime_status", {
      request: { schemaVersion: 1, requestId },
    })
      .then((response) => {
        if (
          response.schemaVersion !== 1 ||
          response.requestId !== requestId ||
          !Number.isSafeInteger(response.sequence) ||
          response.sequence < 0 ||
          !["stopped", "starting", "running", "stopping", "failed"].includes(
            response.data.engine,
          ) ||
          typeof response.data.callingAvailable !== "boolean"
        )
          throw new Error("Invalid runtime response");
        if (active)
          setStatus(
            response.data.callingAvailable
              ? "Desktop connected · SIP calling available"
              : "Desktop connected · Calling not connected",
          );
      })
      .catch(() => {
        if (active) setStatus("Desktop connection unavailable");
      });
    return () => {
      active = false;
    };
  }, []);
  return <span role="status">{t(status)}</span>;
}
