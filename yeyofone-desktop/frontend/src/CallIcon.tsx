export type CallIconName =
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
