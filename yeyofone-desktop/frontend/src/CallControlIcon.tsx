export type ControlIconName =
  "transfer" | "consult" | "hold" | "resume" | "complete";
export function CallControlIcon({ name }: { name: ControlIconName }) {
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === "transfer" && (
        <>
          <path d="M3 8h16m-5-5 5 5-5 5M3 16h7" />
          <path d="M17 16v5m-3-2 3 2 3-2" />
        </>
      )}
      {name === "consult" && (
        <>
          <path d="M3 4h12v9H8l-4 3v-3H3zM18 8h3v10h-3l-4 3v-3h-4v-2" />
          <path d="m7 8 1.5 1.5L11 7" />
        </>
      )}
      {name === "hold" && (
        <>
          <path d="M8 5v14M16 5v14" strokeWidth="4" />
        </>
      )}
      {name === "resume" && (
        <path d="m8 4 12 8-12 8z" fill="currentColor" stroke="none" />
      )}
      {name === "complete" && (
        <>
          <path d="m3 12 5 5 9-11M16 16h5m-2-2 2 2-2 2" />
        </>
      )}
    </svg>
  );
}
