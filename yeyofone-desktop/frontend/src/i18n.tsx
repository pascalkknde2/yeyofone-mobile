import {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  useId,
  type ReactNode,
} from "react";
import { translations } from "./translations";
import "./language.css";
export type Language = "en" | "fr" | "es" | "de";
const languageNames: Record<Language, string> = {
  en: "English",
  fr: "Français",
  es: "Español",
  de: "Deutsch",
};
function initialLanguage(): Language {
  try {
    const saved = localStorage.getItem("yeyofone.language");
    if (saved && saved in languageNames) return saved as Language;
  } catch {
    /* Storage can be unavailable in a WebView. */
  }
  return "en";
}
export function translate(text: string, language: Language): string {
  if (language === "en") return text;
  const exact = translations[text]?.[language];
  if (exact) return exact;
  const extension = text.match(/^Extension (\d+)$/);
  if (extension)
    return `${language === "fr" ? "Poste" : language === "es" ? "Extensión" : "Durchwahl"} ${extension[1]}`;
  const patterns: [RegExp, Record<"fr" | "es" | "de", string>][] = [
    [
      /^(.+) joined the preview room\.$/,
      {
        fr: "$1 a rejoint la salle de démonstration.",
        es: "$1 se unió a la sala de muestra.",
        de: "$1 ist dem Vorschauraum beigetreten.",
      },
    ],
    [
      /^(.+) has the floor in this preview\.$/,
      {
        fr: "$1 a la parole dans cet aperçu.",
        es: "$1 tiene la palabra en esta vista previa.",
        de: "$1 hat in dieser Vorschau das Wort.",
      },
    ],
    [
      /^Room focus: (.+)$/,
      {
        fr: "Sujet actuel : $1",
        es: "Tema actual: $1",
        de: "Aktuelles Thema: $1",
      },
    ],
    [
      /^(\d+) sample recordings$/,
      {
        fr: "$1 enregistrements de démonstration",
        es: "$1 grabaciones de muestra",
        de: "$1 Beispielaufzeichnungen",
      },
    ],
    [
      /^Sample recordings · (.+)$/,
      {
        fr: "Enregistrements de démonstration · $1",
        es: "Grabaciones de muestra · $1",
        de: "Beispielaufzeichnungen · $1",
      },
    ],
    [
      /^Open contact (.+)$/,
      {
        fr: "Ouvrir le contact $1",
        es: "Abrir contacto $1",
        de: "Kontakt $1 öffnen",
      },
    ],
    [
      /^Focus (.+)$/,
      {
        fr: "Mettre $1 au premier plan",
        es: "Enfocar a $1",
        de: "$1 fokussieren",
      },
    ],
    [
      /^Agenda: (.+)$/,
      { fr: "Ordre du jour : $1", es: "Agenda: $1", de: "Agenda: $1" },
    ],
  ];
  for (const [pattern, labels] of patterns) {
    const match = text.match(pattern);
    if (match)
      return labels[language].replace(
        "$1",
        match[1] ? (translations[match[1]]?.[language] ?? match[1]) : "",
      );
  }
  return text;
}
const LanguageContext = createContext<{
  language: Language;
  setLanguage: (value: Language) => void;
  t: (text: string) => string;
} | null>(null);
export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(initialLanguage);
  useEffect(() => {
    document.documentElement.lang = language;
    try {
      localStorage.setItem("yeyofone.language", language);
    } catch {
      /* Choice still works for this session. */
    }
  }, [language]);
  return (
    <LanguageContext.Provider
      value={{ language, setLanguage, t: (text) => translate(text, language) }}
    >
      {children}
    </LanguageContext.Provider>
  );
}
export function useLanguage() {
  const context = useContext(LanguageContext);
  if (!context) throw new Error("LanguageProvider is required");
  return context;
}
function LanguageFlag({ language }: { language: Language }) {
  return (
    <span className={`language-flag flag-${language}`} aria-hidden="true">
      {language === "en" && (
        <svg viewBox="0 0 30 30">
          <rect width="30" height="30" fill="#294d88" />
          <path d="M0 0 30 30M30 0 0 30" stroke="white" strokeWidth="7" />
          <path d="M0 0 30 30M30 0 0 30" stroke="#da4350" strokeWidth="3" />
          <path d="M15 0v30M0 15h30" stroke="white" strokeWidth="10" />
          <path d="M15 0v30M0 15h30" stroke="#da4350" strokeWidth="6" />
        </svg>
      )}
    </span>
  );
}
const supportedLanguages: Language[] = ["en", "fr", "es", "de"];
export function LanguageSelector() {
  const { language, setLanguage, t } = useLanguage();
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const container = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const options = useRef<(HTMLButtonElement | null)[]>([]);
  const menuId = useId();
  useEffect(() => {
    if (open) options.current[activeIndex]?.focus();
  }, [open, activeIndex]);
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (
        event.target instanceof Node &&
        !container.current?.contains(event.target)
      )
        setOpen(false);
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open]);
  function showMenu() {
    setActiveIndex(supportedLanguages.indexOf(language));
    setOpen(true);
  }
  function choose(value: Language) {
    setLanguage(value);
    setOpen(false);
    trigger.current?.focus();
  }
  return (
    <div
      className="language-picker"
      ref={container}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
      }}
    >
      <button
        ref={trigger}
        className={`language-trigger ${open ? "is-open" : ""}`}
        aria-label={`${t("Language")}: ${languageNames[language]}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => (open ? setOpen(false) : showMenu())}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            showMenu();
          }
        }}
      >
        <LanguageFlag language={language} />
        <span>{languageNames[language]}</span>
        <svg
          className="language-chevron"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          aria-hidden="true"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
      {open && (
        <div
          id={menuId}
          className="language-menu"
          role="menu"
          aria-label={t("Language")}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              event.preventDefault();
              setOpen(false);
              trigger.current?.focus();
            }
            if (event.key === "ArrowDown" || event.key === "ArrowUp") {
              event.preventDefault();
              setActiveIndex(
                (index) =>
                  (index +
                    (event.key === "ArrowDown" ? 1 : -1) +
                    supportedLanguages.length) %
                  supportedLanguages.length,
              );
            }
            if (event.key === "Home" || event.key === "End") {
              event.preventDefault();
              setActiveIndex(
                event.key === "Home" ? 0 : supportedLanguages.length - 1,
              );
            }
            const typedIndex = supportedLanguages.findIndex((value) =>
              languageNames[value]
                .toLowerCase()
                .startsWith(event.key.toLowerCase()),
            );
            if (event.key.length === 1 && typedIndex >= 0) {
              event.preventDefault();
              setActiveIndex(typedIndex);
            }
          }}
        >
          {supportedLanguages.map((value, index) => (
            <button
              key={value}
              ref={(element) => {
                options.current[index] = element;
              }}
              role="menuitemradio"
              aria-checked={value === language}
              tabIndex={-1}
              className={`language-option ${value === language ? "is-selected" : ""}`}
              onFocus={() => setActiveIndex(index)}
              onClick={() => choose(value)}
            >
              <LanguageFlag language={value} />
              <span>{languageNames[value]}</span>
              {value === language && (
                <svg
                  className="language-check"
                  width="19"
                  height="19"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  aria-hidden="true"
                >
                  <path d="m5 12 4 4L19 6" />
                </svg>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
