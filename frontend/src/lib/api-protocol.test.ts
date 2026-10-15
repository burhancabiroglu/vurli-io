import { describe, expect, it } from "vitest";

import { errorKeyFrom, sessionIdFrom } from "./api-protocol";

describe("sessionIdFrom", () => {
  it("takes only the value of the session cookie", () => {
    expect(
      sessionIdFrom([
        "OTHER=1; Path=/",
        "VURLI_SESSION=ZTYxNGQwN2QtZjE2MS00NWQ3; Path=/; HttpOnly; SameSite=Lax; Secure",
      ]),
    ).toBe("ZTYxNGQwN2QtZjE2MS00NWQ3");
  });

  it("ignores cookies with a similar name", () => {
    expect(sessionIdFrom(["X_VURLI_SESSION=abc; Path=/", "VURLI_SESSION_OLD=def"])).toBeNull();
  });

  it("rejects values that could smuggle attributes or other cookies", () => {
    expect(sessionIdFrom(["VURLI_SESSION=abc def; Path=/"])).toBeNull();
    expect(sessionIdFrom(["VURLI_SESSION=abc,OTHER=1"])).toBeNull();
    expect(sessionIdFrom(["VURLI_SESSION=; Path=/"])).toBeNull();
    expect(sessionIdFrom(["VURLI_SESSION=" + "a".repeat(201)])).toBeNull();
  });

  it("returns null without a session cookie", () => {
    expect(sessionIdFrom([])).toBeNull();
  });
});

describe("errorKeyFrom", () => {
  it.each([
    [{ code: "invalid_credentials" }, "invalid_credentials"],
    [{ code: "weak_password", reason: "TOO_SHORT" }, "weak_password.TOO_SHORT"],
    [{ code: "invalid_token", reason: 42 }, "invalid_token"],
    [{ code: "<script>" }, "unexpected"],
    [{ code: "weak_password", reason: "../../etc" }, "weak_password"],
    [{ status: 500 }, "unexpected"],
    [null, "unexpected"],
    ["text", "unexpected"],
  ])("%j -> %s", (body, expected) => {
    expect(errorKeyFrom(body)).toBe(expected);
  });
});
