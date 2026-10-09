import { useLanguage } from "./i18n";
import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
} from "react";
import "./conference-room.css";
import {
  LocalCaptures,
  mediaFailure,
  micLevel,
  nextTalking,
} from "./roomMedia";

type RoomIcon =
  | "video"
  | "video-off"
  | "mic"
  | "mic-off"
  | "hand"
  | "share"
  | "people"
  | "close"
  | "leave"
  | "expand"
  | "shrink"
  | "search"
  | "speaking"
  | "pin"
  | "send"
  | "screen"
  | "agenda";
function LiveVideo({
  stream,
  label,
  mirror = false,
}: {
  stream: MediaStream | null;
  label: string;
  mirror?: boolean;
}) {
  return (
    <video
      className={`room-video${mirror ? " is-mirrored" : ""}`}
      ref={(video) => {
        if (video && video.srcObject !== stream) video.srcObject = stream;
      }}
      autoPlay
      muted
      playsInline
      aria-label={label}
    />
  );
}

// Icon paths from Lucide (https://lucide.dev, ISC License); see LICENSES-THIRD-PARTY.md.
function RoomGlyph({ name, size = 22 }: { name: RoomIcon; size?: number }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === "video" && (
        <>
          <path d="m22 8-6 4 6 4V8Z" />
          <rect x="2" y="6" width="14" height="12" rx="2" ry="2" />
        </>
      )}
      {name === "video-off" && (
        <>
          <path d="M10.66 6H14a2 2 0 0 1 2 2v2.34l1 1L22 8v8" />
          <path d="M16 16a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2l10 10Z" />
          <line x1="2" x2="22" y1="2" y2="22" />
        </>
      )}
      {name === "mic" && (
        <>
          <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
          <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
          <line x1="12" x2="12" y1="19" y2="22" />
        </>
      )}
      {name === "mic-off" && (
        <>
          <line x1="2" x2="22" y1="2" y2="22" />
          <path d="M18.89 13.23A7.12 7.12 0 0 0 19 12v-2" />
          <path d="M5 10v2a7 7 0 0 0 12 5" />
          <path d="M15 9.34V5a3 3 0 0 0-5.68-1.33" />
          <path d="M9 9v3a3 3 0 0 0 5.12 2.12" />
          <line x1="12" x2="12" y1="19" y2="22" />
        </>
      )}
      {name === "hand" && (
        <>
          <path d="M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2" />
          <path d="M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2" />
          <path d="M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8" />
          <path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15" />
        </>
      )}
      {name === "share" && (
        <>
          <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8" />
          <polyline points="16 6 12 2 8 6" />
          <line x1="12" x2="12" y1="2" y2="15" />
        </>
      )}
      {name === "people" && (
        <>
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </>
      )}
      {name === "close" && (
        <>
          <path d="M18 6 6 18" />
          <path d="m6 6 12 12" />
        </>
      )}
      {name === "leave" && (
        <>
          <path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.42 19.42 0 0 1-3.33-2.67m-2.67-3.34a19.79 19.79 0 0 1-3.07-8.63A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91" />
          <line x1="22" x2="2" y1="2" y2="22" />
        </>
      )}
      {name === "expand" && (
        <>
          <path d="M8 3H5a2 2 0 0 0-2 2v3" />
          <path d="M21 8V5a2 2 0 0 0-2-2h-3" />
          <path d="M3 16v3a2 2 0 0 0 2 2h3" />
          <path d="M16 21h3a2 2 0 0 0 2-2v-3" />
        </>
      )}
      {name === "shrink" && (
        <>
          <path d="M8 3v3a2 2 0 0 1-2 2H3" />
          <path d="M21 8h-3a2 2 0 0 1-2-2V3" />
          <path d="M3 16h3a2 2 0 0 1 2 2v3" />
          <path d="M16 21v-3a2 2 0 0 1 2-2h3" />
        </>
      )}
      {name === "search" && (
        <>
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.3-4.3" />
        </>
      )}
      {name === "speaking" && (
        <>
          <path d="M2 10v3" />
          <path d="M6 6v11" />
          <path d="M10 3v18" />
          <path d="M14 8v7" />
          <path d="M18 5v13" />
          <path d="M22 10v3" />
        </>
      )}
      {name === "pin" && (
        <>
          <line x1="12" x2="12" y1="17" y2="22" />
          <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z" />
        </>
      )}
      {name === "screen" && (
        <>
          <path d="m9 10 3-3 3 3" />
          <path d="M12 13V7" />
          <rect width="20" height="14" x="2" y="3" rx="2" />
          <path d="M12 17v4" />
          <path d="M8 21h8" />
        </>
      )}
      {name === "agenda" && (
        <>
          <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
          <path d="M14 2v4a2 2 0 0 0 2 2h4" />
          <path d="M10 9H8" />
          <path d="M16 13H8" />
          <path d="M16 17H8" />
        </>
      )}
      {name === "send" && (
        <>
          <path d="m22 2-7 20-4-9-9-4Z" />
          <path d="M22 2 11 13" />
        </>
      )}
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
type Person = (typeof attendees)[number];
const topics = [
  "Team check-in",
  "Product priorities",
  "Decisions & next steps",
];
const sampleChat = [
  {
    from: "alex",
    text: "Morning all! I added the release notes to the agenda.",
  },
  { from: "maya", text: "Thanks, I’ll walk through the API changes." },
];

export function ConferenceRoom({ onClose }: { onClose: () => void }) {
  const { t } = useLanguage();
  const dialog = useRef<HTMLDialogElement>(null);
  const title = useRef<HTMLHeadingElement>(null);
  const [joined, setJoined] = useState(["you", "alex", "maya", "daniel"]);
  const [focused, setFocused] = useState("alex");
  const [stage, setStage] = useState<"meeting" | "notes">("meeting");
  const [panel, setPanel] = useState<"chat" | "participants">("participants");
  const [panelOpen, setPanelOpen] = useState(() => window.innerWidth > 900);
  const [query, setQuery] = useState("");
  // Camera, mic and screen are real local captures shown only on this device;
  // nothing is sent. Camera and mic start off so macOS only asks when you turn them on.
  const [muted, setMuted] = useState(true);
  const [camera, setCamera] = useState(false);
  const [talking, setTalking] = useState(false);
  const [captures] = useState(() => new LocalCaptures());
  const meter = useRef<{ context: AudioContext; frame: number } | null>(null);
  const [raised, setRaised] = useState(false);
  const [sharing, setSharing] = useState<"agenda" | "screen" | null>(null);
  // While you share your screen, someone else can take the stage; your screen then
  // shows in your own tile.
  const [shareOnStage, setShareOnStage] = useState(true);
  const [shareMenu, setShareMenu] = useState(false);
  const [mediaError, setMediaError] = useState("");
  const shareBox = useRef<HTMLDivElement>(null);
  const shareButton = useRef<HTMLButtonElement>(null);
  function closeShareMenu() {
    setShareMenu(false);
    shareButton.current?.focus();
  }
  // Arrow keys, Home and End move between the menu's items, wrapping around.
  function moveInMenu(e: KeyboardEvent<HTMLDivElement>) {
    const items = [
      ...e.currentTarget.querySelectorAll<HTMLButtonElement>(
        '[role="menuitem"]',
      ),
    ];
    const at = items.indexOf(document.activeElement as HTMLButtonElement);
    const next =
      e.key === "ArrowDown"
        ? (at + 1) % items.length
        : e.key === "ArrowUp"
          ? (at - 1 + items.length) % items.length
          : e.key === "Home"
            ? 0
            : e.key === "End"
              ? items.length - 1
              : e.key === "Tab"
                ? -2
                : -1;
    if (next === -2) setShareMenu(false);
    if (next < 0) return;
    e.preventDefault();
    items[next]?.focus();
  }
  // WebKit doesn't focus clicked buttons, so close on outside clicks, not on blur.
  useEffect(() => {
    if (!shareMenu) return;
    const outside = (e: PointerEvent) => {
      if (!shareBox.current?.contains(e.target as Node)) setShareMenu(false);
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [shareMenu]);
  const [seconds, setSeconds] = useState(0);
  const [topic, setTopic] = useState(0);
  const [notes, setNotes] = useState("");
  const [messages, setMessages] = useState(sampleChat);
  const [draft, setDraft] = useState("");
  const [fullscreen, setFullscreen] = useState(false);
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
  function stopSharing() {
    captures.stop("screen");
    setSharing(null);
  }
  function stopMic() {
    captures.stop("microphone");
    if (meter.current) {
      cancelAnimationFrame(meter.current.frame);
      void meter.current.context.close();
      meter.current = null;
    }
    setTalking(false);
    setMuted(true);
  }
  // Release every capture when the room closes.
  useEffect(
    () => () => {
      captures.stopAll();
      if (meter.current) {
        cancelAnimationFrame(meter.current.frame);
        void meter.current.context.close();
      }
    },
    [],
  );
  async function toggleCamera() {
    if (camera) {
      captures.stop("camera");
      setCamera(false);
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setMediaError("Camera and microphone aren’t available in this window.");
      return;
    }
    try {
      captures.set(
        "camera",
        await navigator.mediaDevices.getUserMedia({ video: true }),
      );
      setCamera(true);
      setMediaError("");
    } catch (error) {
      setMediaError(mediaFailure(error, "camera"));
    }
  }
  async function toggleMic() {
    if (!muted) {
      stopMic();
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setMediaError("Camera and microphone aren’t available in this window.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      captures.set("microphone", stream);
      // Light your "speaking" badge from the microphone level.
      const context = new AudioContext();
      void context.resume();
      const analyser = context.createAnalyser();
      analyser.fftSize = 512;
      context.createMediaStreamSource(stream).connect(analyser);
      const samples = new Uint8Array(analyser.fftSize);
      let loud = false;
      const tick = () => {
        if (!meter.current) return;
        analyser.getByteTimeDomainData(samples);
        const next = nextTalking(micLevel(samples), loud);
        if (next !== loud) {
          loud = next;
          setTalking(next);
        }
        meter.current.frame = requestAnimationFrame(tick);
      };
      meter.current = { context, frame: requestAnimationFrame(tick) };
      setMuted(false);
      setMediaError("");
    } catch (error) {
      stopMic();
      setMediaError(mediaFailure(error, "microphone"));
    }
  }
  function showOnStage(id: string) {
    setFocused(id);
    setStage("meeting");
    if (sharing === "screen") setShareOnStage(id === "you");
    else if (sharing === "agenda") stopSharing();
  }
  async function shareScreen() {
    setShareMenu(false);
    setMediaError("");
    if (!navigator.mediaDevices?.getDisplayMedia) {
      setMediaError("Screen sharing isn’t available in this window.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getDisplayMedia({
        video: true,
        audio: false,
      });
      captures.set("screen", stream);
      // Ending the share from the system's own controls stops it here too.
      stream.getVideoTracks()[0]?.addEventListener("ended", () => {
        if (captures.get("screen") === stream) stopSharing();
      });
      setSharing("screen");
      setShareOnStage(true);
      setStage("meeting");
    } catch (error) {
      setMediaError(
        error instanceof DOMException && error.name === "NotAllowedError"
          ? "Screen sharing was cancelled or not allowed."
          : "Screen sharing couldn’t start.",
      );
    }
  }
  function shareAgenda() {
    setShareMenu(false);
    setMediaError("");
    stopSharing();
    setSharing("agenda");
    setStage("meeting");
  }
  useEffect(() => {
    const element = dialog.current;
    const previousFocus =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    element?.showModal();
    // showModal() focuses the first button (Notes); start on the room's name instead.
    title.current?.focus();
    const timer = window.setInterval(() => setSeconds((s) => s + 1), 1000);
    const arrival = window.setTimeout(() => {
      setJoined((old) => (old.includes("elena") ? old : [...old, "elena"]));
    }, 4500);
    return () => {
      window.clearInterval(timer);
      window.clearTimeout(arrival);
      element?.close();
      previousFocus?.focus();
    };
  }, []);
  const participants = attendees.filter((person) => joined.includes(person.id));
  const invited = attendees.filter((person) => !joined.includes(person.id));
  const speaker =
    participants.find((person) => person.id === focused) ?? participants[0]!;
  const match = (person: Person) =>
    person.name.toLowerCase().includes(query.trim().toLowerCase());
  const speaking = (person: Person) =>
    person.id === "you"
      ? !muted && talking
      : person.id === speaker.id && !sharing;
  const handUp = (person: Person) => person.id === "you" && raised;
  const time = `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;

  function sendMessage() {
    const text = draft.trim();
    if (!text) return;
    setMessages((old) => [...old, { from: "you", text }]);
    setDraft("");
  }

  const avatar = (person: Person, className: string) => (
    <span
      className={className}
      style={{ "--participant-color": person.color } as CSSProperties}
    >
      {person.initials}
    </span>
  );

  return (
    <dialog
      ref={dialog}
      className={`conference-dialog ${fullscreen ? "is-fullscreen" : ""}`}
      aria-labelledby="conference-title"
      aria-describedby="conference-preview"
      onCancel={(e) => {
        e.preventDefault();
        if (shareMenu) closeShareMenu();
        else closeRoom();
      }}
    >
      <div className={`room ${panelOpen ? "" : "room--panel-hidden"}`}>
        <section className="room-meeting">
          <header className="room-header">
            <div className="room-title">
              <span>
                {t("Weekly catch-up")} · {time}
              </span>
              <h2 id="conference-title" ref={title} tabIndex={-1}>
                {t("Team room")}
              </h2>
            </div>
            <div className="room-tabs" role="group" aria-label={t("View")}>
              {(["notes", "meeting"] as const).map((id) => (
                <button
                  key={id}
                  aria-pressed={stage === id}
                  className={stage === id ? "selected" : ""}
                  onClick={() => setStage(id)}
                >
                  {t(id === "notes" ? "Notes" : "Meeting")}
                </button>
              ))}
            </div>
            <div className="room-header-actions">
              <button
                aria-label={t(
                  panelOpen ? "Hide room panel" : "Show room panel",
                )}
                title={t(panelOpen ? "Hide room panel" : "Show room panel")}
                aria-expanded={panelOpen}
                aria-controls="conference-room-panel"
                className={panelOpen ? "selected" : ""}
                onClick={() => setPanelOpen(!panelOpen)}
              >
                <RoomGlyph name="people" size={20} />
              </button>
              <button
                aria-label={t(fullscreen ? "Exit fullscreen" : "Fullscreen")}
                title={t(fullscreen ? "Exit fullscreen" : "Fullscreen")}
                aria-pressed={fullscreen}
                onClick={toggleFullscreen}
              >
                <RoomGlyph name={fullscreen ? "shrink" : "expand"} size={20} />
              </button>
              <button
                aria-label={t("Close conference preview")}
                title={t("Close conference preview")}
                onClick={closeRoom}
              >
                <RoomGlyph name="close" size={20} />
              </button>
            </div>
          </header>

          {stage === "notes" ? (
            <div className="room-notes">
              <h3>{t("Agenda")}</h3>
              <ol>
                {topics.map((title, i) => (
                  <li key={title}>
                    <button
                      aria-pressed={topic === i}
                      className={topic === i ? "current" : ""}
                      onClick={() => setTopic(i)}
                    >
                      <span>{i + 1}</span>
                      {t(title)}
                    </button>
                  </li>
                ))}
              </ol>
              <label htmlFor="room-notes">{t("Decisions & next steps")}</label>
              <textarea
                id="room-notes"
                placeholder={t("Capture a decision or an action…")}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                maxLength={2000}
              />
              <p>{t("Private preview notes · Cleared when you leave")}</p>
            </div>
          ) : (
            <>
              <div className="room-stage">
                {mediaError && (
                  <p className="room-media-error" role="alert">
                    {t(mediaError)}
                    <button
                      aria-label={t("Dismiss notification")}
                      onClick={() => setMediaError("")}
                    >
                      <RoomGlyph name="close" size={16} />
                    </button>
                  </p>
                )}
                {sharing === "screen" && shareOnStage ? (
                  <div className="room-screen">
                    <video
                      ref={(video) => {
                        const stream = captures.get("screen");
                        if (video && video.srcObject !== stream)
                          video.srcObject = stream;
                      }}
                      autoPlay
                      muted
                      playsInline
                      aria-label={t("Your shared screen")}
                    />
                    <div className="room-screen__bar">
                      <span>
                        <RoomGlyph name="screen" size={16} />
                        {t("You’re sharing your screen")}
                        <small>
                          {t("Preview only · Not sent to other participants")}
                        </small>
                      </span>
                      <button onClick={stopSharing}>{t("Stop sharing")}</button>
                    </div>
                  </div>
                ) : sharing === "agenda" ? (
                  <div className="room-shared">
                    <span className="room-shared__label">
                      <RoomGlyph name="share" size={16} />
                      {t("Sarah’s agenda · Share preview")}
                    </span>
                    <h3>
                      {t("A little focus.")}
                      <br />
                      {t("A better conversation.")}
                    </h3>
                    <ol>
                      {topics.map((title, i) => (
                        <li
                          key={title}
                          className={topic === i ? "current" : ""}
                        >
                          {t(title)}
                        </li>
                      ))}
                    </ol>
                  </div>
                ) : (
                  <div
                    className="room-speaker"
                    style={
                      { "--participant-color": speaker.color } as CSSProperties
                    }
                  >
                    {speaker.id === "you" && camera ? (
                      <LiveVideo
                        stream={captures.get("camera")}
                        label={t("Your camera")}
                        mirror
                      />
                    ) : (
                      avatar(speaker, "room-speaker__avatar")
                    )}
                    <span className="room-speaker__name">
                      {speaker.name}
                      {speaker.id === "you" && ` (${t("You")})`}
                    </span>
                  </div>
                )}
              </div>
              <div className="room-strip" aria-label={t("Participants")}>
                {participants.map((person) => {
                  const you = person.id === "you";
                  return (
                    <button
                      key={person.id}
                      className="room-tile"
                      style={
                        { "--participant-color": person.color } as CSSProperties
                      }
                      aria-label={`${t("Show on stage")}: ${person.name}`}
                      aria-pressed={person.id === speaker.id}
                      onClick={() => showOnStage(person.id)}
                    >
                      {you && sharing === "screen" && !shareOnStage ? (
                        <LiveVideo
                          stream={captures.get("screen")}
                          label={t("Your shared screen")}
                        />
                      ) : you && camera ? (
                        <LiveVideo
                          stream={captures.get("camera")}
                          label={t("Your camera")}
                          mirror
                        />
                      ) : (
                        avatar(person, "room-tile__avatar")
                      )}
                      <span className="room-tile__name">
                        {person.id === "you" ? t("You") : person.name}
                      </span>
                      <span className="room-tile__badges">
                        {person.id === "you" && sharing === "screen" && (
                          <i className="room-badge">
                            <RoomGlyph name="screen" size={14} />
                          </i>
                        )}
                        {handUp(person) && (
                          <i className="room-badge room-badge--hand">
                            <RoomGlyph name="hand" size={14} />
                          </i>
                        )}
                        {you && muted ? (
                          <i className="room-badge room-badge--muted">
                            <RoomGlyph name="mic-off" size={14} />
                          </i>
                        ) : (
                          speaking(person) && (
                            <i className="room-badge">
                              <RoomGlyph name="speaking" size={14} />
                            </i>
                          )
                        )}
                      </span>
                    </button>
                  );
                })}
              </div>
            </>
          )}

          <div className="room-controls">
            <p id="conference-preview" className="room-preview-note">
              {t(
                "Conference preview · Sample participants · Nothing you share is sent to anyone",
              )}
            </p>
            <div className="room-control-buttons">
              <button
                aria-pressed={!camera}
                className={!camera ? "is-off" : ""}
                aria-label={t(camera ? "Camera off" : "Camera on")}
                title={t(camera ? "Camera off" : "Camera on")}
                onClick={() => void toggleCamera()}
              >
                <RoomGlyph name={camera ? "video" : "video-off"} />
              </button>
              <button
                aria-pressed={muted}
                className={muted ? "is-off" : ""}
                aria-label={t(muted ? "Unmute" : "Mute")}
                title={t(muted ? "Unmute" : "Mute")}
                onClick={() => void toggleMic()}
              >
                <RoomGlyph name={muted ? "mic-off" : "mic"} />
              </button>
              <div className="room-share" ref={shareBox}>
                <button
                  ref={shareButton}
                  className={sharing ? "is-on" : ""}
                  aria-label={t(sharing ? "Stop sharing" : "Share")}
                  title={t(sharing ? "Stop sharing" : "Share")}
                  aria-haspopup={sharing ? undefined : "menu"}
                  aria-expanded={sharing ? undefined : shareMenu}
                  onClick={() =>
                    sharing ? stopSharing() : setShareMenu(!shareMenu)
                  }
                >
                  <RoomGlyph name="share" />
                </button>
                {shareMenu && (
                  <div
                    className="room-share__menu"
                    role="menu"
                    aria-label={t("Share")}
                    onKeyDown={moveInMenu}
                  >
                    <button role="menuitem" autoFocus onClick={shareScreen}>
                      <RoomGlyph name="screen" size={18} />
                      <span>
                        {t("Share screen")}
                        <small>{t("A window or your whole display")}</small>
                      </span>
                    </button>
                    <button role="menuitem" onClick={shareAgenda}>
                      <RoomGlyph name="agenda" size={18} />
                      <span>
                        {t("Share agenda")}
                        <small>{t("The meeting topics")}</small>
                      </span>
                    </button>
                  </div>
                )}
              </div>
              <button
                aria-pressed={raised}
                className={raised ? "is-on" : ""}
                aria-label={t(raised ? "Lower hand" : "Raise hand")}
                title={t(raised ? "Lower hand" : "Raise hand")}
                onClick={() => setRaised(!raised)}
              >
                <RoomGlyph name="hand" />
              </button>
              <button
                className="room-leave"
                aria-label={t("Leave")}
                title={t("Leave")}
                onClick={closeRoom}
              >
                <RoomGlyph name="leave" />
              </button>
            </div>
          </div>
        </section>

        {panelOpen && (
          <aside id="conference-room-panel" className="room-panel">
            <div className="room-panel__tabs" role="tablist">
              {(["chat", "participants"] as const).map((id) => (
                <button
                  key={id}
                  role="tab"
                  aria-selected={panel === id}
                  className={panel === id ? "selected" : ""}
                  onClick={() => setPanel(id)}
                >
                  {t(id === "chat" ? "Chat" : "Participants")}
                  {id === "participants" && <span>{participants.length}</span>}
                </button>
              ))}
            </div>
            {panel === "participants" ? (
              <div className="room-panel__body">
                <label className="room-search">
                  <RoomGlyph name="search" size={18} />
                  <input
                    type="search"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder={t("Search for people")}
                    aria-label={t("Search for people")}
                  />
                </label>
                <h3>{t("On the call")}</h3>
                <ul className="room-roster">
                  {participants.filter(match).map((person) => (
                    <li key={person.id}>
                      {avatar(person, "room-roster__avatar")}
                      <div>
                        <strong>
                          {person.name}
                          {person.id === "you" && ` (${t("You")})`}
                        </strong>
                        <small>
                          {handUp(person)
                            ? t("Hand raised")
                            : t(person.id === "you" ? "Host" : person.role)}
                        </small>
                      </div>
                      {speaking(person) ? (
                        <span
                          className="room-roster__action is-speaking"
                          title={t("Speaking")}
                        >
                          <RoomGlyph name="speaking" size={16} />
                        </span>
                      ) : (
                        <button
                          className="room-roster__action"
                          aria-label={`${t("Show on stage")}: ${person.name}`}
                          title={t("Show on stage")}
                          onClick={() => showOnStage(person.id)}
                        >
                          <RoomGlyph name="pin" size={16} />
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
                {invited.length > 0 && (
                  <>
                    <h3>
                      {t("Invited")} <span>{invited.length}</span>
                    </h3>
                    <ul className="room-roster">
                      {invited.filter(match).map((person) => (
                        <li key={person.id}>
                          {avatar(person, "room-roster__avatar")}
                          <div>
                            <strong>{person.name}</strong>
                            <small>{t(person.role)}</small>
                          </div>
                          <button
                            className="room-admit"
                            onClick={() =>
                              setJoined((old) => [...old, person.id])
                            }
                          >
                            {t("Admit")}
                          </button>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </div>
            ) : (
              <div className="room-panel__body room-chat">
                <ul className="room-chat__messages">
                  {messages.map((message, i) => {
                    const person = attendees.find(
                      (p) => p.id === message.from,
                    )!;
                    return (
                      <li
                        key={i}
                        className={message.from === "you" ? "is-mine" : ""}
                      >
                        {avatar(person, "room-roster__avatar")}
                        <div>
                          <strong>
                            {message.from === "you" ? t("You") : person.name}
                          </strong>
                          <p>
                            {i < sampleChat.length
                              ? t(message.text)
                              : message.text}
                          </p>
                        </div>
                      </li>
                    );
                  })}
                </ul>
                <form
                  className="room-chat__form"
                  onSubmit={(e) => {
                    e.preventDefault();
                    sendMessage();
                  }}
                >
                  <input
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    placeholder={t("Message everyone")}
                    aria-label={t("Message everyone")}
                    maxLength={500}
                  />
                  <button
                    type="submit"
                    aria-label={t("Send")}
                    title={t("Send")}
                    disabled={!draft.trim()}
                  >
                    <RoomGlyph name="send" size={18} />
                  </button>
                </form>
                <p className="room-chat__note">
                  {t("Preview chat · Messages stay on this device")}
                </p>
              </div>
            )}
          </aside>
        )}
      </div>
    </dialog>
  );
}
