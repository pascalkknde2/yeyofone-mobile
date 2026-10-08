import { forwardRef, useImperativeHandle, useRef } from "react";
import { useLanguage } from "./i18n";

type Props = {
  value: string;
  onChange: (value: string) => void;
  invalid?: boolean;
  errorId?: string;
};

export const DialInput = forwardRef<HTMLInputElement, Props>(function DialInput(
  { value, onChange, invalid, errorId },
  ref,
) {
  const { t } = useLanguage();
  const innerRef = useRef<HTMLInputElement>(null);
  useImperativeHandle(ref, () => innerRef.current!, []);

  return (
    <div className="dial-input">
      <label className="dial-input__label" htmlFor="dialer-destination">
        {t("Destination")}
      </label>
      <input
        ref={innerRef}
        id="dialer-destination"
        className="dial-input__field"
        type="text"
        inputMode="tel"
        autoComplete="off"
        autoCorrect="off"
        autoCapitalize="off"
        spellCheck={false}
        maxLength={512}
        value={value}
        placeholder={t("Phone number")}
        onChange={(e) => onChange(sanitize(e.target.value))}
        onPaste={(e) => {
          e.preventDefault();
          const pasted = e.clipboardData.getData("text");
          const node = innerRef.current;
          const start = node?.selectionStart ?? value.length;
          const end = node?.selectionEnd ?? start;
          const text = sanitize(pasted);
          onChange(
            (value.slice(0, start) + text + value.slice(end)).slice(0, 512),
          );
          requestAnimationFrame(() =>
            node?.setSelectionRange(start + text.length, start + text.length),
          );
        }}
        aria-invalid={invalid || undefined}
        aria-describedby={errorId}
      />
    </div>
  );
});

const ALLOWED = /[^0-9a-zA-Z+*#@:.\-_]/g;

function sanitize(value: string): string {
  return value.replace(ALLOWED, "").slice(0, 512);
}
