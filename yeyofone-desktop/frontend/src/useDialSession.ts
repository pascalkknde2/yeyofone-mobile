import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { parseDestinationResult } from "./destinationWire";

type Intent = { id: string; accountId: string; destination: string };
export function useDialSession() {
  const generation = useRef(0);
  const pending = useRef(false);
  const [phase, setPhase] = useState<"idle" | "validating" | "dialing" | "active">("idle");
  const [intent, setIntent] = useState<Intent | null>(null);
  const [error, setError] = useState("");
  function reset() {
    ++generation.current;
    pending.current = false;
    setPhase("idle");
    setIntent(null);
    setError("");
  }
  useEffect(() => () => { ++generation.current; }, []);
  async function dial(accountId: string, destination: string, request: (action: string, extra: Record<string, unknown>) => Promise<boolean>) {
    if (pending.current) return;
    pending.current = true;
    const current = ++generation.current;
    setPhase("validating");
    setError("");
    try {
      const requestId = crypto.randomUUID();
      const normalized = parseDestinationResult(await invoke("validate_destination", {
        request: { schemaVersion: 1, requestId, destination },
      }), requestId);
      if (current !== generation.current) return;
      const command = { id: crypto.randomUUID(), accountId, destination: normalized };
      setIntent(command);
      setPhase("dialing");
      const accepted = await request("dial", command);
      if (current !== generation.current) return;
      setPhase(accepted ? "active" : "idle");
      if (!accepted) setIntent(null);
    } catch {
      if (current === generation.current) {
        setError("Enter a valid extension, phone number or SIP URI.");
        setIntent(null);
        setPhase("idle");
      }
    } finally {
      if (current === generation.current) pending.current = false;
    }
  }
  return { phase, intent, error, reset, dial, busy: phase === "validating" || phase === "dialing" };
}
