import { useEffect, type KeyboardEvent, type RefObject } from "react";

// Arrow keys, Home and End move between a menu's items, wrapping around;
// Tab closes the menu. Attach to the element with role="menu".
export function moveInMenu(e: KeyboardEvent<HTMLElement>, close: () => void) {
  const items = [
    ...e.currentTarget.querySelectorAll<HTMLButtonElement>(
      '[role="menuitem"]:not(:disabled)',
    ),
  ];
  const at = items.indexOf(document.activeElement as HTMLButtonElement);
  if (e.key === "Tab") return close();
  const next =
    e.key === "ArrowDown"
      ? (at + 1) % items.length
      : e.key === "ArrowUp"
        ? (at - 1 + items.length) % items.length
        : e.key === "Home"
          ? 0
          : e.key === "End"
            ? items.length - 1
            : -1;
  if (next < 0) return;
  e.preventDefault();
  items[next]?.focus();
}

// WebKit doesn't focus clicked buttons, so menus close on outside clicks, not on blur.
export function useCloseOnOutsideClick(
  open: boolean,
  box: RefObject<HTMLElement | null>,
  close: () => void,
) {
  useEffect(() => {
    if (!open) return;
    const outside = (e: PointerEvent) => {
      if (!box.current?.contains(e.target as Node)) close();
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open]);
}
