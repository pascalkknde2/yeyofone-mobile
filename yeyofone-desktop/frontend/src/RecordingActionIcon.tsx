export function RecordingActionIcon({
  name,
}: {
  name: "play" | "download" | "cloud-check" | "upload" | "delete";
}) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === "play" && (
        <path d="m8 5 11 7-11 7Z" fill="currentColor" stroke="none" />
      )}
      {name === "download" && (
        <>
          <path d="M12 3v12m-5-5 5 5 5-5" />
          <path d="M4 16v4h16v-4" />
        </>
      )}
      {name === "upload" && (
        <>
          <path d="M12 16V4m-5 5 5-5 5 5" />
          <path d="M4 16v4h16v-4" />
        </>
      )}
      {name === "cloud-check" && (
        <>
          <path d="M6 18a5 5 0 0 1-1-9 7 7 0 0 1 13-1 5 5 0 0 1 1 10" />
          <path d="m9 16 3 3 5-6" />
        </>
      )}
      {name === "delete" && (
        <>
          <path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7" />
        </>
      )}
    </svg>
  );
}
