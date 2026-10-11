import { useEffect, useRef, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { parseHandoffs, type HandoffOffer } from "./handoff";

// Polls for calls on the account's other devices and takes one over on request.
export function useHandoffs() {
  const [offers, setOffers] = useState<HandoffOffer[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const alive = useRef(true),
    pending = useRef(false);

  async function request(
    action: "list" | "take_over",
    extra: Record<string, unknown> = {},
  ) {
    const requestId = crypto.randomUUID();
    const wire = await invoke("handoff_command", {
      request: { schemaVersion: 1, requestId, action, ...extra },
    });
    const next = parseHandoffs(wire, requestId);
    if (alive.current) setOffers(next);
  }

  async function takeOver(offer: HandoffOffer) {
    if (pending.current) return false;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      await request("take_over", {
        offerId: offer.id,
        id: crypto.randomUUID(),
      });
      return true;
    } catch {
      if (alive.current)
        setError("The call could not be moved to this device.");
      return false;
    } finally {
      pending.current = false;
      if (alive.current) setBusy(false);
    }
  }

  useEffect(() => {
    alive.current = true;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      if (isTauri() && !pending.current) {
        try {
          await request("list");
        } catch {
          if (alive.current) setOffers([]);
        }
      }
      if (active) timer = setTimeout(() => void poll(), 1000);
    }
    void poll();
    return () => {
      active = false;
      alive.current = false;
      clearTimeout(timer);
    };
  }, []);

  return { offers, busy, error, takeOver };
}
