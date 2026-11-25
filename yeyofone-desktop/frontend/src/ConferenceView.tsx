import "./conference-view.css";
import { useLanguage } from "./i18n";
import { initials } from "./callGroup";

export type ConferencePerson = {
  ext: string;
  name: string;
  self: boolean;
  calling: boolean;
  // Set when this device can end that person's call (the organiser only).
  onEnd?: () => void;
};

type Props = {
  people: ConferencePerson[];
  // Extensions of the people talking right now.
  speaking: ReadonlySet<string>;
  muted: boolean;
  elapsed?: string;
};

/**
 * The conference as everyone on it sees it: the first other person (or whoever is talking) in the
 * spotlight when there are more than two people, a talking ring around whoever is speaking, and a
 * mute badge on you. The call controls stay in the call screen's own control row.
 */
export function ConferenceView({ people, speaking, muted, elapsed }: Props) {
  const { t } = useLanguage();
  const others = people.filter((p) => !p.self);
  const spotlight =
    people.length > 2
      ? (others.find((p) => speaking.has(p.ext)) ?? others[0])
      : undefined;
  const tile = (p: ConferencePerson, large: boolean) => (
    <li
      key={p.ext}
      className={`conference-view__tile${large ? " is-large" : ""}${p.calling ? " is-calling" : ""}`}
    >
      <span className="conference-view__avatar-wrap">
        <span
          className={`conference-view__avatar${speaking.has(p.ext) ? " is-speaking" : ""}`}
          aria-hidden="true"
        >
          {initials(p.name)}
        </span>
        {p.self && muted && (
          <span
            className="conference-view__muted"
            role="img"
            aria-label={t("Muted")}
          >
            ✕
          </span>
        )}
        {p.onEnd && (
          <button
            type="button"
            className="conference-view__remove"
            aria-label={`${t("End")} ${p.name}`}
            onClick={p.onEnd}
          >
            ✕
          </button>
        )}
      </span>
      <strong>{p.self ? t("You") : p.name}</strong>
      {!p.self && (
        <small role="status">
          {p.calling ? t("Calling…") : t("Active")}
          {speaking.has(p.ext) && ` · ${t("Speaking")}`}
        </small>
      )}
    </li>
  );
  return (
    <header className="conference-view" aria-label={t("Conference")}>
      <h3 className="conference-view__title">{t("Conference call")}</h3>
      <p className="conference-view__count">
        {`${people.length} ${t("people on this call")}`}
        {elapsed && <span> · {elapsed}</span>}
      </p>
      {spotlight && (
        <ul className="conference-view__spotlight">{tile(spotlight, true)}</ul>
      )}
      <ul className="conference-view__row">
        {people.filter((p) => p !== spotlight).map((p) => tile(p, false))}
      </ul>
    </header>
  );
}
