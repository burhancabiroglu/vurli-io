import { describe, expect, it } from "vitest";

import { negotiateLocale } from "./locales";

describe("negotiateLocale", () => {
  it.each([
    ["tr-TR,tr;q=0.9,en;q=0.8", "tr"],
    ["en-US,en;q=0.9", "en"],
    ["de-DE,tr;q=0.7", "tr"],
    ["de-DE,fr;q=0.8", "en"],
    ["en;q=0.5,tr;q=0.8", "tr"],
    ["TR", "tr"],
    ["tr;q=0", "en"],
    ["", "en"],
    [null, "en"],
    ["*", "en"],
    ["tr;q=abc,en", "en"],
  ])("%s -> %s", (header, expected) => {
    expect(negotiateLocale(header)).toBe(expected);
  });

  it("keeps header order for equal weights", () => {
    expect(negotiateLocale("en,tr")).toBe("en");
    expect(negotiateLocale("tr,en")).toBe("tr");
  });
});
