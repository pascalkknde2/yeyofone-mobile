import { useEffect, useRef, useState } from "react";
import type { CallStatus } from "./calls";

// How long each call has been on hold, counted from when this screen first
// saw it held (the engine doesn't report the hold time).
export function useHeldSeconds(calls: CallStatus[]) {
  const since = useRef(new Map<string, number>());
  const [now, setNow] = useState(() => Date.now());
  const held = calls.filter((c) => c.held && c.state === "connected");
  for (const call of held)
    if (!since.current.has(call.id)) since.current.set(call.id, Date.now());
  for (const id of [...since.current.keys()])
    if (!held.some((c) => c.id === id)) since.current.delete(id);
  const anyHeld = held.length > 0;
  useEffect(() => {
    if (!anyHeld) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [anyHeld]);
  return (call: CallStatus) => {
    const start = since.current.get(call.id);
    return start === undefined
      ? null
      : Math.max(0, Math.floor((now - start) / 1000));
  };
}
