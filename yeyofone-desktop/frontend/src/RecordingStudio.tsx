import { RecordingActionIcon } from "./RecordingActionIcon";
import { recordingPeaks } from "./recordingPeaks";
import { useEffect, useRef, useState } from "react";
import { CallIcon } from "./CallWindows";
import { callTime } from "./calls";
import { useLanguage } from "./i18n";
import "./recording-studio.css";

export function RecordingWave({ active = false }: { active?: boolean }) {
  return (
    <div
      className={`recording-wave ${active ? "is-active" : ""}`}
      aria-hidden="true"
    >
      {Array.from({ length: 35 }, (_, index) => (
        <i
          key={index}
          style={{
            height: `${12 + ((index * 17 + 11) % 57)}px`,
            animationDelay: `${index * 35}ms`,
          }}
        />
      ))}
    </div>
  );
}

export function RecordingStudio({
  url,
  title,
  onClose,
  onError,
  onDelete,
}: {
  url: string;
  title: string;
  onClose: () => void;
  onError: () => void;
  onDelete?: () => void;
}) {
  const { t } = useLanguage();
  const audio = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);
  const [peaks, setPeaks] = useState<number[] | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    let context: AudioContext | undefined;
    setPeaks(null);
    async function decode() {
      try {
        const response = await fetch(url, { signal: controller.signal });
        if (!response.ok) throw Error("Audio fetch failed");
        const bytes = await response.arrayBuffer();
        if (!active) return;
        context = new AudioContext();
        const buffer = await context.decodeAudioData(bytes);
        if (!active) return;
        const channels = Array.from(
          { length: buffer.numberOfChannels },
          (_, i) => buffer.getChannelData(i),
        );
        setPeaks(recordingPeaks(channels));
      } catch {
        // Waveform failure must not prevent the audio element from playing.
        if (active) setPeaks([]);
      } finally {
        if (context && context.state !== "closed")
          void context.close().catch(() => {});
      }
    }
    void decode();
    return () => {
      active = false;
      controller.abort();
      if (context && context.state !== "closed")
        void context.close().catch(() => {});
    };
  }, [url]);
  useEffect(() => {
    if (!playing) return;
    let frame: number;
    function update() {
      setPosition(audio.current?.currentTime ?? 0);
      frame = requestAnimationFrame(update);
    }
    frame = requestAnimationFrame(update);
    return () => cancelAnimationFrame(frame);
  }, [playing]);
  async function toggle() {
    if (!audio.current) return;
    if (playing) audio.current.pause();
    else
      try {
        await audio.current.play();
      } catch {
        onError();
      }
  }
  return (
    <section className="recording-studio" aria-label={t("Call recording")}>
      <div className="recording-studio__art" aria-hidden="true">
        <CallIcon name="mic" />
      </div>
      <div className="recording-studio__body">
        <div className="recording-studio__heading">
          <div>
            <small>{t("Call recording")}</small>
            <h3>{title}</h3>
          </div>
          <button
            className="recording-studio__close"
            onClick={onClose}
            aria-label={t("Close")}
          >
            <CallIcon name="close" />
          </button>
        </div>
        <div
          className="recording-wave recording-wave--audio"
          aria-hidden="true"
        >
          {(peaks?.length ? peaks : Array(64).fill(0)).map(
            (peak: number, index: number) => (
              <i
                key={index}
                className={
                  duration > 0 && index / 64 < position / duration
                    ? "is-played"
                    : ""
                }
                style={{ height: `${Math.max(3, peak * 76)}px` }}
              />
            ),
          )}
        </div>
        {peaks?.length === 0 && (
          <small className="recording-wave-status">
            {t("Waveform unavailable")}
          </small>
        )}
        <audio
          ref={audio}
          src={url}
          autoPlay
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => setPlaying(false)}
          onTimeUpdate={() => setPosition(audio.current?.currentTime ?? 0)}
          onLoadedMetadata={() =>
            setDuration(
              Number.isFinite(audio.current?.duration)
                ? audio.current!.duration
                : 0,
            )
          }
          onError={onError}
        />
        <input
          className="recording-studio__seek"
          type="range"
          min={0}
          max={duration || 0}
          step={0.1}
          value={position}
          disabled={!duration}
          aria-label={t("Playback position")}
          onChange={(event) => {
            const next = Number(event.target.value);
            if (audio.current) audio.current.currentTime = next;
            setPosition(next);
          }}
        />
        <div className="recording-studio__time">
          <span>{callTime(Math.floor(position))}</span>
          <span>{callTime(Math.floor(duration))}</span>
        </div>
        <div className="recording-studio__controls">
          <button
            className="recording-studio__play"
            onClick={() => void toggle()}
            aria-label={t(playing ? "Pause" : "Play recording")}
          >
            {playing ? (
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 4h4v16H6zm8 0h4v16h-4z" fill="currentColor" />
              </svg>
            ) : (
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m8 4 12 8-12 8z" fill="currentColor" />
              </svg>
            )}
          </button>
          <button
            className="recording-studio__stop"
            onClick={() => {
              audio.current?.pause();
              if (audio.current) audio.current.currentTime = 0;
              setPosition(0);
            }}
          >
            {t("Stop")}
          </button>
          {onDelete && (
            <button
              className="recording-studio__delete"
              onClick={onDelete}
              aria-label={t("Delete recording")}
              title={t("Delete recording")}
            >
              <RecordingActionIcon name="delete" />
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
