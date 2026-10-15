import { describe, expect, it } from "vitest";

import en from "./i18n/dictionaries/en.json";
import tr from "./i18n/dictionaries/tr.json";
import { errorMessage } from "./error-message";

describe("errorMessage", () => {
  const errors = { weak_password: "general", "weak_password.TOO_SHORT": "specific" };

  it("prefers the most specific message", () => {
    expect(errorMessage(errors, "weak_password.TOO_SHORT", "x")).toBe("specific");
  });

  it("falls back to the general code, then to the generic message", () => {
    expect(errorMessage(errors, "weak_password.UNKNOWN", "x")).toBe("general");
    expect(errorMessage(errors, "something_else", "x")).toBe("x");
  });
});

describe("dictionaries", () => {
  function keys(value: object, prefix = ""): string[] {
    return Object.entries(value).flatMap(([key, child]) =>
      typeof child === "object" && child !== null ? keys(child, `${prefix}${key}.`) : [`${prefix}${key}`],
    );
  }

  it("Turkish and English have exactly the same keys", () => {
    expect(keys(tr).sort()).toEqual(keys(en).sort());
  });
});
