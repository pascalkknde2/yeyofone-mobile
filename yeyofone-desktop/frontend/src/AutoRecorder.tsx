import { useEffect, useRef } from "react";
import { useCalls } from "./useCalls";
import { useAiStatus } from "./AiSettings";

/**
 * With "Record every call for AI notes" on, starts recording each call once it connects.
 * Each call is started only once, so stopping a recording by hand keeps it stopped.
 */
export function AutoRecorder() {
  const { rows, request } = useCalls();
  const { status, refresh } = useAiStatus();
  const started = useRef(new Set<string>());
  // Pick up a changed setting without reopening the app.
  useEffect(() => {
    const timer = setInterval(() => void refresh(), 15_000);
    return () => clearInterval(timer);
  }, [refresh]);
  const enabled = status?.settings.autoRecord === true;
  useEffect(() => {
    if (!enabled) return;
    for (const call of rows) {
      if (
        call.state === "connected" &&
        !call.recording &&
        !call.held &&
        !started.current.has(call.id)
      ) {
        started.current.add(call.id);
        void request("record_start", { id: call.id });
      }
    }
  }, [enabled, rows]);
  return null;
}
