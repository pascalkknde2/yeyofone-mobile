import { useEffect, useRef } from "react";
import { createRingbackTone } from "./ringback";
import type { CallStatus as Call } from "./calls";

const PLAYING_STATES = new Set([
  "ringing",
  "early_media",
  "connecting",
  "connected",
  "ending",
  "ended",
]);

export function useRingback(session: Call | undefined) {
  const ringbackRef = useRef<{ start: () => void; stop: () => void } | null>(
    null,
  );

  useEffect(() => {
    if (!session || session.direction !== "outgoing") {
      ringbackRef.current?.stop();
      ringbackRef.current = null;
      return;
    }
    if (session.state === "ringing") {
      if (!ringbackRef.current) {
        try {
          ringbackRef.current = createRingbackTone();
        } catch {
          ringbackRef.current = null;
        }
      }
      ringbackRef.current?.start();
      return;
    }
    if (PLAYING_STATES.has(session.state)) {
      ringbackRef.current?.stop();
      ringbackRef.current = null;
    }
  }, [session?.direction, session?.state]);

  useEffect(
    () => () => {
      ringbackRef.current?.stop();
      ringbackRef.current = null;
    },
    [],
  );
}
