import { useEffect, useRef, type RefObject } from "react";
import { useLanguage } from "./i18n";
import { insertDialKey } from "./destinationWire";

type Props = {
  value: string;
  onChange: (value: string) => void;
  inputRef: RefObject<HTMLInputElement | null>;
};

export const DIAL_KEYS: { key: string; letters?: string; long?: string }[] = [
  { key: "1" },
  { key: "2", letters: "ABC" },
  { key: "3", letters: "DEF" },
  { key: "4", letters: "GHI" },
  { key: "5", letters: "JKL" },
  { key: "6", letters: "MNO" },
  { key: "7", letters: "PQRS" },
  { key: "8", letters: "TUV" },
  { key: "9", letters: "WXYZ" },
  { key: "*" },
  { key: "0", long: "+" },
  { key: "#" },
];

export function Keypad({ value, onChange, inputRef }: Props) {
  const { t } = useLanguage();
  const longPressTimer = useRef<number | null>(null);
  const longPressFired = useRef(false);

  function insert(text: string) {
    const node = inputRef.current;
    const start = node?.selectionStart ?? value.length;
    const end = node?.selectionEnd ?? start;
    const next = insertDialKey(value, text, start, end);
    if (next.length > 512) return;
    onChange(next);
    requestAnimationFrame(() => {
      node?.focus();
      const pos = start + text.length;
      node?.setSelectionRange(pos, pos);
    });
  }

  function press(_key: string, long?: string) {
    longPressFired.current = false;
    if (!long) {
      return;
    }
    longPressTimer.current = window.setTimeout(() => {
      longPressFired.current = true;
      insert(long);
      longPressTimer.current = null;
    }, 450);
  }

  function release(key: string, _long?: string) {
    if (longPressTimer.current != null) {
      window.clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
    if (!longPressFired.current) insert(key);
    longPressFired.current = false;
  }

  function cancel() {
    if (longPressTimer.current != null) {
      window.clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
    longPressFired.current = false;
  }

  useEffect(() => () => cancel(), []);

  return (
    <div className="keypad" role="group" aria-label={t("Keypad")}>
      {DIAL_KEYS.map(({ key, letters, long }) => (
        <button
          key={key}
          type="button"
          className="keypad__key"
          onPointerDown={() => press(key, long)}
          onPointerUp={() => release(key, long)}
          onPointerLeave={cancel}
          onPointerCancel={cancel}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              insert(key);
            }
          }}
          aria-label={key}
        >
          <span className="keypad__digit">{key}</span>
          <span className="keypad__letters" aria-hidden="true">
            {letters ?? long ?? " "}
          </span>
        </button>
      ))}
      <button
        type="button"
        className="keypad__key keypad__key--action"
        aria-label={t("Delete last digit")}
        onPointerDown={() => {
          longPressFired.current = false;
          longPressTimer.current = window.setTimeout(() => {
            longPressFired.current = true;
            onChange("");
            longPressTimer.current = null;
          }, 450);
        }}
        onPointerUp={() => {
          if (longPressTimer.current != null)
            window.clearTimeout(longPressTimer.current);
          longPressTimer.current = null;
        }}
        onPointerCancel={cancel}
        onPointerLeave={cancel}
        onClick={() => {
          if (!longPressFired.current) {
            const node = inputRef.current;
            const start = node?.selectionStart ?? value.length;
            const end = node?.selectionEnd ?? start;
            const from = start === end ? Math.max(0, start - 1) : start;
            onChange(value.slice(0, from) + value.slice(end));
            requestAnimationFrame(() => node?.setSelectionRange(from, from));
          }
          longPressFired.current = false;
          inputRef.current?.focus();
        }}
        onDoubleClick={() => {
          onChange("");
          inputRef.current?.focus();
        }}
      >
        <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
          <path
            d="M9 5h11v14H9l-6-7 6-7zm3 4l4 4m0-4l-4 4"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
    </div>
  );
}
