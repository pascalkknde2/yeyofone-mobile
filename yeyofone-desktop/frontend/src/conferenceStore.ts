import { useSyncExternalStore } from "react";
import type { Roster } from "./conferenceRoster";

// What the conference host (ConferenceHost) learns, for the call screens to show.
export type ConferenceState = {
  // The roster another organiser sent this device (this device is a participant).
  remote: Roster | null;
  // Extensions talking right now, for a conference this device organises.
  speaking: ReadonlySet<string>;
};

let state: ConferenceState = { remote: null, speaking: new Set() };
const listeners = new Set<() => void>();

export function updateConference(patch: Partial<ConferenceState>) {
  state = { ...state, ...patch };
  listeners.forEach((listener) => listener());
}

export function getConferenceState(): ConferenceState {
  return state;
}

export function useConferenceState(): ConferenceState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => state,
  );
}
