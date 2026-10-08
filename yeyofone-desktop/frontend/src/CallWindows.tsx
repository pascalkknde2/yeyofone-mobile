import { useLanguage } from "./i18n";
import { useEffect, useRef, useState } from "react";
import "./call-windows.css";

export type CallWindowKind = "incoming" | "dialer" | "video";
type CallStage = "ringing" | "dialer" | "active";
type CallIconName =
  | "phone"
  | "hangup"
  | "mic"
  | "speaker"
  | "video"
  | "keypad"
  | "close"
  | "erase"
  | "expand";
export function CallIcon({
  name,
  off = false,
}: {
  name: CallIconName;
  off?: boolean;
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === "phone" && (
        <path d="m7 3 3 5-3 3c2 3 3 4 6 6l3-3 5 3c0 3-2 5-5 4C9 19 5 15 3 8 2 5 4 3 7 3Z" />
      )}
      {name === "hangup" && (
        <path d="M3 16v-4c5-5 13-5 18 0v4h-5v-4c-3-1-5-1-8 0v4Z" />
      )}
      {name === "mic" && (
        <>
          <rect x="9" y="2" width="6" height="13" rx="3" />
          <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8" />
        </>
      )}
      {name === "speaker" && (
        <>
          <path d="M3 9h4l5-5v16l-5-5H3ZM16 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14" />
        </>
      )}
      {name === "video" && (
        <>
          <rect x="2" y="5" width="14" height="14" rx="3" />
          <path d="m16 10 6-4v12l-6-4" />
        </>
      )}
      {name === "keypad" &&
        [5, 12, 19].flatMap((x) =>
          [5, 12, 19].map((y) => (
            <circle key={`${x}-${y}`} cx={x} cy={y} r="1" />
          )),
        )}
      {name === "close" && <path d="m6 6 12 12M6 18 18 6" />}
      {name === "erase" && (
        <>
          <path d="m8 5-6 7 6 7h14V5ZM12 9l6 6m-6 0 6-6" />
        </>
      )}
      {name === "expand" && <path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5" />}
      {off && <path d="m3 3 18 18" strokeWidth="2.3" />}
    </svg>
  );
}
const keys = [
  ["1", ""],
  ["2", "ABC"],
  ["3", "DEF"],
  ["4", "GHI"],
  ["5", "JKL"],
  ["6", "MNO"],
  ["7", "PQRS"],
  ["8", "TUV"],
  ["9", "WXYZ"],
  ["*", ""],
  ["0", "+"],
  ["#", ""],
];
function Keypad({ onKey }: { onKey: (key: string) => void }) {
  return (
    <div className="phone-keypad">
      {keys.map(([key, letters]) => (
        <button
          type="button"
          key={key}
          onClick={() => onKey(key!)}
          aria-label={`${key}${letters ? ` ${letters}` : ""}`}
        >
          <span>{key}</span>
          {letters && <small>{letters}</small>}
        </button>
      ))}
    </div>
  );
}
function Control({
  icon,
  label,
  active,
  onClick,
  off = false,
}: {
  icon: CallIconName;
  label: string;
  active: boolean;
  onClick: () => void;
  off?: boolean;
}) {
  const { t } = useLanguage();
  return (
    <button
      className={`call-control ${active ? "is-on" : ""}`}
      aria-pressed={active}
      onClick={onClick}
    >
      <span>
        <CallIcon name={icon} off={off} />
      </span>
      {t(label)}
    </button>
  );
}
export function CallWindows({
  kind,
  onClose,
}: {
  kind: CallWindowKind;
  onClose: () => void;
}) {
  const { t } = useLanguage();
  const dialog = useRef<HTMLDialogElement>(null);
  const numberInput = useRef<HTMLInputElement>(null);
  const [stage, setStage] = useState<CallStage>(
    kind === "incoming" ? "ringing" : kind === "dialer" ? "dialer" : "active",
  );
  const [number, setNumber] = useState("");
  const [muted, setMuted] = useState(false);
  const [speaker, setSpeaker] = useState(false);
  const [camera, setCamera] = useState(true);
  const [video, setVideo] = useState(kind === "video");
  const [keypad, setKeypad] = useState(false);
  const [tones, setTones] = useState("");
  const [seconds, setSeconds] = useState(0);
  const [error, setError] = useState("");
  const videoArea = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const element = dialog.current;
    const previousFocus =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    element?.showModal();
    if (kind === "dialer") numberInput.current?.focus();
    return () => {
      element?.close();
      previousFocus?.focus();
    };
  }, [kind]);
  useEffect(() => {
    if (stage !== "active") return;
    const timer = window.setInterval(() => setSeconds((old) => old + 1), 1000);
    return () => window.clearInterval(timer);
  }, [stage]);
  const duration = `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  const isVideo = video && stage === "active";
  const title =
    stage === "ringing"
      ? "Incoming call"
      : stage === "dialer"
        ? "Make a call"
        : isVideo
          ? "Video call"
          : "Voice call";
  function startCall() {
    if (!/^\+?[0-9*#]{1,32}$/.test(number)) {
      setError("Enter an extension or phone number, up to 32 characters.");
      numberInput.current?.focus();
      return;
    }
    setError("");
    setStage("active");
  }
  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await videoArea.current?.requestFullscreen();
    } catch {
      setError("Fullscreen is not available in this window.");
    }
  }
  return (
    <dialog
      ref={dialog}
      className={`call-dialog ${isVideo ? "video-dialog" : "phone-dialog"}`}
      aria-labelledby="call-window-title"
      aria-describedby="call-preview-disclaimer"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <div className="call-window">
        <header className="call-window-header">
          <div className="call-window-brand">
            <span className="call-brand-mark">Y</span>
            <span>YeyoFone</span>
          </div>
          <button
            className="call-close"
            onClick={onClose}
            aria-label={t("Close call preview")}
          >
            <CallIcon name="close" />
          </button>
        </header>
        <div className="call-preview-label" id="call-preview-disclaimer">
          <i /> {t("Interactive preview · No real call or media")}
        </div>
        <h2 id="call-window-title" className="sr-only">
          {t(title)}
        </h2>
        {stage === "dialer" ? (
          <div className="dialer-content">
            <div className="dialer-heading">
              <h3>{t("Make a call")}</h3>
              <p>{t("Enter a number or extension")}</p>
            </div>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                startCall();
              }}
            >
              <div className="dialer-number">
                <input
                  ref={numberInput}
                  aria-label={t("Phone number or extension")}
                  inputMode="tel"
                  autoComplete="off"
                  placeholder={t("Enter number")}
                  value={number}
                  maxLength={32}
                  onChange={(e) => {
                    setNumber(e.target.value.replace(/[^+0-9*#]/g, ""));
                    setError("");
                  }}
                  aria-invalid={!!error}
                  aria-describedby={error ? "dialer-error" : undefined}
                />
                <button
                  type="button"
                  className="dialer-erase"
                  disabled={!number}
                  aria-label={t("Delete last digit")}
                  onClick={() => setNumber((old) => old.slice(0, -1))}
                >
                  <CallIcon name="erase" />
                </button>
              </div>
              {error && (
                <p id="dialer-error" className="call-error" role="alert">
                  {t(error)}
                </p>
              )}
              <p className="call-account">
                {t("From")} <strong>Sarah Ndio</strong>
                <span>{t("Extension 1005")}</span>
              </p>
              <Keypad
                onKey={(key) => {
                  setNumber((old) => (old + key).slice(0, 32));
                  setError("");
                }}
              />
              <button className="dial-call-button" type="submit">
                <CallIcon name="phone" /> {t("Call")}
              </button>
            </form>
          </div>
        ) : isVideo ? (
          <div className="video-content">
            <div className="video-stage" ref={videoArea}>
              <div className="video-stage-top">
                <div>
                  <span className="video-live-dot" /> {t("Preview session")}{" "}
                  <span className="video-duration">{duration}</span>
                </div>
                <button
                  className="video-expand"
                  aria-label={t("Toggle fullscreen video preview")}
                  onClick={toggleFullscreen}
                >
                  <CallIcon name="expand" />
                </button>
              </div>
              <div className="video-remote">
                <div className="video-remote-avatar">AM</div>
                <h3>Alex Morgan</h3>
                <p>{t("Video preview · Remote camera not connected")}</p>
              </div>
              <div className={`video-self ${camera ? "" : "camera-off"}`}>
                <span>SN</span>
                <p>{t(camera ? "You · Camera preview" : "Camera off")}</p>
                {!camera && <CallIcon name="video" off />}
              </div>
              <div className="video-stage-caption">
                <span>Alex Morgan</span>
                <span>{t("Extension 1000")}</span>
              </div>
            </div>
            <div className="video-call-bottom">
              <div className="video-status">
                <strong>{t("Video call")}</strong>
                <span>{t("Preview controls only")}</span>
              </div>
              <div className="video-controls">
                <Control
                  icon="mic"
                  label={t(muted ? "Unmute" : "Mute")}
                  active={muted}
                  off={muted}
                  onClick={() => setMuted(!muted)}
                />
                <Control
                  icon="video"
                  label={t(camera ? "Camera off" : "Camera on")}
                  active={!camera}
                  off={!camera}
                  onClick={() => setCamera(!camera)}
                />
                <Control
                  icon="speaker"
                  label="Speaker"
                  active={speaker}
                  onClick={() => setSpeaker(!speaker)}
                />
                <button
                  className="video-end"
                  onClick={onClose}
                  aria-label={t("End video call preview")}
                >
                  <CallIcon name="hangup" />
                </button>
              </div>
            </div>
            {error && (
              <p className="call-error" role="alert">
                {t(error)}
              </p>
            )}
          </div>
        ) : (
          <div className="phone-call-content">
            <div
              className={`phone-call-status ${stage === "ringing" ? "ringing" : ""}`}
            >
              <i />
              {t(stage === "ringing" ? "Incoming call" : "Voice call preview")}
            </div>
            <div
              className={`phone-caller-avatar ${stage === "ringing" ? "is-ringing" : ""}`}
            >
              AM
            </div>
            <h3>{kind === "dialer" ? number : "Alex Morgan"}</h3>
            <p className="phone-caller-detail">
              {t(
                kind === "dialer"
                  ? "Outgoing call preview"
                  : "Extension 1000 · Work",
              )}
            </p>
            <div className="phone-call-duration" role="status">
              {t(stage === "ringing" ? "Calling you…" : duration)}
            </div>
            {stage === "ringing" ? (
              <>
                <div className="incoming-call-actions">
                  <div>
                    <button
                      className="round-call-action decline"
                      onClick={onClose}
                      aria-label={t("Decline incoming call preview")}
                    >
                      <CallIcon name="hangup" />
                    </button>
                    <span>{t("Decline")}</span>
                  </div>
                  <div>
                    <button
                      className="round-call-action answer"
                      onClick={() => setStage("active")}
                      aria-label={t("Answer incoming call preview")}
                    >
                      <CallIcon name="phone" />
                    </button>
                    <span>{t("Answer")}</span>
                  </div>
                </div>
                <p className="call-bottom-hint">
                  {t("Answer to explore the call controls")}
                </p>
              </>
            ) : (
              <>
                <div className="voice-controls">
                  <Control
                    icon="mic"
                    label={t(muted ? "Unmute" : "Mute")}
                    active={muted}
                    off={muted}
                    onClick={() => setMuted(!muted)}
                  />
                  <Control
                    icon="keypad"
                    label="Keypad"
                    active={keypad}
                    onClick={() => setKeypad(!keypad)}
                  />
                  <Control
                    icon="speaker"
                    label="Speaker"
                    active={speaker}
                    onClick={() => setSpeaker(!speaker)}
                  />
                  <Control
                    icon="video"
                    label="Video"
                    active={false}
                    onClick={() => setVideo(true)}
                  />
                </div>
                {keypad && (
                  <div className="active-keypad">
                    <output aria-label={t("Entered tones")}>
                      {t(tones || "Enter tones")}
                    </output>
                    <Keypad
                      onKey={(key) => setTones((old) => (old + key).slice(-20))}
                    />
                  </div>
                )}
                <button
                  className="round-call-action decline voice-end"
                  onClick={onClose}
                  aria-label={t("End voice call preview")}
                >
                  <CallIcon name="hangup" />
                </button>
                <p className="call-bottom-hint">{t("End call")}</p>
              </>
            )}
          </div>
        )}
      </div>
    </dialog>
  );
}
