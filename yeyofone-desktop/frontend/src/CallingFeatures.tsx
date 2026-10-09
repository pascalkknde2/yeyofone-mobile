import { useEffect, useRef, useState } from "react";
import { useLanguage } from "./i18n";
import "./calling-features.css";
export type CallingFeature = "forwarding" | "ring-groups" | "audio-conference";
export type RoutingPreview = {
  enabled: boolean;
  destination: string;
  condition: string;
  groupName: string;
  groupExtension: string;
  strategy: string;
  members: string[];
};
export const initialRoutingPreview: RoutingPreview = {
  enabled: false,
  destination: "",
  condition: "All calls",
  groupName: "Customer support",
  groupExtension: "2000",
  strategy: "Ring everyone",
  members: ["1000", "1001", "1002"],
};
const members = [
  { id: "1000", name: "Alex Morgan", initials: "AM", color: "#e9e3f7" },
  { id: "1001", name: "Maya Wilson", initials: "MW", color: "#f9e5e9" },
  { id: "1002", name: "Daniel Evans", initials: "DE", color: "#e2f4ee" },
  { id: "1003", name: "Elena Carter", initials: "EC", color: "#fbefd9" },
];
function FeatureIcon({
  name,
}: {
  name: "forward" | "group" | "audio" | "mic" | "close" | "phone";
}) {
  return (
    <svg
      width="23"
      height="23"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === "forward" && (
        <>
          <path d="m6 3 3 5-3 3c2 3 3 4 6 6l3-3 5 3c0 3-2 5-5 4C8 19 4 15 2 8 1 5 3 3 6 3ZM13 5h8m-3-3 3 3-3 3" />
        </>
      )}
      {name === "group" && (
        <>
          <circle cx="8" cy="7" r="3" />
          <circle cx="17" cy="8" r="2" />
          <path d="M2 21v-3a6 6 0 0 1 12 0v3ZM17 14a5 5 0 0 1 5 5v2" />
        </>
      )}
      {name === "audio" && (
        <>
          <path d="M3 14v-3a9 9 0 0 1 18 0v3" />
          <rect x="2" y="12" width="4" height="8" rx="2" />
          <rect x="18" y="12" width="4" height="8" rx="2" />
        </>
      )}
      {name === "mic" && (
        <>
          <rect x="9" y="2" width="6" height="13" rx="3" />
          <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8" />
        </>
      )}
      {name === "close" && <path d="m6 6 12 12M6 18 18 6" />}
      {name === "phone" && (
        <path d="M3 16v-4c5-5 13-5 18 0v4h-5v-4c-3-1-5-1-8 0v4Z" />
      )}
    </svg>
  );
}
export function CallingFeatures({
  kind,
  settings,
  onSave,
  onClose,
  onCall,
}: {
  kind: CallingFeature;
  settings: RoutingPreview;
  onSave: (value: RoutingPreview) => void;
  onClose: () => void;
  // Opens the real dialer with this number filled in.
  onCall: (destination: string) => void;
}) {
  const { t } = useLanguage();
  const dialog = useRef<HTMLDialogElement>(null);
  const [draft, setDraft] = useState(settings);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [inRoom, setInRoom] = useState(false);
  const [muted, setMuted] = useState(false);
  const [joined, setJoined] = useState(["1000", "1001"]);
  const [mutedMembers, setMutedMembers] = useState<string[]>([]);
  const [seconds, setSeconds] = useState(0);
  const [roomName, setRoomName] = useState("Team catch-up");
  const title =
    kind === "forwarding"
      ? "Call forwarding"
      : kind === "ring-groups"
        ? "Ring groups"
        : "Audio conference";
  useEffect(() => {
    const element = dialog.current;
    const previous =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    element?.showModal();
    return () => {
      element?.close();
      previous?.focus();
    };
  }, []);
  useEffect(() => {
    if (!inRoom) return;
    const timer = window.setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => window.clearInterval(timer);
  }, [inRoom]);
  function save() {
    if (
      kind === "forwarding" &&
      draft.enabled &&
      (!/^\+?[0-9]{1,32}$/.test(draft.destination) ||
        draft.destination === "1005")
    ) {
      setError("Enter a valid destination different from your extension.");
      return;
    }
    if (
      kind === "ring-groups" &&
      (!draft.groupName.trim() ||
        !/^\d{1,8}$/.test(draft.groupExtension) ||
        !draft.members.length ||
        draft.members.includes(draft.groupExtension))
    ) {
      setError(
        "Enter a group name, a distinct extension, and select at least one member.",
      );
      return;
    }
    setError("");
    onSave({ ...draft, groupName: draft.groupName.trim() });
    setStatus(
      "Preview settings saved for this session. No server changes were made.",
    );
  }
  return (
    <dialog
      ref={dialog}
      className={`calling-feature-dialog feature-${kind}`}
      aria-labelledby="calling-feature-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <header className="calling-feature-header">
        <div>
          <span className="calling-feature-symbol">
            <FeatureIcon
              name={
                kind === "forwarding"
                  ? "forward"
                  : kind === "ring-groups"
                    ? "group"
                    : "audio"
              }
            />
          </span>
          <div>
            <h2 id="calling-feature-title">{t(title)}</h2>
            <p>
              {t(
                kind === "audio-conference"
                  ? "Better conversations, without the camera."
                  : "Keep every call moving.",
              )}
            </p>
          </div>
        </div>
        <button aria-label={t("Close")} onClick={onClose}>
          <FeatureIcon name="close" />
        </button>
      </header>
      <div className="calling-feature-preview">
        {t("Interactive preview · No server routing or live audio")}
      </div>
      <div className="calling-feature-content">
        {kind === "forwarding" ? (
          <>
            <div className="forward-source">
              <span className="feature-avatar">SN</span>
              <div>
                <strong>Sarah Ndio</strong>
                <span>{t("Extension 1005")}</span>
              </div>
              <span className="forward-source-arrow">→</span>
              <div>
                <strong>
                  {draft.enabled && draft.destination
                    ? draft.destination
                    : t("Your phone")}
                </strong>
                <span>
                  {t(
                    draft.enabled ? "Forwarding destination" : "Forwarding off",
                  )}
                </span>
              </div>
            </div>
            <div className="feature-setting-row">
              <div>
                <strong>{t("Enable call forwarding")}</strong>
                <p>{t("Send incoming calls to another number.")}</p>
              </div>
              <button
                className={`feature-switch ${draft.enabled ? "enabled" : ""}`}
                role="switch"
                aria-checked={draft.enabled}
                aria-label={t("Enable call forwarding")}
                onClick={() => {
                  setDraft((old) => ({ ...old, enabled: !old.enabled }));
                  setStatus("");
                }}
              >
                <span />
              </button>
            </div>
            <label className="feature-field">
              {t("Forward to")}
              <input
                inputMode="tel"
                disabled={!draft.enabled}
                value={draft.destination}
                maxLength={32}
                placeholder={t("Number or extension")}
                onChange={(e) => {
                  setDraft((old) => ({
                    ...old,
                    destination: e.target.value.replace(/[^+0-9]/g, ""),
                  }));
                  setError("");
                  setStatus("");
                }}
              />
            </label>
            <label className="feature-field">
              {t("When to forward")}
              <select
                disabled={!draft.enabled}
                value={draft.condition}
                onChange={(e) => {
                  setDraft((old) => ({ ...old, condition: e.target.value }));
                  setStatus("");
                }}
              >
                {["All calls", "When busy", "When unanswered"].map((value) => (
                  <option key={value} value={value}>
                    {t(value)}
                  </option>
                ))}
              </select>
            </label>
            <div className="feature-info">
              {t(
                "This preview does not read or change your PBX forwarding rules.",
              )}
            </div>
          </>
        ) : kind === "ring-groups" ? (
          <>
            <div className="feature-field-grid">
              <label className="feature-field">
                {t("Group name")}
                <input
                  value={draft.groupName}
                  maxLength={60}
                  onChange={(e) => {
                    setDraft((old) => ({ ...old, groupName: e.target.value }));
                    setStatus("");
                  }}
                />
              </label>
              <label className="feature-field">
                {t("Group extension")}
                <input
                  inputMode="numeric"
                  value={draft.groupExtension}
                  maxLength={8}
                  onChange={(e) => {
                    setDraft((old) => ({
                      ...old,
                      groupExtension: e.target.value.replace(/\D/g, ""),
                    }));
                    setStatus("");
                  }}
                />
              </label>
            </div>
            <label className="feature-field">
              {t("Ring strategy")}
              <select
                value={draft.strategy}
                onChange={(e) => {
                  setDraft((old) => ({ ...old, strategy: e.target.value }));
                  setStatus("");
                }}
              >
                {["Ring everyone", "Ring in order"].map((value) => (
                  <option key={value} value={value}>
                    {t(value)}
                  </option>
                ))}
              </select>
            </label>
            <h3>{t("Group members")}</h3>
            <div className="ring-group-members">
              {members.map((member) => (
                <label key={member.id}>
                  <input
                    type="checkbox"
                    checked={draft.members.includes(member.id)}
                    onChange={() => {
                      setDraft((old) => ({
                        ...old,
                        members: old.members.includes(member.id)
                          ? old.members.filter((id) => id !== member.id)
                          : [...old.members, member.id],
                      }));
                      setStatus("");
                    }}
                  />
                  <span
                    className="feature-avatar"
                    style={{ background: member.color }}
                  >
                    {member.initials}
                  </span>
                  <span>
                    <strong>{member.name}</strong>
                    <small>{member.id}</small>
                  </span>
                  {draft.members.includes(member.id) &&
                    draft.strategy === "Ring in order" && (
                      <span className="ring-order">
                        {draft.members.indexOf(member.id) + 1}
                      </span>
                    )}
                </label>
              ))}
            </div>
            <div className="ring-flow">
              <span>{t("Incoming call")}</span>
              <b>→</b>
              <span>{draft.groupExtension}</span>
              <b>→</b>
              <span>
                {draft.members.join(
                  draft.strategy === "Ring everyone" ? " + " : " → ",
                ) || t("Select members")}
              </span>
            </div>
            <div className="ring-group-actions">
              <button
                className="feature-primary"
                disabled={!/^\d{1,8}$/.test(draft.groupExtension)}
                onClick={() => onCall(draft.groupExtension)}
              >
                <FeatureIcon name="phone" />
                {t("Call group")}
              </button>
              <button
                className="feature-secondary"
                onClick={() =>
                  setStatus(
                    draft.members.length
                      ? t(draft.strategy) +
                          ": " +
                          draft.members.join(
                            draft.strategy === "Ring everyone" ? " + " : " → ",
                          )
                      : t("Select members"),
                  )
                }
              >
                {t("Preview ring flow")}
              </button>
            </div>
            <p className="ring-group-call-note">
              {t(
                "Call group dials this extension on your phone system. This preview doesn’t create the group there.",
              )}
            </p>
          </>
        ) : !inRoom ? (
          <div className="audio-lobby">
            <div className="audio-lobby-icon">
              <FeatureIcon name="audio" />
            </div>
            <h3>{t("A room for every voice")}</h3>
            <p>{t("Join a sample audio room and explore host controls.")}</p>
            <label className="feature-field">
              {t("Room name")}
              <input
                value={roomName}
                maxLength={60}
                onChange={(e) => setRoomName(e.target.value)}
              />
            </label>
            <div className="audio-lobby-members">
              {members.slice(0, 2).map((person) => (
                <span
                  className="feature-avatar"
                  key={person.id}
                  style={{ background: person.color }}
                >
                  {person.initials}
                </span>
              ))}
              <span>{t("2 sample participants ready")}</span>
            </div>
            <button
              className="feature-primary"
              disabled={!roomName.trim()}
              onClick={() => {
                setInRoom(true);
                setSeconds(0);
              }}
            >
              {t("Join audio preview")}
            </button>
          </div>
        ) : (
          <div className="audio-room">
            <div className="audio-room-heading">
              <div>
                <h3>{roomName.trim()}</h3>
                <span>{t("Audio preview")}</span>
              </div>
              <span className="audio-timer">
                {String(Math.floor(seconds / 60)).padStart(2, "0")}:
                {String(seconds % 60).padStart(2, "0")}
              </span>
            </div>
            <div className="audio-participant-grid">
              <article>
                <span className="feature-avatar">SN</span>
                <strong>Sarah Ndio</strong>
                <small>{t("Host · You")}</small>
                <span className={`audio-state ${muted ? "muted" : ""}`}>
                  {t(muted ? "Muted" : "Microphone on")}
                </span>
              </article>
              {members
                .filter((person) => joined.includes(person.id))
                .map((person) => (
                  <article key={person.id}>
                    <span
                      className="feature-avatar"
                      style={{ background: person.color }}
                    >
                      {person.initials}
                    </span>
                    <strong>{person.name}</strong>
                    <small>{person.id}</small>
                    <button
                      className={`audio-state ${mutedMembers.includes(person.id) ? "muted" : ""}`}
                      aria-pressed={mutedMembers.includes(person.id)}
                      onClick={() =>
                        setMutedMembers((old) =>
                          old.includes(person.id)
                            ? old.filter((id) => id !== person.id)
                            : [...old, person.id],
                        )
                      }
                    >
                      {t(mutedMembers.includes(person.id) ? "Unmute" : "Mute")}
                    </button>
                  </article>
                ))}
            </div>
            {members.some((person) => !joined.includes(person.id)) && (
              <section className="audio-waiting">
                <h3>{t("Waiting to join")}</h3>
                {members
                  .filter((person) => !joined.includes(person.id))
                  .map((person) => (
                    <div key={person.id}>
                      <span>{person.name}</span>
                      <button
                        onClick={() => setJoined((old) => [...old, person.id])}
                      >
                        {t("Admit")}
                      </button>
                    </div>
                  ))}
              </section>
            )}
            <div className="audio-room-controls">
              <button
                className={`feature-secondary ${muted ? "is-muted" : ""}`}
                aria-pressed={muted}
                onClick={() => setMuted(!muted)}
              >
                <FeatureIcon name="mic" />
                {t(muted ? "Unmute" : "Mute")}
              </button>
              <button
                className="feature-secondary"
                onClick={() => setMutedMembers(joined)}
              >
                {t("Mute all guests")}
              </button>
              <button
                className="audio-leave"
                onClick={() => {
                  setInRoom(false);
                  setMutedMembers([]);
                }}
              >
                <FeatureIcon name="phone" />
                {t("Leave")}
              </button>
            </div>
          </div>
        )}
        {error && (
          <p className="feature-error" role="alert">
            {t(error)}
          </p>
        )}
        {status && (
          <p className="feature-saved" role="status">
            {t(status)}
          </p>
        )}
      </div>
      {kind !== "audio-conference" && (
        <footer className="calling-feature-footer">
          <button className="feature-secondary" onClick={onClose}>
            {t("Cancel")}
          </button>
          <button className="feature-primary" onClick={save}>
            {t("Save preview")}
          </button>
        </footer>
      )}
    </dialog>
  );
}
