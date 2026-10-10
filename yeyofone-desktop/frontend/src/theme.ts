import { useEffect, useState } from "react";

export type ThemeChoice = "light" | "dark" | "system";
const KEY = "yeyofone.theme";

function saved(): ThemeChoice {
  try {
    const value = localStorage.getItem(KEY);
    if (value === "light" || value === "dark" || value === "system")
      return value;
  } catch {
    /* Storage can be unavailable in a WebView. */
  }
  return "system";
}

const systemDark = () =>
  window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;

export function resolveTheme(choice: ThemeChoice, prefersDark: boolean) {
  return choice === "system" ? (prefersDark ? "dark" : "light") : choice;
}

// Applies the theme to <html data-theme> and follows the system setting
// while "system" is chosen.
export function useTheme() {
  const [choice, setChoice] = useState<ThemeChoice>(saved);
  const [prefersDark, setPrefersDark] = useState(systemDark);
  useEffect(() => {
    const query = window.matchMedia?.("(prefers-color-scheme: dark)");
    const changed = () => setPrefersDark(query.matches);
    query?.addEventListener("change", changed);
    return () => query?.removeEventListener("change", changed);
  }, []);
  const theme = resolveTheme(choice, prefersDark);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  function choose(next: ThemeChoice) {
    setChoice(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* Keep the choice for this session only. */
    }
  }
  return { choice, theme, choose };
}
