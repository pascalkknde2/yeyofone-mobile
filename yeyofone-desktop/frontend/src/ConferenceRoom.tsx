import { useLanguage } from "./i18n";
import { useEffect, useRef, useState, type CSSProperties } from "react";
import "./conference-room.css";

type RoomIcon =
  | "video"
  | "mic"
  | "hand"
  | "share"
  | "people"
  | "close"
  | "leave"
  | "grid"
  | "focus"
  | "copy"
  | "expand";
function RoomGlyph({ name, off = false }: { name: RoomIcon; off?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === "video" && (
        <>
          <rect x="2" y="5" width="14" height="14" rx="3" />
          <path d="m16 10 6-4v12l-6-4" />
        </>
      )}
      {name === "mic" && (
        <>
          <rect x="9" y="2" width="6" height="13" rx="3" />
          <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8" />
        </>
      )}
      {name === "people" && (
        <>
          <circle cx="9" cy="7" r="3" />
          <path d="M3 21v-3a6 6 0 0 1 12 0v3ZM16 4a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 5v2" />
        </>
      )}
      {name === "hand" && (
        <path d="M8 12V5a2 2 0 0 1 4 0v7-9a2 2 0 0 1 4 0v9-6a2 2 0 0 1 4 0v10a6 6 0 0 1-6 6h-2c-3 0-4-2-6-5l-3-4a2 2 0 0 1 3-2l2 2" />
      )}
      {name === "share" && (
        <>
          <rect x="2" y="3" width="20" height="14" rx="2" />
          <path d="M8 21h8m-4-4v4M12 13V6m-3 3 3-3 3 3" />
        </>
      )}
      {name === "close" && <path d="m6 6 12 12M6 18 18 6" />}
      {name === "leave" && (
        <path d="M3 16v-4c5-5 13-5 18 0v4h-5v-4c-3-1-5-1-8 0v4Z" />
      )}
      {name === "grid" && (
        <>
          <rect x="3" y="3" width="7" height="7" rx="1" />
          <rect x="14" y="3" width="7" height="7" rx="1" />
          <rect x="3" y="14" width="7" height="7" rx="1" />
          <rect x="14" y="14" width="7" height="7" rx="1" />
        </>
      )}
      {name === "focus" && (
        <>
          <rect x="2" y="3" width="20" height="18" rx="2" />
          <path d="M16 3v18M16 12h6" />
        </>
      )}
      {name === "expand" && <path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5" />}
      {name === "copy" && (
        <>
          <rect x="7" y="7" width="14" height="14" rx="2" />
          <path d="M16 7V3H3v13h4" />
        </>
      )}
      {off && <path d="m3 3 18 18" strokeWidth="2.3" />}
    </svg>
  );
}
const attendees = [
  {
    id: "you",
    name: "Sarah Ndio",
    initials: "SN",
    role: "Host · You",
    color: "#567a9d",
  },
  {
    id: "alex",
    name: "Alex Morgan",
    initials: "AM",
    role: "Product design",
    color: "#6b709d",
  },
  {
    id: "maya",
    name: "Maya Wilson",
    initials: "MW",
    role: "Engineering",
    color: "#946e78",
  },
  {
    id: "daniel",
    name: "Daniel Evans",
    initials: "DE",
    role: "Customer experience",
    color: "#5a847e",
  },
  {
    id: "elena",
    name: "Elena Carter",
    initials: "EC",
    role: "Operations",
    color: "#8d8064",
  },
  {
    id: "james",
    name: "James Brooks",
    initials: "JB",
    role: "Sales",
    color: "#697d9a",
  },
];
export function ConferenceRoom({ onClose }: { onClose: () => void }) {
  const { t } = useLanguage();
  const dialog = useRef<HTMLDialogElement>(null);
  const [joined, setJoined] = useState(["you", "alex", "maya", "daniel"]);
  const [focused, setFocused] = useState("alex");
  const [view, setView] = useState<"grid" | "focus">("grid");
  const [muted, setMuted] = useState(false);
  const [camera, setCamera] = useState(true);
  const [raised, setRaised] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [toast, setToast] = useState(
    "Alex, Maya, and Daniel have joined the preview room.",
  );
  const [topic, setTopic] = useState(0);
  const [queue, setQueue] = useState(["maya"]);
  const [notes, setNotes] = useState("");
  const [panelOpen, setPanelOpen] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const panelButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const changed = () =>
      setFullscreen(document.fullscreenElement === dialog.current);
    document.addEventListener("fullscreenchange", changed);
    return () => document.removeEventListener("fullscreenchange", changed);
  }, []);
  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement === dialog.current)
        await document.exitFullscreen();
      else if (fullscreen) setFullscreen(false);
      else if (dialog.current?.requestFullscreen)
        await dialog.current.requestFullscreen();
      else setFullscreen(true);
    } catch {
      setFullscreen((value) => !value);
    }
  }
  function closeRoom() {
    if (document.fullscreenElement === dialog.current)
      void document.exitFullscreen().catch(() => {});
    onClose();
  }
  useEffect(() => {
    const element = dialog.current;
    const previousFocus =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    element?.showModal();
    const timer = window.setInterval(() => setSeconds((s) => s + 1), 1000);
    const arrival = window.setTimeout(() => {
      setJoined((old) => (old.includes("elena") ? old : [...old, "elena"]));
      setToast("Elena Carter joined the preview room.");
    }, 4500);
    return () => {
      window.clearInterval(timer);
      window.clearTimeout(arrival);
      element?.close();
      previousFocus?.focus();
    };
  }, []);
  const participants = attendees.filter((person) => joined.includes(person.id));
  const waiting = attendees.filter((person) => !joined.includes(person.id));
  const topics = [
    "Team check-in",
    "Product priorities",
    "Decisions & next steps",
  ];
  function toggleHand() {
    setRaised(!raised);
    setQueue((old) =>
      raised ? old.filter((id) => id !== "you") : [...old, "you"],
    );
    setToast(
      raised ? "Your hand is lowered." : "You’re in the speaking queue.",
    );
  }
  function giveFloor(id: string) {
    setFocused(id);
    setView("focus");
    setQueue((old) => old.filter((person) => person !== id));
    if (id === "you") setRaised(false);
    setToast(
      `${attendees.find((p) => p.id === id)?.name} has the floor in this preview.`,
    );
  }
  const renderParticipant = (person: (typeof attendees)[number]) => (
    <button
      key={person.id}
      className={`conference-tile ${person.id === focused ? "focused" : ""} ${person.id === "you" && !camera ? "camera-hidden" : ""}`}
      style={{ "--participant-color": person.color } as CSSProperties}
      onClick={() => {
        setFocused(person.id);
        if (view === "focus") setView("focus");
      }}
      aria-label={t(`Focus ${person.name}`)}
      aria-pressed={person.id === focused}
    >
      <div className="tile-badges">
        {person.id === focused && (
          <span className="speaker-badge">{t("In focus")}</span>
        )}
        {queue.includes(person.id) && (
          <span className="hand-badge">
            <RoomGlyph name="hand" />
          </span>
        )}
      </div>
      <div className="conference-person-avatar">
        {person.id === "you" && !camera ? (
          <RoomGlyph name="video" off />
        ) : (
          person.initials
        )}
      </div>
      <span className="tile-camera-note">
        {t(person.id === "you" && !camera ? "Camera off" : "Camera preview")}
      </span>
      <div className="conference-tile-bottom">
        <div>
          <strong>{person.name}</strong>
          <span>{t(person.role)}</span>
        </div>
        <RoomGlyph
          name="mic"
          off={person.id === "you" ? muted : person.id !== focused}
        />
      </div>
    </button>
  );
  return (
    <dialog
      ref={dialog}
      className={`conference-dialog ${fullscreen ? "is-fullscreen" : ""}`}
      aria-labelledby="conference-title"
      aria-describedby="conference-preview"
      onCancel={(e) => {
        e.preventDefault();
        closeRoom();
      }}
    >
      <div className="conference-shell">
        <header className="conference-header">
          <div className="conference-title">
            <span className="conference-logo">
              <RoomGlyph name="video" />
            </span>
            <div>
              <h2 id="conference-title">{t("Team room")}</h2>
              <p>
                {t("Weekly catch-up")} <span>·</span> {t("Hosted by you")}
              </p>
            </div>
          </div>
          <div className="conference-header-meta">
            <span className="conference-time">
              <i />
              {String(Math.floor(seconds / 60)).padStart(2, "0")}:
              {String(seconds % 60).padStart(2, "0")}
            </span>
            <button
              ref={panelButton}
              className={`conference-panel-toggle ${panelOpen ? "selected" : ""}`}
              aria-label={t(panelOpen ? "Hide room panel" : "Show room panel")}
              title={t(panelOpen ? "Hide room panel" : "Show room panel")}
              aria-expanded={panelOpen}
              aria-controls={panelOpen ? "conference-room-panel" : undefined}
              onClick={() => setPanelOpen(!panelOpen)}
            >
              <RoomGlyph name="people" />
              <span>{participants.length}</span>
            </button>
            <button
              className="conference-fullscreen-toggle"
              aria-label={t(fullscreen ? "Exit fullscreen" : "Fullscreen")}
              title={t(fullscreen ? "Exit fullscreen" : "Fullscreen")}
              aria-pressed={fullscreen}
              onClick={toggleFullscreen}
            >
              <RoomGlyph name="expand" />
            </button>
            <button
              className="conference-close"
              aria-label={t("Close conference preview")}
              onClick={closeRoom}
            >
              <RoomGlyph name="close" />
            </button>
          </div>
        </header>
        <div className="conference-preview" id="conference-preview">
          {t(
            "Interactive conference preview · Sample participants · No live audio, camera, or screen sharing",
          )}
        </div>
        <div
          className={`conference-body ${panelOpen ? "panel-open" : "panel-hidden"}`}
        >
          <div className="conference-main">
            <div className="conference-stage-heading">
              <span>
                <i /> {t("Everyone has a seat at the table")}
              </span>
              <div className="conference-view-switch">
                <button
                  aria-label={t("Grid view")}
                  aria-pressed={view === "grid"}
                  className={view === "grid" ? "selected" : ""}
                  onClick={() => setView("grid")}
                >
                  <RoomGlyph name="grid" />
                </button>
                <button
                  aria-label={t("Focus speaker view")}
                  aria-pressed={view === "focus"}
                  className={view === "focus" ? "selected" : ""}
                  onClick={() => setView("focus")}
                >
                  <RoomGlyph name="focus" />
                </button>
              </div>
            </div>
            {sharing ? (
              <div className="conference-shared-agenda">
                <span className="shared-label">
                  <RoomGlyph name="share" />{" "}
                  {t("Sarah’s agenda · Share preview")}
                </span>
                <h3>
                  {t("A little focus.")}
                  <br />
                  {t("A better conversation.")}
                </h3>
                <ol>
                  {topics.map((title, i) => (
                    <li key={t(title)} className={topic === i ? "current" : ""}>
                      {t(title)}
                    </li>
                  ))}
                </ol>
                <p>{t("Choose an agenda item to guide the room.")}</p>
              </div>
            ) : (
              <div
                className={`conference-participants ${view === "focus" ? "focus-layout" : ""}`}
              >
                {view === "focus" ? (
                  <>
                    <div className="conference-focus-stage">
                      {participants
                        .filter((person) => person.id === focused)
                        .map(renderParticipant)}
                    </div>
                    <div
                      className="conference-thumbnail-rail"
                      aria-label={t("Other participants")}
                    >
                      {participants
                        .filter((person) => person.id !== focused)
                        .map(renderParticipant)}
                    </div>
                  </>
                ) : (
                  participants.map(renderParticipant)
                )}
              </div>
            )}
            <div className="conference-toast" role="status">
              <span className="toast-spark">✦</span>
              {t(toast)}
            </div>
            <div className="conference-agenda">
              <div>
                <span>{t("ROOM FOCUS")}</span>
                <strong>{t(topics[topic])}</strong>
              </div>
              <div className="agenda-steps">
                {topics.map((title, i) => (
                  <button
                    key={t(title)}
                    title={t(title)}
                    aria-label={`Agenda: ${t(title)}`}
                    aria-pressed={topic === i}
                    className={topic === i ? "current" : ""}
                    onClick={() => {
                      setTopic(i);
                      setToast(`Room focus: ${t(title)}`);
                    }}
                  >
                    {i + 1}
                  </button>
                ))}
              </div>
            </div>
          </div>
          {panelOpen && (
            <aside id="conference-room-panel" className="conference-sidebar">
              <div className="conference-sidebar-close">
                <span>{t("Room panel")}</span>
                <button
                  aria-label={t("Hide room panel")}
                  onClick={() => {
                    setPanelOpen(false);
                    panelButton.current?.focus();
                  }}
                >
                  <RoomGlyph name="close" />
                </button>
              </div>
              <div className="conference-panel-heading">
                <h3>{t("In the room")}</h3>
                <span>{participants.length}</span>
              </div>
              <div className="conference-roster">
                {participants.map((person) => (
                  <div key={person.id}>
                    <span
                      className="roster-avatar"
                      style={{ background: person.color }}
                    >
                      {person.initials}
                    </span>
                    <div>
                      <strong>{person.name}</strong>
                      <small>
                        {t(person.id === "you" ? "Host · You" : "Joined")}
                      </small>
                    </div>
                    <i />
                  </div>
                ))}
              </div>
              <section className="conference-queue">
                <div className="conference-panel-heading">
                  <h3>{t("Who’s next")}</h3>
                  <RoomGlyph name="hand" />
                </div>
                <p>{t("A clear speaking order. Fewer interruptions.")}</p>
                {queue.length ? (
                  queue.map((id, i) => (
                    <div className="speaking-queue-row" key={id}>
                      <span>{i + 1}</span>
                      <strong>
                        {attendees.find((p) => p.id === id)?.name}
                      </strong>
                      <button onClick={() => giveFloor(id)}>
                        {t("Give floor")}
                      </button>
                    </div>
                  ))
                ) : (
                  <p className="queue-clear">
                    {t("Everyone’s had their turn.")}
                  </p>
                )}
              </section>
              {waiting.length > 0 && (
                <section className="conference-waiting">
                  <h3>{t("Waiting to join")}</h3>
                  {waiting.map((person) => (
                    <div className="waiting-person" key={person.id}>
                      <span>{person.name}</span>
                      <button
                        onClick={() => {
                          setJoined((old) => [...old, person.id]);
                          setToast(`${person.name} joined the preview room.`);
                        }}
                      >
                        {t("Admit")}
                      </button>
                    </div>
                  ))}
                </section>
              )}
              <section className="conference-notes">
                <label htmlFor="room-notes">
                  {t("Decisions & next steps")}
                </label>
                <textarea
                  id="room-notes"
                  placeholder={t("Capture a decision or an action…")}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  maxLength={2000}
                />
                <p>{t("Private preview notes · Cleared when you leave")}</p>
              </section>
            </aside>
          )}
        </div>
        <footer className="conference-controls">
          <span className="conference-footer-brand">
            YeyoFone <small>{t("Make room for better conversations.")}</small>
          </span>
          <div className="conference-control-buttons">
            <button
              data-control="mic"
              aria-pressed={muted}
              className={muted ? "on" : ""}
              onClick={() => setMuted(!muted)}
            >
              <RoomGlyph name="mic" off={muted} />
              <span>{t(muted ? "Unmute" : "Mute")}</span>
            </button>
            <button
              data-control="camera"
              aria-pressed={!camera}
              className={!camera ? "on" : ""}
              onClick={() => setCamera(!camera)}
            >
              <RoomGlyph name="video" off={!camera} />
              <span>{t(camera ? "Camera off" : "Camera on")}</span>
            </button>
            <button
              data-control="share"
              aria-pressed={sharing}
              className={sharing ? "on" : ""}
              onClick={() => {
                setSharing(!sharing);
                setToast(
                  sharing
                    ? "Agenda share preview stopped."
                    : "Showing an agenda share preview. No device screen is captured.",
                );
              }}
            >
              <RoomGlyph name="share" />
              <span>{t(sharing ? "Stop share" : "Share agenda")}</span>
            </button>
            <button
              data-control="hand"
              aria-pressed={raised}
              className={raised ? "on" : ""}
              onClick={toggleHand}
            >
              <RoomGlyph name="hand" />
              <span>{t(raised ? "Lower hand" : "Raise hand")}</span>
            </button>
            <button className="conference-leave" onClick={closeRoom}>
              <RoomGlyph name="leave" />
              <span>{t("Leave")}</span>
            </button>
          </div>
        </footer>
      </div>
    </dialog>
  );
}
