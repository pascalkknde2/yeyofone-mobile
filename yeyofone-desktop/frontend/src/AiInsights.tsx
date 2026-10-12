import { useEffect, useState } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useLanguage } from "./i18n";
import { aiCommand, aiError, parseInsights, type Insights } from "./ai";
import { useAiStatus } from "./AiSettings";
import "./ai.css";

const SENTIMENTS = ["positive", "neutral", "mixed", "negative"] as const;

/** Totals across every call with AI notes: the manager's coaching overview. */
export function AiInsights({ onSettings }: { onSettings: () => void }) {
  const { t } = useLanguage();
  const { status } = useAiStatus();
  const [insights, setInsights] = useState<Insights | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!isTauri()) return;
    let active = true;
    const load = async () => {
      try {
        const next = parseInsights(await aiCommand("insights"));
        if (active) setInsights(next);
      } catch (e) {
        if (active) setError(aiError(e, "Insights are unavailable."));
      }
    };
    void load();
    let unlisten: (() => void) | undefined;
    void listen("ai-state", () => void load()).then((fn) =>
      active ? (unlisten = fn) : fn(),
    );
    return () => {
      active = false;
      unlisten?.();
    };
  }, []);

  const notReady =
    status && (!status.hasKey || !status.models.some((m) => m.installed));
  const sentimentTotal = insights
    ? SENTIMENTS.reduce((sum, s) => sum + (insights.sentiment[s] ?? 0), 0)
    : 0;

  return (
    <section className="standalone ai-insights">
      <h1>✦ {t("AI insights")}</h1>
      <p className="ai-insights__intro">
        {t(
          "How calls are going, from the AI notes of recorded calls: mood, talk balance, recurring topics and coaching.",
        )}
      </p>
      {error && (
        <p className="ai-error" role="alert">
          {t(error)}
        </p>
      )}
      {notReady && (
        <div className="ai-callout">
          <span>
            {t(
              "Set up AI notes first: add your Claude key and download a speech model.",
            )}
          </span>
          <button className="ai-button" onClick={onSettings}>
            {t("Open AI settings")}
          </button>
        </div>
      )}
      {!insights ? (
        <p className="ai-empty">{t("Loading…")}</p>
      ) : insights.calls === 0 ? (
        <p className="ai-empty">
          {t(
            "No analysed calls yet. Record a call, then open it in Call history and choose AI notes.",
          )}
        </p>
      ) : (
        <>
          <div className="ai-stat-grid">
            <div className="ai-stat">
              <small>{t("Calls analysed")}</small>
              <strong>{insights.calls}</strong>
            </div>
            <div className="ai-stat">
              <small>{t("Average mood")}</small>
              <strong>
                {insights.averageSentiment === null
                  ? "—"
                  : `${insights.averageSentiment > 0 ? "+" : ""}${insights.averageSentiment}`}
              </strong>
              <span>{t("from −100 to +100")}</span>
            </div>
            <div className="ai-stat">
              <small>{t("Your talk share")}</small>
              <strong>
                {insights.averageTalkShare === null
                  ? "—"
                  : `${insights.averageTalkShare}%`}
              </strong>
              <span>{t("Aim for 40–60%")}</span>
            </div>
            <div className="ai-stat">
              <small>{t("Interruptions per call")}</small>
              <strong>{insights.interruptionsPerCall ?? "—"}</strong>
            </div>
            <div className="ai-stat">
              <small>{t("Action items")}</small>
              <strong>{insights.actionItems}</strong>
            </div>
          </div>

          <div className="ai-insight-columns">
            <div className="ai-card">
              <h2>{t("Mood of calls")}</h2>
              <div
                className="ai-mood-bar"
                role="img"
                aria-label={t("Mood of calls")}
              >
                {SENTIMENTS.map((s) =>
                  insights.sentiment[s] ? (
                    <span
                      key={s}
                      className={`ai-mood ai-mood--${s}`}
                      style={{
                        width: `${(insights.sentiment[s]! / sentimentTotal) * 100}%`,
                      }}
                    />
                  ) : null,
                )}
              </div>
              <ul className="ai-legend">
                {SENTIMENTS.map((s) => (
                  <li key={s}>
                    <span className={`ai-dot ai-mood--${s}`} />
                    {t(s[0]!.toUpperCase() + s.slice(1))}{" "}
                    <strong>{insights.sentiment[s] ?? 0}</strong>
                  </li>
                ))}
              </ul>
            </div>
            <div className="ai-card">
              <h2>{t("Top topics")}</h2>
              <ol className="ai-topics">
                {insights.topics.map((topic) => (
                  <li key={topic.topic}>
                    <span>{topic.topic}</span>
                    <strong>
                      {topic.calls} {t(topic.calls === 1 ? "call" : "calls")}
                    </strong>
                  </li>
                ))}
              </ol>
            </div>
          </div>

          <div className="ai-card">
            <h2>{t("Coaching from recent calls")}</h2>
            <ul className="ai-coaching">
              {insights.coaching.map((c, i) => (
                <li key={i}>
                  <strong>{c.tip}</strong>
                  {c.why && <small>{c.why}</small>}
                </li>
              ))}
            </ul>
          </div>
          <p className="ai-settings__note">
            {t("AI can make mistakes; check important details.")}
          </p>
        </>
      )}
    </section>
  );
}
