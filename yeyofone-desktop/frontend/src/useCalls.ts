import { useEffect, useRef, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { parseCalls, type CallStatus } from "./calls";
export function useCalls() {
  const [rows, setRows] = useState<CallStatus[]>([]),
    [available, setAvailable] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const generation = useRef(0),
    pending = useRef(false),
    alive = useRef(true);
  async function request(action: string, extra: Record<string, unknown> = {}) {
    if (pending.current) return false;
    const g = ++generation.current,
      requestId = crypto.randomUUID();
    if (action !== "status") {
      pending.current = true;
      setBusy(true);
      setError("");
    }
    try {
      const wire = await invoke("calls_command", {
        request: { schemaVersion: 1, requestId, action, ...extra },
      });
      const next = parseCalls(wire, requestId);
      if (alive.current && g === generation.current) {
        setRows(next);
        setAvailable(true);
      }
      return true;
    } catch {
      if (alive.current && g === generation.current) {
        if (action === "status") setAvailable(false);
        else
          setError(
            "Call operation failed. Check account registration and try again.",
          );
      }
      return false;
    } finally {
      if (action !== "status") {
        pending.current = false;
        if (alive.current) setBusy(false);
      }
    }
  }
  useEffect(() => {
    alive.current = true;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    let unlisten: undefined | (() => void);
    void listen("call-state", () => {
      if (active && !pending.current) void request("status");
    }).then((fn) => {
      if (active) unlisten = fn;
      else fn();
    });
    async function poll() {
      if (isTauri() && !pending.current) await request("status");
      if (active) timer = setTimeout(() => void poll(), 500);
    }
    void poll();
    return () => {
      active = false;
      alive.current = false;
      ++generation.current;
      clearTimeout(timer);
      unlisten?.();
    };
  }, []);
  return { rows, available, busy, error, request };
}
