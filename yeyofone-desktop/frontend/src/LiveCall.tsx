import { useCalls } from "./useCalls";
import { callLabel, callTime } from "./calls";
import { useLanguage } from "./i18n";
export function LiveCall({ onOpen }: { onOpen: () => void }) {
  const { rows, available } = useCalls();
  const { t } = useLanguage();
  const call =
    rows.find(
      (c) =>
        c.direction === "outgoing" &&
        c.state !== "ended" &&
        c.consultParentId === null,
    ) ?? rows.find((c) => c.direction === "incoming" && c.state !== "ended");
  if (!call) return null;
  return (
    <button className="live-call-banner" onClick={onOpen}>
      {t(
        available
          ? call.held
            ? "On hold"
            : callLabel(call.state)
          : "Call status unavailable",
      )}{" "}
      · {call.destination} · {callTime(call.durationSeconds)} · {t("Open call")}
    </button>
  );
}
