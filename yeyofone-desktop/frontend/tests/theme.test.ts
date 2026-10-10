import { test, expect } from "bun:test";
import { resolveTheme } from "../src/theme";

test("system follows the operating system; explicit choices win", () => {
  expect(resolveTheme("system", true)).toBe("dark");
  expect(resolveTheme("system", false)).toBe("light");
  expect(resolveTheme("dark", false)).toBe("dark");
  expect(resolveTheme("light", true)).toBe("light");
});
